import main.pisharp.*

def call(serviceName) {
    def imageRegistry = "556438351385.dkr.ecr.ap-southeast-1.amazonaws.com"
    def awsCredentialId = "devops-aws-credential"
    def namespaceRegistry = "practical-devops"
    def region = "ap-southeast-1"
    def eksClusterName = "devops-eks"

    def gitopsRepo = 'https://github.com/victoriaman/sd3500_pisharped_gitops.git'
    def gitopsBranch = 'main'

    def gitCredential = 'github'
    def imageBuildTag = "${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"

    def trivy = new Trivy()
    def global = new Global()

    stage ('Prepare Package') {
        script {
            sh "mkdir -p .ci"
            writeFile file: '.ci/html.tpl', text: libraryResource('trivy/html.tpl')
        }
    }
    // Step 1: Scan all the application to check if we can put any sensitive informations in the source code or not
    trivy.trivyScanSecrets()

    // Step 2: Run the unit test to check function code and show the test result
    global.runPythonUnitTest()
    global.processTestResults()

    // Step 3: Scan the vulnerabilities of each python dependencies
    trivy.trivyScanVulnerabilities()

    // Step 5: Install python dependencies
    global.pythonRunInstallDependencies()

    // Step 6: Build docker images with the new tag
    global.buildDockerImages(imageRegistry: imageRegistry, namespaceRegistry: namespaceRegistry, serviceName: serviceName)
    
    // Step 7: Scan the vulnerabilities of the new image
    trivy.trivyScanDockerImages(imageBuildTag)
    
    // Step 8: Push image to image registry and update the new image tag in the gitops repository
    // and then Argocd can sync the new deployment
    global.pushDockerImages(imageRegistry: imageRegistry, namespaceRegistry: namespaceRegistry, serviceName: serviceName, region: region, awsCredentialId: awsCredentialId)
    global.deployToEKS(gitopsRepo: gitopsRepo, gitopsBranch: gitopsBranch, gitCredential: gitCredential, serviceName: serviceName, region: region, awsCredentialId: awsCredentialId, eksClusterName: eksClusterName)
}