package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
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
import java.util.UUID

// ------------------------------------------------------------------ Products
@Composable
fun ProductsScreen(c: AppContainer, bid: String, onBack: () -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val biz by vm.business.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    val cur = biz?.currency ?: "Rs"

    AppScaffold(
        loc.t(R.string.products_and_stock), onBack, snackbar = snack,
        actions = {
            IconButton(onClick = {
                val b = biz
                if (b != null) scope.launch {
                    val f = withContext(Dispatchers.IO) { Reports.stockPdf(ctx, loc, b, products) }
                    Exporter.share(ctx, f, Exporter.PDF, loc.t(R.string.export_stock_list))
                }
            }) { Icon(Icons.Filled.PictureAsPdf, loc.t(R.string.export_stock_list)) }
        },
        fab = { FloatingActionButton(onClick = { editing = null; showForm = true }) { Icon(Icons.Filled.Add, loc.t(R.string.add_product)) } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Field(query, { query = it }, loc.t(R.string.search_products), modifier = Modifier.fillMaxWidth().padding(12.dp))
            val list = products.filter { it.name.contains(query, ignoreCase = true) }
            if (list.isEmpty()) EmptyState(loc.t(R.string.no_products_yet))
            else LazyColumn {
                items(list, key = { it.id }) { p ->
                    val low = p.trackStock && p.stockQty <= p.lowStockAt
                    ListItem(
                        modifier = Modifier.clickable { editing = p; showForm = true },
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(
                                (if (p.trackStock) loc.t(R.string.stock_line, loc.numFmt(p.stockQty), loc.unit(p.unit)) + if (low) loc.t(R.string.low_stock_suffix) else ""
                                else loc.t(R.string.per_unit, loc.unit(p.unit))),
                                color = if (low) Red else MaterialTheme.colorScheme.outline
                            )
                        },
                        trailingContent = { Text(loc.money(cur, p.price), style = MaterialTheme.typography.titleSmall) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showForm) ProductDialog(
        bid, editing, suppliers,
        onSave = { p -> scope.launch { c.repo.save(p); showForm = false } },
        onDelete = editing?.let { e -> { showForm = false; deleting = e } },
        onDismiss = { showForm = false }
    )
    deleting?.let { p ->
        ConfirmDialog(loc.t(R.string.delete_product_title), loc.t(R.string.delete_product_body, p.name), loc.t(R.string.delete),
            onConfirm = { scope.launch { c.repo.remove(p); deleting = null } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun ProductDialog(
    bid: String, initial: Product?, suppliers: List<Supplier>,
    onSave: (Product) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    val loc = L.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var price by remember { mutableStateOf(initial?.price?.let { loc.numFmt(it) } ?: "") }
    var cost by remember { mutableStateOf(initial?.costPrice?.takeIf { it > 0 }?.let { loc.numFmt(it) } ?: "") }
    var track by remember { mutableStateOf(initial?.trackStock ?: false) }
    var stock by remember { mutableStateOf(initial?.stockQty?.let { loc.numFmt(it) } ?: "0") }
    var low by remember { mutableStateOf(initial?.lowStockAt?.let { loc.numFmt(it) } ?: "0") }
    var supplierId by remember { mutableStateOf(initial?.supplierId) }
    var err by remember { mutableStateOf<String?>(null) }
    val units = listOf("pcs", "kg", "dozen", "meter", "liter", "box", "bag")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) loc.t(R.string.add_product) else loc.t(R.string.edit_product)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, loc.t(R.string.product_name_req))
                Field(price, { price = it }, loc.t(R.string.selling_price_req), keyboard = KeyboardType.Decimal)
                Text(loc.t(R.string.unit), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(loc.unit(u)) }) }
                }
                Field(cost, { cost = it }, loc.t(R.string.cost_price_optional), keyboard = KeyboardType.Decimal)
                Picker(loc.t(R.string.supplier), suppliers.firstOrNull { it.id == supplierId }?.name ?: loc.t(R.string.none),
                    listOf(loc.t(R.string.none) to (null as String?)) + suppliers.map { it.name to (it.id as String?) }, { supplierId = it })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(track, { track = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.track_stock))
                }
                if (track) {
                    Field(stock, { stock = it }, loc.t(R.string.current_stock), keyboard = KeyboardType.Decimal)
                    Field(low, { low = it }, loc.t(R.string.low_stock_warn_at), keyboard = KeyboardType.Decimal)
                }
                ErrorText(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pr = Fmt.parse(price)
                when {
                    name.isBlank() -> err = loc.t(R.string.err_enter_product_name)
                    pr == null || pr < 0 -> err = loc.t(R.string.err_valid_price)
                    else -> onSave(
                        (initial ?: Product(UUID.randomUUID().toString(), bid, name)).copy(
                            name = name.trim(), unit = unit, price = pr, costPrice = Fmt.parse(cost) ?: 0.0,
                            trackStock = track, stockQty = if (track) Fmt.parse(stock) ?: 0.0 else 0.0,
                            lowStockAt = if (track) Fmt.parse(low) ?: 0.0 else 0.0, supplierId = supplierId
                        )
                    )
                }
            }) { Text(loc.t(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(loc.t(R.string.delete), color = Red) }
                TextButton(onClick = onDismiss) { Text(loc.t(R.string.cancel)) }
            }
        }
    )
}

// ------------------------------------------------------------------ Suppliers
@Composable
fun SuppliersScreen(c: AppContainer, bid: String, onBack: () -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Supplier?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Supplier?>(null) }

    AppScaffold(loc.t(R.string.suppliers), onBack, fab = {
        FloatingActionButton(onClick = { editing = null; showForm = true }) { Icon(Icons.Filled.Add, loc.t(R.string.add_supplier)) }
    }) { pad ->
        if (suppliers.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(loc.t(R.string.no_suppliers_yet)) }
        else LazyColumn(Modifier.padding(pad)) {
            items(suppliers, key = { it.id }) { s ->
                ListItem(
                    modifier = Modifier.clickable { editing = s; showForm = true },
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(listOf(s.phone, s.address).filter { it.isNotBlank() }.joinToString("  •  ")) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showForm) {
        val init = editing
        var name by remember(init) { mutableStateOf(init?.name ?: "") }
        var phone by remember(init) { mutableStateOf(init?.phone ?: "") }
        var address by remember(init) { mutableStateOf(init?.address ?: "") }
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (init == null) loc.t(R.string.add_supplier) else loc.t(R.string.edit_supplier)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, loc.t(R.string.supplier_name_req))
                    Field(phone, { phone = it }, loc.t(R.string.phone), keyboard = KeyboardType.Phone)
                    Field(address, { address = it }, loc.t(R.string.address))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        c.repo.save((init ?: Supplier(UUID.randomUUID().toString(), bid, name)).copy(name = name.trim(), phone = phone.trim(), address = address.trim()))
                        showForm = false
                    }
                }) { Text(loc.t(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (init != null) TextButton(onClick = { showForm = false; deleting = init }) { Text(loc.t(R.string.delete), color = Red) }
                    TextButton(onClick = { showForm = false }) { Text(loc.t(R.string.cancel)) }
                }
            }
        )
    }
    deleting?.let { s ->
        ConfirmDialog(loc.t(R.string.delete_supplier_title), loc.t(R.string.delete_supplier_body, s.name), loc.t(R.string.delete),
            onConfirm = { scope.launch { c.repo.remove(s); deleting = null } }, onDismiss = { deleting = null })
    }
}

// ------------------------------------------------------------------ Customers
@Composable
fun CustomerDialog(bid: String, initial: Customer?, onSave: (Customer) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val loc = L.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var opening by remember { mutableStateOf(initial?.openingBalance?.takeIf { it != 0.0 }?.let { loc.numFmt(it) } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) loc.t(R.string.add_customer) else loc.t(R.string.edit_customer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, loc.t(R.string.customer_name_req))
                Field(phone, { phone = it }, loc.t(R.string.phone), keyboard = KeyboardType.Phone)
                Field(address, { address = it }, loc.t(R.string.address))
                Field(opening, { opening = it }, loc.t(R.string.opening_arrears_hint), keyboard = KeyboardType.Decimal)
                ErrorText(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ob = if (opening.isBlank()) 0.0 else Fmt.parse(opening)
                when {
                    name.isBlank() -> err = loc.t(R.string.err_enter_customer_name)
                    ob == null -> err = loc.t(R.string.err_valid_amount)
                    else -> onSave((initial ?: Customer(UUID.randomUUID().toString(), bid, name)).copy(name = name.trim(), phone = phone.trim(), address = address.trim(), openingBalance = ob))
                }
            }) { Text(loc.t(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(loc.t(R.string.delete), color = Red) }
                TextButton(onClick = onDismiss) { Text(loc.t(R.string.cancel)) }
            }
        }
    )
}

@Composable
fun CustomersScreen(c: AppContainer, bid: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val loc = L.current
    val vm = vm { BizViewModel(c, bid) }
    val biz by vm.business.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var onlyOwing by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    val cur = biz?.currency ?: "Rs"

    AppScaffold(loc.t(R.string.customers), onBack, fab = {
        FloatingActionButton(onClick = { showForm = true }) { Icon(Icons.Filled.Add, loc.t(R.string.add_customer)) }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Field(query, { query = it }, loc.t(R.string.search_name_phone), modifier = Modifier.fillMaxWidth().padding(12.dp))
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyOwing, onClick = { onlyOwing = false }, label = { Text(loc.t(R.string.filter_all)) })
                FilterChip(selected = onlyOwing, onClick = { onlyOwing = true }, label = { Text(loc.t(R.string.filter_owe_money)) })
            }
            val list = customers.filter {
                (it.name.contains(query, true) || it.phone.contains(query)) && (!onlyOwing || (balances[it.id] ?: 0.0) > 0.004)
            }.let { l -> if (onlyOwing) l.sortedByDescending { balances[it.id] ?: 0.0 } else l }
            if (list.isEmpty()) EmptyState(if (customers.isEmpty()) loc.t(R.string.no_customers_yet) else loc.t(R.string.no_match))
            else LazyColumn {
                items(list, key = { it.id }) { cu ->
                    val bal = balances[cu.id] ?: 0.0
                    ListItem(
                        modifier = Modifier.clickable { onOpen(cu.id) },
                        headlineContent = { Text(cu.name) },
                        supportingContent = { if (cu.phone.isNotBlank()) Text(cu.phone) },
                        trailingContent = {
                            when {
                                bal > 0.004 -> Text(loc.t(R.string.owes_amount, loc.money(cur, bal)), color = Red, style = MaterialTheme.typography.titleSmall)
                                bal < -0.004 -> Text(loc.t(R.string.advance_amount, loc.money(cur, -bal)), color = Green, style = MaterialTheme.typography.titleSmall)
                                else -> Text(loc.t(R.string.settled), color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (showForm) CustomerDialog(bid, null, onSave = { scope.launch { c.repo.save(it); showForm = false } }, onDelete = null, onDismiss = { showForm = false })
}
