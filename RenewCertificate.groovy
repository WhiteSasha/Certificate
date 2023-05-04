//#############################################
// Пайп для обновления сертификата Let's Encrypt
// по мотивам ./doc/RenewCert.txt
//#############################################

def devopsConfig
import groovy.io.*
pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
    }

//Параметризированая джоба. https://devopscube.com/declarative-pipeline-parameters/ Параметры в UI джобы появятся после первого запуска
    parameters{
        choice(
            choices: ['nexus.alex-white.ru', 'jenkins.alex-white.ru', 'seafiles.alex-white.ru', 'alex-white.ru'], 
            name: 'domain'
        )
        booleanParam(
                name: 'DebugMode',
                defaultValue: false,
                description: 'Debug mode'
        )
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
                    //Зачистка WorkSpace!
//                    cleanWs()
                echo "Choosed domain : ${params.domain}"
                    sh 'echo "======Проверка переменных====="'
//                    sh 'echo ${devopsConfig.server.RemoteHost}'
                }
            }
        }

        stage( 'Test SSH copy and run script' ) {
            steps {
                script {
                    sh "echo '==RemoteHost==: ${devopsConfig.server.RemoteHost}'"
                    println "\033[34mTest SSH copy and run script\033[0m"
                    //https://www.jenkins.io/doc/pipeline/steps/ssh-agent/
                    sshagent(credentials: ["${devopsConfig.server.SSHCredentials}"]) {
                        //SUDO https://www.digitalocean.com/community/tutorials/how-to-edit-the-sudoers-file-ru
                        //https://sergeymukhin.com/blog/povrezhdennyy-etc-sudoers-oshibka-v-sintaksise
                        sh """  ssh ${devopsConfig.server.RemoteHost} whoami
                                echo '++==RemoteHost==++: ${devopsConfig.server.RemoteHost}'
                                scp ./files/${devopsConfig.file.TestShName}  ${devopsConfig.server.RemoteHost}:/tmp/${devopsConfig.file.TestShName}
                                ssh ${devopsConfig.server.RemoteHost} chmod +x /tmp/${devopsConfig.file.TestShName}
                                ssh ${devopsConfig.server.RemoteHost} sudo /tmp/${devopsConfig.file.TestShName} ${params.domain}
                                ssh ${devopsConfig.server.RemoteHost} rm /tmp/${devopsConfig.file.TestShName}
                        """
                    }
                }

                }
        }



        stage ('Nginx config') {

            steps {
                echo "STAGE 2"
                script {
                    println "\033[34mNginx config\033[0m"
                    dh = new File('.')
                    dh.eachFile {
                        println(it)
                    }
//                    sshagent(credentials: ["${devopsConfig.server.SSHCredentials}"]) {
//                        sh """  echo '++==RemoteHost==++: ${devopsConfig.server.RemoteHost}'
//                        ssh ${devopsConfig.server.RemoteHost} ls /etc/nginx/sites-enabled/
//                        ssh ${devopsConfig.server.RemoteHost} ls /etc/nginx/sites-available/
//                        """
//                    }
                }
            }
        }

        stage('Debug mode') {
            when {
                expression { params.DebugMode }
            }
            steps {
                script {
                    println "\033[34mDebug mode\033[0m"
                }
            }
        }




        }
    }

            
