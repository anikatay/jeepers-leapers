pipeline {
    agent any
    tools {
        jdk 'jdk21'
        maven 'Maven-3.9.16'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                withMaven(maven: 'Maven-3.9.16', jdk: 'jdk21') {
                    sh 'mvn -version'
                    sh 'echo $JAVA_HOME'
                    sh 'echo $MAVEN_HOME'
                    sh 'mvn -B -X clean package -DskipTests'
                    sh 'docker build -t team-skeleton:latest .'
                }
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
