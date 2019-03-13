node {

    def server
    def buildInfo
//    def rtMaven
    
   def mvnHome
   stage('Preparation') { 
	      git 'https://github.com/TimGundmann/sonos-java.git'
//      	env.JAVA_HOME="${tool 'jdk 9'}"
//      	env.PATH="/var/lib/jenkins/.local/bin:${env.PATH}"
//      	mvnHome = tool 'maven 3.3.9'
   }
   stage("prepare build") {
	    server = Artifactory.server 'gundmannArtifactory'
	
	    rtMaven = Artifactory.newGradleBuild()
	    rtMaven.tool = 'Gradle-2.4'
	    rtMaven.deployer releaseRepo: 'libs-release-local', snapshotRepo: 'libs-snapshot-local', server: server
	    rtMaven.resolver releaseRepo: 'libs-release', snapshotRepo: 'libs-snapshot', server: server
	
	    buildInfo = Artifactory.newBuildInfo()
   }
   stage('Build') {
	   	 rtGradle.run buildFile: 'build.gradle', tasks: 'clean artifactoryPublish'
   }
   
   stage ('Publish build info') {
        server.publishBuildInfo buildInfo
   }
}