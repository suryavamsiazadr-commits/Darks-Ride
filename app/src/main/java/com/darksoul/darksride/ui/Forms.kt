package com.darksoul.darksride.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.darksoul.darksride.garage.Days

@Composable
internal fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Numbers only (whole or decimal). */
@Composable
internal fun NumberInput(label: String, value: String, onChange: (String) -> Unit, decimal: Boolean = true) {
    TextInput(
        label = label,
        value = value,
        onChange = { new -> if (new.all { it.isDigit() || (decimal && it == '.') }) onChange(new) },
        keyboard = if (decimal) KeyboardType.Decimal else KeyboardType.Number
    )
}

/** A date button that opens a calendar. [day] is UTC-midnight millis (see Days). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateInput(label: String, day: Long?, onChange: (Long?) -> Unit, clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Text("$label: ${day?.let { Days.format(it) } ?: "not set"}")
        }
        if (clearable && day != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day ?: Days.today())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onChange)
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

internal fun String.toDoubleOrNullSafe(): Double? = trim().toDoubleOrNull()?.takeIf { it >= 0 }
internal fun String.toIntOrNullSafe(): Int? = trim().toIntOrNull()?.takeIf { it >= 0 }
