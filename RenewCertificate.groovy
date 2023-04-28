//#############################################
// Пайп для обновления сертификата Let's Encrypt
// по мотивам ./doc/RenewCert.txt
//#############################################

def devopsConfig

pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
    }

    stages {
        stage ("Read configs") {
                    steps {
                        script {
                            try {
                                println "\033[34mSTART READ DEVOPS CONFIG STAGE\033[0m"
                                devopsConfig = readYaml(file: './devops-config/jenkins/devops-config.yml')
                                println "\033[32mREAD DEVOPS CONFIG STAGE SUCCESS\033[0m"
                            }
                            catch (e) {
                                println "\033[31mREAD DEVOPS CONFIG STAGE ERROR: ${e}\033[0m"
                                sh 'exit 1'
                            }
                        }
                    }
                }



        stage( 'Проверка подключения по SSH' ) {
            steps {
                script {
                    echo "Проверка подключения по SSH"
                    println "\033[34mПроверка подключения по SSH\033[0m"
                    //https://www.jenkins.io/doc/pipeline/steps/ssh-agent/

                    sshagent(credentials: [${devopsConfig.server.SSHCredentials}]) {
                        sh 'ssh -o StrictHostKeyChecking=no white@192.168.40.180'
                        sh 'ssh white@192.168.40.180 whoami'
                        sh 'ssh white@192.168.40.180 ls'
                        sh 'ssh white@192.168.40.180 pwd'
                    }
                }

                }
        }


        stage ('Stage 2') {
            steps {
                echo "STAGE 2"
                
                }
            }

        }
    }

            
