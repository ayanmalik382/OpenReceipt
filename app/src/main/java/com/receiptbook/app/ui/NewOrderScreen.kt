package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.Loc
//import com.receiptbook.app.i18n.money
//import com.receiptbook.app.i18n.numFmt
import kotlinx.coroutines.launch

/** One editable line in the cart. Text fields bind straight to qty / price. */
class CartRow(
    val key: Int, val name: String, val unit: String, val productId: String?, val trackStock: Boolean, val stock: Double,
    qty: String = "1", price: String = ""
) {
    var qty by mutableStateOf(qty)
    var price by mutableStateOf(price)
    val qtyNum: Double get() = Fmt.parse(qty) ?: 0.0
    val priceNum: Double get() = Fmt.parse(price) ?: 0.0
    val total: Double get() = qtyNum * priceNum
}

class OrderDraftViewModel(private val c: AppContainer, private val bid: String, presetCustomerId: String?) : ViewModel() {
    var customer by mutableStateOf<Customer?>(null)
    var term by mutableStateOf(Term.CASH)
    val rows = mutableStateListOf<CartRow>()
    var discount by mutableStateOf("")
    var received by mutableStateOf("")
    var receivedEdited by mutableStateOf(false)
    var note by mutableStateOf("")
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var nextKey = 1

    init {
        if (presetCustomerId != null) viewModelScope.launch { customer = c.db.dao().customer(presetCustomerId) }
    }

    val subtotal: Double get() = rows.sumOf { it.total }
    val discountNum: Double get() = (Fmt.parse(discount) ?: 0.0).coerceIn(0.0, subtotal)
    val total: Double get() = Fmt.round2(subtotal - discountNum)
    val receivedNum: Double
        get() = if (receivedEdited) (Fmt.parse(received) ?: 0.0) else if (term == Term.CASH) total else 0.0

    fun addProduct(p: Product, loc: Loc) {
        val ex = rows.firstOrNull { it.productId == p.id }
        if (ex != null) ex.qty = loc.numFmt((ex.qtyNum + 1)).replace(",", "")
        else rows.add(CartRow(nextKey++, p.name, p.unit, p.id, p.trackStock, p.stockQty, "1", loc.numFmt(p.price).replace(",", "")))
    }

    fun addCustom(name: String, qty: Double, price: Double, loc: Loc) {
        rows.add(CartRow(nextKey++, name, "pcs", null, false, 0.0, loc.numFmt(qty).replace(",", ""), loc.numFmt(price).replace(",", "")))
    }

//    fun setTerm(t: String) { term = t; receivedEdited = false; received = "" }

    fun save(bizId: String, onSaved: (String) -> Unit) {
        if (saving) return
        val lines = rows.map { CartLine(it.name, it.unit, it.qtyNum, it.priceNum, it.productId, it.trackStock) }
        saving = true
        viewModelScope.launch {
            try {
                val o = c.repo.createOrder(bizId, customer, "", lines, discountNum, receivedNum, term, note)
                onSaved(o.id)
            } catch (e: Exception) {
                error = e.message ?: "Could not save"
            } finally { saving = false }
        }
    }
}

@Composable
fun NewOrderScreen(c: AppContainer, bid: String, presetCustomerId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val loc = L.current
    val biz by remember { c.db.dao().observeBusiness(bid) }.collectAsStateWithLifecycle(null)
    val vmB = vm { BizViewModel(c, bid) }
    val products by vmB.products.collectAsStateWithLifecycle()
    val customers by vmB.customers.collectAsStateWithLifecycle()
    val balances by vmB.balances.collectAsStateWithLifecycle()
    val d = vm { OrderDraftViewModel(c, bid, presetCustomerId) }
    val scope = rememberCoroutineScope()
    var pickCustomer by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }
    var pickProduct by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }

    val b = biz
    val cur = b?.currency ?: "Rs"
    LaunchedEffect(b?.allowCash, b?.allowCredit) {
        if (b != null && d.term == Term.CASH && !b.allowCash && b.allowCredit) {
            d.term = Term.CREDIT
            d.receivedEdited = false
            d.received = ""
        }
    }
    val prev = d.customer?.let { balances[it.id] } ?: 0.0
    val extra = Fmt.round2((d.receivedNum - d.total).coerceAtLeast(0.0))
    val newBalance = Fmt.round2(prev + d.total - minOf(d.receivedNum, d.total) - extra)

    AppScaffold(loc.t(R.string.new_receipt_title), onBack) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- customer
            item {
                ElevatedCard(Modifier.fillMaxWidth().clickable { pickCustomer = true }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(d.customer?.name ?: loc.t(R.string.walk_in_tap_to_choose), style = MaterialTheme.typography.titleSmall)
                            if (d.customer != null) {
                                if (prev > 0.004) Text(loc.t(R.string.previous_arrears, loc.money(cur, prev)), color = Red, style = MaterialTheme.typography.bodyMedium)
                                else if (prev < -0.004) Text(loc.t(R.string.advance_credit_amount, loc.money(cur, -prev)), color = Green)
                                else Text(loc.t(R.string.no_arrears), color = Green)
                            }
                        }
                        Text(loc.t(R.string.change), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // ---- term
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(loc.t(R.string.payment_colon))

                    if (b?.allowCash != false) {
                        FilterChip(
                            selected = d.term == Term.CASH,
                            onClick = {
                                d.term = Term.CASH
                                d.receivedEdited = false
                                d.received = ""
                            },
                            label = { Text(loc.t(R.string.term_cash)) }
                        )
                    }

                    if (b?.allowCredit != false) {
                        FilterChip(
                            selected = d.term == Term.CREDIT,
                            onClick = {
                                d.term = Term.CREDIT
                                d.receivedEdited = false
                                d.received = ""
                            },
                            label = { Text(loc.t(R.string.rc_credit)) }
                        )
                    }
                }
            }
            // ---- items
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickProduct = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(loc.t(R.string.add_products)) }
                    OutlinedButton(onClick = { custom = true }, modifier = Modifier.weight(1f)) { Text(loc.t(R.string.custom_item)) }
                }
            }
            if (d.rows.isEmpty()) item { Text(loc.t(R.string.no_items_yet), color = MaterialTheme.colorScheme.outline) }
            items(d.rows, key = { it.key }) { r ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { d.rows.remove(r) }) { Icon(Icons.Filled.Delete, loc.t(R.string.delete), tint = Red) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Field(r.qty, { r.qty = it }, loc.t(R.string.qty_unit, loc.unit(r.unit)), Modifier.weight(1f), KeyboardType.Decimal)
                            Field(r.price, { r.price = it }, loc.t(R.string.price), Modifier.weight(1f), KeyboardType.Decimal)
                            Text(loc.numFmt(r.total), Modifier.widthIn(min = 64.dp), style = MaterialTheme.typography.titleSmall)
                        }
                        if (r.trackStock && r.qtyNum > r.stock) Text(loc.t(R.string.only_x_in_stock, loc.numFmt(r.stock), loc.unit(r.unit)), color = Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // ---- totals
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MoneyLine(loc.t(R.string.subtotal), loc.money(cur, d.subtotal))
                        Field(d.discount, { d.discount = it }, loc.t(R.string.discount_currency, cur), keyboard = KeyboardType.Decimal)
                        MoneyLine(loc.t(R.string.bill_total), loc.money(cur, d.total), bold = true)
                        Field(
                            if (d.receivedEdited) d.received else loc.numFmt(d.receivedNum).replace(",", ""),
                            { d.received = it; d.receivedEdited = true },
                            if (d.customer != null && prev > 0.004) loc.t(R.string.received_now_arrears) else loc.t(R.string.received_now),
                            keyboard = KeyboardType.Decimal
                        )
                        if (d.total - d.receivedNum > 0.004) MoneyLine(loc.t(R.string.left_unpaid), loc.money(cur, d.total - d.receivedNum), color = Red)
                        if (extra > 0.004) MoneyLine(loc.t(R.string.applied_to_arrears), loc.money(cur, extra), color = Green)
                        if (d.customer != null) {
                            HorizontalDivider()
                            MoneyLine(if (newBalance >= 0) loc.t(R.string.customer_will_owe) else loc.t(R.string.advance_credit), loc.money(cur, Math.abs(newBalance)), bold = true, color = if (newBalance > 0.004) Red else Green)
                        }
                    }
                }
            }
            item { Field(d.note, { d.note = it }, loc.t(R.string.note_on_receipt)) }
            item { ErrorText(d.error) }
            item {
                Button(
                    onClick = {
                        d.error = when {
                            d.rows.isEmpty() -> loc.t(R.string.err_add_product)
                            d.rows.any { it.qtyNum <= 0 } -> loc.t(R.string.err_qty)
                            d.rows.any { it.priceNum < 0 } -> loc.t(R.string.err_price_negative)
                            d.term == Term.CREDIT && d.customer == null -> loc.t(R.string.err_credit_customer)
                            else -> null
                        }
                        if (d.error == null) d.save(bid, onSaved)
                    },
                    enabled = !d.saving && b != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (d.saving) loc.t(R.string.saving) else loc.t(R.string.generate_receipt), style = MaterialTheme.typography.titleMedium) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ---- dialogs
    if (pickCustomer) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pickCustomer = false },
            title = { Text(loc.t(R.string.choose_customer)) },
            text = {
                Column {
                    Field(q, { q = it }, loc.t(R.string.search))
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { d.customer = null; pickCustomer = false }) { Text(loc.t(R.string.walk_in_no_record)) }
                        TextButton(onClick = { pickCustomer = false; newCustomer = true }) { Text(loc.t(R.string.new_customer_plus)) }
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(customers.filter { it.name.contains(q, true) || it.phone.contains(q) }, key = { it.id }) { cu ->
                            val bal = balances[cu.id] ?: 0.0
                            ListItem(
                                modifier = Modifier.clickable { d.customer = cu; pickCustomer = false },
                                headlineContent = { Text(cu.name) },
                                supportingContent = { if (bal > 0.004) Text(loc.t(R.string.owes_amount, loc.money(cur, bal)), color = Red) else if (cu.phone.isNotBlank()) Text(cu.phone) }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickCustomer = false }) { Text(loc.t(R.string.close)) } }
        )
    }
    if (newCustomer) CustomerDialog(bid, null, onSave = { cu -> scope.launch { c.repo.save(cu); d.customer = cu; newCustomer = false } }, onDelete = null, onDismiss = { newCustomer = false })

    if (pickProduct) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pickProduct = false },
            title = { Text(loc.t(R.string.tap_products_to_add)) },
            text = {
                Column {
                    Field(q, { q = it }, loc.t(R.string.search_products))
                    Spacer(Modifier.height(8.dp))
                    val list = products.filter { it.name.contains(q, true) }
                    if (list.isEmpty()) Text(loc.t(R.string.no_products_add_hint))
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.id }) { p ->
                            val inCart = d.rows.firstOrNull { it.productId == p.id }
                            ListItem(
                                modifier = Modifier.clickable { d.addProduct(p, loc) },
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text("${loc.money(cur, p.price)} / ${loc.unit(p.unit)}" + if (p.trackStock) "  •  ${loc.numFmt(p.stockQty)}" else "") },
                                trailingContent = { if (inCart != null) Text("× ${inCart.qty}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickProduct = false }) { Text(loc.t(R.string.done_count, d.rows.size)) } }
        )
    }
    if (custom) {
        var n by remember { mutableStateOf("") }
        var q by remember { mutableStateOf("1") }
        var pr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text(loc.t(R.string.custom_item)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(n, { n = it }, loc.t(R.string.item_name))
                    Field(q, { q = it }, loc.t(R.string.quantity), keyboard = KeyboardType.Decimal)
                    Field(pr, { pr = it }, loc.t(R.string.price), keyboard = KeyboardType.Decimal)
                }
            },
            confirmButton = {
                TextButton(enabled = n.isNotBlank() && (Fmt.parse(q) ?: 0.0) > 0 && (Fmt.parse(pr) ?: -1.0) >= 0, onClick = {
                    d.addCustom(n.trim(), Fmt.parse(q) ?: 1.0, Fmt.parse(pr) ?: 0.0, loc); custom = false
                }) { Text(loc.t(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text(loc.t(R.string.cancel)) } }
        )
    }
}
