package com.receiptbook.app.i18n

/**
 * THE language registry.
 *
 * To add a language (e.g. Farsi):
 *   1. Create app/src/main/res/values-fa/strings.xml  (copy values/strings.xml and translate)
 *   2. Add one line below:  AppLanguage("fa", "فارسی", rtl = true)
 *   3. Run: python3 tools/check_strings.py     (reports missing / wrong placeholders)
 * That's all. The language picker, RTL layout, receipts, PDF and Excel exports pick it up automatically.
 */
data class AppLanguage(val code: String, val nativeName: String, val rtl: Boolean)

object Languages {
    const val DEFAULT = "en"

    val all: List<AppLanguage> = listOf(
        AppLanguage("en", "English", rtl = false),
        AppLanguage("ur", "اردو", rtl = true),
        AppLanguage("fa", "فارسی", rtl = true),
        // AppLanguage("ar", "العربية", rtl = true),
        // AppLanguage("hi", "हिन्दी", rtl = false),
    )

    fun get(code: String?): AppLanguage? = all.firstOrNull { it.code == code }
}
