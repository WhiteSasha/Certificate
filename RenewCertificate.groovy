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
                                echo "Got RemoteHost as ${devopsConfig.server.RemoteHost}"
                                println "\033[32mREAD DEVOPS CONFIG STAGE SUCCESS\033[0m"
                            }
                            catch (e) {
                                println "\033[31mREAD DEVOPS CONFIG STAGE ERROR: ${e}\033[0m"
                                sh 'exit 1'
                            }
                        }
                    }
                }

        stage( 'Проверка переменных' ) {
            steps {
                println "\033[34mПроверка переменных\033[0m"
                script {
                    sh 'printenv'
//                    sh 'echo ${devopsConfig.server.RemoteHost}'
                }
            }
        }

        stage( 'Проверка подключения по SSH' ) {
            steps {
                script {
                    echo "Проверка подключения по SSH"
                    println "\033[34mПроверка подключения по SSH\033[0m"
                    //https://www.jenkins.io/doc/pipeline/steps/ssh-agent/
                    sh '''
                    echo "---BEFORE SSH---"
                    echo "===================="
                       '''
                    sshagent(credentials: ["${devopsConfig.server.SSHCredentials}"]) {
                    sh '''
                    echo "---SSH---"
                    scp README.md  root@192.168.40.109:/tmp/README.md
                    ssh 192.168.40.109 ls /tmp & whoami
                    '''

//                        sh 'ssh -o StrictHostKeyChecking=no white@192.168.40.180'
//                        sh 'echo "---SSH---"'
//                        sh 'ls /tmp'
//                        sh 'pwd'
//                        sh 'whoami'

//                        sh 'scp files/test.sh white@192.168.40.180/tmp/test.sh'
//                        sh 'scp README.md white@192.168.40.180/tmp/README.md'

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

            
