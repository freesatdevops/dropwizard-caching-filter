/*
 * The author disclaims copyright to this source code. In place of
 * a legal notice, here is a blessing:
 *    May you do good and not evil.
 *    May you find forgiveness for yourself and forgive others.
 *    May you share freely, never taking more than you give.
 */
package online.adinor.cachingserver.cache;

import com.google.common.cache.Cache;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import online.adinor.cachingserver.cache.config.Options;
import online.adinor.cachingserver.cache.storage.HttpResponse;
import online.adinor.cachingserver.cache.storage.StatefulCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Lebedenko (andrey.lebedenko@gmail.com)
 */
public class CachingResponseFilter implements ContainerResponseFilter {

  private static final Logger logger = LoggerFactory.getLogger(CachingResponseFilter.class);

  private final Timer timer = new Timer();
  private final Cache<String, StatefulCacheEntry<HttpResponse>> cache;

  @Context private volatile ResourceInfo resourceInfo;

  public CachingResponseFilter(final Cache<String, StatefulCacheEntry<HttpResponse>> cache) {
    this.cache = cache;
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    synchronized (this) {
      final Optional<ResponseCachedByFilter> annotation =
          Optional.ofNullable(resourceInfo)
              .map(ResourceInfo::getResourceMethod)
              .map(m -> m.getDeclaredAnnotation(ResponseCachedByFilter.class));
      annotation.ifPresent(
          a -> {
            final Optional<Role> myRole =
                Optional.ofNullable((Role) requestContext.getProperty(Role.OPTION_NAME));
            myRole.ifPresent(
                role -> {
                  if (role.equals(Role.Producer)) {
                    final String key = (String) requestContext.getProperty(Options.KEY);
                    final StatefulCacheEntry<HttpResponse> entry =
                        (StatefulCacheEntry<HttpResponse>)
                            requestContext.getProperty(Options.CACHE_ENTRY);
                    logger.debug("Response entry: {}", entry);
                    entry.setData(HttpResponse.from(responseContext));
                    entry.setReady();
                    entry.unblock();

                    // enforce per-entry TTL
                    // workaround for ignored feature request
                    // https://github.com/google/guava/issues/1203
                    timer.schedule(
                        new TimerTask() {
                          @Override
                          public void run() {
                            cache.invalidate(key);
                          }
                        },
                        a.value());
                  }
                });
          });
    }
  }
}
