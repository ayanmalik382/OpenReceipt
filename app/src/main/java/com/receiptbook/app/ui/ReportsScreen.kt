package com.receiptbook.app.ui

import androidx.compose.foundation.layout.*
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
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(loc.t(R.string.export_for, loc.rangeText(range)), style = MaterialTheme.typography.titleSmall)
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val b = biz
                    if (b != null) scope.launch {
                        val f = withContext(Dispatchers.IO) { Reports.workbook(ctx, loc, b, range, orders, orderItems, payments, expenses, customers, suppliers, products) }
                        Exporter.share(ctx, f, Exporter.XLSX, loc.t(R.string.share_excel_file))
                    }
                }) { Text(loc.t(R.string.export_excel_full)) }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    val b = biz
                    if (b != null) scope.launch {
                        val f = withContext(Dispatchers.IO) { Reports.ordersPdf(ctx, loc, b, range, orders) }
                        Exporter.share(ctx, f, Exporter.PDF, loc.t(R.string.share_pdf_report))
                    }
                }) { Text(loc.t(R.string.export_pdf_receipts)) }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    val b = biz
                    if (b != null) scope.launch {
                        val f = withContext(Dispatchers.IO) { Reports.summaryPdf(ctx, loc, b, range, s, top, debtors) }
                        Exporter.share(ctx, f, Exporter.PDF, loc.t(R.string.share_pdf_report))
                    }
                }) { Text(loc.t(R.string.export_pdf_summary)) }
            }
        }
    }
}
