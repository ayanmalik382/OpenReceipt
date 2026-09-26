package com.receiptbook.app.export

import android.graphics.Canvas
import android.graphics.Color
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
 * Draws a receipt in a 300-unit-wide coordinate space, in the business's chosen receipt language
 * (see Business.receiptLang / Loc.receiptLoc). Pass canvas = null to only measure the height.
 * Text is drawn with StaticLayout so Urdu/Arabic letters join correctly and the layout mirrors for RTL.
 */
object ReceiptRenderer {
    const val WIDTH = 300f
    private const val M = 12f
    private const val RIGHT = WIDTH - M
    private const val CONTENT_W = WIDTH - 2 * M

    private fun draw(canvas: Canvas?, text: String, x: Float, y: Float, w: Float, paint: Paint, align: Layout.Alignment, rtl: Boolean): Float {
        val tp = TextPaint(paint)
        val heuristic = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, Math.max(1, w.toInt()))
            .setAlignment(align).setTextDirection(heuristic).setLineSpacing(0f, 1f).setIncludePad(false).build()
        canvas?.let { c -> c.save(); c.translate(x, y); sl.draw(c); c.restore() }
        return sl.height.toFloat()
    }

    fun render(canvas: Canvas?, loc: Loc, b: Business, o: SaleOrder, items: List<OrderItem>): Float {
        val rtl = loc.rtl
        val cur = b.currency
        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val bold = TextPaint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val big = TextPaint(bold).apply { textSize = 15f }
        val small = TextPaint(normal).apply { textSize = 8f; color = Color.GRAY }
        val grey = Paint().apply { color = Color.GRAY; strokeWidth = 0.7f }

        val START = Layout.Alignment.ALIGN_NORMAL   // "start" = right in RTL, left in LTR
        val END = Layout.Alignment.ALIGN_OPPOSITE    // "end"   = left in RTL, right in LTR
        val CENTER = Layout.Alignment.ALIGN_CENTER

        var y = 22f
        fun rule() { canvas?.drawLine(M, y, RIGHT, y, grey); y += 11f }
        fun center(s: String, p: TextPaint, extra: Float = 4f) { y += draw(canvas, s, M, y, CONTENT_W, p, CENTER, rtl) + extra }
        fun pair(l: String, v: String, strong: Boolean = false) {
            val p = if (strong) bold else normal
            val lw = CONTENT_W * 0.55f
            val h1 = draw(canvas, l, M, y, lw, p, START, rtl)
            val h2 = draw(canvas, v, M + lw, y, CONTENT_W - lw, p, END, rtl)
            y += Math.max(h1, h2) + 3f
        }

        center(b.name, big, 6f)
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
        val numColW = CONTENT_W * 0.19f
        val nameW = CONTENT_W - numColW * 3

        // Item rows are laid out left-to-right as [name][qty][price][total]; for an RTL receipt
        // language the whole row is mirrored to [total][price][qty][name] so it still reads start-to-end.
        fun header() {
            val labels = listOf(loc.t(R.string.rc_item) to nameW, loc.t(R.string.rc_qty) to numColW, loc.t(R.string.rc_price) to numColW, loc.t(R.string.rc_total) to numColW)
            var x = M
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
            var x = M; var maxH = 0f
            order.forEachIndexed { i, (text, w) ->
                val align = if (!rtl) (if (i == 0) START else END) else (if (i == order.lastIndex) END else START)
                maxH = Math.max(maxH, draw(canvas, text, x, y, w, normal, align, rtl))
                x += w
            }
            y += maxH + 3f
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
            canvas.rotate(-25f, WIDTH / 2, y / 2)
            canvas.drawText(loc.t(R.string.rc_cancelled_stamp), WIDTH / 2, y / 2, wm)
            canvas.restore()
        }
        return y
    }
}
