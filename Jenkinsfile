def buildService(svc) {
    docker.image('maven:3.9-eclipse-temurin-21').inside('-v maven_repo:/root/.m2') {
        sh "cd backend/${svc} && ./mvnw -B clean verify"
    }
}

def sonarService(svc) {
    withSonarQubeEnv('sonarqube') {
        docker.image('maven:3.9-eclipse-temurin-21').inside('-v maven_repo:/root/.m2') {
            sh "cd backend/${svc} && ./mvnw -B sonar:sonar -Dsonar.projectKey=travel-plan-${svc}"
        }
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
                    env.CHANGED_SERVICES = allServices.findAll { svc -> changedFiles.contains("backend/${svc}/") }.join(',')
                    echo env.CHANGED_SERVICES ? "Services modifiés : ${env.CHANGED_SERVICES}" : 'Aucun service backend modifié.'
                }
            }
        }

        stage('Build & Test') {
            when { expression { env.CHANGED_SERVICES } }
            parallel {
                stage('api-gateway') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('api-gateway') } }
                    steps { script { buildService('api-gateway') } }
                }
                stage('auth-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('auth-service') } }
                    steps { script { buildService('auth-service') } }
                }
                stage('payment-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('payment-service') } }
                    steps { script { buildService('payment-service') } }
                }
                stage('travel-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('travel-service') } }
                    steps { script { buildService('travel-service') } }
                }
                stage('user-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('user-service') } }
                    steps { script { buildService('user-service') } }
                }
            }
        }

        stage('SonarQube Analysis') {
            when { expression { env.CHANGED_SERVICES } }
            parallel {
                stage('api-gateway') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('api-gateway') } }
                    steps { script { sonarService('api-gateway') } }
                }
                stage('auth-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('auth-service') } }
                    steps { script { sonarService('auth-service') } }
                }
                stage('payment-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('payment-service') } }
                    steps { script { sonarService('payment-service') } }
                }
                stage('travel-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('travel-service') } }
                    steps { script { sonarService('travel-service') } }
                }
                stage('user-service') {
                    when { expression { env.CHANGED_SERVICES.tokenize(',').contains('user-service') } }
                    steps { script { sonarService('user-service') } }
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
