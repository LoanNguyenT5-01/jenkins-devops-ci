package main.pisharp

def pythonRunInstallDependencies() {
    stage ("Run Install Dependencies") {
        script {
            if (isUnix()) {
                // Ensure the directory exists without failing if it already exists
                sh "mkdir -p results"
                sh 'docker run --rm -v $(pwd):/app python:3.9-slim bash -c "pip install poetry && cd /app && poetry config virtualenvs.in-project true && poetry install"'
            } else {
                // Check if the directory exists before creating it
                bat 'if not exist results mkdir results'
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
    def ecrUri = "275731741847.dkr.ecr.ap-southeast-1.amazonaws.com/practical-devops"
    def awsCredentialsId = 'aws-cli'
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
    // stage("Push Image to ECR") {
        
    //     withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: awsCredentialsId]]) {
    //         script {
    //             if (isUnix()) {
    //                 // Commands for Unix/Linux
    //                 sh """
    //                     aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin ${ecrUri}
    //                     docker tag ${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER} ${ecrUri}:${BRANCH_NAME}-${BUILD_NUMBER}
    //                     docker push ${ecrUri}:${BRANCH_NAME}-${BUILD_NUMBER}
    //                 """
    //             } else {
    //                 // Commands for Windows
    //                 bat """
    //                     aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin ${ecrUri}
    //                     docker tag ${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER} ${ecrUri}:${BRANCH_NAME}-${BUILD_NUMBER}
    //                     docker push ${ecrUri}:${BRANCH_NAME}-${BUILD_NUMBER}
    //                 """
    //             }
    //         }
    //     }
    
    // }
}

def deployToK8S(args) {
    def gitopsRepo = args.gitopsRepo
    def gitCredential = args.gitCredential
    def serviceName = args.serviceName
    def gitopsBranch = args.gitopsBranch
    def newTag = "${BRANCH_NAME}-${BUILD_NUMBER}"
    println("New Tag: ${newTag}")

    stage ("Deploy To K8S Using GitOps Concept test") {
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
                    git credentialsId: "${gitCredential}", url: "${gitopsRepo}", branch: "${gitopsBranch}"
                    withCredentials([gitUsernamePassword(credentialsId: "${gitCredential}")]) {
                        bat """
                            git clone ${gitopsRepo} -b ${gitopsBranch} .
                            set targetDir=nonprod
                            if "%BRANCH_NAME%"=="main" set targetDir=prod

                            set deploymentYamlFile=%targetDir%\\${serviceName}\\deployment.yaml

                            powershell -Command "
                            \$content = Get-Content '%deploymentYamlFile%' -Raw;
                            \$newContent = \$content -replace '(^\\s*image:\\s*loannguyent5/orders-service:)[^\\s]*', '\`\$1\${newTag}';

                            Set-Content '%deploymentYamlFile%' -Value \$newContent
                            "

                            git config user.email "jenkins-ci@example.com"
                            git config user.name "Jenkins"
                            git add %deploymentYamlFile%
                            git commit -m "Update image for ${serviceName}"
                            git push --set-upstream origin "${gitopsBranch}"
                        """
                    }
                }
            }
        }
    }
}

