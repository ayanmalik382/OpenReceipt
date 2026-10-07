package com.receiptbook.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.BusinessFields
import com.receiptbook.app.data.BusinessNatures
import com.receiptbook.app.i18n.L
import com.receiptbook.app.net.ApiClient
import kotlinx.coroutines.launch

/**
 * Finds other businesses that have opted into the cross-account directory (see
 * BusinessScreens.DirectoryListingSection). Requires being signed in - this is a live search
 * against the cloud, there's nothing to show offline.
 */
@Composable
fun DirectoryScreen(c: AppContainer, onBack: () -> Unit, onSignIn: () -> Unit) {
    val loc = L.current
    val scope = rememberCoroutineScope()
    val email by c.session.email.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    var nature by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ApiClient.DirectoryEntry>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch(append: Boolean) {
        loading = true; error = null
        scope.launch {
            try {
                val page = c.api.searchDirectory(
                    field.takeIf { it.isNotBlank() }, nature.takeIf { it.isNotBlank() }, city.takeIf { it.isNotBlank() },
                    query.takeIf { it.isNotBlank() }, offset = if (append) results.size else 0
                )
                results = if (append) results + page.results else page.results
                hasMore = page.hasMore
                searched = true
            } catch (e: java.io.IOException) {
                // DNS failure, no network, host unreachable - a device being offline, not a server
                // problem. Never show the raw exception text ("no address associated with hostname"
                // etc.) to the person; translate it into something they can act on.
                error = loc.t(R.string.err_no_internet)
            } catch (e: Exception) {
                error = e.message ?: loc.t(R.string.err_no_internet)
            } finally {
                loading = false
            }
        }
    }

    AppScaffold(loc.t(R.string.directory_title), onBack) { pad ->
        if (email == null) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(loc.t(R.string.list_my_business_needs_login), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSignIn) { Text(loc.t(R.string.sign_in)) }
            }
            return@AppScaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(query, { query = it }, loc.t(R.string.search_businesses), modifier = Modifier.weight(1f))
                IconButton(onClick = { runSearch(false) }) { Icon(Icons.Filled.Search, loc.t(R.string.search)) }
            }
            Field(
                city, { city = it }, loc.t(R.string.filter_city),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    loc.t(R.string.filter_field),
                    if (field.isBlank()) loc.t(R.string.filter_all) else loc.t(BusinessFields.labelRes(field)),
                    listOf(loc.t(R.string.filter_all) to "") + BusinessFields.all.filter { it.id != BusinessFields.OTHER }.map { loc.t(it.labelRes) to it.id },
                    { field = it; runSearch(false) },
                    modifier = Modifier.weight(1f)
                )
                Picker(
                    loc.t(R.string.filter_nature),
                    if (nature.isBlank()) loc.t(R.string.filter_all) else loc.t(BusinessNatures.labelRes(nature)),
                    listOf(loc.t(R.string.filter_all) to "") + BusinessNatures.all.map { loc.t(it.labelRes) to it.id },
                    { nature = it; runSearch(false) },
                    modifier = Modifier.weight(1f)
                )
            }
            ErrorText(error)
            if (!searched && !loading) {
                LaunchedEffect(Unit) { runSearch(false) }
            }
            if (loading && results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (results.isEmpty() && searched) {
                EmptyState(loc.t(R.string.no_directory_results))
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { entry -> DirectoryCard(entry) }
                    if (hasMore) item {
                        OutlinedButton(onClick = { runSearch(true) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(loc.t(R.string.load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryCard(entry: ApiClient.DirectoryEntry) {
    val loc = L.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (entry.verified) {
                    Icon(Icons.Filled.CheckCircle, loc.t(R.string.verified_business), tint = Green, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                listOfNotNull(
                    loc.t(BusinessFields.labelRes(entry.field)), loc.t(BusinessNatures.labelRes(entry.nature)),
                    entry.city.takeIf { it.isNotBlank() }
                ).joinToString("  •  "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
            )
            if (entry.address.isNotBlank()) Text(entry.address, style = MaterialTheme.typography.bodySmall)
            if (entry.phone.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Phone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(4.dp))
                Text(entry.phone, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}