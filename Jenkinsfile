
pipeline {
   agent any
    options { skipDefaultCheckout() }
	environment { 
        branch = 'master' 
        credentialsId = 'dd'     // credential of jenkins 
        scmUrl = 'https://github.com/Dileep1314/cobertura'
    } 
    stages {
        stage ('Build Stage') {
            agent none
            steps {
                script {
                    stage ('Checkout') {
                        echo 'THIS IS FOR CLONEING'
                        git branch: branch, credentialsId: credentialsId, url: scmUrl
                    }
                    stage ('BUILD') {
					    sh ''' 
						gradle --version
				        gradle clean build --refresh-dependencies tar -x test --info
						
						'''
                        
                    }
				}
			}
		}
    }

  }
