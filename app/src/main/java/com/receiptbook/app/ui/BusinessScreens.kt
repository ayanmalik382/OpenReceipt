package com.receiptbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.data.Business
import com.receiptbook.app.data.BusinessFields
import com.receiptbook.app.data.BusinessNatures
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.Languages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BusinessListScreen(c: AppContainer, onOpen: (String) -> Unit, onNew: () -> Unit, onLanguage: () -> Unit, onSignIn: () -> Unit, onAppIcon: () -> Unit, onDeletedBusinesses: () -> Unit, onDirectory: () -> Unit) {
    val loc = L.current
    val email by c.session.email.collectAsStateWithLifecycle()
    val list by remember { c.db.dao().observeBusinesses() }.collectAsStateWithLifecycle(emptyList())
    val sync by c.sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }

    AppScaffold(
        title = loc.t(R.string.my_businesses),
        actions = {
            IconButton(onClick = onDirectory) { Icon(Icons.Filled.Search, loc.t(R.string.find_other_businesses)) }
            IconButton(onClick = onLanguage) { Icon(Icons.Filled.Language, loc.t(R.string.language)) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(loc.t(R.string.app_icon)) }, onClick = { menu = false; onAppIcon() })
                DropdownMenuItem(text = { Text(loc.t(R.string.recently_deleted_businesses)) }, onClick = { menu = false; onDeletedBusinesses() })
                if (email == null) {
                    DropdownMenuItem(text = { Text(loc.t(R.string.sign_in_to_back_up)) }, onClick = { menu = false; onSignIn() })
                } else {
                    DropdownMenuItem(text = { Text(loc.t(R.string.sync_now)) }, onClick = { menu = false; c.sync.requestNow() })
                    DropdownMenuItem(text = { Text(loc.t(R.string.sign_out)) }, onClick = { menu = false; confirmLogout = true })
                    DropdownMenuItem(text = { Text(loc.t(R.string.delete_account), color = Red) }, onClick = { menu = false; deleteAccount = true })
                }
            }
        },
        fab = { ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(loc.t(R.string.add_business)) }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (email == null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(loc.t(R.string.working_offline), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = onSignIn) { Text(loc.t(R.string.sign_in_to_back_up)) }
                }
            } else {
                val status = when {
                    sync.running -> loc.t(R.string.syncing)
                    sync.errorText != null -> sync.errorText!!
                    sync.errorRes != null -> loc.t(sync.errorRes!!)
                    sync.lastOk > 0 -> loc.t(R.string.backed_up_at, loc.dateTime(sync.lastOk))
                    else -> loc.t(R.string.not_synced_yet)
                }
                Text(
                    "$email  •  $status", style = MaterialTheme.typography.labelMedium,
                    color = if (sync.errorText != null || sync.errorRes != null) Red else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            // First sync ever on this device (never completed one, but one is actively running):
            // the list is very likely empty right now not because there's no data, but because it's
            // still coming down from the cloud. Say so clearly instead of showing a bare empty state.
            if (list.isEmpty() && email != null && sync.running && sync.lastOk == 0L) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(loc.t(R.string.restoring_your_data), style = MaterialTheme.typography.titleMedium)
                        Text(loc.t(R.string.restoring_your_data_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else if (list.isEmpty()) EmptyState(loc.t(R.string.no_business_yet))
            else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { b ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(b.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (b.logoBase64.isNotBlank()) Avatar(b.logoBase64, size = 44.dp)
                            else Icon(Icons.Filled.Store, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Column {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                if (b.address.isNotBlank()) Text(b.address, style = MaterialTheme.typography.bodySmall)
                                if (b.phone.isNotBlank()) Text(b.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmLogout) ConfirmDialog(
        loc.t(R.string.sign_out_confirm_title), loc.t(R.string.sign_out_confirm_body), loc.t(R.string.sign_out),
        onConfirm = {
            confirmLogout = false
            // Local data is untouched by signing out - it stays fully visible and usable; only
            // the account link (and therefore future backup) goes away.
            scope.launch { c.sync.sync(); c.session.logout() }
        }, onDismiss = { confirmLogout = false }
    )

    if (deleteAccount) {
        var pw by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) deleteAccount = false },
            title = { Text(loc.t(R.string.delete_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(loc.t(R.string.delete_account_body))
                    Field(pw, { pw = it }, loc.t(R.string.enter_your_password), password = true)
                    ErrorText(err)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && pw.isNotEmpty(), onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            c.api.deleteAccount(pw)
                            // Unlike sign-out, this is an explicit, confirmed "delete everything"
                            // action, so it does wipe the local copy too, matching the warning above.
                            withContext(Dispatchers.IO) { c.db.clearAllTables() }
                            c.session.forgetCursor(); c.session.logout()
                            deleteAccount = false
                        } catch (e: Exception) {
                            err = e.message ?: loc.t(R.string.err_delete_account_failed)
                        } finally { busy = false }
                    }
                }) { Text(loc.t(R.string.delete_everything), color = Red) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleteAccount = false }) { Text(loc.t(R.string.cancel)) } }
        )
    }
}

@Composable
fun BusinessFormScreen(c: AppContainer, bid: String?, onDone: (String?) -> Unit, onBack: () -> Unit, onTemplate: (String) -> Unit, onTrash: (String) -> Unit) {
    val existing by produceState<Business?>(initialValue = null, bid) {
        value = if (bid == null) null else c.db.dao().business(bid)
    }
    if (bid != null && existing == null) {
        AppScaffold(L.t(R.string.business_settings), onBack) { pad -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    } else {
        BusinessForm(c, existing, onDone, onBack, onTemplate, onTrash)
    }
}

@Composable
private fun BusinessForm(c: AppContainer, ex: Business?, onDone: (String?) -> Unit, onBack: () -> Unit, onTemplate: (String) -> Unit, onTrash: (String) -> Unit) {
    val loc = L.current
    val scope = rememberCoroutineScope()
    val email by c.session.email.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(ex?.name ?: "") }
    var address by remember { mutableStateOf(ex?.address ?: "") }
    var phone by remember { mutableStateOf(ex?.phone ?: "") }
    var cash by remember { mutableStateOf(ex?.allowCash ?: true) }
    var credit by remember { mutableStateOf(ex?.allowCredit ?: true) }
    var currency by remember { mutableStateOf(ex?.currency ?: "Rs") }
    var prefix by remember { mutableStateOf(ex?.receiptPrefix ?: "R") }
    var footer by remember { mutableStateOf(ex?.footerNote ?: "") }
    var receiptLang by remember { mutableStateOf(ex?.receiptLang ?: "") }
    var templateId by remember { mutableStateOf(ex?.templateId ?: "classic") }
    var logo by remember { mutableStateOf(ex?.logoBase64 ?: "") }
    var ntn by remember { mutableStateOf(ex?.ntn ?: "") }
    var city by remember { mutableStateOf(ex?.city ?: "") }
    var fieldOfBusiness by remember { mutableStateOf(ex?.fieldOfBusiness ?: "") }
    var fieldOfBusinessOther by remember { mutableStateOf(ex?.fieldOfBusinessOther ?: "") }
    var natureOfBusiness by remember { mutableStateOf(ex?.natureOfBusiness ?: "") }
    var suppliers by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppScaffold(if (ex == null) loc.t(R.string.new_business) else loc.t(R.string.business_settings), onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ImagePicker(logo, { logo = it }, size = 88.dp, addLabel = loc.t(R.string.add_logo), changeLabel = loc.t(R.string.change_logo), removeLabel = loc.t(R.string.remove_logo))
                Text(loc.t(R.string.logo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            }
            Field(name, { name = it }, loc.t(R.string.business_name_req))
            Field(address, { address = it }, loc.t(R.string.address))
            Field(phone, { phone = it }, loc.t(R.string.business_phone), keyboard = KeyboardType.Phone)

            HorizontalDivider()
            Text(loc.t(R.string.business_details), style = MaterialTheme.typography.titleSmall)
            Field(city, { city = it }, loc.t(R.string.city))
            Field(ntn, { ntn = it }, loc.t(R.string.ntn_optional))
            Picker(
                loc.t(R.string.field_of_business),
                if (fieldOfBusiness.isBlank()) loc.t(R.string.choose) else loc.t(BusinessFields.labelRes(fieldOfBusiness)),
                BusinessFields.all.map { loc.t(it.labelRes) to it.id },
                { fieldOfBusiness = it }
            )
            if (fieldOfBusiness == BusinessFields.OTHER) {
                Field(fieldOfBusinessOther, { fieldOfBusinessOther = it }, loc.t(R.string.other_field_hint))
            }
            Picker(
                loc.t(R.string.nature_of_business),
                if (natureOfBusiness.isBlank()) loc.t(R.string.choose) else loc.t(BusinessNatures.labelRes(natureOfBusiness)),
                BusinessNatures.all.map { loc.t(it.labelRes) to it.id },
                { natureOfBusiness = it }
            )
            HorizontalDivider()

            Text(loc.t(R.string.sales_terms_offered), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(cash, { cash = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.term_cash)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(credit, { credit = it }); Spacer(Modifier.width(8.dp)); Text(loc.t(R.string.term_credit)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(currency, { currency = it.take(5) }, loc.t(R.string.currency), modifier = Modifier.weight(1f))
                Field(prefix, { prefix = it.take(6).uppercase() }, loc.t(R.string.receipt_prefix), modifier = Modifier.weight(1f))
            }
            Field(footer, { footer = it }, loc.t(R.string.receipt_footer))
            Picker(
                loc.t(R.string.receipt_language),
                Languages.get(receiptLang)?.nativeName ?: loc.t(R.string.same_as_app),
                listOf(loc.t(R.string.same_as_app) to "") + Languages.all.map { it.nativeName to it.code },
                { receiptLang = it }
            )
            if (ex == null) {
                Field(suppliers, { suppliers = it }, loc.t(R.string.supplier_names_hint), singleLine = false, minLines = 3)
                Text(loc.t(R.string.receipt_template), style = MaterialTheme.typography.titleSmall)
                Picker(
                    loc.t(R.string.receipt_template),
                    when (templateId) {
                        "modern" -> loc.t(R.string.template_modern); "compact" -> loc.t(R.string.template_compact)
                        "wide" -> loc.t(R.string.template_wide); else -> loc.t(R.string.template_classic)
                    },
                    listOf(
                        loc.t(R.string.template_classic) to "classic", loc.t(R.string.template_modern) to "modern",
                        loc.t(R.string.template_compact) to "compact", loc.t(R.string.template_wide) to "wide"
                    ),
                    { templateId = it }
                )
            } else {
                OutlinedButton(onClick = { onTemplate(ex.id) }, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.receipt_template)) }
                OutlinedButton(onClick = { onTrash(ex.id) }, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.recently_deleted)) }
                HorizontalDivider()
                DirectoryListingSection(c, ex, email, name, city, fieldOfBusiness, fieldOfBusinessOther, natureOfBusiness, phone, address)
            }
            ErrorText(error)
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val digits = phone.count { it.isDigit() }
                error = when {
                    name.isBlank() -> loc.t(R.string.err_enter_business_name)
                    phone.isNotBlank() && digits < 7 -> loc.t(R.string.err_valid_phone)
                    !cash && !credit -> loc.t(R.string.err_choose_sales_term)
                    city.isBlank() -> loc.t(R.string.err_enter_city)
                    fieldOfBusiness.isBlank() -> loc.t(R.string.err_choose_field)
                    fieldOfBusiness == BusinessFields.OTHER && fieldOfBusinessOther.isBlank() -> loc.t(R.string.err_choose_field_other)
                    natureOfBusiness.isBlank() -> loc.t(R.string.err_choose_nature)
                    else -> null
                }
                if (error == null) scope.launch {
                    if (ex == null) {
                        val ownerKey = email ?: com.receiptbook.app.net.SessionStore.LOCAL_OWNER
                        val id = c.repo.createBusiness(
                            ownerKey, name, address, phone, cash, credit, currency, prefix, footer, receiptLang, suppliers.lines(), templateId,
                            logoBase64 = logo, ntn = ntn, city = city, fieldOfBusiness = fieldOfBusiness,
                            fieldOfBusinessOther = fieldOfBusinessOther, natureOfBusiness = natureOfBusiness
                        )
                        onDone(id)
                    } else {
                        c.repo.save(
                            ex.copy(
                                name = name.trim(), address = address.trim(), phone = phone.trim(), allowCash = cash, allowCredit = credit,
                                currency = currency.trim().ifBlank { "Rs" }, receiptPrefix = prefix.trim().ifBlank { "R" },
                                footerNote = footer.trim(), receiptLang = receiptLang, logoBase64 = logo,
                                ntn = ntn.trim(), city = city.trim(), fieldOfBusiness = fieldOfBusiness,
                                fieldOfBusinessOther = if (fieldOfBusiness == BusinessFields.OTHER) fieldOfBusinessOther.trim() else "",
                                natureOfBusiness = natureOfBusiness
                            )
                        )
                        onDone(null)
                    }
                }
            }) { Text(if (ex == null) loc.t(R.string.create_business) else loc.t(R.string.save_changes)) }
            if (ex != null) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.delete_this_business), color = Red) }
            Spacer(Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ReceiptBook",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    "Developed by Ahsan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    "Contact: +92 320 0600402",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
    if (confirmDelete && ex != null) ConfirmDialog(
        loc.t(R.string.delete_business_title), loc.t(R.string.delete_business_body, ex.name), loc.t(R.string.delete),
        onConfirm = { scope.launch { c.repo.remove(ex); confirmDelete = false; onDone("DELETED") } }, onDismiss = { confirmDelete = false }
    )
}

/**
 * The opt-in toggle to publish this business into the cross-account directory (see
 * ApiClient.publishToDirectory / server/app/routers/directory.py). Publishes whatever is
 * currently typed into the form's fields, not just what was last saved - so what you see is what
 * gets listed. Requires being signed in, since the directory only exists in the cloud.
 */
@Composable
private fun DirectoryListingSection(
    c: AppContainer, ex: Business, email: String?, name: String, city: String,
    fieldOfBusiness: String, fieldOfBusinessOther: String, natureOfBusiness: String, phone: String, address: String
) {
    val loc = L.current
    val scope = rememberCoroutineScope()
    var listed by remember(ex.id) { mutableStateOf(ex.listedInDirectory) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(loc.t(R.string.directory_listing), style = MaterialTheme.typography.titleSmall)
        Text(loc.t(R.string.list_my_business_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (email == null) {
            Text(loc.t(R.string.list_my_business_needs_login), style = MaterialTheme.typography.bodySmall, color = Red)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = listed, enabled = !busy,
                    onCheckedChange = { want ->
                        val fieldLabel = if (fieldOfBusiness == BusinessFields.OTHER) fieldOfBusinessOther else fieldOfBusiness
                        // Publishing requires city/field/nature to be filled in (the server rejects
                        // a listing without them). Check this FIRST so a business created before
                        // these fields existed gets a clear "fill this in" message instead of a
                        // silent failure with nothing stored.
                        if (want && (city.isBlank() || fieldOfBusiness.isBlank() || fieldLabel.isBlank() || natureOfBusiness.isBlank())) {
                            error = loc.t(R.string.directory_needs_details)
                        } else {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    if (want) c.api.publishToDirectory(ex.id, name, city, fieldLabel, natureOfBusiness, phone, address)
                                    else c.api.unpublishFromDirectory(ex.id)
                                    c.repo.save(ex.copy(listedInDirectory = want))
                                    listed = want
                                } catch (e: java.io.IOException) {
                                    error = loc.t(R.string.err_no_internet)
                                } catch (e: Exception) {
                                    error = loc.t(R.string.publish_failed)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(loc.t(R.string.list_my_business))
                if (busy) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
            }
            ErrorText(error)
            if (listed) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(loc.t(R.string.directory_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(4.dp))
                        Text(name.ifBlank { ex.name }, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(city.takeIf { it.isNotBlank() }, phone.takeIf { it.isNotBlank() }).joinToString("  •  "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}