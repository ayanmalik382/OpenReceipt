package com.receiptbook.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.export.Exporter
import com.receiptbook.app.export.ReceiptStyle
import com.receiptbook.app.export.ReceiptTemplates
import com.receiptbook.app.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun sampleOrder(bid: String) = SaleOrder(
    id = "sample", businessId = bid, receiptNo = "R-0007", customerId = "c1", customerName = "Ali Traders",
    customerPhone = "0300 1234567", date = System.currentTimeMillis(), term = Term.CASH,
    subtotal = 3200.0, discount = 200.0, total = 3000.0, paid = 3000.0, previousBalance = 500.0,
)

private fun sampleItems(bid: String) = listOf(
    OrderItem("i1", bid, "sample", null, "Rice (Basmati)", "kg", 5.0, 320.0, 1600.0),
    OrderItem("i2", bid, "sample", null, "Cooking Oil", "liter", 4.0, 400.0, 1600.0),
)

@Composable
fun TemplateScreen(c: AppContainer, bid: String, onBack: () -> Unit) {
    val loc = L.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val biz by remember { c.db.dao().observeBusiness(bid) }.collectAsStateWithLifecycle(null)
    var customizing by remember { mutableStateOf(false) }
    var previewStyle by remember { mutableStateOf<ReceiptStyle?>(null) }

    val b = biz ?: return AppScaffold(loc.t(R.string.receipt_template), onBack) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    val current = ReceiptTemplates.styleFor(b)

    fun apply(templateId: String, config: String = "") {
        scope.launch { c.repo.save(b.copy(templateId = templateId, templateConfig = config)) }
    }

    AppScaffold(loc.t(R.string.receipt_template), onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ReceiptTemplates.presetIds) { id ->
                val style = when (id) {
                    "modern" -> ReceiptTemplates.MODERN; "compact" -> ReceiptTemplates.COMPACT
                    "wide" -> ReceiptTemplates.WIDE; else -> ReceiptTemplates.CLASSIC
                }
                val (nameRes, descRes) = when (id) {
                    "modern" -> R.string.template_modern to R.string.template_modern_desc
                    "compact" -> R.string.template_compact to R.string.template_compact_desc
                    "wide" -> R.string.template_wide to R.string.template_wide_desc
                    else -> R.string.template_classic to R.string.template_classic_desc
                }
                TemplateCard(
                    title = loc.t(nameRes), description = loc.t(descRes), style = style,
                    selected = b.templateId == id,
                    onSelect = { apply(id) },
                    onPreview = { previewStyle = style }
                )
            }
            item {
                TemplateCard(
                    title = loc.t(R.string.template_custom), description = loc.t(R.string.template_custom_desc),
                    style = if (b.templateId == "custom") current else current.copy(accentColor = ReceiptTemplates.accentChoices[1]),
                    selected = b.templateId == "custom",
                    onSelect = { customizing = true },
                    onPreview = { previewStyle = if (b.templateId == "custom") current else current }
                )
            }
        }
    }

    if (customizing) {
        CustomTemplateEditor(
            initial = if (b.templateId == "custom") current else ReceiptTemplates.MODERN,
            onSave = { style -> apply("custom", ReceiptTemplates.encode(style)); customizing = false },
            onPreview = { previewStyle = it },
            onDismiss = { customizing = false }
        )
    }

    previewStyle?.let { style ->
        val bitmap by produceState<Bitmap?>(initialValue = null, style, b) {
            value = withContext(Dispatchers.Default) { Exporter.receiptBitmap(L.receiptLoc(b), style, b.copy(name = b.name.ifBlank { "Sample Shop" }), sampleOrder(bid), sampleItems(bid)) }
        }
        AlertDialog(
            onDismissRequest = { previewStyle = null },
            title = { Text(loc.t(R.string.preview)) },
            text = {
                val bmp = bitmap
                if (bmp == null) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().background(Color.White), contentScale = ContentScale.FillWidth)
            },
            confirmButton = { TextButton(onClick = { previewStyle = null }) { Text(loc.t(R.string.close)) } }
        )
    }
}

@Composable
private fun TemplateCard(title: String, description: String, style: ReceiptStyle, selected: Boolean, onSelect: () -> Unit, onPreview: () -> Unit) {
    val loc = L.current
    ElevatedCard(
        Modifier.fillMaxWidth().then(if (selected) Modifier.border(2.dp, Color(style.accentColor), MaterialTheme.shapes.medium) else Modifier)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniReceiptPreview(style, Modifier.width(64.dp).height(84.dp).clip(MaterialTheme.shapes.small).background(Color.White).clickable { onPreview() })
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (selected) {
                AssistChip(onClick = {}, label = { Text(loc.t(R.string.in_use)) }, leadingIcon = { Icon(Icons.Filled.Check, null) })
            } else {
                TextButton(onClick = onSelect) { Text(loc.t(R.string.use_this_template)) }
            }
        }
    }
}

/** A cheap, fast schematic preview (lines and a colored header bar) - not the real renderer, just
 * enough for someone to tell templates apart at a glance in a list. Tap it for the real preview. */
@Composable
private fun MiniReceiptPreview(style: ReceiptStyle, modifier: Modifier) {
    val accent = Color(style.accentColor)
    Canvas(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        val w = size.width; val h = size.height
        if (style.showBadge) drawCircle(accent, radius = w * 0.09f, center = Offset(w / 2, h * 0.12f))
        drawLine(accent, Offset(w * 0.15f, h * 0.24f), Offset(w * 0.85f, h * 0.24f), strokeWidth = if (style.boldHeader) 3f else 1.5f)
        val dashEffect = if (style.dividerStyle == ReceiptStyle.DASHED) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
        val ys = listOf(0.36f, 0.46f, 0.56f, 0.66f)
        ys.forEach { fy -> drawLine(Color.DarkGray.copy(alpha = 0.6f), Offset(w * 0.15f, h * fy), Offset(w * 0.85f, h * fy), strokeWidth = 1f) }
        drawLine(accent, Offset(w * 0.15f, h * 0.76f), Offset(w * 0.85f, h * 0.76f), strokeWidth = 1.5f, pathEffect = dashEffect)
        if (style.dividerStyle == ReceiptStyle.DOUBLE) drawLine(accent, Offset(w * 0.15f, h * 0.79f), Offset(w * 0.85f, h * 0.79f), strokeWidth = 1.5f)
        drawLine(accent, Offset(w * 0.15f, h * 0.86f), Offset(w * 0.6f, h * 0.86f), strokeWidth = 2f)
    }
}

@Composable
private fun CustomTemplateEditor(initial: ReceiptStyle, onSave: (ReceiptStyle) -> Unit, onPreview: (ReceiptStyle) -> Unit, onDismiss: () -> Unit) {
    val loc = L.current
    var accent by remember { mutableStateOf(initial.accentColor) }
    var paper by remember { mutableStateOf(initial.paperWidth) }
    var bold by remember { mutableStateOf(initial.boldHeader) }
    var badge by remember { mutableStateOf(initial.showBadge) }
    var divider by remember { mutableStateOf(initial.dividerStyle) }
    var compact by remember { mutableStateOf(initial.compact) }

    fun current() = ReceiptStyle(paper, accent, bold, badge, divider, compact)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loc.t(R.string.customize_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(loc.t(R.string.accent_color), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptTemplates.accentChoices.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(if (accent == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { accent = c }
                        )
                    }
                }
                Text(loc.t(R.string.paper_size), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = paper == 260f, onClick = { paper = 260f }, label = { Text(loc.t(R.string.paper_58mm)) })
                    FilterChip(selected = paper == 300f, onClick = { paper = 300f }, label = { Text(loc.t(R.string.paper_80mm)) })
                    FilterChip(selected = paper == 480f, onClick = { paper = 480f }, label = { Text(loc.t(R.string.paper_a4)) })
                }
                Text(loc.t(R.string.divider_style), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = divider == ReceiptStyle.SOLID, onClick = { divider = ReceiptStyle.SOLID }, label = { Text(loc.t(R.string.divider_solid)) })
                    FilterChip(selected = divider == ReceiptStyle.DASHED, onClick = { divider = ReceiptStyle.DASHED }, label = { Text(loc.t(R.string.divider_dashed)) })
                    FilterChip(selected = divider == ReceiptStyle.DOUBLE, onClick = { divider = ReceiptStyle.DOUBLE }, label = { Text(loc.t(R.string.divider_double)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(bold, { bold = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.bold_business_name)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(badge, { badge = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.show_logo_badge)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(compact, { compact = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.compact_spacing)) }
                TextButton(onClick = { onPreview(current()) }) { Text(loc.t(R.string.preview)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current()) }) { Text(loc.t(R.string.save_custom_template)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(loc.t(R.string.cancel)) } }
    )
}
