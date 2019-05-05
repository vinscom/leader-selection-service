pipeline {
  agent any
  stages {
    stage('package') {
      steps {
         withSonarQubeEnv('SonarCloud') {
            withMaven(maven: 'M3') {
               sh "mvn clean package sonar:sonar -Dsonar.projectKey=vinscom_leader-selection-service -Dsonar.organization=vinscom-github -Dsonar.branch.name=${GIT_BRANCH}"
            }
         }
      }
    }
    stage("Quality Gate") {
      steps {
         junit '**/target/surefire-reports/TEST-*.xml'
      }
    }
  }
}
