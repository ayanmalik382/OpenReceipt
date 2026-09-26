package com.receiptbook.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Fmt {
    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    fun num(v: Double): String {
        val r = round2(v)
        return if (r % 1.0 == 0.0) String.format(Locale.US, "%,d", r.toLong())
        else String.format(Locale.US, "%,.2f", r)
    }

    fun money(cur: String, v: Double): String = "$cur ${num(v)}"
    fun date(ts: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
    fun dateTime(ts: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
    fun fileStamp(ts: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    fun parse(s: String): Double? = s.trim().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }

    fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(ts)
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis - 1
    }

    /** Material date picker returns UTC midnight; convert to local start of that calendar day. */
    fun fromPickerUtc(ms: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        u.timeInMillis = ms
        val l = Calendar.getInstance()
        l.clear()
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        return l.timeInMillis
    }
}

data class DateRange(val from: Long, val to: Long, val label: String) {
    companion object {
        fun today(): DateRange {
            val n = System.currentTimeMillis()
            return DateRange(Fmt.startOfDay(n), Fmt.endOfDay(n), "Today")
        }
        fun last7(): DateRange {
            val n = System.currentTimeMillis()
            return DateRange(Fmt.startOfDay(n - 6L * 86_400_000L), Fmt.endOfDay(n), "7 days")
        }
        fun month(): DateRange {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1)
            return DateRange(Fmt.startOfDay(c.timeInMillis), Fmt.endOfDay(System.currentTimeMillis()), "Month")
        }
        fun all(): DateRange = DateRange(0L, 253_402_300_799_000L, "All")
    }
}
