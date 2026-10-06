@file:OptIn(ExperimentalMaterial3Api::class)

package com.ryan.tdee.ui.home

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.tdee.core.Analysis
import com.ryan.tdee.core.DefaultCalories
import com.ryan.tdee.core.Entry
import com.ryan.tdee.core.Plan
import com.ryan.tdee.core.Settings
import com.ryan.tdee.core.formatNumber
import com.ryan.tdee.core.parseNumber
import com.ryan.tdee.ui.TdeeViewModel
import com.ryan.tdee.ui.components.AppIcons
import com.ryan.tdee.ui.components.DatePickerDialogFor
import com.ryan.tdee.ui.components.RollingNumber
import com.ryan.tdee.ui.components.dateLabel
import com.ryan.tdee.ui.components.format
import com.ryan.tdee.ui.components.formatRate
import com.ryan.tdee.ui.components.formatSigned
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HomeScreen(viewModel: TdeeViewModel, onOpenProgress: () -> Unit, onOpenSettings: () -> Unit) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TDEE") },
                actions = {
                    IconButton(onClick = onOpenProgress) { Icon(AppIcons.ShowChart, "Progress") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val a = analysis
        if (a == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                DateSelector(date, onShift = viewModel::shiftDate, onPick = viewModel::selectDate)
                val plan = remember(a, date) { a.plan(date) }
                SummaryCard(plan, a.settings)
                LogCard(
                    date = date,
                    entry = a.entry(date),
                    suggestedCalories = remember(a, date, plan) { suggestedCalories(a, date, plan) },
                    settings = a.settings,
                    onSave = { weightKg, calories ->
                        viewModel.save(date, weightKg, calories)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(
                                if (weightKg == null && calories == null) "Entry cleared" else "Saved"
                            )
                        }
                    },
                )
                val logged = remember(a, date) { a.daysLogged(date) }
                AnimatedVisibility(
                    visible = logged < a.settings.days,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) { AccuracyNotice(logged, a.settings.days) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun suggestedCalories(analysis: Analysis, date: LocalDate, plan: Plan?): Double? =
    when (analysis.settings.defaultCalories) {
        DefaultCalories.NEED_TO_EAT -> plan?.needToEat
        DefaultCalories.PREVIOUS -> analysis.previousCalories(date)
        DefaultCalories.NONE -> null
    }

@Composable
private fun DateSelector(date: LocalDate, onShift: (Long) -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
        }
        AnimatedContent(
            targetState = date,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            contentAlignment = Alignment.Center,
            label = "date",
            modifier = Modifier.weight(1f),
        ) { shown ->
            TextButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(dateLabel(shown, today), style = MaterialTheme.typography.titleMedium)
            }
        }
        FilledTonalIconButton(onClick = { onShift(1) }, enabled = date < today) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
        }
    }
    if (picking) {
        DatePickerDialogFor(initial = date, onDismiss = { picking = false }, onConfirm = onPick)
    }
}

@Composable
private fun SummaryCard(plan: Plan?, settings: Settings) {
    val eu = settings.energyUnit
    val wu = settings.weightUnit
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Need to eat", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(
                    text = plan?.let { eu.format(it.needToEat) } ?: "—",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    eu.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
            }
            Text(
                if (plan == null) {
                    "Log at least two weigh-ins and some calories to get an estimate"
                } else {
                    "Goal ${wu.formatRate(settings.goalKgPerWeek)} ${wu.label}/week"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Stat("TDEE", plan?.let { eu.format(it.tdee) }, eu.label, Modifier.weight(1f))
                Stat("Change needed", plan?.let { eu.formatSigned(it.calorieChangeNeeded) }, "${eu.label}/day", Modifier.weight(1f))
                Stat("Trend", plan?.let { wu.formatRate(it.weightChangePerWeekKg) }, "${wu.label}/week", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        RollingNumber(value ?: "—", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun LogCard(
    date: LocalDate,
    entry: Entry?,
    suggestedCalories: Double?,
    settings: Settings,
    onSave: (weightKg: Double?, calories: Double?) -> Unit,
) {
    val wu = settings.weightUnit
    val eu = settings.energyUnit
    val keys = arrayOf<Any?>(date, entry, wu, eu)
    var weight by rememberSaveable(*keys) {
        mutableStateOf(entry?.weightKg?.let { wu.format(it) }.orEmpty())
    }
    // A day with no entry yet starts from the default calorie value; an existing entry shows exactly what was saved.
    var calories by rememberSaveable(*keys) {
        mutableStateOf(
            if (entry != null) {
                entry.calories?.let { formatNumber(eu.fromKcal(it), 1) }.orEmpty()
            } else {
                suggestedCalories?.let { eu.format(it) }.orEmpty()
            }
        )
    }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1400)
            justSaved = false
        }
    }

    val weightValue = parseNumber(weight)
    val caloriesValue = parseNumber(calories)
    val weightError = weight.isNotBlank() && (weightValue == null || weightValue <= 0 || weightValue > 1500)
    val caloriesError = calories.isNotBlank() && (caloriesValue == null || caloriesValue < 0 || caloriesValue > 100_000)
    val canSave = !weightError && !caloriesError && (weight.isNotBlank() || calories.isNotBlank() || entry != null)

    val focus = LocalFocusManager.current
    val view = LocalView.current
    val save = {
        if (canSave) {
            onSave(
                weightValue?.takeIf { weight.isNotBlank() }?.let(wu::toKg),
                caloriesValue?.takeIf { calories.isNotBlank() }?.let(eu::toKcal),
            )
            focus.clearFocus()
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            )
            justSaved = true
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") },
                    suffix = { Text(wu.label) },
                    singleLine = true,
                    isError = weightError,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text(if (eu.label == "kcal") "Calories" else "Energy") },
                    suffix = { Text(eu.label) },
                    singleLine = true,
                    isError = caloriesError,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.weight(1f),
                )
            }
            val buttonColor by animateColorAsState(
                if (justSaved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                label = "saveColor",
            )
            Button(
                onClick = save,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.align(Alignment.End).height(48.dp),
            ) {
                AnimatedContent(
                    targetState = justSaved,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "saveLabel",
                ) { saved ->
                    if (saved) {
                        Icon(Icons.Filled.Check, "Saved", Modifier.size(20.dp))
                    } else {
                        Text(if (weight.isBlank() && calories.isBlank()) "Clear entry" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyNotice(logged: Long, days: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null)
            Text(
                if (logged <= 0) {
                    "Log your weight and calories every day. The estimate settles after about $days days."
                } else {
                    "Day $logged of $days. The estimate settles once you have about $days days of data, so expect it to move until then."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
