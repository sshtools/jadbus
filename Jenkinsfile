pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.0' 
		jdk 'Graal JDK 21' 
	}

	stages {
		stage ('Jadbus Installers') {
			parallel {
			    /*
                 * Deploy helper library to maven centrals
                 */
                stage ('Deploy Helper Library To Maven Repo') {
                    agent {
                        label 'posix'
                    }
                    steps {
                        configFileProvider([
                                configFile(
                                    fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
                                    replaceTokens: true,
                                    targetLocation: 'jadaptive.build.properties',
                                    variable: 'BUILD_PROPERTIES'
                                )
                            ]) {
                            withMaven(
                                globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
                            ) {
                                sh 'mvn -U ' +
                                   '-DperformRelease=true ' +
                                   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
                                   'clean deploy'
                            }
                        }
                    }
                }
                
				/*
				 * Linux AMD64 Installers and Packages
				 */
				stage ('Linux AMD64 Jadbus Installers') {
					agent {
						label 'install4j && linux && x86_64'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
					 				replaceTokens: true,
					 				targetLocation: 'jadaptive.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
					 		) {
					 		  	sh 'mvn -U -Dbuild.mediaTypes=unixInstaller,unixArchive,linuxRPM,linuxDeb ' +
					 		  	   '-Dbuild.buildIds=26,37,116 ' +
					 		  	   '-P cross-platform,native-image,installers ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-DbuildInstaller=true clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'installer/target/media/*', name: 'linux-jadbus-amd64'
			        			
			        			/* Stash updates.xml */
			        			dir('installer/target/media') {
									stash includes: 'updates.xml', name: 'linux-jadbus-amd64-updates-xml'
			        			}
					 		}
        				}
					}
				}
                
				/*
				 * Linux AARCH64 Installers and Packages
				 */
				stage ('Linux AARCH64 Jadbus Installers') {
					agent {
						label 'install4j && linux && aarch64'
					}
					steps {
						configFileProvider([
					 			configFile(
					 				fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
					 				replaceTokens: true,
					 				targetLocation: 'jadaptive.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
								jdk: 'Graal JDK 21',
					 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
					 		) {
					 		  	sh 'mvn -U -Dbuild.mediaTypes=unixInstaller,unixArchive,linuxRPM,linuxDeb ' +
					 		  	   '-P cross-platform,native-image,installers ' +
					 		  	   '-Dbuild.buildIds=133,136,139 ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-DbuildInstaller=true clean package'
					 		  	
					 		  	/* Stash installers */
			        			stash includes: 'installer/target/media/*', name: 'linux-jadbus-aarch64'
			        			
			        			/* Stash updates.xml */
			        			dir('installer/target/media') {
									stash includes: 'updates.xml', name: 'linux-jadbus-aarch64-updates-xml'
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
					 				fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
					 				replaceTokens: true,
					 				targetLocation: 'jadaptive.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
					 		) {
					 		  	bat 'mvn -U -Dinstall4j.verbose=true -Dbuild.mediaTypes=windows,windowsArchive ' +
					 		  	    '-Dinstall4j.exe.suffix=.exe ' +
					 		  	    '"-Dbuild.projectProperties=%BUILD_PROPERTIES%" ' +
					 		  	   '-P cross-platform,native-image,installers ' +
				 		  	        '-DbuildInstaller=true clean package'
					 		  	
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
					 				fileId: 'b60f3998-d8fd-434b-b3c8-ed52aa52bc2e',  
					 				replaceTokens: true,
					 				targetLocation: 'jadaptive.build.properties',
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
					 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528'
					 		) {
					 			// -Dinstall4j.disableNotarization=true 
					 		  	sh 'mvn -U -Dbuild.mediaTypes=macos,macosFolder,macosFolderArchive ' +
					 		  	   '-Dbuild.projectProperties=$BUILD_PROPERTIES ' +
					 		  	   '-P cross-platform,native-image,installers ' +
					 		  	   '-DbuildInstaller=true clean package'
					 		  	
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
		 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528',
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
	 		  	unstash 'linux-amd64-jadbus'
	 		  	unstash 'linux-aarch64-jadbus'
	 		  	unstash 'windows-jadbus'
	 		  	unstash 'macos-jadbus'
	 		  	
				/* Unstash updates.xml */
	 		  	dir('installer/target/media-linux-amd64') {
	 		  		unstash 'linux-jadbus-amd64-updates-xml'
    			}
	 		  	dir('installer/target/media-linux-aarch64') {
	 		  		unstash 'linux-jadbus-aarch64-updates-xml'
    			}
	 		  	dir('installer/target/media-windows') {
	 		  		unstash 'windows-jadbus-updates-xml'
    			}
	 		  	dir('installer/target/media-macos') {
	 		  		unstash 'macos-jadbus-updates-xml'
    			}
    			
    			/* Merge all updates.xml into one */
    			withMaven(
		 			globalMavenSettingsConfig: '14324b85-c597-44e8-a575-61f925dba528',
		 		) {
					sh 'mvn -P merge-installers -pl installer com.sshtools:updatesxmlmerger-maven-plugin:merge'
		 		}
		 		
    			/* Upload all installers */
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