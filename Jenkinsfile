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