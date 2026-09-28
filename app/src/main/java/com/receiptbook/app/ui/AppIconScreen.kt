package com.receiptbook.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.receiptbook.app.AppIconManager
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L

@Composable
fun AppIconScreen(onBack: () -> Unit) {
    val loc = L.current
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(AppIconManager.current(ctx)) }

    AppScaffold(loc.t(R.string.choose_app_icon), onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f, fill = false)
            ) {
                items(AppIconManager.options) { o ->
                    val isSel = o.id == selected
                    ElevatedCard(
                        Modifier.fillMaxWidth().clickable {
                            if (!isSel) { AppIconManager.set(ctx, o.id); selected = o.id }
                        }.then(if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(o.previewRes), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                            Spacer(Modifier.height(8.dp))
                            Text(loc.t(o.nameRes), style = MaterialTheme.typography.titleSmall)
                            if (isSel) Text(loc.t(R.string.in_use), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Text(loc.t(R.string.icon_changed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
