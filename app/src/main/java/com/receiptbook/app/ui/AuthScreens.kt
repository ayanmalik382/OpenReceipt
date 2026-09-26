package com.receiptbook.app.ui

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptbook.app.AppContainer
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L
import com.receiptbook.app.net.ApiClient
import kotlinx.coroutines.launch
import java.io.IOException

class AuthViewModel(private val c: AppContainer) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    private fun exec(onUnverified: (() -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true; error = null; info = null
            try {
                block()
            } catch (e: ApiClient.ApiException) {
                if (e.code == 403 && onUnverified != null) onUnverified() else error = e.message
            } catch (e: IOException) {
                error = L.t(R.string.err_offline_auth)
            } catch (e: Exception) {
                error = e.message ?: L.t(R.string.srv_server_error)
            } finally {
                loading = false
            }
        }
    }

    private fun validEmail(e: String) = Patterns.EMAIL_ADDRESS.matcher(e).matches()

    fun register(email: String, password: String, confirm: String, onOk: (String) -> Unit) {
        val e = email.trim().lowercase()
        if (!validEmail(e)) { error = L.t(R.string.err_enter_valid_email); return }
        if (password.length < 8) { error = L.t(R.string.err_password_min); return }
        if (password != confirm) { error = L.t(R.string.err_passwords_no_match); return }
        exec { c.api.register(e, password); onOk(e) }
    }

    fun verify(email: String, otp: String, onOk: () -> Unit) = exec {
        val token = c.api.verify(email, otp.trim())
        c.session.login(email, token); c.sync.requestNow(); onOk()
    }

    fun resend(email: String) = exec { c.api.sendOtp(email); info = L.t(R.string.code_resent) }

    fun login(email: String, password: String, onOk: () -> Unit, onUnverified: (String) -> Unit) {
        val e = email.trim().lowercase()
        if (!validEmail(e) || password.isEmpty()) { error = L.t(R.string.err_enter_email_password); return }
        exec(onUnverified = { viewModelScope.launch { try { c.api.sendOtp(e) } catch (_: Exception) {}; onUnverified(e) } }) {
            val token = c.api.login(e, password)
            c.session.login(e, token); c.sync.requestNow(); onOk()
        }
    }

    fun sendResetCode(email: String, onOk: () -> Unit) {
        val e = email.trim().lowercase()
        if (!validEmail(e)) { error = L.t(R.string.err_enter_valid_email); return }
        exec { c.api.sendOtp(e); onOk() }
    }

    fun reset(email: String, otp: String, password: String, onOk: () -> Unit) {
        if (password.length < 8) { error = L.t(R.string.err_password_min); return }
        val e = email.trim().lowercase()
        exec {
            val token = c.api.reset(e, otp.trim(), password)
            c.session.login(e, token); c.sync.requestNow(); onOk()
        }
    }
}

@Composable
private fun AuthColumn(title: String, subtitle: String, onLanguage: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).statusBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Text("ReceiptBook", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            content()
        }
        if (onLanguage != null) IconButton(onClick = onLanguage, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.Language, L.t(R.string.language), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onSuccess: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit, onVerify: (String) -> Unit, onLanguage: () -> Unit) {
    val loc = L.current
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AuthColumn(loc.t(R.string.sign_in), loc.t(R.string.auth_tagline), onLanguage) {
        Field(email, { email = it }, loc.t(R.string.email), keyboard = KeyboardType.Email)
        Field(pw, { pw = it }, loc.t(R.string.password), password = true)
        ErrorText(vm.error)
        Button(onClick = { vm.login(email, pw, onSuccess, onVerify) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
            if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(loc.t(R.string.sign_in))
        }
        TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(loc.t(R.string.forgot_password)) }
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.create_new_account)) }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onCodeSent: (String) -> Unit, onBack: () -> Unit) {
    val loc = L.current
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AuthColumn(loc.t(R.string.create_account), loc.t(R.string.register_tagline)) {
        Field(email, { email = it }, loc.t(R.string.email), keyboard = KeyboardType.Email)
        Field(pw, { pw = it }, loc.t(R.string.password_hint), password = true)
        Field(pw2, { pw2 = it }, loc.t(R.string.confirm_password), password = true)
        ErrorText(vm.error)
        Button(onClick = { vm.register(email, pw, pw2, onCodeSent) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
            if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(loc.t(R.string.send_code))
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(loc.t(R.string.already_have_account)) }
    }
}

@Composable
fun VerifyScreen(email: String, vm: AuthViewModel, onSuccess: () -> Unit) {
    val loc = L.current
    var otp by remember { mutableStateOf("") }
    AuthColumn(loc.t(R.string.enter_code), loc.t(R.string.code_sent_to, email)) {
        Field(otp, { if (it.length <= 6) otp = it.filter { ch -> ch.isDigit() } }, loc.t(R.string.code_hint), keyboard = KeyboardType.NumberPassword)
        ErrorText(vm.error)
        vm.info?.let { Text(it, color = Green) }
        Button(onClick = { vm.verify(email, otp, onSuccess) }, enabled = !vm.loading && otp.length == 6, modifier = Modifier.fillMaxWidth()) { Text(loc.t(R.string.verify)) }
        TextButton(onClick = { vm.resend(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(loc.t(R.string.send_code_again)) }
    }
}

@Composable
fun ForgotScreen(vm: AuthViewModel, onSuccess: () -> Unit, onBack: () -> Unit) {
    val loc = L.current
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AuthColumn(loc.t(R.string.reset_password), if (sent) loc.t(R.string.reset_tagline_enter) else loc.t(R.string.reset_tagline_send)) {
        Field(email, { email = it }, loc.t(R.string.email), keyboard = KeyboardType.Email, enabled = !sent)
        if (sent) {
            Field(otp, { if (it.length <= 6) otp = it.filter { ch -> ch.isDigit() } }, loc.t(R.string.code_hint), keyboard = KeyboardType.NumberPassword)
            Field(pw, { pw = it }, loc.t(R.string.password_hint), password = true)
        }
        ErrorText(vm.error)
        Button(
            onClick = { if (sent) vm.reset(email, otp, pw, onSuccess) else vm.sendResetCode(email) { sent = true } },
            enabled = !vm.loading, modifier = Modifier.fillMaxWidth()
        ) { Text(if (sent) loc.t(R.string.change_password) else loc.t(R.string.send_code)) }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(loc.t(R.string.back_to_sign_in)) }
    }
}
