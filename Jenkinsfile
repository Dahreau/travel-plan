def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify"
}

def sonarService(svc) {
    withSonarQubeEnv('sonarqube') {
        sh "cd backend/${svc} && ./mvnw -B sonar:sonar -Dsonar.projectKey=travel-plan-${svc}"
    }
}

pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    def target = env.CHANGE_TARGET ?: 'main'
                    sh "git fetch --no-tags origin ${target}:refs/remotes/origin/${target} || true"
                }
            }
        }

        stage('Detect changed services') {
            steps {
                script {
                    def baseRef = env.CHANGE_TARGET ? "origin/${env.CHANGE_TARGET}" : 'HEAD~1'
                    def changedFiles = sh(script: "git diff --name-only ${baseRef} HEAD", returnStdout: true).trim()
                    def allServices = sh(script: 'ls backend', returnStdout: true).trim().split('\n') as List
                    def jenkinsfileChanged = changedFiles.contains('Jenkinsfile')

                    env.CHANGED_SERVICES = jenkinsfileChanged
                        ? allServices.join(',')
                        : allServices.findAll { svc -> changedFiles.contains("backend/${svc}/") }.join(',')
                    echo jenkinsfileChanged
                        ? "Jenkinsfile modifié : tous les services seront testés (${env.CHANGED_SERVICES})"
                        : (env.CHANGED_SERVICES ? "Services modifiés : ${env.CHANGED_SERVICES}" : 'Aucun service backend modifié.')

                    env.INFRA_CHANGED = (changedFiles.contains('docker-compose.yml') || changedFiles.contains('infra/') || jenkinsfileChanged) ? 'true' : 'false'
                    echo "Infra modifiée : ${env.INFRA_CHANGED}"
                }
            }
        }

        stage('Validate infra') {
            when { expression { env.INFRA_CHANGED == 'true' } }
            steps {
                sh '''
                    set -e
                    if [ -d infra ]; then
                        for f in $(find infra -name "*.sh"); do
                            echo "Shell : $f"
                            bash -n "$f"
                        done
                    fi
                '''
            }
        }

        stage('Build & Test') {
            when { expression { env.CHANGED_SERVICES } }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.tokenize(',')
                    parallel(services.collectEntries { svc ->
                        [svc, { buildService(svc) }]
                    })
                }
            }
        }

        stage('SonarQube Analysis') {
            when { expression { env.CHANGED_SERVICES } }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.tokenize(',')
                    parallel(services.collectEntries { svc ->
                        [svc, { sonarService(svc) }]
                    })
                }
            }
        }

        stage('Quality Gate') {
            when { expression { env.CHANGED_SERVICES } }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Deploy') {
            when { branch 'main' }
            steps {
                echo 'TODO: build images Docker + ansible-playbook deploy.yml (étape à venir, pas encore construite)'
            }
        }
    }
}
