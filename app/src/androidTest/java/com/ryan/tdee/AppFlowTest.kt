package com.ryan.tdee

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ryan.tdee.core.Algorithm
import com.ryan.tdee.core.CsvFormat
import com.ryan.tdee.core.EnergyUnit
import com.ryan.tdee.core.Settings
import com.ryan.tdee.core.ThemeMode
import com.ryan.tdee.core.WeightUnit
import com.ryan.tdee.ui.components.tableDateLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Drives the real app on a device or emulator through the flows people actually use. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TdeeApp>()
    private val today: LocalDate = LocalDate.now()

    @Before
    fun resetState() = runBlocking {
        app.entryRepository.clear()
        app.settingsRepository.update { Settings() }
        Intents.init()
    }

    @After
    fun releaseIntents() = Intents.release()

    // ---- helpers ----

    private fun waitFor(timeoutMs: Long = 15_000, condition: () -> Boolean) = rule.waitUntil(timeoutMs, condition)

    private fun hasLabel(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun hasDescription(text: String, substring: Boolean = false) =
        rule.onAllNodesWithContentDescription(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun field(index: Int) = rule.onAllNodes(hasSetTextAction())[index]

    private fun SemanticsNodeInteraction.value(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun entries() = runBlocking { app.entryRepository.entries.first() }

    private fun settings() = runBlocking { app.settingsRepository.settings.first() }

    /** 30 days of plausible data ending yesterday, so that "today" has no entry. */
    private fun seed(days: Int = 30) = runBlocking {
        for (i in 1..days) {
            val weight = 80.0 - 0.01 * i + (if (i % 3 == 0) 0.2 else 0.0)
            app.entryRepository.save(today.minusDays(i.toLong()), weight, 2800.0 + (i % 5) * 50)
        }
    }

    private fun waitForHomeWithEstimate() = waitFor { hasLabel("Need to eat") && !hasDescription("—") }

    private fun shot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- tests ----

    @Test
    fun emptyAppExplainsWhatToDo() {
        waitFor { hasLabel("Need to eat") }
        assertTrue(hasLabel("Keep logging your weight and calories. Your first estimate appears after about a week."))
        shot("01-home-empty")

        rule.onNodeWithContentDescription("Progress").performClick()
        waitFor { hasLabel("Nothing to show yet. Log a few days and your trends will appear here.") }
        rule.onNodeWithText("Table").performClick()
        waitFor { hasLabel("No entries yet.") }
        shot("02-progress-empty")
    }

    @Test
    fun aNewDayStartsWithEmptyBoxesAndPastDaysKeepTheirValues() {
        seed()
        waitForHomeWithEstimate()
        assertEquals("", field(0).value())
        assertEquals("", field(1).value())
        shot("03-home-new-day")

        rule.onNodeWithContentDescription("Previous day").performClick()
        waitFor { field(1).value() == "2850" }
        assertEquals("79.99", field(0).value())

        rule.onNodeWithContentDescription("Next day").performClick()
        waitFor { field(1).value().isEmpty() }
        assertEquals("", field(0).value())
    }

    @Test
    fun savingAnEntryStoresItAndUpdatesTheEstimate() {
        seed()
        waitForHomeWithEstimate()
        field(0).performTextInput("80.4")
        field(1).performTextInput("3100")
        rule.onNodeWithText("Save").performClick()

        waitFor { entries().any { it.date == today } }
        val saved = entries().first { it.date == today }
        assertEquals(80.4, saved.weightKg!!, 1e-9)
        assertEquals(3100.0, saved.calories!!, 1e-9)
        shot("04-home-saved")
    }

    @Test
    fun implausibleValuesCannotBeSaved() {
        seed()
        waitForHomeWithEstimate()
        field(0).performTextInput("1e308")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        field(0).performTextClearance()
        field(0).performTextInput("abc")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        field(0).performTextClearance()
        field(0).performTextInput("80")
        field(1).performTextInput("99999")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        field(1).performTextClearance()
        field(1).performTextInput("2500")
        rule.onNodeWithText("Save").assertIsEnabled()
        assertNull(entries().firstOrNull { it.date == today })
    }

    @Test
    fun graphTogglesAndTableEditing() {
        seed(60)
        waitForHomeWithEstimate()

        rule.onNodeWithContentDescription("Progress").performClick()
        waitFor { hasDescription("Progress graph", substring = true) }
        shot("05-progress-graph")

        // Weight's toggle cycles: line + dots -> dots -> line -> hidden.
        rule.onAllNodesWithText("Line + dots")[0].performClick()
        waitFor { hasLabel("Dots") }
        rule.onNodeWithText("Dots").performClick()
        waitFor { hasLabel("Line") }
        rule.onNodeWithText("Line").performClick()
        waitFor { hasLabel("Hidden") }
        shot("06-progress-weight-hidden")
        rule.onNodeWithText("Hidden").performClick()
        waitFor { !hasLabel("Hidden") }

        rule.onNodeWithText("1M").performClick()
        shot("07-progress-1m")
        rule.onNodeWithText("All").performClick()

        rule.onNodeWithText("Table").performClick()
        val yesterday = tableDateLabel(today.minusDays(1))
        waitFor { hasLabel(yesterday) && !hasLabel("Line + dots") }
        // The header names every column and its unit.
        listOf("Date", "Weight", "Calories", "TDEE", "kg", "kcal").forEach { assertTrue("missing header $it", hasLabel(it)) }
        shot("08-progress-table")

        // Edit a row.
        val twoDaysAgo = today.minusDays(2)
        rule.onNodeWithText(tableDateLabel(twoDaysAgo)).performClick()
        waitFor { hasLabel("Delete") }
        shot("09-edit-dialog")
        field(0).performTextClearance()
        field(0).performTextInput("81.25")
        rule.onNodeWithText("Save").performClick()
        waitFor { entries().first { it.date == twoDaysAgo }.weightKg == 81.25 }

        // Delete a row.
        rule.onNodeWithText(yesterday).performClick()
        waitFor { hasLabel("Delete") }
        rule.onNodeWithText("Delete").performClick()
        waitFor { entries().none { it.date == today.minusDays(1) } }
    }

    @Test
    fun settingsAreAppliedAndPersisted() {
        seed()
        waitForHomeWithEstimate()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor { hasLabel("Days used for calculations") }
        shot("10-settings")

        rule.onNodeWithText("Days used for calculations").performClick()
        waitFor { hasLabel("Save") }
        field(0).performTextClearance()
        field(0).performTextInput("28")
        rule.onNodeWithText("Save").performClick()
        waitFor { settings().days == 28 }

        rule.onNodeWithText("Algorithm").performClick()
        waitFor { hasLabel("Smoothed") }
        rule.onNodeWithText("Smoothed").performClick()
        waitFor { settings().algorithm == Algorithm.SMOOTHED }

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dark"))
        rule.onNodeWithText("Dark").performClick()
        waitFor { settings().themeMode == ThemeMode.DARK }
        shot("11-settings-dark")

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Pounds"))
        rule.onNodeWithText("Pounds").performClick()
        waitFor { settings().weightUnit == WeightUnit.LB }

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Clear data"))
        rule.onNodeWithText("Clear data").performClick()
        waitFor { hasLabel("Delete everything") }
        rule.onNodeWithText("Delete everything").performClick()
        waitFor { entries().isEmpty() }
    }

    @Test
    fun anImportedFileWithBadRowsOnlyBringsInTheGoodOnes() {
        val csv = """
            "Date","Kilograms","Calories","TDEE"
            "${today.minusDays(2)}","80.5","2900","2800"
            "${today.minusDays(1)}","1e308","2900",""
            "9999-12-31","80","2900",""
            "${today}","80.2","","2810"
        """.trimIndent()
        val result = CsvFormat.parse(csv)
        assertEquals(2, result.skippedRows)
        runBlocking { app.entryRepository.import(result.entries) }
        waitFor { entries().size == 2 }
        assertNotNull(entries().firstOrNull { it.date == today })

        rule.onNodeWithContentDescription("Progress").performClick()
        rule.onNodeWithText("Table").performClick()
        waitFor { hasLabel(tableDateLabel(today)) }
    }

    @Test
    fun typedTextSurvivesRotation() {
        seed()
        waitForHomeWithEstimate()
        field(0).performTextInput("80.4")
        field(1).performTextInput("3100")
        rule.activityRule.scenario.recreate()
        waitFor { hasLabel("Need to eat") && field(0).value() == "80.4" }
        assertEquals("3100", field(1).value())
    }

    @Test
    fun poundsAndKilojoulesAreShownEverywhere() {
        seed(40)
        runBlocking { app.settingsRepository.update { it.copy(weightUnit = WeightUnit.LB, energyUnit = EnergyUnit.KJ) } }
        waitForHomeWithEstimate()
        listOf("lb", "kJ", "kJ/day", "lb/week").forEach { assertTrue("home is missing $it", hasLabel(it)) }
        shot("12-home-lb-kj")

        rule.onNodeWithContentDescription("Progress").performClick()
        waitFor { hasDescription("Progress graph", substring = true) }
        assertTrue(rule.onAllNodesWithText("lb", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("kJ", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("13-progress-lb-kj")
    }

    @Test
    fun aYearOfDataDrawsEveryRangeAndCanBeScrubbed() {
        runBlocking {
            for (i in 1..450) {
                app.entryRepository.save(today.minusDays(i.toLong()), 70.0 + (450 - i) * 0.02 + (i % 7) * 0.1, 2900.0 + (i % 9) * 40)
            }
        }
        waitForHomeWithEstimate()
        rule.onNodeWithContentDescription("Progress").performClick()
        waitFor { hasDescription("Progress graph", substring = true) }
        for (range in listOf("1M", "3M", "6M", "1Y", "All")) {
            rule.onNodeWithText(range).performClick()
            rule.waitForIdle()
            shot("14-progress-$range")
        }
        assertTrue(hasLabel("Latest", substring = true))
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Progress graph", substring = true))
            .performTouchInput { swipeLeft() }
        waitFor { !hasLabel("Latest", substring = true) }
        shot("15-progress-scrubbed")
    }

    @Test
    fun exportWritesTheOriginalFileFormat() {
        seed(10)
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "export.csv").apply { delete() }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(out)))
        )
        waitForHomeWithEstimate()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor { hasLabel("Days used for calculations") }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Export data to CSV"))
        rule.onNodeWithText("Export data to CSV").performClick()
        waitFor { out.exists() && out.length() > 0 }

        val lines = out.readLines()
        assertEquals("\"Date\",\"Kilograms\",\"Calories\",\"TDEE\"", lines[0])
        assertEquals(11, lines.size)
        assertTrue(lines[1].startsWith("\"${today.minusDays(1)}\","))
        val row = Regex("^\"\\d{4}-\\d{2}-\\d{2}\",\"[^\"]*\",\"[^\"]*\",\"[^\"]*\"$")
        assertTrue(lines.drop(1).all { row.matches(it) })
        // And what was exported can be read back unchanged.
        assertEquals(entries(), CsvFormat.parse(out.readText()).entries)
    }

    @Test
    fun importReadsTheOriginalFileFormatAndSkipsBadRows() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "import.csv")
        file.writeText(
            """
            "Date","Kilograms","Calories","TDEE"
            "${today.minusDays(3)}","79.7","","2925"
            "${today.minusDays(2)}","80.2","2500","2883"
            "${today.minusDays(1)}","","3000","2422"
            "not a date","80","2000",""
            "${today.minusDays(4)}","1e308","2000",""
            """.trimIndent()
        )
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        )
        waitFor { hasLabel("Need to eat") }
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor { hasLabel("Days used for calculations") }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Import data from CSV"))
        rule.onNodeWithText("Import data from CSV").performClick()
        waitFor { hasLabel("Imported 3 days, skipped 2 invalid rows") }

        val byDate = entries().associateBy { it.date }
        assertEquals(3, byDate.size)
        assertEquals(79.7, byDate.getValue(today.minusDays(3)).weightKg!!, 1e-9)
        assertNull(byDate.getValue(today.minusDays(3)).calories)
        assertEquals(2500.0, byDate.getValue(today.minusDays(2)).calories!!, 1e-9)
        assertNull(byDate.getValue(today.minusDays(1)).weightKg)
        shot("16-after-import")
    }
}
