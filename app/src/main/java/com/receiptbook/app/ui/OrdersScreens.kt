package com.receiptbook.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.export.Exporter
import com.receiptbook.app.export.ReceiptTemplates
import com.receiptbook.app.export.Reports
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Orange = Color(0xFFB26A00)

@Composable
private fun statusOf(o: SaleOrder): Pair<String, Color> {
    val loc = L.current
    return when {
        o.status == Status.CANCELLED -> loc.t(R.string.status_cancelled) to Color.Gray
        o.total - o.paid <= 0.004 -> loc.t(R.string.status_paid) to Green
        o.paid > 0 -> loc.t(R.string.status_partial) to Orange
        else -> loc.t(R.string.status_unpaid) to Red
    }
}

@Composable
fun OrdersScreen(c: AppContainer, bid: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val biz by vm.business.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val orderItems by vm.items.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val cur = biz?.currency ?: "Rs"

    AppScaffold(loc.t(R.string.receipts), onBack, actions = {
        IconButton(onClick = {
            val b = biz
            if (b != null) scope.launch {
                val f = withContext(Dispatchers.IO) { Reports.ordersPdf(ctx, loc, b, range, orders) }
                Exporter.share(ctx, f, Exporter.PDF, loc.t(R.string.share_pdf_report))
            }
        }) { Icon(Icons.Filled.PictureAsPdf, loc.t(R.string.export_pdf)) }
        IconButton(onClick = {
            val b = biz
            if (b != null) scope.launch {
                val f = withContext(Dispatchers.IO) { Reports.workbook(ctx, loc, b, range, orders, orderItems, payments, expenses, customers, suppliers, products) }
                Exporter.share(ctx, f, Exporter.XLSX, loc.t(R.string.share_excel_file))
            }
        }) { Icon(Icons.Filled.TableChart, loc.t(R.string.export_excel)) }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            DateRangeBar(range) { vm.range.value = it }
            val list = orders.filter { query.isBlank() || it.customerName.contains(query, true) || it.receiptNo.contains(query, true) }
            val s = Repository.summarize(list, emptyList(), emptyList())
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    MoneyLine(loc.t(R.string.receipts), list.count { it.status == Status.ACTIVE }.toString())
                    MoneyLine(loc.t(R.string.total_sales), loc.money(cur, s.sales), bold = true)
                    MoneyLine(loc.t(R.string.received), loc.money(cur, s.cashAtSale))
                    MoneyLine(loc.t(R.string.unpaid_on_bills), loc.money(cur, s.creditGiven), color = if (s.creditGiven > 0.004) Red else Color.Unspecified)
                }
            }
            Field(query, { query = it }, loc.t(R.string.search_customer_receipt), modifier = Modifier.fillMaxWidth().padding(12.dp))
            if (list.isEmpty()) EmptyState(loc.t(R.string.no_receipts_period))
            else LazyColumn {
                items(list, key = { it.id }) { o ->
                    val (label, color) = statusOf(o)
                    ListItem(
                        modifier = Modifier.clickable { onOpen(o.id) },
                        headlineContent = { Text("${o.receiptNo}  •  ${o.customerName.ifBlank { loc.t(R.string.rp_walk_in) }}") },
                        supportingContent = { Text(loc.dateTime(o.date)) },
                        trailingContent = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(loc.money(cur, o.total), style = MaterialTheme.typography.titleSmall)
                                Text(label, color = color, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(c: AppContainer, bid: String, oid: String, onBack: () -> Unit) {
    val loc = L.current
    val dao = c.db.dao()
    val order by dao.observeOrder(oid).collectAsStateWithLifecycle(null)
    val biz by dao.observeBusiness(bid).collectAsStateWithLifecycle(null)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf(false) }

    // Receipts render in the BUSINESS's chosen receipt language, which may differ from the app's UI language.
    val rloc = biz?.let { L.receiptLoc(it) } ?: loc
    val bitmap by produceState<Bitmap?>(initialValue = null, order, biz) {
        val o = order
        val b = biz
        value = if (o != null && b != null) {
            withContext(Dispatchers.Default) { Exporter.receiptBitmap(L.receiptLoc(b), ReceiptTemplates.styleFor(b), b, o, dao.items(o.id)) }
        } else null
    }

    AppScaffold(order?.receiptNo ?: loc.t(R.string.receipts), onBack, snackbar = snack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val bmp = bitmap
            if (bmp == null) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            else Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Image(bmp.asImageBitmap(), "Receipt", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
            val o = order
            val b = biz
            if (o != null && b != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Exporter.receiptPdf(ctx, rloc, ReceiptTemplates.styleFor(b), b, o, dao.items(o.id)) }
                            Exporter.share(ctx, f, Exporter.PDF, loc.t(R.string.share_receipt_pdf))
                        }
                    }) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(loc.t(R.string.pdf)) }
                    Button(modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Exporter.receiptPng(ctx, rloc, ReceiptTemplates.styleFor(b), b, o, dao.items(o.id)) }
                            Exporter.share(ctx, f, Exporter.PNG, loc.t(R.string.share_receipt_image))
                        }
                    }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text(loc.t(R.string.image)) }
                }
                if (o.status == Status.ACTIVE) OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(loc.t(R.string.cancel_this_receipt), color = Red)
                }
            }
        }
    }
    if (confirmCancel) ConfirmDialog(
        loc.t(R.string.cancel_receipt_title), loc.t(R.string.cancel_receipt_body), loc.t(R.string.cancel_this_receipt),
        onConfirm = { scope.launch { c.repo.cancelOrder(oid); confirmCancel = false; snack.showSnackbar(loc.t(R.string.receipt_cancelled)) } },
        onDismiss = { confirmCancel = false }
    )
}
