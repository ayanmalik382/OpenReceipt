package com.receiptbook.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object Term { const val CASH = "CASH"; const val CREDIT = "CREDIT" }
object Status { const val ACTIVE = "ACTIVE"; const val CANCELLED = "CANCELLED" }

/** Every synced row carries these. dirty = has local changes not yet pushed to the server. */
interface Syncable {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
}

@Serializable
@Entity(tableName = "businesses", indices = [Index("ownerEmail")])
data class Business(
    @PrimaryKey override val id: String,
    val ownerEmail: String,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val allowCash: Boolean = true,
    val allowCredit: Boolean = true,
    val currency: String = "Rs",
    val receiptPrefix: String = "R",
    val nextReceiptNo: Int = 1,
    /** Empty = default footer in the receipt language. */
    val footerNote: String = "",
    /** Language of printed receipts. Empty = same as the app language. */
    @ColumnInfo(defaultValue = "''") val receiptLang: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "suppliers", indices = [Index("businessId")])
data class Supplier(
    @PrimaryKey override val id: String,
    val businessId: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "products", indices = [Index("businessId")])
data class Product(
    @PrimaryKey override val id: String,
    val businessId: String,
    val name: String,
    val unit: String = "pcs",
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val trackStock: Boolean = false,
    val stockQty: Double = 0.0,
    val lowStockAt: Double = 0.0,
    val supplierId: String? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "customers", indices = [Index("businessId")])
data class Customer(
    @PrimaryKey override val id: String,
    val businessId: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    /** Arrears the customer already owed before using the app. */
    val openingBalance: Double = 0.0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "orders", indices = [Index("businessId"), Index("customerId"), Index("date")])
data class SaleOrder(
    @PrimaryKey override val id: String,
    val businessId: String,
    val receiptNo: String,
    val customerId: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val date: Long,
    val term: String = Term.CASH,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    /** Amount received against THIS bill. */
    val paid: Double = 0.0,
    /** Customer arrears at the moment this receipt was made. */
    val previousBalance: Double = 0.0,
    /** Extra money received at this sale that went to old arrears (also stored as a Payment). */
    val arrearsPaid: Double = 0.0,
    val status: String = Status.ACTIVE,
    val note: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "order_items", indices = [Index("businessId"), Index("orderId")])
data class OrderItem(
    @PrimaryKey override val id: String,
    val businessId: String,
    val orderId: String,
    val productId: String? = null,
    val name: String,
    val unit: String = "pcs",
    val qty: Double,
    val price: Double,
    val lineTotal: Double,
    val position: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "payments", indices = [Index("businessId"), Index("customerId"), Index("date")])
data class Payment(
    @PrimaryKey override val id: String,
    val businessId: String,
    val customerId: String,
    val date: Long,
    val amount: Double,
    val method: String = "Cash",
    val note: String = "",
    /** Receipt number this money was received with (translated when displayed). */
    @ColumnInfo(defaultValue = "''") val refReceipt: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

@Serializable
@Entity(tableName = "expenses", indices = [Index("businessId"), Index("date")])
data class Expense(
    @PrimaryKey override val id: String,
    val businessId: String,
    val supplierId: String? = null,
    val category: String = "Other",
    val date: Long,
    val amount: Double,
    val note: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true
) : Syncable

data class CustomerBalance(val customerId: String, val balance: Double)

val PAYMENT_METHODS = listOf("Cash", "Bank", "JazzCash", "Easypaisa", "Cheque")
val EXPENSE_CATEGORIES = listOf("Supplier payment", "Rent", "Salaries", "Utilities", "Transport", "Other")
