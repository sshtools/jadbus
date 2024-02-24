pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 17' 
	}

	stages {
		stage ('Jadbus Installers') {
			parallel {
				/*
				 * Linux Installers and Packages
				 */
				stage ('Linux Jadbus Installers') {
					agent {
						label 'install4j && linux'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 		  	sh 'mvn -U -Dbuild.mediaTypes=unixInstaller,unixArchive,linuxRPM,linuxDeb ' +
					 		  	   '-P cross-platform,installers ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-DbuildInstaller=true clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'installer/target/media/*', name: 'linux-jadbus'
			        			
			        			/* Stash updates.xml */
			        			dir('installer/target/media') {
									stash includes: 'updates.xml', name: 'linux-jadbus-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * Windows installers
				 */
				stage ('Windows Jadbus Installers') {
					agent {
						label 'install4j && windows'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 		  	bat 'mvn -U -Dinstall4j.verbose=true -Dbuild.mediaTypes=windows,windowsArchive ' +
					 		  	    '"-Dbuild.projectProperties=%BUILD_PROPERTIES%" ' +
					 		  	   '-P cross-platform,installers ' +
				 		  	        'clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'installer/target/media/*', name: 'windows-jadbus'
			        			
			        			/* Stash updates.xml */
			        			dir('installer/target/media') {
									stash includes: 'updates.xml', name: 'windows-jadbus-updates-xml'
			        			}
					 		}
        				}
					}
				}
				
				/*
				 * MacOS installers
				 */
				stage ('MacOS Jadbus Installers') {
					agent {
						label 'install4j && macos'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'bb62be43-6246-4ab5-9d7a-e1f35e056d69',  
					 				replaceTokens: true,
					 				targetLocation: 'hypersocket.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde'
					 		) {
					 			// -Dinstall4j.disableNotarization=true 
					 		  	sh 'mvn -U -Dbuild.mediaTypes=macos,macosFolder,macosFolderArchive ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-P cross-platform,installers ' +
					 		  	   'clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'installer/target/media/*', name: 'macos-jadbus'
			        			
			        			/* Stash updates.xml */
			        			dir('installer/target/media') {
									stash includes: 'updates.xml', name: 'macos-jadbus-updates-xml'
			        			}
					 		}
        				}
					}
				}
			}
		}
		
		stage ('Deploy') {
			agent {
				label 'linux'
			}
			steps {
    			
    			/* Clean */
    			withMaven(
		 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde',
		 		) {
					sh 'mvn clean'
		 		}
			
				script {
					/* Create full version number from Maven POM version and the
					   build number */
					def pom = readMavenPom file: 'pom.xml'
					pom_version_array = pom.version.split('\\.')
					suffix_array = pom_version_array[2].split('-')
					env.FULL_VERSION = pom_version_array[0] + '.' + pom_version_array[1] + "." + suffix_array[0] + "-${BUILD_NUMBER}"
					echo 'Full Maven Version ' + env.FULL_VERSION
				}
				
				/* Unstash installers */
	 		  	unstash 'linux-jadbus'
	 		  	unstash 'windows-jadbus'
	 		  	unstash 'macos-jadbus'
	 		  	
				/* Unstash updates.xml */
	 		  	dir('installer/target/media-linux') {
	 		  		unstash 'linux-jadbus-updates-xml'
    			}
	 		  	dir('installer/target/media-windows') {
	 		  		unstash 'windows-jadbus-updates-xml'
    			}
	 		  	dir('installer/target/media-macos') {
	 		  		unstash 'macos-jadbus-updates-xml'
    			}
    			
    			/* Merge all updates.xml into one */
    			withMaven(
		 			globalMavenSettingsConfig: '4bc608a8-6e52-4765-bd72-4763f45bfbde',
		 		) {
					sh 'mvn -P merge-installers -pl installer com.sshtools:updatesxmlmerger-maven-plugin:merge'
		 		}
		 		
    			/* Upload all installers */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'sshtools-public/Jadbus/' + env.FULL_VERSION, 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'installer/target/media/*', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'JADAPTIVE Buckets', 
		 			userMetadata: []
		 		)
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'sshtools-public/jadbus/' + env.FULL_VERSION, 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'installer/target/media/*', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'JADAPTIVE Buckets', 
		 			userMetadata: []
		 		)
		 		
    			/* Copy the merged updates.xml to the nightly directory so updates can be seen
    			by anyone on this channel */
		 		s3Upload(
		 			consoleLogLevel: 'INFO', 
		 			dontSetBuildResultOnFailure: false, 
		 			dontWaitForConcurrentBuildCompletion: false, 
		 			entries: [[
		 				bucket: 'sshtools-public/jadbus/continuous', 
		 				noUploadOnFailure: true, 
		 				selectedRegion: 'eu-west-1', 
		 				sourceFile: 'installer/target/media/updates.xml', 
		 				storageClass: 'STANDARD', 
		 				useServerSideEncryption: false]], 
		 			pluginFailureResultConstraint: 'FAILURE', 
		 			profileName: 'JADAPTIVE Buckets', 
		 			userMetadata: []
		 		)
			}					
		}		
	}
}