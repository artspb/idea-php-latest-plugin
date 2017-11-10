package me.artspb.idea.php.latest.plugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.php.config.PhpProjectConfigurable
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import com.jetbrains.php.ui.PhpUiUtil
import org.rauschig.jarchivelib.ArchiverFactory
import java.io.File
import java.net.UnknownHostException

class PhpLocalInterpreterUpdater(val project: Project) : ProjectComponent {

    companion object {
        val LOG = Logger.getInstance("#me.artspb.idea.php.latest.plugin.PhpLocalInterpreterUpdater")
        val NAME = "PHP latest"
    }

    override fun projectOpened() {
        if (!SystemInfo.isMac && !SystemInfo.isLinux || project.isDefault) {
            return
        }
        StartupManager.getInstance(project).runWhenProjectIsInitialized {
            requestRelease { release ->
                val serverVersion = release.tagName

                val cacheDir = getCacheDir()
                val phpDir = getPhpDir(cacheDir, serverVersion)
                val executable = getExecutableFile(phpDir)

                val localVersion = getLocalVersion(cacheDir)
                if (localVersion.isNotEmpty() && serverVersion == localVersion) {
                    addInterpreterConditionally(executable.path)
                } else {
                    if (cacheDir.exists()) {
                        FileUtil.delete(cacheDir)
                    }
                    if (!phpDir.mkdirs()) {
                        throw IllegalStateException("Unable to create directories: ${phpDir.path}")
                    }
                    downloadInterpreter(phpDir, executable, release) {
                        if (!project.isDisposed) {
                            addInterpreterConditionally(executable.path)
                            fireNotification(serverVersion)
                        }
                    }
                }
            }
        }
    }

    private fun requestRelease(onSuccess: (release: GitHubRelease) -> Unit) {
        var release: GitHubRelease? = null
        object : Task.Backgroundable(project, "Requesting version of the latest PHP interpreter...", false) {
            override fun run(indicator: ProgressIndicator) {
                release = requestRelease()
            }

            override fun onSuccess() = onSuccess(release ?: throw IllegalStateException("Release must be present"))

            override fun onThrowable(error: Throwable) {
                if (error is UnknownHostException) {
                    LOG.info(error.message)
                } else {
                    super.onThrowable(error)
                }
            }
        }.queue()
    }

    private fun getCacheDir() = File(PathManager.getSystemPath(), "plugins/idea-php-latest-plugin")

    private fun getPhpDir(cacheDir: File, version: String) = File(cacheDir, "php-$version")

    private fun getExecutableFile(phpDir: File) = File(phpDir, "php.sh")

    private fun getLocalVersion(cacheDir: File): String {
        if (cacheDir.exists()) {
            for (file in cacheDir.listFiles() ?: arrayOf()) {
                val name = file.name
                if (name.startsWith("php-") && getExecutableFile(file).exists()) {
                    return name.substringAfter("php-")
                }
            }
        }
        return ""
    }

    private fun downloadInterpreter(phpDir: File, executable: File, release: GitHubRelease, onSuccess: () -> Unit) {
        object : Task.Backgroundable(project, "Downloading the latest PHP interpreter...") {
            override fun run(indicator: ProgressIndicator) {
                val archive = File(phpDir, "php.tar.gz")

                val url = release.assets.firstOrNull()?.browserDownloadUrl ?: throw IllegalStateException("Archive URL must be present")
                downloadInterpreter(url, archive, indicator)

                ArchiverFactory.createArchiver("tar", "gz").extract(archive, phpDir)
                if (!archive.delete()) {
                    throw IllegalStateException("Unable to delete a file: ${archive.path}")
                }

                replaceTemplate(executable, phpDir.path)
                if (SystemInfo.isMac) {
                    replaceTemplate(File(phpDir, "ini/conf.d/ext-xdebug.ini"), phpDir.path)
                } else if (SystemInfo.isLinux) {
                    File(phpDir, "etc/php/7.1/cli/conf.d/").listFiles()?.forEach { replaceTemplate(it, phpDir.path) }
                }
            }

            override fun onSuccess() = onSuccess()
        }.queue()
    }

    private fun replaceTemplate(file: File, path: String) = FileUtil.writeToFile(file, FileUtil.loadFile(file).replace("$(pwd)", path))

    private fun addInterpreterConditionally(executable: String) {
        val manager = PhpInterpretersManagerImpl.getInstance(project)
        val interpreter = manager.findInterpreter(NAME)
        if (interpreter == null) {
            manager.addInterpreter(createInterpreter(executable))
        } else if (interpreter.homePath != executable) {
            val interpreters = manager.interpreters
            interpreters.remove(interpreter)
            interpreters.add(createInterpreter(executable))
            manager.interpreters = interpreters
        }
    }

    private fun createInterpreter(homePath: String): PhpInterpreter {
        val interpreter = PhpInterpreter()
        interpreter.name = NAME
        interpreter.homePath = homePath
        interpreter.setIsProjectLevel(true)
        return interpreter
    }

    private fun fireNotification(version: String) {
        val notification = Notification(
                NAME, NAME,
                """The latest PHP interpreter $version has been downloaded. Would you like to configure it?""",
                NotificationType.INFORMATION
        )
        notification.addAction(object : DumbAwareAction("Configure") {
            override fun actionPerformed(e: AnActionEvent) {
                PhpUiUtil.editConfigurable(project, PhpProjectConfigurable(project))
                notification.expire()
            }
        })
        Notifications.Bus.notify(notification, project)
    }

    override fun getComponentName() = "idea.php.latest.plugin.PhpLocalInterpreterUpdater"

    override fun disposeComponent() {}

    override fun projectClosed() {}

    override fun initComponent() {}
}