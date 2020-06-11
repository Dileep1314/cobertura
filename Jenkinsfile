pipeline {
    agent {
        node {
            label 'DockerIO-2'
        }
    }
    options { 
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '5', daysToKeepStr: '30'))
    }
	environment {
			   
			GITUSERNAME      = 'smdagent'
            GITAPIKEY        = credentials('ML-GIT-API')
           
  
		
				
                
            }
    stages {
	    
		stage ('Code Checkout and PUBLISHING THE LAST CHANGES') {
		
		    steps {
            sh ''' 
				rm -rf ${REPO}
			    git -c http.sslverify=false clone -b ${BRANCH} https://${GITUSERNAME}:${GITAPIKEY}@gbsgit.in.dst.ibm.com/Admin-Metlife/GSSP-SMD/JAVA-MS/$REPO.git
				cd ${REPO}			
                        
			'''           
                        
            lastChanges format: 'LINE', matchWordsThreshold: '0.25', matching: 'NONE', matchingMaxComparisons: '1000', showFiles: true, since: 'LAST_SUCCESSFUL_BUILD', specificBuild: '', specificRevision: '', synchronisedScroll: true,vcsDir: "$REPO/"
            }
		}
		stage ('GRADLE BUILD') {
		   steps {
 
            sh ''' 
				         
				echo $JAVA_HOME
				export JAVA_HOME=/opt/jdk1.8.0_91
				export PATH=$PATH:/opt/jdk1.8.0_91/bin
				java -version
			    cd ${REPO}
			    export GRADLE_HOME=/opt/gradle-2.9
				export PATH=${GRADLE_HOME}/bin:${PATH}
			    gradle --version
				
				gradle clean build --refresh-dependencies tar -x test --info
				
				ls -l 
				
                            
            '''
			}
                        
        }
    
    }
}
