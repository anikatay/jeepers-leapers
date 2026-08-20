pipeline {
    agent any
    tools {
        jdk 'jdk21'
        maven 'Maven3'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                sh 'mvn -B -X clean package -DskipTests'
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
