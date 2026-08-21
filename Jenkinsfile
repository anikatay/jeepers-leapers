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
        stage('JAVA HOME UPDATE'){
            steps {
                sh 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.x86_64'
                sh 'export PATH=$JAVA_HOME/bin:$PATH'
                sh 'echo $JAVA_HOME'
                sh 'echo $MAVEN_HOME'
            }
        }
        stage('Build Image') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
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
