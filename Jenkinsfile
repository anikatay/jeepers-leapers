pipeline {
    agent any
    tools{
        maven 'Maven3'
    }
    environment {
        DB_PORT_J = "${DB_PORT_J}"
        DB_PASSWORD = "${DB_PASSWORD}"
        DB_HOST = "${DB_HOST}"
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Environment check'){
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'docker --version'
                sh 'docker-compose --version'
            }
        }
        stage('Unit Tests') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Smoke Test') {
            steps {
                sh '''
                    pg_isready -h 10.14.141.36 -p 8100 -U paysprint
                '''
                sh 'curl -f http://localhost:8090 || (echo "Frontend down!!!" && exit 1)'
            }
            post {
                always {
                    sh 'docker-compose logs'
                    sh 'docker-compose down -v'
                }
            }
        }
    }
    post {
        always {
            chuckNorris()
        }
    }
}