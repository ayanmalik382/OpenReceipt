package com.receiptbook.app.i18n

import com.receiptbook.app.data.Fmt
import java.text.NumberFormat

/** Locale-aware number/money formatting used on receipts and reports, always with Latin (0-9) digits. */
fun Loc.numFmt(v: Double): String {
    val r = Fmt.round2(v)
    val nf = NumberFormat.getNumberInstance(fmtLocale)
    nf.maximumFractionDigits = if (r % 1.0 == 0.0) 0 else 2
    nf.minimumFractionDigits = if (r % 1.0 == 0.0) 0 else 2
    return nf.format(r)
}

fun Loc.money(currency: String, v: Double): String = "$currency ${numFmt(v)}"
