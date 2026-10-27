pipeline {
    agent any
    tools{
        maven 'Maven3'
    }
    environment {
        DB_PORT_J = credentials("DB_PORT_J")
        DB_PASSWORD = credentials("DB_PASSWORD")
        JWT_SHARED_SECRET = credentials("JWT_SHARED_SECRET")
        Market_port = credentials("Market_port")
        Kafka_port = credentials("Kafka_port")
        App_port = credentials("App_port")
        Frontend_port = credentials("Frontend_port")
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
                    docker-compose version
                '''
            }
        }
        stage('Start Application Stack') {
            steps {
                sh '''
                    set -eu
                    docker-compose config --quiet
                    docker-compose up -d --build
                '''
            }
        }
        stage('Populate Analytics') {
            steps {
                sh 'bash analytics/run_etl.sh'
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
                        docker-compose exec -T db \
                            pg_isready -U paysprint -d paysprint >/dev/null 2>&1
                    }

                    frontend_ready() {
                        curl -fsS "http://localhost:${frontend_host_port}/" >/dev/null 2>&1
                    }

                    # Logs in as a seeded user and builds the analysis context for one of
                    # their trades, covering auth and both databases; does not call OpenAI
                    analysis_context_ready() {
                        token="$(curl -fsS -X POST -H 'Content-Type: application/json' \
                            -d '{"email":"bob@example.com","password":"abcd1234"}' \
                            "http://localhost:${app_host_port}/api/auth/login" | sed -n 's/.*"token":"\\([^"]*\\)".*/\\1/p')"
                        [ -n "$token" ] && curl -fsS -o /dev/null -H "Authorization: Bearer $token" \
                            "http://localhost:${app_host_port}/api/trades/c3000000-0000-0000-0000-000000000007/analysis/context"
                    }

                    backend_ready() {
                        status="$(curl -sS -o /dev/null -w '%{http_code}' \
                            "http://localhost:${app_host_port}/api/instruments")"

                        case "$status" in
                            2*|3*|4*) return 0 ;;
                            *) return 1 ;;
                        esac
                    }

                    frontend_host_port="${Frontend_port%%:*}"
                    app_host_port="${App_port%%:*}"

                    wait_for "PostgreSQL" postgres_ready
                    wait_for "Frontend" frontend_ready
                    wait_for "Backend" backend_ready
                    wait_for "Trade analysis context" analysis_context_ready
                '''
            }
        }
    }
    post {
        always {
            sh '''
                set -eu
                docker-compose down --remove-orphans
            '''
        }
    }
}
