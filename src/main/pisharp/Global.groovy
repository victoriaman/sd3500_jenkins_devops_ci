package main.pisharp

def buildDockerImages(args) {
    def imageRegistry = args.imageRegistry               // e.g., 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com
    def namespaceRegistry = args.namespaceRegistry       // e.g., my-repo-group
    def serviceName = args.serviceName                   // e.g., my-service

    def imageTag = "${imageRegistry}/${namespaceRegistry}/${serviceName}:${BRANCH_NAME}-${BUILD_NUMBER}"

    stage("Build Docker Image") {
        sh "docker build --force-rm --no-cache -t ${imageTag} -f ./Dockerfile ."
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

def deployToEKS(args) {
    def serviceName = args.serviceName
    def awsCredentialId = args.awsCredentialId
    def eksClusterName = args.eksClusterName
    def region = args.region

    stage ("Deploy To K8S Using GitOps Concept") {
        script {
            // Determine the target directory based on the branch
            def envDir = (env.BRANCH_NAME == 'main') ? 'prod' : 'nonprod'
            def deploymentYamlFile = "jenkins/${envDir}/${serviceName}.yaml"

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

                    kubectl get ns devops-ns >/dev/null 2>&1 || kubectl create ns devops-ns

                    kubectl config set-context --current --namespace devops-ns

                    kubectl apply -f ${deploymentYamlFile}
                """
            }
        }
    }
}

def detectServiceNameFromCommitedYAMLFile(args) {
    def gitCredentialId = args.gitCredentialId
    def gitopsRepo = args.gitopsRepo
    def detectedService = "unknown"

    stage("Detect serviceName from committed YAML file") {
        dir('repo') {
            git url: "${gitopsRepo}",
                branch: "${env.BRANCH_NAME}",
                credentialsId: "${gitCredentialId}"

            script {
                def changedFiles = sh(
                    script: "git diff --name-only HEAD~1 HEAD",
                    returnStdout: true
                ).trim().split("\n")

                for (file in changedFiles) {
                    if (file ==~ /.*backend\.yaml$/) {
                        detectedService = "backend"
                        break
                    }
                    if (file ==~ /.*frontend\.yaml$/) {
                        detectedService = "frontend"
                        break
                    }
                }

                echo "Detected service: ${detectedService}"
            }
        }
    }

    return detectedService
}