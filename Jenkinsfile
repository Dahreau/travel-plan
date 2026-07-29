def SERVICES = ['api-gateway', 'auth-service', 'user-service', 'travel-service', 'payment-service']

def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify -DforkCount=1 -DreuseForks=false"
}

def sonarService(svc) {
    withSonarQubeEnv('sonarqube') {
        sh "cd backend/${svc} && ./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=travel-plan-${svc} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
    }
}

def buildFrontend() {
    sh '''
        cd frontend
        npm install
        npm run build
        npx ng test --watch=false --coverage --coverage-reporters=lcov
    '''
}

def sonarFrontend() {
    withSonarQubeEnv('sonarqube') {
        sh "cd frontend && npx --yes @sonar/scan -Dsonar.projectKey=travel-plan-frontend -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
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
                    SERVICES.each { svc -> buildService(svc) }
                    buildFrontend()
                }
            }
        }

        stage('SonarQube Analysis & Quality Gate') {
            steps {
                script {
                    SERVICES.each { svc -> sonarService(svc) }
                    sonarFrontend()
                }
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    rm -rf /deploy-workspace/*
                    cp -a "$WORKSPACE"/. /deploy-workspace/
                    cd ansible
                    ansible-galaxy collection install -r requirements.yml
                    ansible-playbook -i inventory.ini playbooks/site.yml -e project_dir="$HOST_REPO_PATH/infra/ci/deploy-workspace"
                '''
            }
        }
    }
}
