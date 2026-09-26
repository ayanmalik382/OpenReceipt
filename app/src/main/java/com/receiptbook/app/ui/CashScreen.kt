package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.money
import kotlinx.coroutines.launch

@Composable
fun CashScreen(c: AppContainer, bid: String, onBack: () -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val biz by vm.business.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var delPay by remember { mutableStateOf<Payment?>(null) }
    var delExp by remember { mutableStateOf<Expense?>(null) }
    val cur = biz?.currency ?: "Rs"
    val custName = customers.associate { it.id to it.name }
    val supName = suppliers.associate { it.id to it.name }

    AppScaffold(loc.t(R.string.payments_and_expenses), onBack, snackbar = snack, fab = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(if (tab == 0) loc.t(R.string.receive_payment) else loc.t(R.string.add_expense)) })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(loc.t(R.string.money_in)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(loc.t(R.string.money_out)) })
            }
            DateRangeBar(range) { vm.range.value = it }
            if (tab == 0) {
                MoneyLine(loc.t(R.string.total_received), loc.money(cur, payments.sumOf { it.amount }), bold = true, color = Green)
                if (payments.isEmpty()) EmptyState(loc.t(R.string.no_payments_period))
                else LazyColumn {
                    items(payments, key = { it.id }) { p ->
                        ListItem(
                            modifier = Modifier.clickable { delPay = p },
                            headlineContent = { Text(custName[p.customerId] ?: loc.t(R.string.customer_label)) },
                            supportingContent = { Text("${loc.dateTime(p.date)}  •  ${loc.method(p.method)}" + if (p.note.isNotBlank()) "  •  ${p.note}" else "") },
                            trailingContent = { Text(loc.money(cur, p.amount), color = Green, style = MaterialTheme.typography.titleSmall) }
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                MoneyLine(loc.t(R.string.total_spent), loc.money(cur, expenses.sumOf { it.amount }), bold = true, color = Red)
                if (expenses.isEmpty()) EmptyState(loc.t(R.string.no_expenses_period))
                else LazyColumn {
                    items(expenses, key = { it.id }) { e ->
                        ListItem(
                            modifier = Modifier.clickable { delExp = e },
                            headlineContent = { Text(loc.category(e.category) + (supName[e.supplierId]?.let { " – $it" } ?: "")) },
                            supportingContent = { Text(loc.dateTime(e.date) + if (e.note.isNotBlank()) "  •  ${e.note}" else "") },
                            trailingContent = { Text(loc.money(cur, e.amount), color = Red, style = MaterialTheme.typography.titleSmall) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var method by remember { mutableStateOf("Cash") }
        var customerId by remember { mutableStateOf<String?>(null) }
        var supplierId by remember { mutableStateOf<String?>(null) }
        var category by remember { mutableStateOf(EXPENSE_CATEGORIES[0]) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (tab == 0) loc.t(R.string.receive_payment) else loc.t(R.string.add_expense)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == 0) {
                        Picker(loc.t(R.string.customer_label), customers.firstOrNull { it.id == customerId }?.name ?: loc.t(R.string.choose_customer), customers.map { it.name to (it.id as String?) }, { customerId = it })
                        Picker(loc.t(R.string.method), loc.method(method), PAYMENT_METHODS.map { loc.method(it) to it }, { method = it })
                    } else {
                        Picker(loc.t(R.string.category), loc.category(category), EXPENSE_CATEGORIES.map { loc.category(it) to it }, { category = it })
                        Picker(loc.t(R.string.supplier), suppliers.firstOrNull { it.id == supplierId }?.name ?: loc.t(R.string.none), listOf(loc.t(R.string.none) to (null as String?)) + suppliers.map { it.name to (it.id as String?) }, { supplierId = it })
                    }
                    Field(amount, { amount = it }, loc.t(R.string.amount), keyboard = KeyboardType.Decimal)
                    Field(note, { note = it }, loc.t(R.string.note_optional))
                    ErrorText(err)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = Fmt.parse(amount)
                    when {
                        a == null || a <= 0 -> err = loc.t(R.string.err_valid_amount)
                        tab == 0 && customerId == null -> err = loc.t(R.string.err_choose_customer)
                        else -> scope.launch {
                            if (tab == 0) c.repo.addPayment(bid, customerId!!, a, method, note) else c.repo.addExpense(bid, supplierId, category, a, note)
                            showAdd = false
                        }
                    }
                }) { Text(loc.t(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(loc.t(R.string.cancel)) } }
        )
    }
    delPay?.let { p -> ConfirmDialog(loc.t(R.string.delete_payment_title), loc.t(R.string.delete_payment_body, loc.money(cur, p.amount), custName[p.customerId] ?: loc.t(R.string.customer_label)), loc.t(R.string.delete),
        onConfirm = { scope.launch { c.repo.remove(p); delPay = null } }, onDismiss = { delPay = null }) }
    delExp?.let { e -> ConfirmDialog(loc.t(R.string.delete_expense_title), "${loc.category(e.category)}: ${loc.money(cur, e.amount)}", loc.t(R.string.delete),
        onConfirm = { scope.launch { c.repo.remove(e); delExp = null } }, onDismiss = { delExp = null }) }
}
