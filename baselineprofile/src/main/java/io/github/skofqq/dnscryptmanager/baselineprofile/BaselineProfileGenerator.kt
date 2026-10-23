package io.github.skofqq.dnscryptmanager.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start into Status, then the paths people take every time: Settings, the
 * Servers sub-page and Logs. Labels are matched in English and Russian.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // The first start of a fresh install asks Magisk for root.
        device.wait(Until.findObject(By.textContains("Grant")), 3_000)?.click()
        device.wait(Until.hasObject(By.text(TAB_STATUS)), 15_000)

        tap(TAB_SETTINGS)
        tap(SERVERS)
        device.pressBack()
        device.waitForIdle()
        tap(TAB_LOGS)
        tap(TAB_STATUS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.tap(label: java.util.regex.Pattern) {
        device.wait(Until.findObject(By.text(label)), 5_000)?.click()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "io.github.skofqq.dnscryptmanager"
        val TAB_STATUS: java.util.regex.Pattern = java.util.regex.Pattern.compile("Status|Статус")
        val TAB_SETTINGS: java.util.regex.Pattern = java.util.regex.Pattern.compile("Settings|Настройки")
        val TAB_LOGS: java.util.regex.Pattern = java.util.regex.Pattern.compile("Logs|Журналы|Логи")
        val SERVERS: java.util.regex.Pattern = java.util.regex.Pattern.compile("Servers|Серверы")
    }
}
