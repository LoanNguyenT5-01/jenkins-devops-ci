package main.pisharp

def sonarQubeAnalysis(projectKey, sonarHostURL, sonarAuthToken) {
    def SONAR_HOST_URL = sonarHostURL
    def SONAR_TOKEN = sonarAuthToken
    def scannerHome = tool 'SonarQubeScanner'
    stage('Analysis Static Code By SonarQube') {
        script {
            withSonarQubeEnv('SonarQube') {
                
                if (isUnix()) {
                    // Print the token for debugging purposes (caution: avoid in production)
                    echo "SONAR_TOKEN: ${SONAR_TOKEN}"
                    
                    // Run SonarQube Scanner inside a Docker container on Linux
                    sh """
                    ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${projectKey}-${env.BRANCH_NAME} \
                    -Dsonar.sources=. \
                    -Dsonar.exclusions=**/tests/**,**/.venv/** \
                    -Dsonar.host.url=${SONAR_HOST_URL} \
                    -Dsonar.token=${SONAR_TOKEN} \
                    -Dsonar.python.coverage.reportPaths=results/coverage.xml
                    """
                } else {
                    // Print the token for debugging purposes (caution: avoid in production)
                    echo "SONAR_TOKEN: ${SONAR_TOKEN}"
                    
                    // Run SonarQube Scanner inside a Docker container on Windows
                    bat """
                    call ${scannerHome}\\bin\\sonar-scanner ^
                    -Dsonar.projectKey=${projectKey}-${env.BRANCH_NAME} ^
                    -Dsonar.sources=. ^
                    -Dsonar.exclusions=**/tests/**,**/.venv/** ^
                    -Dsonar.host.url=${SONAR_HOST_URL} ^
                    -Dsonar.token=${SONAR_TOKEN} ^
                    -Dsonar.python.coverage.reportPaths=results/coverage.xml
                    """
                }
            }
        }
    }
    stage('Quality Gate Check') {
        timeout(time: 2, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}
