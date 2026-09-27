package com.receiptbook.app.export

import com.receiptbook.app.data.Business
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything that changes the LOOK of a printed receipt (as opposed to Loc, which changes the
 * LANGUAGE). A business picks one of the predefined presets, or "custom" with its own ReceiptStyle
 * saved as JSON on Business.templateConfig. ReceiptRenderer/Exporter only ever see a ReceiptStyle -
 * they don't need to know whether it came from a preset or a custom pick.
 */
@Serializable
data class ReceiptStyle(
    /** Drawing width in points. Roughly: 58mm thermal ~260, 80mm thermal ~300-320, A4 print ~480. */
    val paperWidth: Float = 300f,
    /** ARGB int used for the business name, section totals, and dividers. */
    val accentColor: Int = 0xFF000000.toInt(),
    val boldHeader: Boolean = true,
    /** A small circular badge with the business's first letter, drawn above its name. */
    val showBadge: Boolean = false,
    val dividerStyle: String = SOLID,
    /** Tighter line spacing and margins - fits more on a short thermal roll. */
    val compact: Boolean = false,
) {
    companion object {
        const val SOLID = "solid"
        const val DASHED = "dashed"
        const val DOUBLE = "double"
    }
}

object ReceiptTemplates {
    val CLASSIC = ReceiptStyle(paperWidth = 300f, accentColor = 0xFF000000.toInt(), boldHeader = true, showBadge = false, dividerStyle = ReceiptStyle.SOLID, compact = false)
    val MODERN = ReceiptStyle(paperWidth = 300f, accentColor = 0xFF0B6E4F.toInt(), boldHeader = true, showBadge = true, dividerStyle = ReceiptStyle.SOLID, compact = false)
    val COMPACT = ReceiptStyle(paperWidth = 260f, accentColor = 0xFF000000.toInt(), boldHeader = false, showBadge = false, dividerStyle = ReceiptStyle.DASHED, compact = true)
    val WIDE = ReceiptStyle(paperWidth = 480f, accentColor = 0xFF1A3C6E.toInt(), boldHeader = true, showBadge = true, dividerStyle = ReceiptStyle.DOUBLE, compact = false)

    /** Order matters: this is the order shown in the template picker. */
    val presetIds = listOf("classic", "modern", "compact", "wide")
    private val presets: Map<String, ReceiptStyle> = mapOf("classic" to CLASSIC, "modern" to MODERN, "compact" to COMPACT, "wide" to WIDE)

    /** Swatch colors offered when building a custom template. */
    val accentChoices = listOf(
        0xFF000000.toInt(), 0xFF0B6E4F.toInt(), 0xFF1A3C6E.toInt(), 0xFFB3261E.toInt(),
        0xFF7B3F9E.toInt(), 0xFFB26A00.toInt(), 0xFF00696E.toInt(), 0xFF4A4A4A.toInt(),
    )

    fun styleFor(business: Business): ReceiptStyle {
        if (business.templateId == "custom" && business.templateConfig.isNotBlank()) {
            return runCatching { Json.decodeFromString<ReceiptStyle>(business.templateConfig) }.getOrDefault(CLASSIC)
        }
        return presets[business.templateId] ?: CLASSIC
    }

    fun encode(style: ReceiptStyle): String =
        Json.encodeToString(ReceiptStyle.serializer(), style)
}
