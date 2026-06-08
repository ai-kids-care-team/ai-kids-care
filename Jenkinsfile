pipeline {
  agent any

  stages {
    stage('Checkout Code') {
      steps {
        checkout scm
      }
    }

    stage('List Files') {
      steps {
        sh 'ls -al'
      }
    }

    stage('Test') {
      // Requires Docker on the Jenkins agent (Testcontainers spins a PostgreSQL container).
      // Failing tests block the deployment stage below.
      steps {
        dir('backend') {
          sh './gradlew test'
        }
      }
      post {
        always {
          junit allowEmptyResults: true,
                testResults: 'backend/build/test-results/**/*.xml'
        }
      }
    }

    stage('Docker Compose Up') {
      steps {
        sh '''
        docker compose down --remove-orphans --volumes --rmi local || true
        docker compose up -d --build
        '''
      }
    }
  }
}