package main.pisharp

def pythonRunInstallDependencies() {
    stage ("Run Install Dependencies") {
        script {
            if (isUnix()) {
                sh "mkdir -p results"
                sh 'docker run --rm -v $(pwd):/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install"'
            } else {
                bat 'mkdir results'
                bat 'docker run --rm -v %CD%:/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install"'
            }
        }
    }
}

def runPythonUnitTest() {
    stage ("Run Unit Tests") {
        script {
            if (isUnix()) {
                sh "mkdir -p results"
                sh 'docker run --rm -v $(pwd):/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install && poetry run pytest --cov=app --cov-report=xml:results/coverage.xml --junitxml=results/test-results.xml"'
            } else {
                bat 'mkdir results'
                bat 'docker run --rm -v %CD%:/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install && poetry run pytest --cov=app --cov-report=xml:results/coverage.xml --junitxml=results/test-results.xml"'
            }
        }
    }
}

def processTestResults() {
    stage ('Process Test Results') {
        xunit thresholds: [
            failed(unstableThreshold: '0'),
            skipped()
        ], tools: [
            JUnit(deleteOutputFiles: true, failIfNotNew: false, pattern: 'results/test-results.xml', skipNoTestFiles: true, stopProcessingIfError: true)
        ]
        cobertura autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: "results/coverage.xml", conditionalCoverageTargets: '70, 0, 0', failUnhealthy: true, failUnstable: true, maxNumberOfBuilds: 0, onlyStable: false, sourceEncoding: 'ASCII', zoomCoverageChart: false
    }
}

def buildDockerImages(args) {
    def imageRegistry = args.imageRegistry
    def credentialDockerId = args.credentialDockerId
    def namespaceRegistry = args.namespaceRegistry
    def serviceName = args.serviceName
    stage ("Build Images by Docker") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialDockerId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            docker.withRegistry("https://${imageRegistry}", credentialDockerId) {
                if (isUnix()) {
                    docker.build("${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}", "--force-rm --no-cache -f Dockerfile .")
                } else {
                    bat "docker build --force-rm --no-cache -f Dockerfile . -t ${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"
                }
            }
        }
    }
}

def pushDockerImages(args) {
    def imageRegistry = args.imageRegistry
    def credentialDockerId = args.credentialDockerId
    def namespaceRegistry = args.namespaceRegistry
    def serviceName = args.serviceName
    stage ("Push Docker Images") {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialDockerId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            docker.withRegistry("https://${imageRegistry}", credentialDockerId) {
                if (isUnix()) {
                    sh "docker push ${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"
                } else {
                    bat "docker push ${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"
                }
            }
        }
    }
}

def deployToK8S(args) {
    def gitopsRepo = args.gitopsRepo
    def gitCredential = args.gitCredential
    def serviceName = args.serviceName
    def gitopsBranch = args.gitopsBranch
    def newTag = "${BRANCH_NAME}-${BUILD_NUMBER}"
    stage ("Deploy To K8S Using GitOps Concept") {
        script {
            if (isUnix()) {
                dir('gitops') {
                    git credentialsId: "${gitCredential}", url: "${gitopsRepo}", branch: "${gitopsBranch}"
                    def targetDir = (env.BRANCH_NAME == 'main') ? 'prod' : 'nonprod'
                    def deploymentYamlFile = "${targetDir}/${serviceName}/deployment.yaml"

                    sh """
                        sed -i "s|\\(image: [^:]*:\\)[^ ]*|\\1${newTag}|g" ${deploymentYamlFile}
                    """
                    withCredentials([gitUsernamePassword(credentialsId: "${gitCredential}")]) {
                        sh """
                        git config user.email "jenkins-ci@example.com"
                        git config user.name "Jenkins"
                        git add ${deploymentYamlFile}
                        git commit -m "Update image to ${serviceName}"
                        git push origin ${gitopsBranch}
                        """
                    }
                }
            } else {
                dir('gitops') {
                    bat """
                    git clone https://${gitCredential}@${gitopsRepo} -b ${gitopsBranch} .
                    set targetDir=nonprod
                    if "%BRANCH_NAME%"=="main" set targetDir=prod
                    set deploymentYamlFile=%targetDir%\\%serviceName%\\deployment.yaml
                    powershell -Command "((Get-Content %deploymentYamlFile% -Raw) -replace '(^\\s*image: [^:]*:)[^ ]*', '\\1${newTag}') | Set-Content %deploymentYamlFile%"
                    git config user.email "jenkins-ci@example.com"
                    git config user.name "Jenkins"
                    git add %deploymentYamlFile%
                    git commit -m "Update image to ${serviceName}"
                    git push origin %gitopsBranch%
                    """
                }
            }
        }
    }
}
