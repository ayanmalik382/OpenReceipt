package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.Languages

/** Language picker. Tapping a language updates L (a Compose State), which redraws the whole app instantly. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val loc = L.current
    AppScaffold(loc.t(R.string.choose_language), onBack) { pad ->
        Column(Modifier.padding(pad)) {
            LazyColumn {
                items(Languages.all, key = { it.code }) { lang ->
                    ListItem(
                        modifier = Modifier.clickable { L.setLanguage(lang.code) },
                        headlineContent = { Text(lang.nativeName) },
                        trailingContent = { if (loc.code == lang.code) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    HorizontalDivider()
                }
            }
            Text(
                loc.t(R.string.restart_hint),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
