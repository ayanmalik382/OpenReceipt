package com.receiptbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.Repository
import com.receiptbook.app.data.Status
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.money
import com.receiptbook.app.i18n.numFmt

@Composable
fun HomeScreen(
    c: AppContainer, bid: String, onBack: () -> Unit, go: (String) -> Unit
) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val biz by vm.business.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val orders by vm.todayOrders.collectAsStateWithLifecycle()
    val pays by vm.todayPayments.collectAsStateWithLifecycle()
    val exps by vm.todayExpenses.collectAsStateWithLifecycle()
    val cur = biz?.currency ?: "Rs"
    val today = Repository.summarize(orders, pays, exps)
    val receivable = balances.values.filter { it > 0.004 }.sum()
    val low = products.count { it.trackStock && it.stockQty <= it.lowStockAt }

    AppScaffold(biz?.name ?: "", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- Business identity header (logo shown here, and reused on every receipt/export)
            biz?.let { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (b.logoBase64.isNotBlank()) Avatar(b.logoBase64, size = 56.dp)
                    else Box(
                        Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Store, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp)) }
                    Column {
                        Text(b.name, style = MaterialTheme.typography.titleLarge)
                        if (b.address.isNotBlank()) Text(b.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // ---- The 4 at-a-glance numbers, right under the business header
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(loc.t(R.string.todays_sales), loc.money(cur, today.sales), loc.t(R.string.receipts_count, orders.count { it.status == Status.ACTIVE }), Modifier.weight(1f))
                Stat(loc.t(R.string.cash_in_today), loc.money(cur, today.cashIn), loc.t(R.string.out_amount, loc.numFmt(today.expenses)), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(loc.t(R.string.customers_owe_you), loc.money(cur, receivable), loc.t(R.string.customers_count, balances.values.count { it > 0.004 }), Modifier.weight(1f), if (receivable > 0) Red else Green)
                Stat(loc.t(R.string.low_stock), "$low", loc.t(R.string.products_to_refill), Modifier.weight(1f), if (low > 0) Red else Green)
            }

            // ---- New receipt/order sits right below those 4 numbers
            Button(onClick = { go("neworder/$bid") }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Filled.Receipt, null); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.new_receipt_order), style = MaterialTheme.typography.titleMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.Filled.ReceiptLong, loc.t(R.string.tile_receipts), Modifier.weight(1f)) { go("orders/$bid") }
                Tile(Icons.Filled.People, loc.t(R.string.tile_customers), Modifier.weight(1f)) { go("customers/$bid") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.Filled.Inventory2, loc.t(R.string.tile_products), Modifier.weight(1f)) { go("products/$bid") }
                Tile(Icons.Filled.LocalShipping, loc.t(R.string.tile_suppliers), Modifier.weight(1f)) { go("suppliers/$bid") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.Filled.AccountBalanceWallet, loc.t(R.string.tile_cash), Modifier.weight(1f)) { go("cash/$bid") }
                Tile(Icons.Filled.Assessment, loc.t(R.string.tile_reports), Modifier.weight(1f)) { go("reports/$bid") }
            }
            Tile(Icons.Filled.Settings, loc.t(R.string.business_settings), Modifier.fillMaxWidth()) { go("business/edit/$bid") }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ReceiptBook", style = MaterialTheme.typography.titleSmall)
                Text("Developed by Conscitool", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text("Contact: +92 339 8000402", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, sub: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
