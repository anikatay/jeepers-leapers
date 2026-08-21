pipeline {
     agent any
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
            }
        }
        stage('Build Image') {
            steps {
                sh 'ls -la /usr/lib/jvm/ | grep java-21'
                sh 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.x86_64'
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
