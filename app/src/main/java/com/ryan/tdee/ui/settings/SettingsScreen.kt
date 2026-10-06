@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ryan.tdee.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.tdee.BuildConfig
import com.ryan.tdee.core.Algorithm
import com.ryan.tdee.core.DefaultCalories
import com.ryan.tdee.core.EnergyUnit
import com.ryan.tdee.core.KCAL_PER_KG
import com.ryan.tdee.core.Settings
import com.ryan.tdee.core.ThemeMode
import com.ryan.tdee.core.WeightUnit
import com.ryan.tdee.core.formatNumber
import com.ryan.tdee.core.formatSigned
import com.ryan.tdee.ui.TdeeViewModel
import com.ryan.tdee.ui.components.AppIcons
import com.ryan.tdee.ui.components.Choice
import com.ryan.tdee.ui.components.ChoiceDialog
import com.ryan.tdee.ui.components.ConfirmDialog
import com.ryan.tdee.ui.components.DatePickerDialogFor
import com.ryan.tdee.ui.components.NumberDialog
import com.ryan.tdee.ui.components.dateLabel
import com.ryan.tdee.ui.theme.supportsDynamicColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class Dialog { GOAL, DAYS, ALGORITHM, START_DATE, DEFAULT_CALORIES, CLEAR, ABOUT_ALGORITHM }

@Composable
fun SettingsScreen(viewModel: TdeeViewModel, onBack: () -> Unit, onOpenGraphSettings: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                val result = viewModel.importCsv(text)
                buildString {
                    append("Imported ${result.entries.size} days")
                    if (result.skippedRows > 0) append(", skipped ${result.skippedRows} invalid rows")
                }
            }.getOrElse { "Import failed: ${it.message ?: "unreadable file"}" }
            snackbar.showSnackbar(message)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching {
                val csv = viewModel.exportCsv()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray()) }
                }
                "Exported"
            }.getOrElse { "Export failed: ${it.message ?: "couldn't write file"}" }
            snackbar.showSnackbar(message)
        }
    }

    val wu = s.weightUnit
    val update: ((Settings) -> Settings) -> Unit = viewModel::updateSettings

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            section("Calculation")
            item {
                SettingItem(
                    "Weekly weight change goal",
                    "${formatSigned(wu.fromKg(s.goalKgPerWeek), 2)} ${wu.label}/week · negative to lose, positive to gain",
                ) { dialog = Dialog.GOAL }
            }
            item {
                SettingItem("Days used for calculations", "${s.days} days") { dialog = Dialog.DAYS }
            }
            item {
                SettingItem(
                    "Algorithm",
                    when (s.algorithm) {
                        Algorithm.CLASSIC -> "Classic: matches the original app"
                        Algorithm.SMOOTHED -> "Smoothed: weights recent days more"
                    },
                ) { dialog = Dialog.ALGORITHM }
            }
            item {
                SettingItem(
                    "Calculation starting date",
                    s.startDate?.let { "From ${dateLabel(it)}" } ?: "From the first entry",
                ) { dialog = Dialog.START_DATE }
            }
            item {
                SettingItem(
                    "Default calorie value",
                    when (s.defaultCalories) {
                        DefaultCalories.NEED_TO_EAT -> "\"Need to eat\" value"
                        DefaultCalories.PREVIOUS -> "Last logged calories"
                        DefaultCalories.NONE -> "Empty"
                    },
                ) { dialog = Dialog.DEFAULT_CALORIES }
            }
            item {
                SettingItem("How TDEE is calculated", null) { dialog = Dialog.ABOUT_ALGORITHM }
            }

            section("Appearance")
            item {
                SegmentedSetting(
                    "Theme",
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    s.themeMode,
                ) { mode -> update { it.copy(themeMode = mode) } }
            }
            if (supportsDynamicColor) {
                item {
                    ListItem(
                        headlineContent = { Text("Dynamic colour") },
                        supportingContent = { Text("Use colours from your wallpaper") },
                        trailingContent = {
                            Switch(
                                checked = s.dynamicColor,
                                onCheckedChange = { on -> update { it.copy(dynamicColor = on) } },
                                thumbContent = if (s.dynamicColor) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                                } else null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
            item {
                SettingItem("Graph settings", "Trend line smoothing", trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                    onOpenGraphSettings()
                }
            }

            section("Units")
            item {
                SegmentedSetting("Weight", listOf(WeightUnit.KG to "Kilograms", WeightUnit.LB to "Pounds"), s.weightUnit) { unit ->
                    update { it.copy(weightUnit = unit) }
                }
            }
            item {
                SegmentedSetting("Energy", listOf(EnergyUnit.KCAL to "Calories", EnergyUnit.KJ to "Kilojoules"), s.energyUnit) { unit ->
                    update { it.copy(energyUnit = unit) }
                }
            }

            section("Data")
            item {
                SettingItem(
                    "Import data from CSV",
                    "Format: yyyy-MM-dd,weight,calories. Days already logged are replaced.",
                    leading = AppIcons.Upload,
                ) { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }
            }
            item {
                SettingItem("Export data to CSV", "Exports all data, including TDEE", leading = AppIcons.Download) {
                    exportLauncher.launch("TdeeData.csv")
                }
            }
            item {
                SettingItem("Clear data", "Deletes every entry", leading = Icons.Filled.Delete) { dialog = Dialog.CLEAR }
            }

            section("About")
            item { SettingItem("Version", BuildConfig.VERSION_NAME, onClick = null) }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }

    when (dialog) {
        Dialog.GOAL -> NumberDialog(
            title = "Weekly weight change goal",
            description = "Negative to lose weight, positive to gain. Around ±0.25–0.5 ${wu.label}/week is a sustainable pace for most people.",
            initial = formatNumber(wu.fromKg(s.goalKgPerWeek), 2),
            suffix = "${wu.label}/week",
            allowDecimal = true,
            onDismiss = { dialog = null },
            onConfirm = { v -> update { it.copy(goalKgPerWeek = wu.toKg(v)) } },
            validate = { if (kotlin.math.abs(wu.toKg(it)) > 2) "That's more than 2 kg a week" else null },
            extraContent = { setText ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-0.5, -0.25, 0.0, 0.25, 0.5).forEach { kg ->
                        val text = formatNumber(wu.fromKg(kg), 2)
                        AssistChip(onClick = { setText(text) }, label = { Text(formatSigned(wu.fromKg(kg), 2)) })
                    }
                }
            },
        )
        Dialog.DAYS -> NumberDialog(
            title = "Days used for calculations",
            description = "How many days of data each TDEE estimate uses. More days give a steadier estimate that reacts more slowly.",
            initial = s.days.toString(),
            suffix = "days",
            allowDecimal = false,
            onDismiss = { dialog = null },
            onConfirm = { v -> update { it.copy(days = v.toInt()) } },
            validate = { if (it < 7 || it > 120 || it % 1.0 != 0.0) "Enter a whole number from 7 to 120" else null },
        )
        Dialog.ALGORITHM -> ChoiceDialog(
            title = "Algorithm",
            choices = listOf(
                Choice(
                    Algorithm.CLASSIC,
                    "Classic",
                    "Straight-line fit over the last ${s.days} days. Identical to the original app.",
                ),
                Choice(
                    Algorithm.SMOOTHED,
                    "Smoothed",
                    "Uses more history and weights recent days more (half-life ${s.days} days). Moves far less from day to day and never jumps when an old day drops out.",
                ),
            ),
            selected = s.algorithm,
            onDismiss = { dialog = null },
            onSelect = { a -> update { it.copy(algorithm = a) } },
        )
        Dialog.START_DATE -> DatePickerDialogFor(
            initial = s.startDate ?: LocalDate.now(),
            onDismiss = { dialog = null },
            onConfirm = { d -> update { it.copy(startDate = d) } },
            neutralButton = {
                TextButton(onClick = {
                    update { it.copy(startDate = null) }
                    dialog = null
                }) { Text("Use first entry") }
            },
        )
        Dialog.DEFAULT_CALORIES -> ChoiceDialog(
            title = "Default calorie value",
            choices = listOf(
                Choice(DefaultCalories.NEED_TO_EAT, "\"Need to eat\" value", "Pre-fill new days with your target"),
                Choice(DefaultCalories.PREVIOUS, "Last logged calories", "Pre-fill with what you logged most recently"),
                Choice(DefaultCalories.NONE, "Empty", "Leave the field blank"),
            ),
            selected = s.defaultCalories,
            onDismiss = { dialog = null },
            onSelect = { d -> update { it.copy(defaultCalories = d) } },
        )
        Dialog.CLEAR -> ConfirmDialog(
            title = "Clear all data?",
            message = "Every logged day will be deleted. Export a CSV first if you might want it back.",
            confirmLabel = "Delete everything",
            onDismiss = { dialog = null },
            onConfirm = viewModel::clearData,
        )
        Dialog.ABOUT_ALGORITHM -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("How TDEE is calculated") },
            text = {
                Text(
                    "Your body stores or burns about ${formatNumber(KCAL_PER_KG, 0)} kcal for each kilogram of weight change.\n\n" +
                        "TDEE = average intake − ${formatNumber(KCAL_PER_KG, 0)} × your weight trend (kg/day)\n\n" +
                        "The weight trend is the slope of a best-fit line through your weigh-ins, so one heavy or light morning barely moves it.\n\n" +
                        "Need to eat = TDEE + goal × ${formatNumber(KCAL_PER_KG, 0)} ÷ 7\n\n" +
                        "Change needed = need to eat − your recent average intake.\n\n" +
                        "Days without a weigh-in or calories are simply skipped, but the more days you log the better the estimate."
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Got it") } },
        )
        null -> Unit
    }
}

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }

private fun LazyListScope.section(title: String) {
    item(key = "section-$title") {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SettingItem(
    title: String,
    summary: String?,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = leading?.let { { Icon(it, null) } },
        trailingContent = trailing?.let { { Icon(it, null) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun <T> SegmentedSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label) },
                )
            }
        }
    }
}
