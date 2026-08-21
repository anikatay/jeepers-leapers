pipeline {
     agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('environment check'){
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'docker --version'
            }
        }
        stage('Build Image') {
            steps {
                sh 'mvn -B clean package -DskipTests'
                sh 'docker build -t team-skeleton:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm team-skeleton:latest'
            }
        }
    }
    post {
        always {
            chuckNorris()
        }
    }
}
