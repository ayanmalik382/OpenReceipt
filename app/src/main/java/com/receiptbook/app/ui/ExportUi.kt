package com.receiptbook.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.receiptbook.app.R
import com.receiptbook.app.export.Exporter
import com.receiptbook.app.i18n.L
import kotlinx.coroutines.launch
import java.io.File

/**
 * One consistent way to export anything (receipt PDF/image, report PDF, Excel): the trigger you
 * supply opens a small menu offering **Share** (the normal Android share sheet) or **Save to
 * device** (copies the file into Downloads/ReceiptBook). [generate] builds the file only when a
 * choice is made, so nothing is created if the person just dismisses the menu.
 */
@Composable
fun ExportMenu(
    mime: String,
    title: String,
    generate: suspend () -> File,
    trigger: @Composable (open: () -> Unit) -> Unit
) {
    val loc = L.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<File?>(null) }

    fun toast(res: Int) = Toast.makeText(ctx, loc.t(res), Toast.LENGTH_SHORT).show()

    suspend fun save(f: File) = toast(if (Exporter.saveToDownloads(ctx, f, mime)) R.string.saved_to_downloads else R.string.save_failed)

    // Android 9 and older can't write to Downloads without asking; 10+ never needs this.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val f = pendingSave; pendingSave = null
        if (granted && f != null) scope.launch { save(f) } else toast(R.string.save_failed)
    }

    Box {
        trigger { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                text = { Text(loc.t(R.string.share)) },
                onClick = {
                    open = false
                    scope.launch {
                        val f = runCatching { generate() }.getOrNull()
                        if (f == null) toast(R.string.save_failed) else Exporter.share(ctx, f, mime, title)
                    }
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Save, null) },
                text = { Text(loc.t(R.string.save_to_device)) },
                onClick = {
                    open = false
                    scope.launch {
                        val f = runCatching { generate() }.getOrNull()
                        when {
                            f == null -> toast(R.string.save_failed)
                            Build.VERSION.SDK_INT >= 29 ||
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> save(f)
                            else -> { pendingSave = f; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                        }
                    }
                }
            )
        }
    }
}
