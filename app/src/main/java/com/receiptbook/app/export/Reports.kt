package com.receiptbook.app.export

import android.content.Context
import com.receiptbook.app.R
import com.receiptbook.app.data.*
import com.receiptbook.app.i18n.Loc
import com.receiptbook.app.i18n.money
import com.receiptbook.app.i18n.numFmt
import java.io.File

/** Builds the report files (PDF / Excel) used by the Orders and Reports screens, in Loc's language. */
object Reports {
    private fun stamp(r: DateRange) = if (r.label == "All") "all" else "${Fmt.fileStamp(r.from)}-${Fmt.fileStamp(r.to)}"

    fun ordersPdf(ctx: Context, loc: Loc, b: Business, range: DateRange, orders: List<SaleOrder>): File {
        val cur = b.currency
        val s = Repository.summarize(orders, emptyList(), emptyList())
        return Exporter.tablePdf(
            ctx, loc, "Sales_${stamp(range)}.pdf",
            TableSpec(
                title = loc.t(R.string.rp_sales_title, b.name),
                subtitle = loc.rangeText(range),
                headers = listOf(
                    Triple(loc.t(R.string.rp_col_receipt), 1.1f, false), Triple(loc.t(R.string.rp_col_date), 1.3f, false),
                    Triple(loc.t(R.string.rp_col_customer), 2.3f, false), Triple(loc.t(R.string.rp_col_term), 0.8f, false),
                    Triple(loc.t(R.string.rp_col_total), 1.1f, true), Triple(loc.t(R.string.rp_col_paid), 1.1f, true), Triple(loc.t(R.string.rp_col_due), 1.1f, true)
                ),
                rows = orders.map {
                    val cancelled = it.status == Status.CANCELLED
                    listOf(
                        it.receiptNo, loc.date(it.date),
                        (it.customerName.ifBlank { loc.t(R.string.rp_walk_in) }) + if (cancelled) loc.t(R.string.rp_cancelled_suffix) else "",
                        if (it.term == Term.CREDIT) loc.t(R.string.rc_credit) else loc.t(R.string.rc_cash),
                        loc.numFmt(it.total), loc.numFmt(it.paid), loc.numFmt(Math.max(0.0, it.total - it.paid))
                    )
                },
                summary = listOf(
                    loc.t(R.string.rp_receipts_active) to orders.count { it.status == Status.ACTIVE }.toString(),
                    loc.t(R.string.rp_total_sales) to loc.money(cur, s.sales),
                    loc.t(R.string.rp_received_at_sale) to loc.money(cur, s.cashAtSale),
                    loc.t(R.string.rp_credit_unpaid) to loc.money(cur, s.creditGiven)
                )
            )
        )
    }

    fun summaryPdf(
        ctx: Context, loc: Loc, b: Business, range: DateRange, s: CashSummary,
        topProducts: List<Triple<String, Double, Double>>, debtors: List<Pair<String, Double>>
    ): File {
        val cur = b.currency
        val rows = mutableListOf<List<String>>()
        rows += listOf(loc.t(R.string.rp_total_sales), loc.money(cur, s.sales))
        rows += listOf(loc.t(R.string.received_at_sale), loc.money(cur, s.cashAtSale))
        rows += listOf(loc.t(R.string.arrears_payments_received), loc.money(cur, s.arrearsReceived))
        rows += listOf(loc.t(R.string.rp_total_cash_in), loc.money(cur, s.cashIn))
        rows += listOf(loc.t(R.string.expenses_supplier_payments), loc.money(cur, s.expenses))
        rows += listOf(loc.t(R.string.rp_net_cash_flow), loc.money(cur, s.net))
        rows += listOf(loc.t(R.string.rp_new_credit), loc.money(cur, s.creditGiven))
        rows += listOf("", "")
        rows += listOf(loc.t(R.string.rp_top_products_header), "")
        topProducts.forEach { rows += listOf(it.first, "${loc.numFmt(it.second)} / ${loc.numFmt(it.third)}") }
        rows += listOf("", "")
        rows += listOf(loc.t(R.string.rp_debtors_header), "")
        debtors.forEach { rows += listOf(it.first, loc.money(cur, it.second)) }
        return Exporter.tablePdf(
            ctx, loc, "Summary_${stamp(range)}.pdf",
            TableSpec(
                loc.t(R.string.rp_summary_title, b.name), loc.rangeText(range),
                listOf(Triple(loc.t(R.string.rp_col_item), 3f, false), Triple(loc.t(R.string.rp_col_amount), 1.6f, true)),
                rows
            )
        )
    }

    fun statementPdf(ctx: Context, loc: Loc, b: Business, c: Customer, rows: List<LedgerRow>, balance: Double): File {
        val cur = b.currency
        return Exporter.tablePdf(
            ctx, loc, "Statement_${c.name}.pdf",
            TableSpec(
                title = loc.t(R.string.rp_statement_title, c.name),
                subtitle = "${b.name}   |   ${loc.date(System.currentTimeMillis())}",
                headers = listOf(
                    Triple(loc.t(R.string.rp_col_date), 1.2f, false), Triple(loc.t(R.string.rp_col_details), 3f, false),
                    Triple(loc.t(R.string.rp_col_debit), 1.2f, true), Triple(loc.t(R.string.rp_col_credit), 1.2f, true), Triple(loc.t(R.string.rp_col_balance), 1.2f, true)
                ),
                rows = rows.map {
                    listOf(if (it.date == 0L) "-" else loc.date(it.date), it.details,
                        if (it.debit != 0.0) loc.numFmt(it.debit) else "", if (it.credit != 0.0) loc.numFmt(it.credit) else "", loc.numFmt(it.balance))
                },
                summary = listOf((if (balance >= 0) loc.t(R.string.rp_amount_due) else loc.t(R.string.rp_advance_credit)) to loc.money(cur, Math.abs(balance)))
            )
        )
    }

    fun stockPdf(ctx: Context, loc: Loc, b: Business, products: List<Product>): File =
        Exporter.tablePdf(
            ctx, loc, "Stock_${Fmt.fileStamp(System.currentTimeMillis())}.pdf",
            TableSpec(
                loc.t(R.string.rp_stock_title, b.name), loc.date(System.currentTimeMillis()),
                listOf(
                    Triple(loc.t(R.string.rp_col_product), 3f, false), Triple(loc.t(R.string.rp_col_unit), 1f, false),
                    Triple(loc.t(R.string.rp_col_price), 1.3f, true), Triple(loc.t(R.string.rp_col_stock), 1.3f, true)
                ),
                products.map { listOf(it.name, loc.unit(it.unit), loc.numFmt(it.price), if (it.trackStock) loc.numFmt(it.stockQty) else "-") }
            )
        )

    fun workbook(
        ctx: Context, loc: Loc, b: Business, range: DateRange,
        orders: List<SaleOrder>, items: List<OrderItem>, payments: List<Payment>,
        expenses: List<Expense>, customers: List<Customer>, suppliers: List<Supplier>, products: List<Product>
    ): File {
        val custName = customers.associate { it.id to it.name }
        val supName = suppliers.associate { it.id to it.name }
        val orderById = orders.associateBy { it.id }
        val s = Repository.summarize(orders, payments, expenses)

        val ordersSheet = mutableListOf<List<Any?>>(
            listOf(
                loc.t(R.string.rp_col_receipt), loc.t(R.string.rp_col_date), loc.t(R.string.rp_col_customer), loc.t(R.string.phone),
                loc.t(R.string.rp_col_term), "Status", loc.t(R.string.subtotal), loc.t(R.string.rc_discount), loc.t(R.string.rp_col_total),
                loc.t(R.string.rp_col_paid), loc.t(R.string.rp_col_due), loc.t(R.string.rc_previous_arrears), loc.t(R.string.rc_arrears_received), "Note"
            )
        )
        orders.forEach {
            ordersSheet += listOf(
                it.receiptNo, loc.dateTime(it.date), it.customerName.ifBlank { loc.t(R.string.rp_walk_in) }, it.customerPhone,
                if (it.term == Term.CREDIT) loc.t(R.string.rc_credit) else loc.t(R.string.rc_cash), it.status,
                it.subtotal, it.discount, it.total, it.paid, Math.max(0.0, it.total - it.paid), it.previousBalance, it.arrearsPaid, it.note
            )
        }
        val itemsSheet = mutableListOf<List<Any?>>(
            listOf(loc.t(R.string.rp_col_receipt), loc.t(R.string.rp_col_date), loc.t(R.string.rp_col_customer), loc.t(R.string.rp_col_product), loc.t(R.string.rp_col_unit), loc.t(R.string.quantity), loc.t(R.string.rp_col_price), loc.t(R.string.rc_total))
        )
        items.forEach { i ->
            val o = orderById[i.orderId] ?: return@forEach
            itemsSheet += listOf(o.receiptNo, loc.dateTime(o.date), o.customerName.ifBlank { loc.t(R.string.rp_walk_in) }, i.name, loc.unit(i.unit), i.qty, i.price, i.lineTotal)
        }
        val paySheet = mutableListOf<List<Any?>>(listOf(loc.t(R.string.rp_col_date), loc.t(R.string.customer_label), loc.t(R.string.amount), loc.t(R.string.method), loc.t(R.string.note_optional)))
        payments.forEach { paySheet += listOf(loc.dateTime(it.date), custName[it.customerId] ?: "", it.amount, loc.method(it.method), it.note) }
        val expSheet = mutableListOf<List<Any?>>(listOf(loc.t(R.string.rp_col_date), loc.t(R.string.category), loc.t(R.string.supplier), loc.t(R.string.amount), loc.t(R.string.note_optional)))
        expenses.forEach { expSheet += listOf(loc.dateTime(it.date), loc.category(it.category), supName[it.supplierId] ?: "", it.amount, it.note) }
        val prodSheet = mutableListOf<List<Any?>>(listOf(loc.t(R.string.rp_col_product), loc.t(R.string.rp_col_unit), loc.t(R.string.rp_col_price), "Cost", loc.t(R.string.rp_col_stock), "Low-stock level"))
        products.forEach { prodSheet += listOf(it.name, loc.unit(it.unit), it.price, it.costPrice, if (it.trackStock) it.stockQty else null, if (it.trackStock) it.lowStockAt else null) }
        val sumSheet = listOf<List<Any?>>(
            listOf(loc.t(R.string.rp_business), b.name), listOf(loc.t(R.string.rp_period), loc.rangeText(range)), listOf(),
            listOf(loc.t(R.string.rp_total_sales), s.sales), listOf(loc.t(R.string.received_at_sale), s.cashAtSale),
            listOf(loc.t(R.string.arrears_payments_received), s.arrearsReceived), listOf(loc.t(R.string.rp_total_cash_in), s.cashIn),
            listOf(loc.t(R.string.expenses_supplier_payments), s.expenses), listOf(loc.t(R.string.rp_net_cash_flow), s.net),
            listOf(loc.t(R.string.rp_new_credit), s.creditGiven)
        )
        return Exporter.xlsx(
            ctx, loc, "ReceiptBook_${stamp(range)}.xlsx",
            listOf(
                Exporter.Sheet(loc.t(R.string.rp_sheet_summary), sumSheet), Exporter.Sheet(loc.t(R.string.rp_sheet_receipts), ordersSheet),
                Exporter.Sheet(loc.t(R.string.rp_sheet_items), itemsSheet), Exporter.Sheet(loc.t(R.string.rp_sheet_payments), paySheet),
                Exporter.Sheet(loc.t(R.string.rp_sheet_expenses), expSheet), Exporter.Sheet(loc.t(R.string.rp_sheet_products), prodSheet)
            )
        )
    }
}
