import main.pisharp.*

void call(Map pipelineParams) {

    def awsCredentialId = "devops-aws-credential"
    def region = "ap-southeast-1"
    def eksClusterName = "devops-eks"

    def trivy = new Trivy()
    def global = new Global()

    pipeline {

        agent any

        options {
            disableConcurrentBuilds()
            disableResume()
            timeout(time: 1, unit: 'HOURS')
        }
        
        stages {
            stage ('Load Pipeline') {
                when {
                    allOf {
                        // Condition Check
                        anyOf{
                            // Branch Event: Nornal Flow
                            anyOf {
                                branch 'main'
                            }
                            // Manual Run: Only if checked.
                            allOf{
                                triggeredBy 'UserIdCause'
                            }
                        }
                    }
                }
                steps {
                    script {
                        // Step 1: Detect service name from committed YAML file
                        def serviceName = global.detectServiceNameFromCommitedYAMLFile(
                            gitCredentialId: 'devops-github-credential',
                            gitopsRepo: 'https://github.com/victoriaman/sd3500_pisharped_gitops.git'
                        )

                        // End pipeline with error when can't detect out serviceName
                        if (serviceName == "unknown") {
                            error("❌ Could not detect a valid service from committed YAML files. Ending pipeline.")
                        }

                        // Step 2: Deploy to EKS
                        global.deployToEKS(
                            serviceName: serviceName,
                            awsCredentialId: awsCredentialId,
                            eksClusterName: eksClusterName,
                            region: region
                        )
                    }
                }
            }
        }

        post {
            cleanup {
                cleanWs()
            }
        }
    }
}