import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.SSHUpload
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.buildSteps.sshExec
import jetbrains.buildServer.configs.kotlin.buildSteps.sshUpload
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2022.04"

project {

    vcsRoot(BotHandly)
    vcsRoot(TsBot)

    buildType(BuildBot)
    buildType(GetJobId)
    buildType(CreateRunner)
    buildType(StopBot)
    buildType(UpdRemote)

    params {
        password("env.bot_token_personal", "credentialsJSON:ba941522-108a-4065-92fd-29b818fe88c1")
        param("env.bot_name_work", "DevopsOnbBot")
        select("server", "vdsina", display = ParameterDisplay.PROMPT,
                options = listOf("vdsina" to "89.22.230.133", "jino" to "ovz1.j42474764.n03kn.vps.myjino.ru"))
        select("server_name", "v1203966.hosted-by-vdsina.ru", display = ParameterDisplay.PROMPT,
                options = listOf("v1203966.hosted-by-vdsina.ru", "ovz1.j42474764.n03kn.vps.myjino.ru"))
        param("env.bot_profiles_personal", "work;personal")
        param("env.bot_name_personal", "Daily_zBot")
        password("env.bot_pass_work", "credentialsJSON:a4603b03-dfcc-426c-8f17-c861aad72a37")
        param("env.bot_profiles_work", "work")
        param("env.bot_server_port_work", "8081")
        param("env.bot_conf_file_personal", "dutybot-zarckir.yaml")
        param("env.bot_ssl_port_work", "8444")
        password("google_sercret", "credentialsJSON:41f3f6e1-8ec7-42fd-91c9-cac7c7e3ce85")
        password("env.bot_token_work", "credentialsJSON:ce1e98a8-cbee-4727-ab00-23773b6df6ed")
        param("env.bot_port_work", "8081")
        param("SNAP", "-SNAPSHOT")
        param("maven-settings", "settings.xml")
        select("bot_env", "personal", display = ParameterDisplay.PROMPT,
                options = listOf("personal", "work"))
        param("env.bot_port_personal", "8080")
        text("port", "22", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("env.bot_server_port_personal", "8080")
        param("env.bot_ssl_port_personal", "8443")
        text("bot_src_path", "/home/vpn/runners", display = ParameterDisplay.PROMPT, allowEmpty = false)
        password("env.bot_pass_personal", "credentialsJSON:a4603b03-dfcc-426c-8f17-c861aad72a37")
        param("google_client", "861159728080-n66sv67i1h7hau60cm7lejthtm8n9ec9.apps.googleusercontent.com")
        param("env.bot_conf_file_work", "dutybot.yaml")
        text("username", "vpn", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }
    buildTypesOrder = arrayListOf(GetJobId, BuildBot, CreateRunner, UpdRemote, StopBot)
}

object BuildBot : BuildType({
    name = "BuildBot"

    artifactRules = """
        +:target/*full.jar
        +:**/resources/*.p12
        +:**/resources/logback.xml
    """.trimIndent()
    buildNumberPattern = "${GetJobId.depParamRefs["env.branch_normalized"]}${GetJobId.depParamRefs["env.commit_hash"]}"

    params {
        param("env.SNAP", "-SNAPSHOT")
        param("branch_normalized", "qwe")
        param("commit_hash", "qwe")
    }

    vcs {
        root(BotHandly)

        cleanCheckout = true
    }

    steps {
        script {
            name = "Get version from file"
            scriptContent = """
                #!/usr/bin/env bash
                set -xeuo pipefail
                ls -latr
                echo "##teamcity[setParameter name='branch_normalized' value='${'$'}(git rev-parse --abbrev-ref HEAD | tr '[A-Z]' '[a-z]' | tr -cs '[[:alnum:]]' '-')']"
                echo "##teamcity[setParameter name='commit_hash' value='${'$'}(git rev-parse --short HEAD)']"
            """.trimIndent()
        }
        maven {
            name = "build_maven_project"
            goals = "clean package"
            mavenVersion = bundled_3_6()
            userSettingsSelection = "settings.xml"
            jdkHome = "%env.JAVA_HOME%"
        }
    }

    triggers {
        vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_DEFAULT
            branchFilter = ""
            watchChangesInDependencies = true
        }
    }

    dependencies {
        snapshot(GetJobId) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        exists("env.JAVA_HOME")
    }
})

object CreateRunner : BuildType({
    name = "CreateRunner"

    artifactRules = """
        +:*.jar
        +:**/resources/*.p12
        +:**/resources/logback.xml
        +:**/*.sh
    """.trimIndent()
    buildNumberPattern = "%build.counter%-${BuildBot.depParamRefs["branch_normalized"]}${BuildBot.depParamRefs["commit_hash"]}-%bot_env%-%server_name%"

    params {
        select("bot_env", "personal", display = ParameterDisplay.PROMPT,
                options = listOf("personal", "work"))
    }

    vcs {
        root(BotHandly)
    }

    steps {
        script {
            name = "create sh"
            scriptContent = """
                #!/usr/bin/env bash
                
                env_value(){
                
                  env=(${'$'}(env | grep -E "^${'$'}1"))
                  env=(${'$'}(echo ${'$'}{env[0]} | tr "=" "\n"))
                  echo "${'$'}{env[1]}"  
                
                }
                
                set -xeuo pipefail
                
                bot_env=${'$'}(echo %bot_env%)
                app_jar=(${'$'}(find ./ -path '**/*-full.jar'))
                template=${'$'}{app_jar/.jar/}-${'$'}bot_env.jar
                eval cp ${'$'}app_jar ${'$'}template
                
                file_name="${'$'}{template##*/}"
                
                echo "#!/bin/sh
                
                export token=${'$'}(env_value bot_token_${'$'}bot_env)
                export name=${'$'}(env_value bot_name_${'$'}bot_env)
                
                export confFile=${'$'}(env_value bot_conf_file_${'$'}bot_env)
                export profiles=${'$'}(env_value bot_profiles_${'$'}bot_env)
                
                export port=${'$'}(env_value bot_port_${'$'}bot_env)
                export sslPort=${'$'}(env_value bot_ssl_port_${'$'}bot_env)
                export pass=${'$'}(env_value bot_pass_${'$'}bot_env)
                
                export GOOGLE_CLIENT_ID=${'$'}(echo %google_client%)
                export GOOGLE_CLIENT_SECRET=${'$'}(echo %google_sercret%)
                export serverHost=${'$'}(echo %server_name%)
                export serverPort=${'$'}(env_value bot_server_port_${'$'}bot_env)
                
                export confDir='./conf'
                export TZ=Europe/Moscow
                
                eval kill \${'$'}(ps aux | grep '${'$'}bot_env.jar' | awk -F ' ' '{print \${'$'}2}' | head -1)
                
                eval java -XX:MaxRAMPercentage=75.0 -Dlogback.configurationFile=./conf/logback.xml -jar ${'$'}file_name &
                
                exit 0
                " > ${'$'}bot_env.sh
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            watchChangesInDependencies = true
        }
        vcs {
            watchChangesInDependencies = true

            buildParams {
                select("bot_env", "work", display = ParameterDisplay.PROMPT,
                        options = listOf("personal", "work"))
            }
        }
    }

    dependencies {
        dependency(BuildBot) {
            snapshot {
            }

            artifacts {
                cleanDestination = true
                artifactRules = """
                    +:*.jar
                    +:**/*.p12
                    +:**/logback.xml
                """.trimIndent()
            }
        }
    }
})

object GetJobId : BuildType({
    name = "Get_Job_Id"

    buildNumberPattern = "%build.counter%-%teamcity.build.branch%-%build.vcs.number.1%"

    params {
        param("env.branch_normalized", "")
        param("env.commit_hash", "")
    }

    vcs {
        root(BotHandly)
    }

    steps {
        script {
            scriptContent = """
                #!/usr/bin/env bash
                set -xeuo pipefail
                ls -latr
                branch_normalized=${'$'}(git rev-parse --abbrev-ref HEAD | tr '[A-Z]' '[a-z]' | tr -cs '[[:alnum:]]' '-')
                commit_hash=${'$'}(git rev-parse --short HEAD)
                export branch_normalized=${'$'}branch_normalized
                export commit_hash=${'$'}commit_hash
                echo "##teamcity[setParameter name='branch_normalized' value='${'$'}(git rev-parse --abbrev-ref HEAD | tr '[A-Z]' '[a-z]' | tr -cs '[[:alnum:]]' '-')']"
                echo "##teamcity[setParameter name='commit_hash' value='${'$'}(git rev-parse --short HEAD)']"
                echo "##teamcity[setParameter name='env.branch_normalized' value='${'$'}(git rev-parse --abbrev-ref HEAD | tr '[A-Z]' '[a-z]' | tr -cs '[[:alnum:]]' '-')']"
                echo "##teamcity[setParameter name='env.commit_hash' value='${'$'}(git rev-parse --short HEAD)']"
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            branchFilter = ""
        }
    }
})

object StopBot : BuildType({
    name = "Stop_bot"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    buildNumberPattern = "%build.counter%-%env.bot_env%-%env.server_name%"
    maxRunningBuilds = 1

    params {
        select("env.bot_env", "personal", display = ParameterDisplay.PROMPT,
                options = listOf("personal", "work"))
    }

    vcs {
        root(BotHandly)
    }

    steps {
        sshExec {
            commands = """
                eval kill ${'$'}(ps aux | grep '%env.bot_env%.jar' | awk -F ' ' '{print ${'$'}2}' | head -1)
                sleep 5
                ps aux | grep java
            """.trimIndent()
            targetUrl = "%env.server%"
            authMethod = uploadedKey {
                username = "%env.username%"
                key = "id_ed25519_github"
            }
            param("jetbrains.buildServer.sshexec.port", "%env.port%")
        }
    }
})

object UpdRemote : BuildType({
    name = "UpdRemote"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    buildNumberPattern = "%build.counter%-${BuildBot.depParamRefs["branch_normalized"]}${BuildBot.depParamRefs["commit_hash"]}-%bot_env%-%server_name%"
    maxRunningBuilds = 1

    params {
        select("bot_env", "personal", display = ParameterDisplay.PROMPT,
                options = listOf("personal", "work"))
    }

    vcs {
        root(BotHandly)
    }

    steps {
        sshUpload {
            transportProtocol = SSHUpload.TransportProtocol.SFTP
            sourcePath = """
                *-personal.jar
                personal.sh
                *-work.jar
                work.sh
                *.p12 => conf
                logback.xml => conf
            """.trimIndent()
            targetUrl = "%server%:%bot_src_path%"
            authMethod = uploadedKey {
                username = "%username%"
                key = "id_ed25519_github"
            }
            param("jetbrains.buildServer.sshexec.port", "%port%")
        }
        sshExec {
            commands = """
                cd runners/
                ls -latr
                botenv=${'$'}(echo %bot_env%)
                nohup sh ${'$'}botenv.sh > ./${'$'}botenv-depl.log 2>&1 &
                sleep 10
                cat ${'$'}botenv-depl.log
                rm ${'$'}botenv-depl.log
                ps aux | grep ${'$'}botenv
            """.trimIndent()
            targetUrl = "%server%"
            authMethod = uploadedKey {
                username = "%username%"
                key = "id_ed25519_github"
            }
            param("jetbrains.buildServer.sshexec.port", "%port%")
        }
    }

    triggers {
        vcs {
            branchFilter = "+:master"
            watchChangesInDependencies = true
        }
        vcs {
            branchFilter = "+:master"
            watchChangesInDependencies = true

            buildParams {
                select("bot_env", "work", display = ParameterDisplay.PROMPT,
                        options = listOf("personal", "work"))
            }
        }
    }

    dependencies {
        dependency(CreateRunner) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = """
                    +:*.jar
                    +:**/*.p12
                    +:**/logback.xml
                    +:*.sh
                """.trimIndent()
            }
        }
    }
})

object BotHandly : GitVcsRoot({
    name = "bot-handly"
    url = "git@github.com:Zarckir/bot-handly.git"
    branch = "refs/heads/master"
    branchSpec = """
        +:refs/heads/*
        +:refs/tags/*
    """.trimIndent()
    useTagsAsBranches = true
    authMethod = uploadedKey {
        uploadedKey = "id_ed25519_github"
    }
})

object TsBot : GitVcsRoot({
    name = "ts-bot"
    url = "git@github.com:Zarckir/bot-teamcity.git"
    pushUrl = "git@github.com:Zarckir/bot-teamcity.git"
    branch = "refs/heads/master"
    branchSpec = """
        +:refs/heads/*
        +:refs/tags/*
    """.trimIndent()
    useTagsAsBranches = true
    authMethod = uploadedKey {
        uploadedKey = "id_ed25519_github"
    }
})
