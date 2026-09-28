package com.receiptbook.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L
import com.receiptbook.app.media.ImageStore
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.receiptbook.app.data.DateRange
import com.receiptbook.app.data.Fmt
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

/**
 * A circular avatar (business logo / customer / supplier photo). Tap to pick a new photo from the
 * gallery, or clear it if one is already set. The picked image is decoded, downsampled and
 * JPEG-compressed by [ImageStore] off the main thread before [onChange] is called with the
 * resulting Base64 string, so callers never have to worry about oversized images themselves.
 */
@Composable
fun ImagePicker(
    base64: String,
    onChange: (String) -> Unit,
    size: Dp = 72.dp,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Store,
    addLabel: String = L.t(R.string.add_photo),
    changeLabel: String = L.t(R.string.change_photo),
    removeLabel: String = L.t(R.string.remove_photo)
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bitmap = remember(base64) { ImageStore.decode(base64) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; error = null
        scope.launch {
            val result = ImageStore.compressToBase64(ctx, uri)
            busy = false
            if (result == null) error = L.t(R.string.image_failed) else onChange(result)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = !busy) { pick.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(size / 3), strokeWidth = 2.dp)
                bitmap != null -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(placeholder, null, Modifier.size(size / 2.2f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (bitmap != null && !busy) {
                IconButton(
                    onClick = { onChange("") },
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) { Icon(Icons.Filled.Close, removeLabel, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { pick.launch("image/*") }, enabled = !busy) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (bitmap != null) changeLabel else addLabel, style = MaterialTheme.typography.labelMedium)
        }
        ErrorText(error)
    }
}

/** Small circular thumbnail for list rows (business list, customer list) - decoded once per row. */
@Composable
fun Avatar(base64: String, size: Dp = 40.dp, placeholder: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Store) {
    val bitmap = remember(base64) { ImageStore.decode(base64) }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(placeholder, null, Modifier.size(size / 2f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
