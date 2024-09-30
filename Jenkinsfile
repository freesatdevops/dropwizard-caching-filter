pipeline {
    agent {
        label 'swarm'
    }
    environment {
        MAVEN_OPTS = "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
    }
    tools {
        jdk "JDK17"
    }
    stages {
        stage('Build & Test') {
            steps {
                script {
                    try {
                        sh "mvn -B -U clean install pmd:pmd checkstyle:checkstyle"

                        step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
                        step([$class: 'ArtifactArchiver', artifacts: '**/target/site/'])
                        step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
                        recordIssues(tools: [pmdParser()])

                    } catch (e) {
                        echo "Maven build or analysis failed: ${e}"
                        throw e
                    }

                }
            }
        }

        stage('Deploy to Nexus') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'master') {
                        sh "mvn deploy"
                    }
                }
            }
        }
    }
}
