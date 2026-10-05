package com.receiptbook.app.ui
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.Customer
import com.receiptbook.app.data.Product
import com.receiptbook.app.data.Supplier
import com.receiptbook.app.i18n.L
import kotlinx.coroutines.launch

private data class TrashItem(
    val type: String,
    val id: String,
    val name: String
)

@Composable
fun TrashScreen(
    c: AppContainer,
    bid: String,
    onBack: () -> Unit
) {
    val loc = L.current

    val suppliers by c.db.dao()
        .observeDeletedSuppliers(bid)
        .collectAsStateWithLifecycle(emptyList())

    val products by c.db.dao()
        .observeDeletedProducts(bid)
        .collectAsStateWithLifecycle(emptyList())

    val customers by c.db.dao()
        .observeDeletedCustomers(bid)
        .collectAsStateWithLifecycle(emptyList())

    val items = remember(suppliers, products, customers) {
        buildList {
            suppliers.forEach {
                add(TrashItem("supplier", it.id, it.name))
            }

            products.forEach {
                add(TrashItem("product", it.id, it.name))
            }

            customers.forEach {
                add(TrashItem("customer", it.id, it.name))
            }
        }
    }

    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<TrashItem?>(null) }
    var permanentDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AppScaffold(
        title = loc.t(R.string.recently_deleted),
        onBack = onBack
    ) { pad ->

        if (items.isEmpty()) {
            Box(
                Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(loc.t(R.string.no_recently_deleted))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items,
                    key = { "${it.type}:${it.id}" }
                ) { item ->

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (item.type) {
                                "customer" -> Icons.Filled.Person
                                "supplier" -> Icons.Filled.Store
                                else -> Icons.Filled.Inventory2
                            }

                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    when (item.type) {
                                        "customer" -> "Customer"
                                        "supplier" -> "Supplier"
                                        else -> "Product"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            IconButton(
                                onClick = {
                                    selected = item
                                    permanentDelete = false
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = loc.t(R.string.restore)
                                )
                            }

                            IconButton(
                                onClick = {
                                    selected = item
                                    permanentDelete = true
                                }
                            ) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    contentDescription = loc.t(R.string.delete_permanently),
                                    tint = Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->

        val title = if (permanentDelete) {
            loc.t(R.string.delete_permanently)
        } else {
            loc.t(R.string.restore)
        }

        val body = if (permanentDelete) {
            "${loc.t(R.string.delete_permanently)} \"${item.name}\"? ${loc.t(R.string.delete_permanently_body)}"
        } else {
            "${loc.t(R.string.restore)} \"${item.name}\"?"
        }
        AlertDialog(
            onDismissRequest = {
                if (!busy) selected = null
            },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true

                        scope.launch {
                            try {
                                when (item.type) {
                                    "supplier" -> {
                                        val value = c.db.dao().supplier(item.id)
                                        if (value != null) {
                                            if (permanentDelete) {
                                                c.repo.purge("supplier", item.id)
                                            } else {
                                                c.repo.restore(value)
                                            }
                                        }
                                    }

                                    "product" -> {
                                        val value = c.db.dao().product(item.id)
                                        if (value != null) {
                                            if (permanentDelete) {
                                                c.repo.purge("product", item.id)
                                            } else {
                                                c.repo.restore(value)
                                            }
                                        }
                                    }

                                    "customer" -> {
                                        val value = c.db.dao().customer(item.id)
                                        if (value != null) {
                                            if (permanentDelete) {
                                                c.repo.purge("customer", item.id)
                                            } else {
                                                c.repo.restore(value)
                                            }
                                        }
                                    }
                                }
                            } finally {
                                busy = false
                                selected = null
                            }
                        }
                    }
                ) {
                    Text(title)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { selected = null }
                ) {
                    Text(loc.t(R.string.cancel))
                }
            }
        )
    }
}