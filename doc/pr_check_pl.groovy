def devopsConfig

pipeline {

    agent {
//        label 'clearAgent'
        label 'masterLin'
    }
    options {
        timestamps()
        ansiColor('xterm')
    }
    parameters {
        booleanParam(name: 'clearWorkSpace', defaultValue: true, description: 'Очистка WorkSpace')
        string(name: 'branchToBuild', defaultValue: 'develop', description: 'Ветка для сборки в ручном режиме')
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
        stage ("Prepare the environment") {
            steps {
                script {
                    try {
                        println "\033[34mSTART PREPARE ENVIRONMENT STAGE\033[0m"
                        if (env.pullRequest) {
                            pullRequest = new groovy.json.JsonSlurper().parseText("${pullRequest}")
                        }
                        jobname = JOB_NAME.replace('/', ' >> ')
                        println "\033[32mPREPARE ENVIRONMENT STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mPREPARE ENVIRONMENT STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }
        stage ("Push 'INPROGRESS' status to BitBucket") {
            when {expression
                    {env.pullRequest}
            }
            steps {
                script {
                    try {
                        setBuildStatus("${devopsConfig.tuz.pass}", "${devopsConfig.git.httpsurl}", "${pullRequest.fromRef.repository.project.key}", "${jobname} #${BUILD_ID}", pullRequest.author.user.displayName, BUILD_URL, pullRequest.fromRef.latestCommit, 'INPROGRESS')
                        println "\033[32mPush 'INPROGRESS' status to BitBucket SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mPush 'INPROGRESS' status to BitBucket ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }
        stage('Checkout and prepare project') {
            steps {
                script {
                    try {
                        println "\033[34mSTART CHECKOUT AND PREPARE PROJECT STAGE\033[0m"
                        if (env.pullRequest) {
                            getCheckoutRef("${devopsConfig.tuz.ssh}", devopsConfig.git.sshurl, devopsConfig.git.project, devopsConfig.git.repository_pl, "${pullRequest.fromRef.displayId}:${pullRequest.fromRef.displayId} ${pullRequest.toRef.displayId}:${pullRequest.toRef.displayId}")
                            sh "git merge ${pullRequest.toRef.displayId}"
                            sonarBranch = "${pullRequest.fromRef.displayId}"
                        } else {
                            getCheckoutRef("${devopsConfig.tuz.ssh}", devopsConfig.git.sshurl, devopsConfig.git.project, devopsConfig.git.repository_pl, "${params.branchToBuild}:${params.branchToBuild}")
                            sonarBranch = "${params.branchToBuild}"
                        }
                        println "\033[32mCHECKOUT AND PREPARE PROJECT STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mCHECKOUT AND PREPARE PROJECT STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage ("NodeJS install") {
            steps {
                nodejs(configId: "${devopsConfig.npm.configId}", nodeJSInstallationName: "${devopsConfig.npm.nodeJSInstallationName}") {
                    script {
                        try {
                            println "\033[34mSTART NodeJS INSTALL STAGE\033[0m"
                            sh "npm -X ci --legacy-peer-deps"
                            println "\033[32mNodeJS INSTALL STAGE SUCCESS\033[0m"
                        }
                        catch (e) {
                            println "\033[31mNodeJS INSTALL STAGE ERROR: ${e}\033[0m"
                            sh 'exit 1'
                        }
                        finally {
                            archiveArtifacts artifacts: "**/*.log,*.log", allowEmptyArchive: true
                        }
                    }
                }
            }
        }

//        stage ("NodeJS lint") {
//            steps {
//                nodejs(configId: "${devopsConfig.npm.configId}", nodeJSInstallationName: "${devopsConfig.npm.nodeJSInstallationName}") {
//                    script {
//                        try {
//                            println "\033[34mSTART NodeJS LINT STAGE\033[0m"
//                            sh "npm run lint --loglevel verbose"
//                            println "\033[32mNodeJS LINT STAGE SUCCESS\033[0m"
//                        }
//                        catch (e) {
//                            println "\033[31mNodeJS LINT STAGE ERROR: ${e}\033[0m"
//                            sh 'exit 1'
//                        }
//                        finally {
//                            archiveArtifacts artifacts: "**/*.log,*.log", allowEmptyArchive: true
//                        }
//                    }
//                }
//            }
//        }

        stage ("NodeJS test") {
            steps {
                nodejs(configId: "${devopsConfig.npm.configId}", nodeJSInstallationName: "${devopsConfig.npm.nodeJSInstallationName}") {
                    script {
                        try {
                            println "\033[34mSTART NodeJS TEST STAGE\033[0m"
                            if (fileExists("${WORKSPACE}/node_modules/@enigma/test-configs")) {
                                sh "${WORKSPACE}/node_modules/.bin/enigma-tools-test start -rc 40"
                            } else {
                                sh "npm run test:coverage"
                            }
                            println "\033[32mNodeJS TEST STAGE SUCCESS\033[0m"
                        }
                        catch (e) {
                            println "\033[31mNodeJS TEST STAGE ERROR: ${e}\033[0m"
                            sh 'exit 1'
                        }
                        finally {
                            archiveArtifacts artifacts: "**/*.log,*.log", allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage ("SonarQube scan") {
            steps {
                nodejs(configId: "${devopsConfig.npm.configId}", nodeJSInstallationName: "${devopsConfig.npm.nodeJSInstallationName}") {
                    script {
                        try {
                            println "\033[34mSTART SonarQube SCAN STAGE\033[0m"
                            withCredentials([
                                    usernamePassword(
                                            credentialsId: "${devopsConfig.sonar.token}",
                                            usernameVariable: 'NAME',
                                            passwordVariable: 'SONAR_TOKEN'
                                    )
                            ]){
                                def sonarHome = tool 'SonarQube Scanner 3.0.3.778'
                                def sonarParams =  " -Dsonar.projectKey=${devopsConfig.sonar.projectKey_pl} " +
                                        " -Dsonar.host.url=${devopsConfig.sonar.url} " +
                                        " -Dsonar.login=${SONAR_TOKEN} " +
                                        " -Dsonar.branch.name=${sonarBranch} " +
                                        " -Dsonar.javascript.lcov.reportPaths=${WORKSPACE}/coverage/lcov.info "
//                                            " -Dsonar.typescript.lcov.reportPaths=${WORKSPACE}/coverage/lcov.info "
                                sh "${sonarHome}/bin/sonar-scanner ${sonarParams}"
                            }
                            println "\033[32mSonarQube SCAN STAGE SUCCESS\033[0m"
                        }
                        catch (e) {
                            println "\033[31mSonarQube SCAN STAGE ERROR: ${e}\033[0m"
                            sh 'exit 1'
                        }
                        finally {
                            archiveArtifacts artifacts: "**/*.log,*.log", allowEmptyArchive: true
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                try {
                    if (env.pullRequest) {
                        setBuildStatus("${devopsConfig.tuz.pass}", devopsConfig.git.httpsurl, pullRequest.fromRef.repository.project.key, "${jobname} #${BUILD_ID}", pullRequest.author.user.displayName, BUILD_URL, pullRequest.fromRef.latestCommit, 'SUCCESSFUL')
                        println "\033[32mPush 'SUCCESSFUL' status to BitBucket SUCCESS\033[0m"
                    }
                }
                catch (e) {
                    println "\033[31mPush 'SUCCESSFUL' status to BitBucket ERROR: ${e}\033[0m"
                    sh 'exit 1'
                }
            }
        }
        failure {
            script {
                try {
                    if (env.pullRequest) {
                        setBuildStatus("${devopsConfig.tuz.pass}", devopsConfig.git.httpsurl, pullRequest.fromRef.repository.project.key, "${jobname} #${BUILD_ID}", pullRequest.author.user.displayName, BUILD_URL, pullRequest.fromRef.latestCommit, 'FAILED')
                        println "\033[32mPush 'FAILED' status to BitBucket SUCCESS\033[0m"
                    }
                }
                catch (e) {
                    println "\033[31mPush 'FAILED' status to BitBucket ERROR: ${e}\033[0m"
                    sh 'exit 1'
                }
            }
        }
        always {
            script {
                if (params.clearWorkSpace == true)
                {
                    cleanWs()
                }
            }
        }
    }
}

def getCheckoutRef(String credentialsId, String url, String project, String slug, String ref) {
    sh 'git init'
    sh 'git reset --hard --quiet'
    sh 'git clean -xfd --quiet'
    withSSH(credentialsId) {
        sh "git fetch --quiet --tags ${url}/${project}/${slug}.git $ref"
    }
    sh 'git checkout FETCH_HEAD'
    sh 'git prune -v'
    sh (script: 'git rev-parse HEAD', returnStdout: true).trim()
}

/**
 * Получение файла/директории из git без выкачивания репозитория
 *
 * @param credentialsId Jenkins credential name
 * @param url ссылка https на BitBucket
 * @param project Название проекта
 * @param slug Название репозитория
 * @param ref Branch/tag
 * @param path Path внутри репозитория, архив которого хотим получить
 */
def archive(def credentialsId, String url, String project, String slug, String ref, String path) {
    withSSH(credentialsId) {
        sh "git archive --remote=${url}/${project}/${slug}.git ${ref} ${path} | tar -x"
    }
}

def withSSH(String credential, Closure body) {
    withEnv(["GIT_SSH_COMMAND=ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"]) {
        sshagent(credentials: [credential]) {
            body.call()
        }
    }
}

def setBuildStatus(def credentialsId, String url, String projectKey, String jobName, String jobDescription, String jobUrl, String latestCommit, String status) {
    def requestBody = """
            "state": "${status}",
            "key": "${projectKey}",
            "name": "${jobName}",
            "description": "${jobDescription}",
            "url": "${jobUrl}"
        """
    withCredentials([
            usernamePassword(
                    credentialsId: "${credentialsId}",
                    usernameVariable: 'USER',
                    passwordVariable: 'PASSWORD'
            )
    ]) {
        def NODELTAUSER = ("${USER}").split('@')[0]
        sh "curl -k -s -u \"${NODELTAUSER}:${PASSWORD}\" -H \"Content-Type: application/json\" -X POST ${url}/rest/build-status/1.0/commits/${latestCommit} -d '{${requestBody}}'"
    }
}
