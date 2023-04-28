def latestCommitHash
def latestCommitHash_pl
def devopsConfig
def flags = ["ci"]
def repoDigest
def imageHash

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
        booleanParam(name: 'reconfigure', defaultValue: false, description: 'Выполнить запуск реконфигурации, для применения настроек, после внесения изменений в JenkinsFile')
        string(name: 'version', defaultValue: '99.001.01', description: 'Версия дистрибутива. К версии через "-" будет добавлен номер сборки')
        booleanParam(name: 'release', defaultValue: false, description: 'Выпуск релизной сборки. Выполняется проставление тэга с текущей версией на последний коммит, запуск Sonar, создание ReleaseNotes, запуск OSS, SAST, отправка флагов и ReleaseNotes в QGM')
//        string(name: 'fromCommitHash', defaultValue: '', description: '(BH)Указание на коммит с которого начинается формирование ReleaseNotes. Если оставить пустым будет взят коммит с тэгом предыдущей версии')
//        string(name: 'fromCommitHash_pl', defaultValue: '', description: '(PL)Указание на коммит с которого начинается формирование ReleaseNotes. Если оставить пустым будет взят коммит с тэгом предыдущей версии')
        listGitBranches(
                remoteURL: 'ssh://git@stash.delta.sbrf.ru:7999/corpins/dms-ufs.git',
                credentialsId: 'CAB-SA-DVO04594-SSH-VAULT',
                listSize: '20',
                name: 'BRANCH',
                type: 'PT_BRANCH',
                tagFilter: '*',
                branchFilter: '.*',
                sortMode: 'ASCENDING_SMART',
                selectedValue: 'NONE',
                quickFilterEnabled: 'false',
                description: '(BH)Выбор ветки для сборки')
        listGitBranches(
                remoteURL: 'ssh://git@stash.delta.sbrf.ru:7999/corpins/dms-ui.git',
                credentialsId: 'CAB-SA-DVO04594-SSH-VAULT',
                listSize: '20',
                name: 'BRANCH_pl',
                type: 'PT_BRANCH',
                tagFilter: '*',
                branchFilter: '.*',
                sortMode: 'ASCENDING_SMART',
                selectedValue: 'NONE',
                quickFilterEnabled: 'false',
                description: '(PL)Выбор ветки для сборки')
    }
    environment {
        VERSION_PATTERN = /\d{2}\.\d{3}\.\d{2}-\d{3}/
        ARTIFACT_NAME_OS = ''
        VERSION = ''
        DOCKER_IMAGE_NAME = ''
    }

    stages {

        stage('Reconfigure') {
            when {
                expression { params.reconfigure }
            }
            steps {
                script {
                    try {
                        sh 'exit 1'
                    }
                    finally{
                        println "\033[34mSTART RECONFIGURE STAGE\033[0m"
                        currentBuild.displayName += " JOB RECONFIGURE"
                        println "\033[32mRECONFIGURE STAGE SUCCESS\033[0m"
                        currentBuild.result = 'NOT_BUILT'
                    }
                }
            }
        }

        stage('Set build version') {
            steps {
                script {
                    try {
                        println "\033[34mSET BUILD VERSION STAGE\033[0m"
                        def build = (env.BUILD_NUMBER).toString().padLeft(3, '0')
                        VERSION = "${params.version}-${build}"
                        currentBuild.displayName += " $VERSION"
                        println "\033[32mSET BUILD VERSION SUCCESS STAGE: $VERSION\033[0m"
                    }
                    catch (e) {
                        println "\033[31mSET BUILD VERSION STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Read devops config') {
            steps {
                script {
                    try {
                        println "\033[34mSTART READ DEVOPS CONFIG STAGE\033[0m"
                        devopsConfig = readYaml(file: './devops-config/jenkins/devops-config.yml')
                        cleanWs()
                        println "\033[32mREAD DEVOPS CONFIG STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mREAD DEVOPS CONFIG STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Checkout BH git') {
            steps {
                script {
                    try {
                        println "\033[34mSTART CHECKOUT BH GIT STAGE\033[0m"
                        dir ('BH'){
                            latestCommitHash = getCheckoutRef("${devopsConfig.tuz.ssh}", devopsConfig.git.sshurl, devopsConfig.git.project, devopsConfig.git.repository, BRANCH)
                            if (params.release){
                                List versionTags = tags().findAll { it.matches(VERSION_PATTERN) }.sort()
                                def lastVersion = versionTags.isEmpty() ? '' : versionTags.last()
                                fromCommit = lastVersion ? rev(lastVersion ?: latestCommitHash.trim()) : ''
                            }
                        }
                        println "\033[32mCHECKOUT BH GIT STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mCHECKOUT BH GIT STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Checkout PL git') {
            steps {
                script {
                    try {
                        println "\033[34mSTART CHECKOUT PL GIT STAGE\033[0m"
                        dir ('PL'){
                            latestCommitHash_pl = getCheckoutRef("${devopsConfig.tuz.ssh}", devopsConfig.git.sshurl, devopsConfig.git.project, devopsConfig.git.repository_pl, BRANCH_pl)
                            if (params.release){
                                List versionTags_pl = tags().findAll { it.matches(VERSION_PATTERN) }.sort()
                                def lastVersion_pl = versionTags_pl.isEmpty() ? '' : versionTags_pl.last()
                                fromCommit_pl = lastVersion_pl ? rev(lastVersion_pl ?: latestCommitHash_pl.trim()) : ''
                            }
                        }
                        println "\033[32mCHECKOUT PL GIT STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mCHECKOUT PL GIT STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Create ReleaseNotes') {
            when {
                expression { params.release }
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART CREATE RELEASENOTES STAGE\033[0m"
                        println "\033[32m(BH)CURRENT COMMIT: ${latestCommitHash} FROM COMMIT: ${fromCommit}\033[0m"
                        println "\033[32m(PL)CURRENT COMMIT: ${latestCommitHash_pl} FROM COMMIT: ${fromCommit_pl}\033[0m"
                        library('DPMPipelineUtils@1.4')
                        rn = releaseNotes()
                                .setArtifactRepository("maven-distr")
                                .setGroup("${devopsConfig.nexus.groupId}")
                                .setArtifact("${devopsConfig.nexus.artifactId}")
                                .setVersion("${VERSION}")
                                .setQgmCredsId("${devopsConfig.tuz.pass}")
                                .addRepo(
                                        "${WORKSPACE}/BH",
                                        "${devopsConfig.tuz.ssh}",
                                        "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository}.git",
                                        true,
                                        "${fromCommit}",
                                        "${latestCommitHash}")
//                                    "(dpmt|DPMT)-[0-9]+")                               //Опционально, RegExp pattern, по которому будет произведен поиск JIRA Issue Key, в Commit Message. Ключ проекта должен быть указан в круглых скобках.
                                .addRepo(
                                        "${WORKSPACE}/PL",
                                        "${devopsConfig.tuz.ssh}",
                                        "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository_pl}.git",
                                        true,
                                        "${fromCommit_pl}",
                                        "${latestCommitHash_pl}")
//                                    "(dpmt|DPMT)-[0-9]+")                               //Опционально, RegExp pattern, по которому будет произведен поиск JIRA Issue Key, в Commit Message. Ключ проекта должен быть указан в круглых скобках.
                        json = rn.createReleaseNotesJSON()
                        writeJSON (file: "${WORKSPACE}/ReleaseNotes.json", json: json)
                        println "\033[32mCREATE RELEASENOTES STAGE SUCCESS: ${WORKSPACE}/ReleaseNotes.json\033[0m"
                    }
                    catch (e) {
                        println "\033[31mCREATE RELEASENOTES STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('SAST BH') {
            when {
                expression { params.release }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART SAST BH STAGE\033[0m"
                        library('ru.sbrf.devsecops@master')
                        dir('BH'){
                            runSastCx(devopsConfig, "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository}.git", "${BRANCH}", latestCommitHash)
                            def sastStatus = getQGFlag(latestCommitHash)
                            println "\033[32mSAST BH SUCCESS: ${sastStatus}\033[0m"
                        }
                    }
                    catch (e) {
                        println "\033[31mSAST BH STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('SAST PL') {
            when {
                expression { params.release }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART SAST PL STAGE\033[0m"
                        dir('PL'){
                            runSastCx(devopsConfig, "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository_pl}.git", "${BRANCH_pl}", latestCommitHash_pl)
                            def sastStatus = getQGFlag(latestCommitHash_pl)
                            println "\033[32mSAST PL SUCCESS: ${sastStatus}\033[0m"
                        }
                    }
                    catch (e) {
                        println "\033[31mSAST PL STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Build BH') {
            steps {
                script {
                    dir ('BH') {
                        withMaven(jdk: "${devopsConfig.maven.jdk}", maven: "${devopsConfig.maven.home}", mavenSettingsConfig: "${devopsConfig.maven.settings_id}") {
                            try {
                                println "\033[34mSTART BUILD BH STAGE\033[0m"
                                if (params.release == true) {
                                    withCredentials([
                                            usernamePassword(
                                                    credentialsId: "${devopsConfig.sonar.token}",
                                                    usernameVariable: 'NAME',
                                                    passwordVariable: 'SONAR_TOKEN'
                                            )
                                    ]) {
                                        def sonarParams =  " -Dsonar.projectKey=${devopsConfig.sonar.projectKey} " +
                                                " -Dsonar.host.url=${devopsConfig.sonar.url} " +
                                                " -Dsonar.login=${SONAR_TOKEN} " +
                                                " -Dsonar.branch.name=${BRANCH} "
                                        sh "mvn ${devopsConfig.maven.buildReleaseCommand} ${sonarParams} -l ${BUILD_TAG}_build.log && mvn generate-resources -P generate-sup-configs -l ${BUILD_TAG}_sup-import.log"
                                    }
                                }
                                else {
                                    sh "mvn ${devopsConfig.maven.buildCommand} -l ${BUILD_TAG}_build.log  && mvn generate-resources -P generate-sup-configs -l ${BUILD_TAG}_sup-import.log"
                                }
                                println "\033[32mBUILD BH STAGE SUCCESS\033[0m"
                            } catch(e) {
                                println "\033[31mBUILD BH STAGE ERROR, check log: ${BUILD_TAG}_build.log: ${e}\033[0m"
                                sh 'exit 1'
                            } finally {
                                archiveArtifacts artifacts: "**/*.log,*.log", allowEmptyArchive: true
                            }
                        }

                    }
                }
            }
        }

        stage ("Build PL") {
            steps {
                script {
                    dir ('PL') {
                        nodejs(configId: "${devopsConfig.npm.configId}", nodeJSInstallationName: "${devopsConfig.npm.nodeJSInstallationName}") {
                            try {
                                println "\033[34mSTART BUILD PL STAGE\033[0m"
                                if (params.release == true){
                                    sh "${devopsConfig.npm.buildReleaseCommand}"
                                }
                                else {
                                    sh "${devopsConfig.npm.buildCommand}"
                                }
                                println "\033[32mBUILD PL STAGE SUCCESS\033[0m"
                            }
                            catch (e) {
                                println "\033[31mBUILD PL STAGE ERROR: ${e}\033[0m"
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

        stage('OSS BH') {
            when {
                expression { params.release }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART OSS BH STAGE\033[0m"
                        dir ('BH'){
                            runOSS(devopsConfig, "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository}.git", "${BRANCH}", latestCommitHash)
                            def ossStatus = getOSSQGFlag(latestCommitHash)
                            println "\033[32mOSS BH STAGE SUCCESS: ${ossStatus}\033[0m"
                        }
                    }
                    catch (e) {
                        println "\033[31mOSS BH STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('OSS PL') {
            when {
                expression { params.release }
            }
            options {
                timeout(time: 15, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART OSS PL STAGE\033[0m"
                        dir ('PL'){
                            runOSS(devopsConfig, "${devopsConfig.git.sshurl}/${devopsConfig.git.project}/${devopsConfig.git.repository_pl}.git", "${BRANCH_pl}", latestCommitHash_pl)
                            def ossStatus = getOSSQGFlag(latestCommitHash_pl)
                            println "\033[32mOSS PL STAGE SUCCESS: ${ossStatus}\033[0m"
                        }
                    }
                    catch (e) {
                        println "\033[31mOSS PL STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

//        stage('Build and push docker image') {
//            steps {
//                script {
//                    try {
//                        println "\033[34mSTART BUILD AND PUSH DOCKER IMAGE STAGE\033[0m"
//                        DOCKER_IMAGE_NAME = "${devopsConfig.registry.devUrl}/${devopsConfig.registry.project}/${devopsConfig.registry.artifactId}:${VERSION}"
//                        docker.withRegistry("https://${devopsConfig.registry.devUrl}", "${devopsConfig.tuz.pass}") {
//                            docker.build(DOCKER_IMAGE_NAME,
//                                    "-f ${WORKSPACE}/BH/devops-config/conf/openshift/rmkib_kksb_ci_dh/Dockerfile --build-arg VERSION=${VERSION} --build-arg JAVA_IMAGE=${devopsConfig.docker.javaImage} --force-rm .")
//                                    .push()
//                        }
//                        repoDigest = sh(script: "docker inspect ${DOCKER_IMAGE_NAME} --format='{{index .RepoDigests 0}}'", returnStdout: true).trim()
//                        imageHash = repoDigest.split('@').last()
//                        writeFile([file: "${WORKSPACE}/BH/devops-config/conf/openshift/rmkib_kksb_ci_dh/.sha256", text: "${imageHash}"])
//                        println "\033[32mDELTA IMAGE PULL URL: docker-internal.registry-ci.delta.sbrf.ru/${devopsConfig.registry.project}/${devopsConfig.registry.artifactId}@${imageHash}\033[0m"
//                        println "\033[32mALPHA IMAGE PULL URL: registry.ca.sbrf.ru/${devopsConfig.registry.project}/${devopsConfig.registry.artifactId}@${imageHash}\033[0m"
//                        println "\033[32mBUILD AND PUSH DOCKER IMAGE STAGE SUCCESS\033[0m"
//                    }
//                    catch (e) {
//                        println "\033[31mBUILD AND PUSH DOCKER IMAGE STAGE ERROR: ${e}\033[0m"
//                        sh 'exit 1'
//                    }
//                }
//            }
//        }

//        stage('Prepare artifact') {
//            steps {
//                script {
//                    try {
//                        println "\033[34mSTART PREPARE ARTIFACT STAGE\033[0m"
//                        sh "mkdir ${WORKSPACE}/package && mkdir ${WORKSPACE}/package/bh && mkdir ${WORKSPACE}/package/pl"
//                        sh "cp -r ${WORKSPACE}/BH/devops-config/conf ${WORKSPACE}/package/conf"
//                        sh "cp -r ${WORKSPACE}/BH/insurance-dh-war/target/conf/* ${WORKSPACE}/package/conf"
//                        sh "cp -r ${WORKSPACE}/BH/insurance-dh-war/target/rmkib-ci-dh-war.jar ${WORKSPACE}/package/bh/"
//                        sh "cp -r ${WORKSPACE}/BH/meta-info-bh-insurance*.tgz ${WORKSPACE}/package/bh/"
//                        sh "cp -r ${WORKSPACE}/PL/meta-info-pl-insurance*.tgz ${WORKSPACE}/package/pl/"
//                        sh "cp -r ${WORKSPACE}/PL/enigma-module.insurance*.tgz ${WORKSPACE}/package/pl/"
//                        dir('package') {
//                            sh "sed -i 's/\${jenkins_env.fp_image_hash}/${devopsConfig.registry.artifactId}@${imageHash}/' conf/openshift/rmkib_kksb_ci_dh/dc.yaml"
//                            sh "sed -i 's/\${jenkins_env.fp_artifact_version}/${VERSION}/' conf/openshift/rmkib_kksb_ci_dh/dc.yaml"
//                        }
//                        ARTIFACT_NAME_OS = "${devopsConfig.nexus.artifactId}-${VERSION}.zip"
//                        sh "zip -rq ${WORKSPACE}/package/${ARTIFACT_NAME_OS} package"
//                        println "\033[32mPREPARE ARTIFACT STAGE SUCCESS\033[0m"
//                    }
//                    catch (e) {
//                        println "\033[31mPREPARE ARTIFACT STAGE ERROR: ${e}\033[0m"
//                        sh 'exit 1'
//                    }
//                }
//            }
//        }

//        stage('Publish artifact to nexus3') {
//            steps {
//                withMaven(jdk: "${devopsConfig.maven.jdk}", maven: "${devopsConfig.maven.home}", mavenSettingsConfig: "${devopsConfig.maven.settings_id}") {
//                    script {
//                        try {
//                            println "\033[34mSTART PUBLISH ARTIFACT TO NEXUS3 STAGE\033[0m"
//                            sh 	"mvn deploy:deploy-file -X " +
//                                "-Dfile=${WORKSPACE}/package/${ARTIFACT_NAME_OS} " +
//                                "-Dversion=${VERSION} " +
//                                "-DgeneratePom=true " +
//                                "-DgroupId=${devopsConfig.nexus.groupId} " +
//                                "-DartifactId=${devopsConfig.nexus.artifactId} " +
//                                "-DrepositoryId=${devopsConfig.nexus.deployRepoId} " +
//                                "-Durl=https://nexus-ci.delta.sbrf.ru/repository/maven-distr-dev " +
//                                "-Dpackaging=zip " +
//                                "-Dclassifier=distrib " +
//                                "-l ${BUILD_TAG}_deploy.log"
//                            println "\033[32mPUBLISH ARTIFACT TO NEXUS3 STAGE SUCCESS\033[0m"
//                            def nUrl = (devopsConfig.nexus.groupId).replace(".", "/")
//                            println "\033[32mDELTA ARTIFACT DOWNLOAD URL:\033[0m https://nexus-ci.delta.sbrf.ru/repository/maven-distr/${nUrl}/${devopsConfig.nexus.artifactId}/${VERSION}/${devopsConfig.nexus.artifactId}-${VERSION}-distrib.zip"
//                            println "\033[32mALPHA ARTIFACT DOWNLOAD URL:\033[0m https://int.nexus.ca.sbrf.ru/repository/maven-distr/${nUrl}/${devopsConfig.nexus.artifactId}/${VERSION}/${devopsConfig.nexus.artifactId}-${VERSION}-distrib.zip"
//                        } catch(e) {
//                            println "\033[31mPUBLISH ARTIFACT TO NEXUS3 STAGE ERROR, check log: ${BUILD_TAG}_deploy.log: ${e}\033[0m"
//                            sh 'exit 1'
//                        } finally {
//                            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
//                        }
//                    }
//                }
//            }
//        }

        stage('Push ReleaseNotes') {
            when {
                expression { params.release }
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART PUSH RELEASENOTES STAGE\033[0m"
                        echo "upload release notes"
                        rn.uploadReleaseNotes(json)
                        println "\033[32mPUSH RELEASENOTES STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mPUSH RELEASENOTES STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }

        stage('Push QG flags') {
            when {
                expression { params.release }
            }
            steps {
                script {
                    try {
                        println "\033[34mSTART PUSH QG FLAGS STAGE\033[0m"
                        qg = qualityGates()
                                .setArtifactRepository("maven-distr")
                                .setNexusUrl("https://nexus-ci.delta.sbrf.ru/")
                                .setGroup("${devopsConfig.nexus.groupId}")
                                .setArtifact("${devopsConfig.nexus.artifactId}")
                                .setVersion("${VERSION}")
                                .setQgmCredsId("${devopsConfig.tuz.pass}")
                        for (key in flags) {
                            qg.uploadFlag(key, "ok")
                        }
                        println "\033[32mPUSH QG FLAGS STAGE SUCCESS\033[0m"
                    }
                    catch (e) {
                        println "\033[31mPUSH QG FLAGS STAGE ERROR: ${e}\033[0m"
                        sh 'exit 1'
                    }
                }
            }
        }
    }

    post {
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
    sh "git config remote.origin.url ${url}/${project}/${slug}.git"
    sh 'git checkout FETCH_HEAD'
    sh 'git prune -v'
    sh (script: 'git rev-parse HEAD', returnStdout: true).trim()
}

def tag(def credentialsId, String tagName) {
    withSSH(credentialsId) {
        sh "git tag ${tagName}"
        sh "git push --tags"
    }
}

def tags() {
    sh(script: "git tag", returnStdout: true)
            .split('\n').findAll { it }
}

String rev(String revision) {
    sh(returnStdout: true, script: "git rev-parse ${revision}").trim()
}

def withSSH(String credential, Closure body) {
    withEnv(["GIT_SSH_COMMAND=ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"]) {
        sshagent(credentials: [credential]) {
            body.call()
        }
    }
}
