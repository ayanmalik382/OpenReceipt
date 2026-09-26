package com.receiptbook.app.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.receiptbook.app.R
import com.receiptbook.app.data.Business
import com.receiptbook.app.data.DateRange
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Strings + date formatting for ONE language. Can be created for any language, not only the UI one (used for receipts). */
class Loc(val lang: AppLanguage, base: Context) {
    val code: String get() = lang.code
    val rtl: Boolean get() = lang.rtl

    /** Used to pick resources. */
    val locale: Locale = Locale.forLanguageTag(lang.code)
    /** Used to format dates: same language but always Latin digits (0-9), as used in Pakistani shops. */
    val fmtLocale: Locale = Locale.forLanguageTag("${lang.code}-u-nu-latn")

    private val ctx: Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).also { it.setLocale(locale); it.setLayoutDirection(locale) }
    )

    fun t(@StringRes id: Int, vararg args: Any): String = ctx.getString(id, *args)

    fun date(ts: Long): String = SimpleDateFormat("dd MMM yyyy", fmtLocale).format(Date(ts))
    fun dateTime(ts: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", fmtLocale).format(Date(ts))
    fun numFmt(value: Number): String =
        NumberFormat.getNumberInstance(fmtLocale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }.format(value)

    fun money(currency: String, amount: Double): String =
        "${numFmt(amount)} $currency"

    fun rangeText(r: DateRange): String = if (r.label == "All") t(R.string.range_all_time) else "${date(r.from)} – ${date(r.to)}"

    // Values such as units / payment methods / expense categories are STORED as stable English keys
    // (so data syncs and exports consistently) and only TRANSLATED when shown. Custom values pass through.
    fun unit(u: String): String = when (u) {
        "pcs" -> t(R.string.unit_pcs); "kg" -> t(R.string.unit_kg); "dozen" -> t(R.string.unit_dozen)
        "meter" -> t(R.string.unit_meter); "liter" -> t(R.string.unit_liter); "box" -> t(R.string.unit_box)
        "bag" -> t(R.string.unit_bag); else -> u
    }

    fun method(m: String): String = when (m) {
        "Cash" -> t(R.string.method_cash); "Bank" -> t(R.string.method_bank); "JazzCash" -> t(R.string.method_jazzcash)
        "Easypaisa" -> t(R.string.method_easypaisa); "Cheque" -> t(R.string.method_cheque); else -> m
    }

    fun category(c: String): String = when (c) {
        "Supplier payment" -> t(R.string.cat_supplier_payment); "Rent" -> t(R.string.cat_rent)
        "Salaries" -> t(R.string.cat_salaries); "Utilities" -> t(R.string.cat_utilities)
        "Transport" -> t(R.string.cat_transport); "Other" -> t(R.string.cat_other); else -> c
    }
}

/**
 * App-wide language state. [current] is a Compose State: any @Composable that reads L.current or
 * calls L.t(...) is automatically subscribed and recomposes the instant the language changes -
 * no navigation restart, no manual "key" plumbing needed.
 */
object L {
    private lateinit var app: Context
    private val cache = HashMap<String, Loc>()
    private lateinit var state: androidx.compose.runtime.MutableState<Loc>

    val current: Loc get() = state.value
    /** Exposed for callers that want to read it as an explicit State (e.g. derivedStateOf). */
    val currentState: State<Loc> get() = state

    private val prefs get() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (::app.isInitialized) return // idempotent: attachBaseContext may call this more than once
        app = context.applicationContext
        val saved = prefs.getString("lang", null)
        // First launch: follow the phone's language if we support it, otherwise English.
        val code = Languages.get(saved)?.code ?: Languages.get(Locale.getDefault().language)?.code ?: Languages.DEFAULT
        state = mutableStateOf(forCode(code))
        Locale.setDefault(current.fmtLocale)
    }

    fun forCode(code: String?): Loc {
        val lang = Languages.get(code) ?: Languages.get(Languages.DEFAULT)!!
        return cache.getOrPut(lang.code) { Loc(lang, app) }
    }

    fun setLanguage(code: String) {
        prefs.edit().putString("lang", code).apply()
        state.value = forCode(code)
        Locale.setDefault(current.fmtLocale)
    }

    /** Language used on a business's receipts. Empty = same as the app. */
    fun receiptLoc(b: Business): Loc = if (b.receiptLang.isBlank()) current else forCode(b.receiptLang)

    /** Wrap an Activity base context so system dialogs (date picker) follow the chosen language at launch. */
    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(current.locale)
        cfg.setLayoutDirection(current.locale)
        return base.createConfigurationContext(cfg)
    }

    fun t(@StringRes id: Int, vararg args: Any): String = current.t(id, *args)
}

/** An error whose text is translated when it is shown. */
class AppError(@StringRes val res: Int, private vararg val args: Any) : Exception() {
    override val message: String get() = L.t(res, *args)
}

fun need(condition: Boolean, @StringRes res: Int, vararg args: Any) {
    if (!condition) throw AppError(res, *args)
}
