pipeline {
    agent any

    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }

    environment {
        DOCKER_IMAGE = 'ghandgevikas/hoststar:latest'
        SCANNER_HOME = tool 'sonar-scanner'
        CLUSTER_NAME = 'EKS-CLOUD'
        REGION = 'ap-south-1'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout from Git') {
            steps {
                git branch: 'dev', url: 'https://github.com/Vikasghandge/HOST-STAR-CLONE.git'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh '''${SCANNER_HOME}/bin/sonar-scanner \
                    -Dsonar.projectName=Hotstar \
                    -Dsonar.projectKey=Hotstar'''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-token'
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                dir('Hotstar-Clone-main') {
                    sh 'npm install'
                }
            }
        }

       // /*
        // Optional: OWASP Dependency Check
    //    stage('OWASP FS SCAN') {
      //      steps {
      //          dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'DP-Check'
//                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
  //          }
//        }
  //      */

        stage('Docker Scout FS') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                        sh 'docker-scout quickview fs://.'
                        sh 'docker-scout cves fs://.'
                    }
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                dir('Hotstar-Clone-main') {
                    script {
                        withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                            sh "docker build -t ${DOCKER_IMAGE} ."
                            sh "docker push ${DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }

        stage('Docker Scout Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                        sh "docker-scout quickview ${DOCKER_IMAGE}"
                        sh "docker-scout cves ${DOCKER_IMAGE}"
                        sh "docker-scout recommendations ${DOCKER_IMAGE}"
                    }
                }
            }
        }

        stage('Deploy Docker Locally') {
            steps {
               sh "docker run -d --name hotstar -p 3000:3000 ${DOCKER_IMAGE}"
            }
        }

        