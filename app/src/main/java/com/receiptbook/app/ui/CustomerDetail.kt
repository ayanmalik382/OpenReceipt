package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
fun CustomerDetailScreen(c: AppContainer, bid: String, cid: String, onBack: () -> Unit, onNewOrder: () -> Unit, onOpenOrder: (String) -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val dao = c.db.dao()
    val biz by vm.business.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val customer by dao.observeCustomer(cid).collectAsStateWithLifecycle(null)
    val orders by dao.observeCustomerOrders(cid).collectAsStateWithLifecycle(emptyList())
    val payments by dao.observeCustomerPayments(cid).collectAsStateWithLifecycle(emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var payDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    val cu = customer
    val b = biz
    val bal = balances[cid] ?: 0.0
    val cur = b?.currency ?: "Rs"

    AppScaffold(cu?.name ?: loc.t(R.string.customers), onBack, snackbar = snack, actions = {
        IconButton(onClick = { editDialog = true }) { Icon(Icons.Filled.Edit, loc.t(R.string.edit)) }
    }) { pad ->
        if (cu == null || b == null) return@AppScaffold
        val ledger = remember(loc, cu, orders, payments) { Repository.ledger(loc, cu, orders, payments) }
        val unpaid = remember(cu, orders, payments) { Repository.unpaidBills(cu, orders, payments) }
        Column(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                  Avatar(cu.photoBase64, size = 56.dp, placeholder = androidx.compose.material.icons.Icons.Filled.Person)
                  Column {
                    Text(
                        when { bal > 0.004 -> loc.t(R.string.owes_you); bal < -0.004 -> loc.t(R.string.advance_credit); else -> loc.t(R.string.account_settled) },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(loc.money(cur, Math.abs(bal)), style = MaterialTheme.typography.headlineMedium, color = if (bal > 0.004) Red else Green)
                    if (cu.phone.isNotBlank()) Text(cu.phone, style = MaterialTheme.typography.bodyMedium)
                    if (cu.address.isNotBlank()) Text(cu.address, style = MaterialTheme.typography.bodySmall)
                  }
                }
            }
            // ---- Unpaid-bills check: which bills still have money owing
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(loc.t(R.string.unpaid_bills), style = MaterialTheme.typography.titleSmall)
                    if (unpaid.isEmpty) Text(loc.t(R.string.no_unpaid_bills), color = Green, style = MaterialTheme.typography.bodyMedium)
                    if (unpaid.openingDue > 0.004) MoneyLine(loc.t(R.string.ledger_opening), loc.money(cur, unpaid.openingDue), color = Red)
                    Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                    unpaid.bills.forEach { ub ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenOrder(ub.order.id) }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(ub.order.receiptNo, style = MaterialTheme.typography.bodyMedium)
                                Text(loc.date(ub.order.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(loc.money(cur, ub.due), color = Red, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    }
                    if (!unpaid.isEmpty) {
                        HorizontalDivider()
                        MoneyLine(loc.t(R.string.total_to_collect), loc.money(cur, unpaid.total), bold = true, color = Red)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewOrder, modifier = Modifier.weight(1f)) { Text(loc.t(R.string.new_receipt)) }
                FilledTonalButton(onClick = { payDialog = true }, modifier = Modifier.weight(1f)) { Text(loc.t(R.string.receive_payment)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ExportMenu(Exporter.PDF, loc.t(R.string.share_statement), { withContext(Dispatchers.IO) { Reports.statementPdf(ctx, loc, b, cu, ledger, bal) } }) { open ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = open) { Text(loc.t(R.string.statement_pdf)) }
                    }
                }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = bal > 0.004, onClick = {
                    Exporter.shareText(ctx, loc.t(R.string.reminder_message, cu.name, b.name, loc.money(cur, bal)), loc.t(R.string.send_reminder_title))
                }) { Text(loc.t(R.string.send_reminder)) }
            }
            Text(loc.t(R.string.account_history), style = MaterialTheme.typography.titleSmall)
            if (ledger.isEmpty()) Text(loc.t(R.string.no_transactions_yet), color = MaterialTheme.colorScheme.outline)
            LazyColumn {
                items(ledger.reversed()) { r ->
                    ListItem(
                        headlineContent = { Text(r.details) },
                        supportingContent = { Text(if (r.date == 0L) "" else loc.date(r.date)) },
                        trailingContent = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                if (r.debit != 0.0) Text("+ ${loc.numFmt(r.debit)}", color = Red)
                                if (r.credit != 0.0) Text("- ${loc.numFmt(r.credit)}", color = Green)
                                Text(loc.t(R.string.balance_short, loc.numFmt(r.balance)), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (payDialog && cu != null) {
        var amount by remember { mutableStateOf(if (bal > 0.004) loc.numFmt(bal).replace(",", "") else "") }
        var method by remember { mutableStateOf("Cash") }
        var note by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { payDialog = false },
            title = { Text(loc.t(R.string.receive_payment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(amount, { amount = it }, loc.t(R.string.received_now), keyboard = KeyboardType.Decimal)
                    Picker(loc.t(R.string.method), loc.method(method), PAYMENT_METHODS.map { loc.method(it) to it }, { method = it })
                    Field(note, { note = it }, loc.t(R.string.note_optional))
                    ErrorText(err)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = Fmt.parse(amount)
                    if (a == null || a <= 0) err = loc.t(R.string.err_valid_amount)
                    else scope.launch { c.repo.addPayment(bid, cid, a, method, note); payDialog = false }
                }) { Text(loc.t(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { payDialog = false }) { Text(loc.t(R.string.cancel)) } }
        )
    }
    if (editDialog && cu != null) CustomerDialog(bid, cu,
        onSave = { scope.launch { c.repo.save(it); editDialog = false } },
        onDelete = { editDialog = false; deleteDialog = true }, onDismiss = { editDialog = false })
    if (deleteDialog && cu != null) ConfirmDialog(loc.t(R.string.delete_customer_title), loc.t(R.string.delete_customer_body, cu.name), loc.t(R.string.delete),
        onConfirm = {
            scope.launch {
                val err = c.repo.remove(cu)
                deleteDialog = false
                if (err == null) onBack() else snack.showSnackbar(err)
            }
        }, onDismiss = { deleteDialog = false })
}
