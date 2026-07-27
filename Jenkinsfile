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
                    env.CHANGED_SERVICES = allServices.findAll { svc -> changedFiles.contains("backend/${svc}/") }.join(',')
                    echo env.CHANGED_SERVICES ? "Services modifiés : ${env.CHANGED_SERVICES}" : 'Aucun service backend modifié.'

                    env.INFRA_CHANGED = (changedFiles.contains('docker-compose.yml') || changedFiles.contains('infra/')) ? 'true' : 'false'
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
                sh '''
                    set -e
                    cat > .env.ci <<EOF
POSTGRES_ADMIN_PASSWORD=ci_dummy
AUTH_DB_PASSWORD=ci_dummy
USER_DB_PASSWORD=ci_dummy
PAYMENT_DB_PASSWORD=ci_dummy
NEO4J_PASSWORD=ci_dummy
VAULT_DEV_ROOT_TOKEN=ci_dummy
POSTGRES_HOST_PORT=15432
NEO4J_HTTP_HOST_PORT=17474
NEO4J_BOLT_HOST_PORT=17687
VAULT_HOST_PORT=18200
ZIPKIN_HOST_PORT=19411
EOF
                    trap 'docker compose --env-file .env.ci -p travel-plan-app-citest down -v; rm -f .env.ci' EXIT
                    docker compose --env-file .env.ci -p travel-plan-app-citest up -d --wait --wait-timeout 180 || {
                        echo "--- up --wait failed, dumping logs before teardown ---"
                        docker compose --env-file .env.ci -p travel-plan-app-citest logs
                        exit 1
                    }
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
