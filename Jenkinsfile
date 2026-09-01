pipeline {
    agent any
    tools{
        maven 'Maven3'
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
        stage('Compose validate and build') {
            steps {
                sh 'docker-compose config'
                sh 'docker-compose build'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker-compose up -d --build'
                sh 'sleep 5'
                sh 'docker-compose ps'
                sh '''
                    docker-compose exec -T db pg_isready -U paysprint
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