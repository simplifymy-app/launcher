package app.simplifymy.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerE2ETest {
    @get:Rule val rule = createAndroidComposeRule<HomeActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    @Test
    fun swipeUpOpensDrawerAndSearchCalculates() {
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = top + bottom * 0.1f) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(string(R.string.search_apps_hint)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(string(R.string.search_apps_hint)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("12*7+1")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("= 85").fetchSemanticsNodes().isNotEmpty() }
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsE2ETest {
    @get:Rule val rule = createAndroidComposeRule<SettingsActivity>()

    private val container get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext.container

    @Test
    fun frequentRowToggleWritesThrough() {
        val before = container.settings.settings.value.frequentRow
        rule.onNodeWithText(rule.activity.getString(R.string.page_drawer)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.frequent_row)).performClick()
        rule.waitUntil(5_000) { container.settings.settings.value.frequentRow != before }
        assertEquals(!before, container.settings.settings.value.frequentRow)
        runBlocking { container.settings.update { it.copy(frequentRow = before) } }
    }
}
