package com.receiptbook.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.receiptbook.app.i18n.L
import com.receiptbook.app.ui.AppNav

class MainActivity : ComponentActivity() {
    private val container get() = (application as ReceiptBookApp).container

    // Wrap the base context so system dialogs (e.g. the date picker) show in the chosen language at launch.
    override fun attachBaseContext(newBase: Context) {
        L.init(newBase)
        super.attachBaseContext(L.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Reading L.current here subscribes this composable to language changes: switching
            // language anywhere in the app (see LanguageScreen) flips RTL/LTR instantly, no restart.
            val dir = if (L.current.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val dark = isSystemInDarkTheme()
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                MaterialTheme(
                    colorScheme = if (dark) darkColorScheme(primary = Color(0xFF6FD3AE), onPrimary = Color(0xFF003826))
                    else lightColorScheme(primary = Color(0xFF0B6E4F), onPrimary = Color.White, secondary = Color(0xFF3B6E5E))
                ) { AppNav(container) }
            }
        }
    }

    override fun onStart() { super.onStart(); container.sync.requestNow() }
    override fun onStop() { super.onStop(); container.sync.requestNow() } // push latest work when leaving the app
}
