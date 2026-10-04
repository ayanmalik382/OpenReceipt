package com.receiptbook.app.data

import androidx.room.withTransaction
import com.receiptbook.app.R
import com.receiptbook.app.i18n.L
import com.receiptbook.app.i18n.Loc
import com.receiptbook.app.i18n.need
import com.receiptbook.app.i18n.numFmt
import com.receiptbook.app.net.SessionStore
import java.util.UUID

data class CartLine(
    val name: String,
    val unit: String,
    val qty: Double,
    val price: Double,
    val productId: String? = null,
    val trackStock: Boolean = false
)

/** A bill that still has money owing on it, after later payments are applied oldest-first. */
data class UnpaidBill(val order: SaleOrder, val due: Double)

data class UnpaidSummary(val openingDue: Double, val bills: List<UnpaidBill>) {
    val total: Double get() = openingDue + bills.sumOf { it.due }
    val isEmpty: Boolean get() = openingDue <= 0.004 && bills.isEmpty()
}

data class LedgerRow(val date: Long, val details: String, val debit: Double, val credit: Double, val balance: Double)

data class CashSummary(
    val sales: Double,           // value of active bills
    val cashAtSale: Double,      // received on the bills themselves
    val arrearsReceived: Double, // payments received against arrears
    val expenses: Double,
    val creditGiven: Double      // sold on credit, not yet collected
) {
    val cashIn: Double get() = cashAtSale + arrearsReceived
    val net: Double get() = cashIn - expenses
}

class Repository(private val db: AppDatabase, private val session: SessionStore) {
    private val dao = db.dao()
    private fun now() = System.currentTimeMillis()
    fun newId(): String = UUID.randomUUID().toString()

    // ---------- Business ----------
    suspend fun createBusiness(
        email: String, name: String, address: String, phone: String,
        cash: Boolean, credit: Boolean, currency: String, prefix: String, footer: String,
        receiptLang: String, supplierNames: List<String>,
        templateId: String = "classic", templateConfig: String = "", logoBase64: String = "",
        ntn: String = "", city: String = "", fieldOfBusiness: String = "", fieldOfBusinessOther: String = "", natureOfBusiness: String = ""
    ): String {
        val id = newId()
        val t = now()
        db.withTransaction {
            dao.upsertBusiness(
                Business(
                    id = id, ownerEmail = email, name = name.trim(), address = address.trim(), phone = phone.trim(),
                    allowCash = cash, allowCredit = credit, currency = currency.trim().ifBlank { "Rs" },
                    receiptPrefix = prefix.trim().ifBlank { "R" }, nextReceiptNo = 1, footerNote = footer.trim(),
                    receiptLang = receiptLang, templateId = templateId, templateConfig = templateConfig, logoBase64 = logoBase64,
                    ntn = ntn.trim(), city = city.trim(), fieldOfBusiness = fieldOfBusiness,
                    fieldOfBusinessOther = if (fieldOfBusiness == BusinessFields.OTHER) fieldOfBusinessOther.trim() else "",
                    natureOfBusiness = natureOfBusiness,
                    updatedAt = t, deleted = false, dirty = true
                )
            )
            supplierNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
                dao.upsertSupplier(Supplier(newId(), id, it, updatedAt = t))
            }
        }
        return id
    }

    suspend fun save(b: Business) = dao.upsertBusiness(b.copy(updatedAt = now(), dirty = true))
    suspend fun remove(b: Business) = dao.upsertBusiness(b.copy(deleted = true, updatedAt = now(), dirty = true))
    suspend fun restore(b: Business) = dao.upsertBusiness(b.copy(deleted = false, updatedAt = now(), dirty = true))

    // ---------- Simple CRUD ----------
    suspend fun save(s: Supplier) = dao.upsertSupplier(s.copy(updatedAt = now(), dirty = true))
    suspend fun remove(s: Supplier) = dao.upsertSupplier(s.copy(deleted = true, updatedAt = now(), dirty = true))
    suspend fun restore(s: Supplier) = dao.upsertSupplier(s.copy(deleted = false, updatedAt = now(), dirty = true))
    suspend fun save(p: Product) = dao.upsertProduct(p.copy(updatedAt = now(), dirty = true))
    suspend fun remove(p: Product) = dao.upsertProduct(p.copy(deleted = true, updatedAt = now(), dirty = true))
    suspend fun restore(p: Product) = dao.upsertProduct(p.copy(deleted = false, updatedAt = now(), dirty = true))
    suspend fun save(c: Customer) = dao.upsertCustomer(c.copy(updatedAt = now(), dirty = true))

    /** Returns a (translated) error message if the customer can't be removed. */
    suspend fun remove(c: Customer): String? {
        val bal = dao.balanceOf(c.id) ?: 0.0
        if (Math.abs(bal) > 0.004) return L.t(R.string.err_customer_balance, L.current.numFmt(bal))
        dao.upsertCustomer(c.copy(deleted = true, updatedAt = now(), dirty = true))
        return null
    }
    suspend fun restore(c: Customer) = dao.upsertCustomer(c.copy(deleted = false, updatedAt = now(), dirty = true))

    // ---------- Orders ----------
    suspend fun createOrder(
        bid: String, customer: Customer?, walkInName: String, lines: List<CartLine>,
        discount: Double, received: Double, term: String, note: String
    ): SaleOrder = db.withTransaction {
        val biz = dao.business(bid)
        need(biz != null, R.string.err_business_not_found)
        need(lines.isNotEmpty(), R.string.err_add_product)
        need(lines.all { it.qty > 0 && it.price >= 0 }, R.string.err_qty)
        if (term == Term.CREDIT) need(customer != null, R.string.err_credit_customer)

        val subtotal = Fmt.round2(lines.sumOf { it.qty * it.price })
        val disc = Fmt.round2(discount.coerceIn(0.0, subtotal))
        val total = Fmt.round2(subtotal - disc)
        val prev = Fmt.round2(customer?.let { dao.balanceOf(it.id) } ?: 0.0)

        val paid = Fmt.round2(minOf(received, total).coerceAtLeast(0.0))
        val extra = Fmt.round2((received - total).coerceAtLeast(0.0))
        if (extra > 0) {
            need(customer != null, R.string.err_more_than_bill)
            need(extra <= maxOf(prev, 0.0) + 0.004, R.string.err_more_than_arrears)
        }

        val t = now()
        val receiptNo = "${biz!!.receiptPrefix}-${biz.nextReceiptNo.toString().padStart(4, '0')}"
        val order = SaleOrder(
            id = newId(), businessId = bid, receiptNo = receiptNo,
            customerId = customer?.id,
            customerName = customer?.name ?: walkInName.trim(),
            customerPhone = customer?.phone ?: "",
            date = t, term = term, subtotal = subtotal, discount = disc, total = total,
            paid = paid, previousBalance = prev, arrearsPaid = extra,
            status = Status.ACTIVE, note = note.trim(), updatedAt = t, dirty = true
        )
        dao.upsertOrder(order)
        dao.upsertItems(lines.mapIndexed { i, l ->
            OrderItem(newId(), bid, order.id, l.productId, l.name, l.unit, l.qty, l.price,
                Fmt.round2(l.qty * l.price), i, t, false, true)
        })
        lines.forEach { l ->
            if (l.trackStock && l.productId != null) {
                dao.product(l.productId)?.let { p ->
                    dao.upsertProduct(p.copy(stockQty = p.stockQty - l.qty, updatedAt = t, dirty = true))
                }
            }
        }
        if (extra > 0 && customer != null) {
            dao.upsertPayment(
                Payment(id = newId(), businessId = bid, customerId = customer.id, date = t, amount = extra,
                    method = "Cash", note = "", refReceipt = receiptNo, updatedAt = t, deleted = false, dirty = true)
            )
        }
        dao.upsertBusiness(biz.copy(nextReceiptNo = biz.nextReceiptNo + 1, updatedAt = t, dirty = true))
        order
    }

    /** Receipts are never deleted: cancel keeps an audit trail and returns stock. */
    suspend fun cancelOrder(orderId: String) = db.withTransaction {
        val o = dao.order(orderId) ?: return@withTransaction
        if (o.status == Status.CANCELLED) return@withTransaction
        val t = now()
        dao.upsertOrder(o.copy(status = Status.CANCELLED, updatedAt = t, dirty = true))
        dao.items(orderId).forEach { i ->
            if (i.productId != null) {
                dao.product(i.productId)?.let { p ->
                    if (p.trackStock) dao.upsertProduct(p.copy(stockQty = p.stockQty + i.qty, updatedAt = t, dirty = true))
                }
            }
        }
        // Extra arrears money taken with this receipt stays as a payment: that cash was really received.
    }

    // ---------- Payments & expenses ----------
    suspend fun addPayment(bid: String, customerId: String, amount: Double, method: String, note: String, date: Long = now()) {
        need(amount > 0, R.string.err_amount)
        dao.upsertPayment(
            Payment(id = newId(), businessId = bid, customerId = customerId, date = date, amount = Fmt.round2(amount),
                method = method, note = note.trim(), updatedAt = now(), deleted = false, dirty = true)
        )
    }
    suspend fun remove(p: Payment) = dao.upsertPayment(p.copy(deleted = true, updatedAt = now(), dirty = true))
    suspend fun restore(p: Payment) = dao.upsertPayment(p.copy(deleted = false, updatedAt = now(), dirty = true))

    suspend fun addExpense(bid: String, supplierId: String?, category: String, amount: Double, note: String, date: Long = now()) {
        need(amount > 0, R.string.err_amount)
        dao.upsertExpense(Expense(newId(), bid, supplierId, category, date, Fmt.round2(amount), note.trim(), now(), false, true))
    }
    suspend fun remove(e: Expense) = dao.upsertExpense(e.copy(deleted = true, updatedAt = now(), dirty = true))
    suspend fun restore(e: Expense) = dao.upsertExpense(e.copy(deleted = false, updatedAt = now(), dirty = true))

    // ---------- Permanent deletion ("Recently deleted" -> "Delete permanently") ----------
    // Only reachable on an item that is ALREADY soft-deleted (deleted = true), so it has already
    // been hidden and (if signed in) already synced as deleted to the cloud. This removes it from
    // this phone right away, and queues the matching row for removal from the cloud too - see
    // SyncManager.flushPurges(), which retries this automatically once the app is back online.
    suspend fun purge(entity: String, id: String) {
        when (entity) {
            "business" -> dao.hardDeleteBusiness(id)
            "supplier" -> dao.hardDeleteSupplier(id)
            "product" -> dao.hardDeleteProduct(id)
            "customer" -> dao.hardDeleteCustomer(id)
            "payment" -> dao.hardDeletePayment(id)
            "expense" -> dao.hardDeleteExpense(id)
            else -> error("Unknown entity: $entity")
        }
        if (session.token != null) session.addPendingPurge(entity, id) // nothing to reach in the cloud if never signed in
    }

    // ---------- Pure helpers ----------
    companion object {
        fun ledger(loc: Loc, c: Customer, orders: List<SaleOrder>, payments: List<Payment>): List<LedgerRow> {
            data class Ev(val date: Long, val order: Int, val details: String, val debit: Double, val credit: Double)
            val ev = mutableListOf<Ev>()
            orders.filter { it.status == Status.ACTIVE }.forEach { o ->
                ev += Ev(o.date, 0, loc.t(R.string.ledger_receipt, o.receiptNo), o.total, 0.0)
                if (o.paid > 0) ev += Ev(o.date, 1, loc.t(R.string.ledger_paid_on, o.receiptNo), 0.0, o.paid)
            }
            payments.forEach { p ->
                val d = if (p.refReceipt.isNotBlank()) loc.t(R.string.ledger_arrears_with, p.refReceipt)
                else loc.t(R.string.ledger_payment, loc.method(p.method)) + if (p.note.isNotBlank()) " - ${p.note}" else ""
                ev += Ev(p.date, 2, d, 0.0, p.amount)
            }
            ev.sortWith(compareBy({ it.date }, { it.order }))
            var bal = c.openingBalance
            val out = mutableListOf<LedgerRow>()
            if (c.openingBalance != 0.0) out += LedgerRow(0L, loc.t(R.string.ledger_opening), c.openingBalance, 0.0, bal)
            ev.forEach { e ->
                bal = Fmt.round2(bal + e.debit - e.credit)
                out += LedgerRow(e.date, e.details, e.debit, e.credit, bal)
            }
            return out
        }

        /**
         * Which bills are still unpaid? Payments received later (arrears payments) aren't tied to a
         * specific bill, so they're applied oldest-first: first to any opening balance the customer
         * had before using the app, then to the oldest bill, and so on. What's left is what is
         * genuinely still owed, bill by bill.
         */
        fun unpaidBills(c: Customer, orders: List<SaleOrder>, payments: List<Payment>): UnpaidSummary {
            var credit = payments.sumOf { it.amount }
            var openingDue = maxOf(c.openingBalance, 0.0)
            val useOnOpening = minOf(credit, openingDue)
            openingDue -= useOnOpening; credit -= useOnOpening
            val bills = mutableListOf<UnpaidBill>()
            for (o in orders.filter { it.status == Status.ACTIVE }.sortedBy { it.date }) {
                var due = Fmt.round2(o.total - o.paid)
                if (due <= 0.004) continue
                val use = minOf(credit, due)
                due = Fmt.round2(due - use); credit -= use
                if (due > 0.004) bills += UnpaidBill(o, due)
            }
            return UnpaidSummary(Fmt.round2(openingDue), bills)
        }

        fun summarize(orders: List<SaleOrder>, payments: List<Payment>, expenses: List<Expense>): CashSummary {
            val act = orders.filter { it.status == Status.ACTIVE }
            return CashSummary(
                sales = act.sumOf { it.total },
                cashAtSale = act.sumOf { it.paid },
                arrearsReceived = payments.sumOf { it.amount },
                expenses = expenses.sumOf { it.amount },
                creditGiven = act.sumOf { it.total - it.paid }
            )
        }
    }
}