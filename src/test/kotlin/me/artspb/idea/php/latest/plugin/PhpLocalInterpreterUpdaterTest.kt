package me.artspb.idea.php.latest.plugin

import com.intellij.testFramework.LightPlatformTestCase
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl

class PhpLocalInterpreterUpdaterTest : LightPlatformTestCase() {

    fun testPhpLatestIsAdded() {
        val manager = getInterpretersManager()

        val interpreter = manager.findInterpreter(PhpLocalInterpreterUpdater.NAME)
        assertNotNull(interpreter)

        deleteInterpreter(interpreter!!, manager)
        assertNull(manager.findInterpreter(PhpLocalInterpreterUpdater.NAME))

        getUpdater().projectOpened()
        assertNotNull(manager.findInterpreter(PhpLocalInterpreterUpdater.NAME))
    }

    private fun getUpdater() = getProject().getComponent(PhpLocalInterpreterUpdater::class.java)

    private fun getInterpretersManager() = PhpInterpretersManagerImpl.getInstance(getProject())

    private fun deleteInterpreter(interpreter: PhpInterpreter, manager: PhpInterpretersManagerImpl) {
        val interpreters = manager.interpreters
        interpreters.remove(interpreter)
        manager.interpreters = interpreters
    }
}