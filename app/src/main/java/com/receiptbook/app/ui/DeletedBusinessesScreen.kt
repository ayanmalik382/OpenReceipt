package com.receiptbook.app.ui
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import com.receiptbook.app.data.Business
import com.receiptbook.app.i18n.L
import kotlinx.coroutines.launch

@Composable
fun DeletedBusinessesScreen(
    c: AppContainer,
    onBack: () -> Unit
) {
    val loc = L.current
    val businesses by c.db.dao()
        .observeDeletedBusinesses()
        .collectAsStateWithLifecycle(emptyList())

    val scope = rememberCoroutineScope()

    var restoreBusiness by remember { mutableStateOf<Business?>(null) }
    var purgeBusiness by remember { mutableStateOf<Business?>(null) }
    var busy by remember { mutableStateOf(false) }

    AppScaffold(
        title = loc.t(R.string.recently_deleted_businesses),
        onBack = onBack
    ) { pad ->

        if (businesses.isEmpty()) {
            EmptyState(loc.t(R.string.no_recently_deleted_businesses))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    businesses,
                    key = { it.id }
                ) { business ->

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Store,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    business.name,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                if (business.city.isNotBlank()) {
                                    Text(
                                        business.city,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (business.phone.isNotBlank()) {
                                    Text(
                                        business.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            IconButton(
                                onClick = { restoreBusiness = business }
                            ) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = loc.t(R.string.restore)
                                )
                            }

                            IconButton(
                                onClick = { purgeBusiness = business }
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

    restoreBusiness?.let { business ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) restoreBusiness = null
            },
            title = {
                Text(loc.t(R.string.restore))
            },
            text = {
                Text(
                    "${loc.t(R.string.restore)} \"${business.name}\"?"
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                c.repo.restore(business)
                            } finally {
                                busy = false
                                restoreBusiness = null
                            }
                        }
                    }
                ) {
                    if (busy) {
                        CircularProgressIndicator()
                    } else {
                        Text(loc.t(R.string.restore))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { restoreBusiness = null }
                ) {
                    Text(loc.t(R.string.cancel))
                }
            }
        )
    }

    purgeBusiness?.let { business ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) purgeBusiness = null
            },
            title = {
                Text(loc.t(R.string.delete_permanently))
            },
            text = {
                Text(
                    "${loc.t(R.string.delete_permanently)} \"${business.name}\"? ${loc.t(R.string.delete_permanently_body)}"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                c.repo.purge("business", business.id)
                            } finally {
                                busy = false
                                purgeBusiness = null
                            }
                        }
                    }
                ) {
                    Text(
                        loc.t(R.string.delete_permanently),
                        color = Red
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { purgeBusiness = null }
                ) {
                    Text(loc.t(R.string.cancel))
                }
            }
        )
    } }
