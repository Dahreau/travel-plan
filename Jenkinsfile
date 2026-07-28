def SERVICES = ['api-gateway', 'auth-service', 'user-service', 'travel-service']
// payment-service rejoint cette liste avec sa propre PR d'implementation

def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify -DforkCount=1 -DreuseForks=false"
}

def sonarService(svc) {
    withSonarQubeEnv('sonarqube') {
        sh "cd backend/${svc} && ./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=travel-plan-${svc} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
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
            }
        }

        stage('Validate infra') {
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
            steps {
                script {
                    parallel(SERVICES.collectEntries { svc ->
                        [svc, { buildService(svc) }]
                    })
                }
            }
        }

        stage('SonarQube Analysis & Quality Gate') {
            steps {
                script {
                    parallel(SERVICES.collectEntries { svc ->
                        [svc, { sonarService(svc) }]
                    })
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
