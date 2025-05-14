#!/usr/bin/env groovy
void call(Map pipelineParams) {

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
                                branch 'develop'
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
                        ciPipelineTemplate("backend")
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