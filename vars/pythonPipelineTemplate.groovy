import main.pisharp.*

def call(serviceName) {
    def imageRegistry = "registry.hub.docker.com"
    def credentialDockerId = "dockerhub-demo-token"
    def namespaceRegistry = "LoanNguyenT5-01"
    def gitopsRepo = 'https://github.com/LoanNguyenT5-01/pisharped-gitops.git'
    def gitopsBranch = 'main'
    def gitCredential = 'github'
    def imageBuildTag = "${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"
    def sonarHostURL = 'http://localhost:9000/'
    def sonarAuthToken = 'sqa_1723b9c52ae3a914378ff348b49245e1152ea4d9'
    def trivy = new Trivy()
    def global = new Global()
    def sonar = new Sonar()

    stage('Prepare Package') {
        script {
            if (isUnix()) {
                sh "mkdir -p .ci"
            } else {
                bat "mkdir .ci"
            }
            writeFile file: isUnix() ? '.ci/html.tpl' : '.ci\\html.tpl', text: libraryResource('trivy/html.tpl')
        }
    }


    // Step 1: Scan all the application to check if we can put any sensitive information in the source code or not
    trivy.trivyScanSecrets()

    // Step 2: Run the unit test to check function code and show the test result
    global.runPythonUnitTest()
    global.processTestResults()

    // Step 3: Scan the vulnerabilities of each python dependency
    trivy.trivyScanVulnerabilities()

    // Step 4: Scan static code to check the Code smell, Bug, Vulnerability
    // sonar.sonarQubeAnalysis(serviceName, sonarHostURL, sonarAuthToken)

    // Step 5: Install python dependencies
    global.pythonRunInstallDependencies()

    // // Step 6: Build docker images with the new tag
    // global.buildDockerImages(imageRegistry: imageRegistry, credentialDockerId: credentialDockerId, namespaceRegistry: namespaceRegistry, serviceName: serviceName)

    // // Step 7: Scan the vulnerabilities of the new image
    // trivy.trivyScanDockerImages(imageBuildTag)

    // // Step 8: Push image to image registry and update the new image tag in the gitops repository
    // // and then ArgoCD can sync the new deployment
    // global.pushDockerImages(imageRegistry: imageRegistry, credentialDockerId: credentialDockerId, namespaceRegistry: namespaceRegistry, serviceName: serviceName)
    // global.deployToK8S(gitopsRepo: gitopsRepo, gitopsBranch: gitopsBranch, gitCredential: gitCredential, serviceName: serviceName)
}
