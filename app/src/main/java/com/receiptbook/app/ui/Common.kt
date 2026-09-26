package com.receiptbook.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.receiptbook.app.R
import com.receiptbook.app.data.DateRange
import com.receiptbook.app.data.Fmt
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.numFmt

val Red = Color(0xFFB3261E)
val Green = Color(0xFF1B7F3B)

class VmFactory<T : ViewModel>(private val make: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
}

@Composable
inline fun <reified T : ViewModel> vm(noinline make: () -> T): T = viewModel<T>(factory = VmFactory(make))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = fab,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        content = content
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, color = Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val loc = L.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel ?: loc.t(R.string.delete), color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(loc.t(R.string.cancel)) } }
    )
}

@Composable
fun <T> Picker(
    label: String,
    selectedText: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selectedText", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (t, v) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onSelect(v) }) }
        }
    }
}

@Composable
fun MoneyLine(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeBar(range: DateRange, onChange: (DateRange) -> Unit) {
    val loc = L.current
    var step by remember { mutableStateOf(0) } // 0 = closed, 1 = pick start, 2 = pick end
    var start by remember { mutableStateOf(0L) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(
                DateRange.today() to loc.t(R.string.range_today), DateRange.last7() to loc.t(R.string.range_7_days),
                DateRange.month() to loc.t(R.string.range_month), DateRange.all() to loc.t(R.string.range_all_time)
            )
            presets.forEach { (r, label) -> FilterChip(selected = range.label == r.label, onClick = { onChange(r) }, label = { Text(label) }) }
            FilterChip(selected = range.label == "Custom", onClick = { step = 1 }, label = { Text(loc.t(R.string.range_pick_dates)) })
        }
        Text(loc.rangeText(range), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
    if (step == 1) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = { TextButton(onClick = { val m = st.selectedDateMillis; if (m != null) { start = Fmt.fromPickerUtc(m); step = 2 } else step = 0 }) { Text(loc.t(R.string.range_next_end_date)) } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(loc.t(R.string.cancel)) } }
        ) { DatePicker(st, title = { Text("  " + loc.t(R.string.range_start_date), Modifier.padding(16.dp)) }) }
    }
    if (step == 2) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val m = st.selectedDateMillis
                    if (m != null) {
                        val end = Fmt.endOfDay(Fmt.fromPickerUtc(m))
                        onChange(DateRange(minOf(start, end), maxOf(Fmt.endOfDay(start), end), "Custom"))
                    }
                    step = 0
                }) { Text(loc.t(R.string.range_apply)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(loc.t(R.string.cancel)) } }
        ) { DatePicker(st, title = { Text("  " + loc.t(R.string.range_end_date), Modifier.padding(16.dp)) }) }
    }
}
