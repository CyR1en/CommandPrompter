pipeline {
    stages {
        agent any
        stage('build') {
            steps {
                sh 'gradlew clean build'
            }
        }
    }
}
