package com.receiptbook.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.i18n.Loc

/**
 * Draws a receipt in the business's chosen visual template (ReceiptStyle: colors, paper width,
 * divider style, badge) AND its chosen receipt language (Loc: text, direction, digits).
 * Pass canvas = null to only measure the height. Text is drawn with StaticLayout so Urdu/Arabic
 * letters join correctly and the layout mirrors for RTL languages, independent of the template.
 */
object ReceiptRenderer {

    private fun draw(canvas: Canvas?, text: String, x: Float, y: Float, w: Float, paint: Paint, align: Layout.Alignment, rtl: Boolean): Float {
        val tp = TextPaint(paint)
        val heuristic = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, Math.max(1, w.toInt()))
            .setAlignment(align).setTextDirection(heuristic).setLineSpacing(0f, 1f).setIncludePad(false).build()
        canvas?.let { c -> c.save(); c.translate(x, y); sl.draw(c); c.restore() }
        return sl.height.toFloat()
    }

    fun render(canvas: Canvas?, loc: Loc, style: ReceiptStyle, b: Business, o: SaleOrder, items: List<OrderItem>): Float {
        val rtl = loc.rtl
        val cur = b.currency
        val width = style.paperWidth
        val m = if (style.compact) 8f else 12f
        val right = width - m
        val contentW = width - 2 * m
        val gap = if (style.compact) 2f else 3f
        val lineGap = if (style.compact) 2f else 3f

        val black = Color.BLACK
        val accent = style.accentColor
        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = black; textSize = if (style.compact) 9f else 10f }
        val bold = TextPaint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val nameStyle = TextPaint(bold).apply { textSize = if (style.compact) 13f else 15f; color = accent; typeface = if (style.boldHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        val accentBold = TextPaint(bold).apply { color = accent }
        val small = TextPaint(normal).apply { textSize = 8f; color = Color.GRAY }
        val line = Paint().apply { color = accent; strokeWidth = if (style.compact) 0.6f else 0.8f }
        if (style.dividerStyle == ReceiptStyle.DASHED) line.pathEffect = DashPathEffect(floatArrayOf(3f, 2.5f), 0f)

        val START = Layout.Alignment.ALIGN_NORMAL   // "start" = right in RTL, left in LTR
        val END = Layout.Alignment.ALIGN_OPPOSITE    // "end"   = left in RTL, right in LTR
        val CENTER = Layout.Alignment.ALIGN_CENTER

        var y = if (style.showBadge) 34f else 22f
        fun rule() {
            canvas?.drawLine(m, y, right, y, line)
            if (style.dividerStyle == ReceiptStyle.DOUBLE) canvas?.drawLine(m, y + 2.2f, right, y + 2.2f, line)
            y += if (style.dividerStyle == ReceiptStyle.DOUBLE) 12f else 10f
        }
        fun center(s: String, p: TextPaint, extra: Float = 4f) { y += draw(canvas, s, m, y, contentW, p, CENTER, rtl) + extra }
        fun pair(l: String, v: String, strong: Boolean = false) {
            val p = if (strong) accentBold else normal
            val lw = contentW * 0.55f
            val h1 = draw(canvas, l, m, y, lw, p, START, rtl)
            val h2 = draw(canvas, v, m + lw, y, contentW - lw, p, END, rtl)
            y += Math.max(h1, h2) + gap
        }

        if (style.showBadge) {
            val letter = b.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
            canvas?.drawCircle(width / 2, 12f, 12f, badge)
            canvas?.drawText(letter, width / 2, 17f, badgeText)
        }
        center(b.name, nameStyle, if (style.compact) 4f else 6f)
        if (b.address.isNotBlank()) center(b.address, normal, 1f)
        if (b.phone.isNotBlank()) center(loc.t(R.string.rc_phone) + ": " + b.phone, normal, 1f)
        y += 4f; rule()
        pair(loc.t(R.string.rc_receipt_no), o.receiptNo, true)
        pair(loc.t(R.string.rc_date), loc.dateTime(o.date))
        pair(loc.t(R.string.rc_customer), if (o.customerName.isBlank()) loc.t(R.string.rc_walk_in) else o.customerName)
        if (o.customerPhone.isNotBlank()) pair(loc.t(R.string.rc_phone), o.customerPhone)
        pair(loc.t(R.string.rc_payment), if (o.term == Term.CREDIT) loc.t(R.string.rc_credit) else loc.t(R.string.rc_cash))
        rule()

        // Item table: name column is "start"-anchored and wide; qty/price/total are three "end" columns.
        val numColW = contentW * 0.19f
        val nameW = contentW - numColW * 3

        // Item rows are laid out left-to-right as [name][qty][price][total]; for an RTL receipt
        // language the whole row is mirrored to [total][price][qty][name] so it still reads start-to-end.
        fun header() {
            val labels = listOf(loc.t(R.string.rc_item) to nameW, loc.t(R.string.rc_qty) to numColW, loc.t(R.string.rc_price) to numColW, loc.t(R.string.rc_total) to numColW)
            var x = m
            val order = if (rtl) labels.reversed() else labels
            var maxH = 0f
            order.forEachIndexed { i, (text, w) ->
                val align = if (!rtl) (if (i == 0) START else END) else (if (i == order.lastIndex) END else START)
                maxH = Math.max(maxH, draw(canvas, text, x, y, w, bold, align, rtl))
                x += w
            }
            y += maxH + 5f; rule()
        }
        fun itemRow(name: String, qty: String, price: String, total: String) {
            val cols = listOf(name to nameW, qty to numColW, price to numColW, total to numColW)
            val order = if (rtl) cols.reversed() else cols
            var x = m; var maxH = 0f
            order.forEachIndexed { i, (text, w) ->
                val align = if (!rtl) (if (i == 0) START else END) else (if (i == order.lastIndex) END else START)
                maxH = Math.max(maxH, draw(canvas, text, x, y, w, normal, align, rtl))
                x += w
            }
            y += maxH + lineGap
        }

        header()
        for (li in items) itemRow(li.name, "${loc.numFmt(li.qty)} ${loc.unit(li.unit)}", loc.numFmt(li.price), loc.numFmt(li.lineTotal))
        rule()

        pair(loc.t(R.string.rc_subtotal), loc.money(cur, o.subtotal))
        if (o.discount > 0) pair(loc.t(R.string.rc_discount), "- " + loc.money(cur, o.discount))
        pair(loc.t(R.string.rc_total), loc.money(cur, o.total), true)
        pair(loc.t(R.string.rc_received), loc.money(cur, o.paid))
        val due = o.total - o.paid
        if (due > 0.004) pair(loc.t(R.string.rc_bill_balance), loc.money(cur, due))

        if (o.customerId != null) {
            rule()
            pair(loc.t(R.string.rc_previous_arrears), loc.money(cur, o.previousBalance))
            if (o.arrearsPaid > 0) pair(loc.t(R.string.rc_arrears_received), "- " + loc.money(cur, o.arrearsPaid))
            val nb = Fmt.round2(o.previousBalance + o.total - o.paid - o.arrearsPaid)
            pair(if (nb >= 0) loc.t(R.string.rc_total_arrears) else loc.t(R.string.rc_advance_credit), loc.money(cur, Math.abs(nb)), true)
        }

        rule()
        if (o.note.isNotBlank()) center(o.note, normal)
        val footer = b.footerNote.ifBlank { loc.t(R.string.footer_default) }
        if (footer.isNotBlank()) center(footer, normal)
        y += 2f
        center(loc.t(R.string.rc_made_with), small, 8f)
        y += 6f

        if (o.status == Status.CANCELLED && canvas != null) {
            val wm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 200, 0, 0); textSize = 34f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.save()
            canvas.rotate(-25f, width / 2, y / 2)
            canvas.drawText(loc.t(R.string.rc_cancelled_stamp), width / 2, y / 2, wm)
            canvas.restore()
        }
        return y
    }
}
