import main.pisharp.*

def call(serviceName) {
    def imageRegistry = "556438351385.dkr.ecr.ap-southeast-1.amazonaws.com"
    def awsCredentialId = "devops-aws-credential"
    def namespaceRegistry = "practical-devops"
    def region = "ap-southeast-1"

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
    global.processTestResults()

    // Step 3: Scan the vulnerabilities of each dependencies
    trivy.trivyScanVulnerabilities()

    // Step 4: Build docker images with the new tag
    global.buildDockerImages(imageRegistry: imageRegistry, namespaceRegistry: namespaceRegistry, serviceName: serviceName)
    
    // Step 5: Scan the vulnerabilities of the new image
    trivy.trivyScanDockerImages(imageBuildTag)
    
    // Step 6: Push image to image registry
    global.pushDockerImages(imageRegistry: imageRegistry, namespaceRegistry: namespaceRegistry, serviceName: serviceName, region: region, awsCredentialId: awsCredentialId)
}