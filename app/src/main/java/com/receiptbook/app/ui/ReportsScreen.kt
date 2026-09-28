package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.export.Exporter
import com.receiptbook.app.export.Reports
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.money
import com.receiptbook.app.i18n.numFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(c: AppContainer, bid: String, onBack: () -> Unit) {
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
    val balances by vm.balances.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur = biz?.currency ?: "Rs"
    var pickedCustomerId by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val pickedCustomer = customers.firstOrNull { it.id == pickedCustomerId }
    // Full history of the chosen customer (not limited to the date range) so balances carry over correctly.
    val custOrders by remember(pickedCustomerId) { pickedCustomerId?.let { c.db.dao().observeCustomerOrders(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val custPays by remember(pickedCustomerId) { pickedCustomerId?.let { c.db.dao().observeCustomerPayments(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())

    val s = Repository.summarize(orders, payments, expenses)
    val top = orderItems.groupBy { it.name }.map { (n, l) -> Triple(n, l.sumOf { it.qty }, l.sumOf { it.lineTotal }) }.sortedByDescending { it.third }.take(8)
    val debtors = customers.mapNotNull { cu -> (balances[cu.id] ?: 0.0).takeIf { it > 0.004 }?.let { cu.name to it } }.sortedByDescending { it.second }
    val low = products.filter { it.trackStock && it.stockQty <= it.lowStockAt }

    AppScaffold(loc.t(R.string.reports_and_export), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DateRangeBar(range) { vm.range.value = it }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(loc.t(R.string.cash_flow), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    MoneyLine(loc.t(R.string.total_sales), loc.money(cur, s.sales))
                    MoneyLine(loc.t(R.string.received_at_sale), loc.money(cur, s.cashAtSale))
                    MoneyLine(loc.t(R.string.arrears_payments_received), loc.money(cur, s.arrearsReceived))
                    MoneyLine(loc.t(R.string.total_cash_in), loc.money(cur, s.cashIn), bold = true, color = Green)
                    MoneyLine(loc.t(R.string.expenses_supplier_payments), loc.money(cur, s.expenses), color = Red)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MoneyLine(loc.t(R.string.net_cash_flow), loc.money(cur, s.net), bold = true, color = if (s.net >= 0) Green else Red)
                    MoneyLine(loc.t(R.string.new_credit_given), loc.money(cur, s.creditGiven))
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(loc.t(R.string.top_products), style = MaterialTheme.typography.titleMedium)
                    if (top.isEmpty()) Text(loc.t(R.string.no_sales_period), color = MaterialTheme.colorScheme.outline)
                    top.forEach { MoneyLine("${it.first}  (${loc.numFmt(it.second)})", loc.money(cur, it.third)) }
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(loc.t(R.string.customers_who_owe_now), style = MaterialTheme.typography.titleMedium)
                    if (debtors.isEmpty()) Text(loc.t(R.string.nobody_owes_you), color = Green)
                    debtors.take(10).forEach { MoneyLine(it.first, loc.money(cur, it.second), color = Red) }
                    if (debtors.isNotEmpty()) MoneyLine(loc.t(R.string.total_to_collect), loc.money(cur, debtors.sumOf { it.second }), bold = true)
                }
            }
            if (low.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(loc.t(R.string.low_stock), style = MaterialTheme.typography.titleMedium)
                    low.forEach { MoneyLine(it.name, "${loc.numFmt(it.stockQty)} ${loc.unit(it.unit)}", color = Red) }
                }
            }
            // ---------------------------------------------------------- Reports by customer
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(loc.t(R.string.reports_by_customer), style = MaterialTheme.typography.titleMedium)
                    val cu = pickedCustomer
                    if (cu == null) {
                        Text(loc.t(R.string.select_customer_report), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        FilledTonalButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text(loc.t(R.string.choose_customer))
                        }
                    } else {
                        val b = biz
                        val period = remember(loc, cu, range, custOrders, custPays) { Reports.customerPeriod(loc, cu, range, custOrders, custPays) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(cu.photoBase64, size = 40.dp, placeholder = Icons.Filled.Person)
                            Column(Modifier.weight(1f)) {
                                Text(loc.t(R.string.customer_report_for, cu.name), style = MaterialTheme.typography.titleSmall)
                                Text(loc.t(R.string.cr_receipts_in_period, period.receipts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            TextButton(onClick = { showPicker = true }) { Text(loc.t(R.string.change_customer)) }
                        }
                        HorizontalDivider()
                        MoneyLine(loc.t(R.string.cr_brought_forward), loc.money(cur, period.broughtForward))
                        MoneyLine(loc.t(R.string.cr_total_billed), loc.money(cur, period.billed))
                        MoneyLine(loc.t(R.string.cr_total_paid), loc.money(cur, period.paid), color = Green)
                        MoneyLine(loc.t(R.string.cr_balance_now), loc.money(cur, period.closing), bold = true, color = if (period.closing > 0.004) Red else Green)
                        if (period.rows.isEmpty()) Text(loc.t(R.string.no_activity_period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        if (b != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    ExportMenu(Exporter.PDF, loc.t(R.string.share_pdf_report), { withContext(Dispatchers.IO) { Reports.customerReportPdf(ctx, loc, b, cu, range, period) } }) { open ->
                                        Button(onClick = open, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.pdf)) }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    ExportMenu(Exporter.XLSX, loc.t(R.string.share_excel_file), { withContext(Dispatchers.IO) { Reports.customerWorkbook(ctx, loc, b, cu, range, period, custOrders, custPays) } }) { open ->
                                        OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("Excel") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(loc.t(R.string.export_for, loc.rangeText(range)), style = MaterialTheme.typography.titleSmall)
                val b = biz
                if (b != null) {
                    ExportMenu(Exporter.XLSX, loc.t(R.string.share_excel_file), { withContext(Dispatchers.IO) { Reports.workbook(ctx, loc, b, range, orders, orderItems, payments, expenses, customers, suppliers, products) } }) { open ->
                        Button(modifier = Modifier.fillMaxWidth(), onClick = open) { Text(loc.t(R.string.export_excel_full)) }
                    }
                    ExportMenu(Exporter.PDF, loc.t(R.string.share_pdf_report), { withContext(Dispatchers.IO) { Reports.ordersPdf(ctx, loc, b, range, orders) } }) { open ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = open) { Text(loc.t(R.string.export_pdf_receipts)) }
                    }
                    ExportMenu(Exporter.PDF, loc.t(R.string.share_pdf_report), { withContext(Dispatchers.IO) { Reports.summaryPdf(ctx, loc, b, range, s, top, debtors) } }) { open ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = open) { Text(loc.t(R.string.export_pdf_summary)) }
                    }
                }
            }
        }
    }

    if (showPicker) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(loc.t(R.string.choose_customer)) },
            text = {
                Column {
                    Field(q, { q = it }, loc.t(R.string.search_name_phone))
                    Spacer(Modifier.height(8.dp))
                    val list = customers.filter { it.name.contains(q, true) || it.phone.contains(q) }
                    if (list.isEmpty()) Text(loc.t(R.string.no_match))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(list, key = { it.id }) { cu ->
                            val bal = balances[cu.id] ?: 0.0
                            ListItem(
                                modifier = Modifier.clickable { pickedCustomerId = cu.id; showPicker = false },
                                leadingContent = { Avatar(cu.photoBase64, size = 36.dp, placeholder = Icons.Filled.Person) },
                                headlineContent = { Text(cu.name) },
                                supportingContent = { if (bal > 0.004) Text(loc.t(R.string.owes_amount, loc.money(cur, bal)), color = Red) else if (cu.phone.isNotBlank()) Text(cu.phone) }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(loc.t(R.string.close)) } }
        )
    }
}
