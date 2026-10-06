@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ryan.tdee.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.tdee.core.Analysis
import com.ryan.tdee.core.DayValue
import com.ryan.tdee.core.Entry
import com.ryan.tdee.core.GraphRange
import com.ryan.tdee.core.SeriesMode
import com.ryan.tdee.core.formatNumber
import com.ryan.tdee.core.latestOnOrBefore
import com.ryan.tdee.core.parseNumber
import com.ryan.tdee.core.valueOn
import com.ryan.tdee.ui.TdeeViewModel
import com.ryan.tdee.ui.components.AppIcons
import com.ryan.tdee.ui.components.format
import com.ryan.tdee.ui.components.tableDateLabel
import com.ryan.tdee.ui.theme.LocalSeriesColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ProgressScreen(viewModel: TdeeViewModel, onBack: () -> Unit, onOpenGraphSettings: () -> Unit) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenGraphSettings) { Icon(AppIcons.Tune, "Graph settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
                listOf("Graph", "Table").forEachIndexed { index, title ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            val a = analysis
            if (a != null) {
                // Swiping is left to the graph's scrubbing gesture; the tabs switch pages.
                HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> GraphPage(a, viewModel)
                        else -> TablePage(a, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphPage(analysis: Analysis, viewModel: TdeeViewModel) {
    val settings = analysis.settings
    val graph = analysis.graph
    val colors = LocalSeriesColors.current
    val wu = settings.weightUnit
    val eu = settings.energyUnit

    val endDay = graph.lastDay
    if (endDay == null) {
        EmptyState("Nothing to show yet. Log a few days and your trends will appear here.")
        return
    }

    val series = remember(graph, settings, colors) {
        fun List<DayValue>.weight() = map { DayValue(it.day, wu.fromKg(it.value)) }
        fun List<DayValue>.energy() = map { DayValue(it.day, eu.fromKcal(it.value)) }
        listOf(
            ChartSeries(graph.weight.weight(), graph.weightTrend.weight(), colors.weight, false, settings.weightMode),
            ChartSeries(graph.calories.energy(), graph.caloriesTrend.energy(), colors.calories, true, settings.calorieMode),
            ChartSeries(graph.tdee.energy(), graph.tdeeTrend.energy(), colors.tdee, true, settings.tdeeMode),
        )
    }
    val firstDay = graph.firstDay ?: endDay
    val startDay = settings.graphRange.days?.let { maxOf(firstDay, endDay - it) } ?: firstDay
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp),
    ) {
        ReadoutRow(series, selected ?: endDay, isLatest = selected == null, weightLabel = wu.label, energyLabel = eu.label)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            TdeeChart(
                series = series,
                startDay = startDay,
                endDay = endDay,
                selectedDay = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GraphRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = settings.graphRange == range,
                    onClick = { viewModel.updateSettings { it.copy(graphRange = range) } },
                    shape = SegmentedButtonDefaults.itemShape(index, GraphRange.entries.size),
                    label = { Text(range.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeriesToggle("Weight", colors.weight, settings.weightMode, Modifier.weight(1f)) {
                viewModel.updateSettings { it.copy(weightMode = it.weightMode.next()) }
            }
            SeriesToggle("Calories", colors.calories, settings.calorieMode, Modifier.weight(1f)) {
                viewModel.updateSettings { it.copy(calorieMode = it.calorieMode.next()) }
            }
            SeriesToggle("TDEE", colors.tdee, settings.tdeeMode, Modifier.weight(1f)) {
                viewModel.updateSettings { it.copy(tdeeMode = it.tdeeMode.next()) }
            }
        }
    }
}

/** Values at the scrubbed day, or the latest values when nothing is selected. */
@Composable
private fun ReadoutRow(series: List<ChartSeries>, day: Long, isLatest: Boolean, weightLabel: String, energyLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        AnimatedContent(targetState = day to isLatest, label = "readout", modifier = Modifier.weight(1f)) { (d, latest) ->
            Column {
                Text(
                    (if (latest) "Latest · " else "") + tableDateLabel(LocalDate.ofEpochDay(d)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val labels = listOf(weightLabel, energyLabel, energyLabel)
                    val names = listOf("", "", "TDEE ")
                    series.forEachIndexed { i, s ->
                        val value = s.points.valueOn(d) ?: s.trend.latestOnOrBefore(d)
                        if (value != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(s.color, CircleShape))
                                Text(
                                    "${names[i]}${formatNumber(value, if (i == 0) 2 else 0)} ${labels[i]}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A chip that cycles a series through line + dots → dots → line → hidden. */
@Composable
private fun SeriesToggle(label: String, color: Color, mode: SeriesMode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val active = mode != SeriesMode.OFF
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press",
    )
    val container by animateColorAsState(
        if (active) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "container",
    )
    val content by animateColorAsState(
        if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "content",
    )
    val corner by animateDpAsState(if (active) 24.dp else 12.dp, label = "corner")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        interactionSource = interaction,
        modifier = modifier.scale(scale),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "modeIcon",
            ) { SeriesModeIcon(it, content) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
            Text(
                when (mode) {
                    SeriesMode.BOTH -> "Line + dots"
                    SeriesMode.POINTS -> "Dots"
                    SeriesMode.LINE -> "Line"
                    SeriesMode.OFF -> "Hidden"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeriesModeIcon(mode: SeriesMode, color: Color) {
    Canvas(Modifier.width(36.dp).height(22.dp)) {
        val pts = listOf(0.1f to 0.7f, 0.37f to 0.3f, 0.63f to 0.6f, 0.9f to 0.25f)
            .map { (fx, fy) -> Offset(size.width * fx, size.height * fy) }
        if (mode == SeriesMode.OFF) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(
                path,
                color.copy(alpha = 0.5f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )
            return@Canvas
        }
        if (mode.showLine) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (mode.showPoints) {
            pts.forEach { drawCircle(color, 3.5.dp.toPx(), it) }
        }
    }
}

@Composable
private fun TablePage(analysis: Analysis, viewModel: TdeeViewModel) {
    val settings = analysis.settings
    val rows = remember(analysis) { analysis.entries.asReversed() }
    var editing by remember { mutableStateOf<Entry?>(null) }

    if (rows.isEmpty()) {
        EmptyState("No entries yet.")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        stickyHeader {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                TableCell("Date", 1.4f, header = true)
                TableCell(settings.weightUnit.label, 1f, header = true)
                TableCell(settings.energyUnit.label, 1f, header = true)
                TableCell("TDEE", 1f, header = true)
            }
        }
        items(rows, key = { it.date.toEpochDay() }) { entry ->
            Surface(onClick = { editing = entry }, color = Color.Transparent, modifier = Modifier.animateItem()) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        TableCell(tableDateLabel(entry.date), 1.4f)
                        TableCell(entry.weightKg?.let { settings.weightUnit.format(it) }.orEmpty(), 1f)
                        TableCell(entry.calories?.let { settings.energyUnit.format(it) }.orEmpty(), 1f)
                        TableCell(analysis.tdeeByDate[entry.date]?.let { settings.energyUnit.format(it) }.orEmpty(), 1f)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            analysis = analysis,
            onDismiss = { editing = null },
            onSave = { w, c -> viewModel.save(entry.date, w, c) },
            onDelete = { viewModel.delete(entry.date) },
        )
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, header: Boolean = false) {
    Text(
        text,
        style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
        fontWeight = if (header) FontWeight.SemiBold else null,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun EditEntryDialog(
    entry: Entry,
    analysis: Analysis,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?) -> Unit,
    onDelete: () -> Unit,
) {
    val wu = analysis.settings.weightUnit
    val eu = analysis.settings.energyUnit
    var weight by remember { mutableStateOf(entry.weightKg?.let { wu.format(it) }.orEmpty()) }
    var calories by remember { mutableStateOf(entry.calories?.let { formatNumber(eu.fromKcal(it), 1) }.orEmpty()) }
    val weightValue = parseNumber(weight)
    val caloriesValue = parseNumber(calories)
    val weightError = weight.isNotBlank() && (weightValue == null || weightValue <= 0)
    val caloriesError = calories.isNotBlank() && (caloriesValue == null || caloriesValue < 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tableDateLabel(entry.date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") },
                    suffix = { Text(wu.label) },
                    singleLine = true,
                    isError = weightError,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories") },
                    suffix = { Text(eu.label) },
                    singleLine = true,
                    isError = caloriesError,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !weightError && !caloriesError,
                onClick = {
                    onSave(
                        weightValue?.takeIf { weight.isNotBlank() }?.let(wu::toKg),
                        caloriesValue?.takeIf { calories.isNotBlank() }?.let(eu::toKcal),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
