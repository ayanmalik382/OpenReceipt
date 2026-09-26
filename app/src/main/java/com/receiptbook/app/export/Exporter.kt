package com.receiptbook.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.receiptbook.app.data.*
import com.receiptbook.app.i18n.Loc
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TableSpec(
    val title: String,
    val subtitle: String,
    /** header label, weight, and whether this column holds a number (numbers stay in LTR reading order even on an RTL page). */
    val headers: List<Triple<String, Float, Boolean>>,
    val rows: List<List<String>>,
    val summary: List<Pair<String, String>> = emptyList()
)

object Exporter {
    const val PDF = "application/pdf"
    const val PNG = "image/png"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    // ---------- Receipt ----------
    fun receiptBitmap(loc: Loc, b: Business, o: SaleOrder, items: List<OrderItem>): Bitmap {
        val h = Math.ceil(ReceiptRenderer.render(null, loc, b, o, items).toDouble()).toInt()
        val scale = 2f
        val bmp = Bitmap.createBitmap((ReceiptRenderer.WIDTH * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        ReceiptRenderer.render(c, loc, b, o, items)
        return bmp
    }

    fun receiptPdf(ctx: Context, loc: Loc, b: Business, o: SaleOrder, items: List<OrderItem>): File {
        val h = Math.ceil(ReceiptRenderer.render(null, loc, b, o, items).toDouble()).toInt()
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(ReceiptRenderer.WIDTH.toInt(), h, 1).create())
        page.canvas.drawColor(Color.WHITE)
        ReceiptRenderer.render(page.canvas, loc, b, o, items)
        doc.finishPage(page)
        val f = File(dir(ctx), "Receipt_${safe(o.receiptNo)}.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun receiptPng(ctx: Context, loc: Loc, b: Business, o: SaleOrder, items: List<OrderItem>): File {
        val bmp = receiptBitmap(loc, b, o, items)
        val f = File(dir(ctx), "Receipt_${safe(o.receiptNo)}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    // ---------- Generic A4 table report (RTL-aware, Urdu/Arabic-shaping via StaticLayout) ----------
    private class Pager(val doc: PdfDocument) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f
        var started = false
        fun next() {
            if (started) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            canvas = page.canvas
            y = 44f
            started = true
        }
        fun finish() { if (started) doc.finishPage(page) }
    }

    private fun cellLayout(text: String, w: Int, paint: TextPaint, align: Layout.Alignment, rtl: Boolean): StaticLayout {
        val heuristic = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, Math.max(1, w))
            .setAlignment(align).setTextDirection(heuristic).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false).build()
    }

    fun tablePdf(ctx: Context, loc: Loc, fileName: String, spec: TableSpec): File {
        val doc = PdfDocument()
        val pg = Pager(doc)
        val left = 32f
        val right = 563f
        val width = right - left
        val totalW = spec.headers.sumOf { it.second.toDouble() }.toFloat()
        val colW = spec.headers.map { (width * it.second / totalW) }
        // Column order on the page: for RTL, the first logical column sits at the right edge.
        val order = if (loc.rtl) spec.headers.indices.reversed().toList() else spec.headers.indices.toList()
        val xs = FloatArray(spec.headers.size)
        run { var x = left; order.forEach { i -> xs[i] = x; x += colW[i] } }

        val START = Layout.Alignment.ALIGN_NORMAL
        val END = Layout.Alignment.ALIGN_OPPOSITE
        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val bold = TextPaint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val h1 = TextPaint(bold).apply { textSize = 16f }
        val grey = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        fun cell(text: String, i: Int, p: TextPaint, numeric: Boolean): Float {
            val w = (colW[i] - 6f).toInt()
            // Numeric columns always read left-to-right; text columns follow the page's own direction.
            val align = if (numeric) (if (loc.rtl) START else END) else START
            val sl = cellLayout(text, w, p, align, if (numeric) false else loc.rtl)
            pg.canvas.save(); pg.canvas.translate(xs[i] + 3f, pg.y); sl.draw(pg.canvas); pg.canvas.restore()
            return sl.height.toFloat()
        }
        fun header(): Float {
            var maxH = 0f
            spec.headers.forEachIndexed { i, (text, _, numeric) -> maxH = Math.max(maxH, cell(text, i, bold, numeric)) }
            return maxH
        }
        fun newPage(first: Boolean, withHeader: Boolean = true) {
            pg.next()
            if (first) {
                val titleSl = cellLayout(spec.title, (right - left).toInt(), h1, START, loc.rtl)
                pg.canvas.save(); pg.canvas.translate(left, pg.y); titleSl.draw(pg.canvas); pg.canvas.restore()
                pg.y += titleSl.height + 8f
                val subSl = cellLayout(spec.subtitle, (right - left).toInt(), normal, START, loc.rtl)
                pg.canvas.save(); pg.canvas.translate(left, pg.y); subSl.draw(pg.canvas); pg.canvas.restore()
                pg.y += subSl.height + 14f
            }
            if (withHeader) {
                val hh = header()
                pg.y += hh + 5f
                pg.canvas.drawLine(left, pg.y, right, pg.y, grey)
                pg.y += 13f
            }
        }

        newPage(true)
        for (r in spec.rows) {
            if (pg.y > 780f) newPage(false)
            var rowH = 0f
            r.forEachIndexed { i, t -> rowH = Math.max(rowH, cell(t, i, normal, spec.headers.getOrNull(i)?.third ?: false)) }
            pg.y += rowH + 6f
        }
        if (spec.summary.isNotEmpty()) {
            if (pg.y + spec.summary.size * 16f + 24f > 810f) newPage(false, withHeader = false)
            pg.y += 4f
            pg.canvas.drawLine(left, pg.y, right, pg.y, grey)
            pg.y += 16f
            val labelW = (width * 0.62f).toInt()
            val valueW = (width * 0.38f).toInt()
            for ((l, v) in spec.summary) {
                val lx = if (loc.rtl) left + valueW else left
                val vx = if (loc.rtl) left else left + labelW
                val lsl = cellLayout(l, labelW, bold, START, loc.rtl)
                val vsl = cellLayout(v, valueW, bold, if (loc.rtl) START else END, false)
                pg.canvas.save(); pg.canvas.translate(lx, pg.y); lsl.draw(pg.canvas); pg.canvas.restore()
                pg.canvas.save(); pg.canvas.translate(vx, pg.y); vsl.draw(pg.canvas); pg.canvas.restore()
                pg.y += Math.max(lsl.height, vsl.height) + 4f
            }
        }
        pg.finish()
        val f = File(dir(ctx), safe(fileName))
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    // ---------- Excel ----------
    class Sheet(val name: String, val rows: List<List<Any?>>)

    fun xlsx(ctx: Context, loc: Loc, fileName: String, sheets: List<Sheet>): File {
        val f = File(dir(ctx), safe(fileName))
        XlsxWriter.write(f, sheets, rtl = loc.rtl)
        return f
    }

    // ---------- Share ----------
    fun share(ctx: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle)
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun shareText(ctx: Context, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        val chooser = Intent.createChooser(send, chooserTitle)
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}

/** Minimal dependency-free .xlsx writer (inline strings, numbers). Opens in Excel, WPS and Google Sheets. */
object XlsxWriter {
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\n' && ch != '\t' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun col(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n >= 0) { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 }
        return sb.toString()
    }

    private fun sheetXml(rows: List<List<Any?>>, rtl: Boolean): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (rtl) sb.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>""")
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, v ->
                val ref = "${col(c)}${r + 1}"
                when (v) {
                    null -> {}
                    is Number -> sb.append("""<c r="$ref"><v>${v.toDouble()}</v></c>""")
                    else -> sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    fun write(file: File, sheets: List<Exporter.Sheet>, rtl: Boolean) {
        val names = sheets.mapIndexed { i, s -> s.name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31).ifBlank { "Sheet${i + 1}" } }
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun entry(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            entry(
                "[Content_Types].xml",
                head + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    sheets.indices.joinToString("") {
                        """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                    } + "</Types>"
            )
            entry(
                "_rels/.rels",
                head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
            )
            entry(
                "xl/workbook.xml",
                head + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    names.mapIndexed { i, n -> """<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                    "</sheets></workbook>"
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    sheets.indices.joinToString("") {
                        """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
                    } + "</Relationships>"
            )
            sheets.forEachIndexed { i, s -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(s.rows, rtl)) }
        }
    }
}
