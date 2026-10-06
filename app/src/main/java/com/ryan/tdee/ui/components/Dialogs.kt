package com.ryan.tdee.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ryan.tdee.core.parseNumber
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

private fun LocalDate.toUtcMillis() = toEpochDay() * DAY_MS
private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(this, DAY_MS))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogFor(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    maxDate: LocalDate = LocalDate.now(),
    neutralButton: (@Composable () -> Unit)? = null,
) {
    val maxMillis = maxDate.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = 2000..maxDate.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int) = year <= maxDate.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) }
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = {
            Row {
                neutralButton?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(state) }
}

/** Asks for a number; [validate] returns an error message or null. */
@Composable
fun NumberDialog(
    title: String,
    description: String?,
    initial: String,
    suffix: String?,
    allowDecimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    validate: (Double) -> String? = { null },
    extraContent: (@Composable (setText: (String) -> Unit) -> Unit)? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val value = parseNumber(field.text)
    val error = when {
        field.text.isBlank() -> null
        value == null -> "Not a number"
        else -> validate(value)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    suffix = suffix?.let { { Text(it) } },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                extraContent?.invoke { field = TextFieldValue(it, TextRange(it.length)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && error == null,
                onClick = {
                    value?.let(onConfirm)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

data class Choice<T>(val value: T, val label: String, val description: String? = null)

@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(choice.value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            choice.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
