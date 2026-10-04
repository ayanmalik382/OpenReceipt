package com.receiptbook.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L

/** A name + phone number picked from the phone's contacts. */
data class PickedContact(val name: String, val phone: String)

private fun readContact(contentResolver: android.content.ContentResolver, phoneUri: Uri): PickedContact? {
    return contentResolver.query(phoneUri, null, null, null, null)?.use { c ->
        if (!c.moveToFirst()) return null
        val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
        val number = if (numberIdx >= 0) c.getString(numberIdx) ?: "" else ""
        if (name.isBlank() && number.isBlank()) null else PickedContact(name, number)
    }
}

/**
 * "Import from contacts" text button. Asks for READ_CONTACTS the first time it's used (only then -
 * never on screen load), launches the system contact picker scoped directly to phone numbers (so
 * if a contact has several numbers, the system's own picker lets the person choose which one), and
 * hands the result back via [onPicked]. Denying the permission just leaves manual entry available,
 * nothing breaks.
 */
@Composable
fun ImportFromContactsButton(onPicked: (PickedContact) -> Unit, modifier: Modifier = Modifier) {
    val loc = L.current
    val ctx = LocalContext.current

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            readContact(ctx.contentResolver, uri)?.let(onPicked)
        }
    }

    fun launchPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContact.launch(intent)
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPicker()
    }

    TextButton(
        modifier = modifier,
        onClick = {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            if (granted) launchPicker() else requestPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    ) {
        Icon(Icons.Filled.Contacts, null)
        Text(" " + loc.t(R.string.import_from_contacts))
    }
}