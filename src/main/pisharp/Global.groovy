package main.pisharp

def buildDockerImages(args) {
    def imageRegistry = args.imageRegistry               // e.g., 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com
    def namespaceRegistry = args.namespaceRegistry       // e.g., my-repo-group
    def serviceName = args.serviceName                   // e.g., my-service

    def imageTag = "${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"

    stage("Build Docker Image") {
        sh "docker build --force-rm --no-cache -t ${imageTag} -f Dockerfile ."
    }
}

def pushDockerImages(args) {
    def imageRegistry = args.imageRegistry
    def namespaceRegistry = args.namespaceRegistry
    def serviceName = args.serviceName
    def region = args.region
    def awsCredentialId = args.awsCredentialId   // Jenkins AWS credentials ID

    def imageTag = "${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"

    stage("Authenticate and Push Docker Image to ECR") {
        withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding',
             credentialsId: awsCredentialId,
             accessKeyVariable: 'AWS_ACCESS_KEY_ID',
             secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
        ]) {
            sh """
                aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                aws configure set default.region ${region}

                aws ecr get-login-password --region ${region} | \
                docker login --username AWS --password-stdin ${imageRegistry}

                docker push ${imageTag}
            """
        }
    }
}

def deployToEKS(args){
    def gitopsRepo = args.gitopsRepo
    def gitopsBranch = args.gitopsBranch
    def gitCredential = args.gitCredential
    def serviceName = args.serviceName
    def newTag = "${BRANCH_NAME}-${BUILD_NUMBER}"

    def awsCredentialId = args.awsCredentialId
    def eksClusterName = args.eksClusterName
    def region = args.region

    stage ("Deploy To K8S Using GitOps Concept") {
        script {
            // Clone the GitOps repository
            dir('gitops') {
                git credentialsId: "${gitCredential}", url: "${gitopsRepo}", branch: "${gitopsBranch}"

                // Determine the target directory based on the branch
                def targetDir = (env.BRANCH_NAME == 'main') ? 'prod' : 'nonprod'
                def deploymentYamlFile = "${targetDir}/${serviceName}/deployment.yaml"

                // Update the image tag in the deployment YAML file
                sh """
                    sed -i "s|\\(image: [^:]*:\\)[^ ]*|\\1${newTag}|g" ${deploymentYamlFile}
                """
                withCredentials([gitUsernamePassword(credentialsId: "${gitCredential}")]) {
                    // Commit and push the changes
                    sh """
                    git config user.email "jenkins-ci@example.com"
                    git config user.name "Jenkins"
                    git add ${deploymentYamlFile}
                    git commit -m "Update image to ${serviceName}"
                    git push origin ${gitopsBranch}
                    """
                }

                // Authenticate and apply to EKS
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: awsCredentialId,
                     accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                     secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                ]) {
                    sh """
                        aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                        aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                        aws configure set default.region ${region}

                        aws eks update-kubeconfig --name ${eksClusterName} --region ${region}
                        kubectl apply -f ${deploymentYamlFile}
                    """
                }
            }
        }
    }
}