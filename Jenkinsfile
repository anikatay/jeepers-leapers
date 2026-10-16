pipeline {
    agent any
    tools{
        maven 'Maven3'
    }
    environment {
        DB_PORT_J = credentials("DB_PORT_J")
        DB_PASSWORD = credentials("DB_PASSWORD")
        JWT_SHARED_SECRET = credentials("JWT_SHARED_SECRET")
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Environment check'){
            steps {
                sh '''
                    set -eu
                    java -version
                    mvn -version
                    node --version
                    npm --version
                    docker --version
                    docker compose version
                '''
            }
        }
        stage('Backend Tests') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Frontend Tests') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --watch=false'
                    sh 'npm run build -- --configuration production'
                }
            }
        }
        stage('Start Application Stack') {
            steps {
                sh '''
                    set -eu
                    docker compose config --quiet
                    docker compose up -d --build
                '''
            }
        }
        stage('Smoke Test') {
            steps {
                sh '''
                    set -eu

                    wait_for() {
                        name="$1"
                        shift

                        for attempt in $(seq 1 60); do
                            if "$@"; then
                                return 0
                            fi
                            sleep 2
                        done

                        echo "$name did not become ready" >&2
                        return 1
                    }

                    postgres_ready() {
                        docker compose exec -T db \
                            pg_isready -U paysprint -d paysprint >/dev/null 2>&1
                    }

                    frontend_ready() {
                        curl -fsS http://localhost:8090/ >/dev/null 2>&1
                    }

                    backend_ready() {
                        status="$(curl -sS -o /dev/null -w '%{http_code}' \
                            http://localhost:8095/api/instruments)"

                        case "$status" in
                            2*|3*|4*) return 0 ;;
                            *) return 1 ;;
                        esac
                    }

                    wait_for "PostgreSQL" postgres_ready
                    wait_for "Frontend" frontend_ready
                    wait_for "Backend" backend_ready
                '''
            }
        }
    }
    post {
        always {
            script {
                if (getContext(hudson.FilePath) != null) {
                    def logsExit = sh(
                        script: 'docker compose logs --no-color',
                        returnStatus: true
                    )

                    if (logsExit != 0) {
                        echo "docker compose logs failed with exit code ${logsExit}"
                    }

                    def cleanupExit = sh(
                        script: 'docker compose down -v --remove-orphans',
                        returnStatus: true
                    )

                    if (cleanupExit != 0) {
                        currentBuild.result = 'FAILURE'
                        echo "docker compose cleanup failed with exit code ${cleanupExit}"
                    }
                } else {
                    echo 'Skipping Docker cleanup: no Jenkins workspace is available.'
                }
            }
        }
    }
}