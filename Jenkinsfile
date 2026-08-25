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
        stage('Demo Test'){
            steps{
                script{
                    def rand = Math.random()
                    if(rand < 0.5){
                        error("Demo test failed")
                    }else{
                        echo "Demo test passed"
                    }
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