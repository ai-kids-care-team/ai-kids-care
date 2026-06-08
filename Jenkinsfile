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

    // Demo / CI reset (ADR-0012): wipes data volumes and rebuilds from initdb seeds.
    // This is intentional for demo environments where a clean state is required each time.
    // For production deployments, use docker-compose.prod.yml (no --volumes, no seed wipe).
    //
    // Production deploy command (run manually on prod host, DO NOT add to this CI stage):
    //   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
    stage('Demo Deploy (CI Reset)') {
      steps {
        sh '''
        docker compose down --remove-orphans --volumes --rmi local || true
        docker compose up -d --build
        '''
      }
    }
  }
}