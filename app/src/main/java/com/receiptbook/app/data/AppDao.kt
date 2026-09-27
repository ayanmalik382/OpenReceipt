package com.receiptbook.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---- Business
    // Deliberately NOT filtered by ownerEmail: this app is offline-first, so every business ever
    // created on this device stays visible and usable whether or not the person is signed in.
    // ownerEmail is written for informational/sync purposes only (see Repository.createBusiness).
    @Query("SELECT * FROM businesses WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeBusinesses(): Flow<List<Business>>
    @Query("SELECT * FROM businesses WHERE id = :id") fun observeBusiness(id: String): Flow<Business?>
    @Query("SELECT * FROM businesses WHERE id = :id") suspend fun business(id: String): Business?
    @Upsert suspend fun upsertBusiness(b: Business)
    @Query("SELECT * FROM businesses WHERE dirty = 1") suspend fun dirtyBusinesses(): List<Business>

    // ---- Suppliers
    @Query("SELECT * FROM suppliers WHERE businessId = :bid AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeSuppliers(bid: String): Flow<List<Supplier>>
    @Query("SELECT * FROM suppliers WHERE id = :id") suspend fun supplier(id: String): Supplier?
    @Upsert suspend fun upsertSupplier(s: Supplier)
    @Query("SELECT * FROM suppliers WHERE dirty = 1") suspend fun dirtySuppliers(): List<Supplier>

    // ---- Products
    @Query("SELECT * FROM products WHERE businessId = :bid AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeProducts(bid: String): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: String): Product?
    @Upsert suspend fun upsertProduct(p: Product)
    @Query("SELECT * FROM products WHERE dirty = 1") suspend fun dirtyProducts(): List<Product>

    // ---- Customers
    @Query("SELECT * FROM customers WHERE businessId = :bid AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeCustomers(bid: String): Flow<List<Customer>>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun customer(id: String): Customer?
    @Query("SELECT * FROM customers WHERE id = :id") fun observeCustomer(id: String): Flow<Customer?>
    @Upsert suspend fun upsertCustomer(c: Customer)
    @Query("SELECT * FROM customers WHERE dirty = 1") suspend fun dirtyCustomers(): List<Customer>

    /** Arrears = opening balance + (bill total - paid on bill) for active bills - later payments. */
    @Query(
        """SELECT c.id AS customerId,
           c.openingBalance
           + COALESCE((SELECT SUM(o.total - o.paid) FROM orders o WHERE o.customerId = c.id AND o.status = 'ACTIVE' AND o.deleted = 0), 0)
           - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.customerId = c.id AND p.deleted = 0), 0) AS balance
           FROM customers c WHERE c.businessId = :bid AND c.deleted = 0"""
    )
    fun observeBalances(bid: String): Flow<List<CustomerBalance>>

    @Query(
        """SELECT c.openingBalance
           + COALESCE((SELECT SUM(o.total - o.paid) FROM orders o WHERE o.customerId = c.id AND o.status = 'ACTIVE' AND o.deleted = 0), 0)
           - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.customerId = c.id AND p.deleted = 0), 0)
           FROM customers c WHERE c.id = :cid"""
    )
    suspend fun balanceOf(cid: String): Double?

    // ---- Orders
    @Query("SELECT * FROM orders WHERE businessId = :bid AND deleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeOrders(bid: String, from: Long, to: Long): Flow<List<SaleOrder>>
    @Query("SELECT * FROM orders WHERE customerId = :cid AND deleted = 0 ORDER BY date")
    fun observeCustomerOrders(cid: String): Flow<List<SaleOrder>>
    @Query("SELECT * FROM orders WHERE id = :id") suspend fun order(id: String): SaleOrder?
    @Query("SELECT * FROM orders WHERE id = :id") fun observeOrder(id: String): Flow<SaleOrder?>
    @Upsert suspend fun upsertOrder(o: SaleOrder)
    @Query("SELECT * FROM orders WHERE dirty = 1") suspend fun dirtyOrders(): List<SaleOrder>

    @Query("SELECT * FROM order_items WHERE orderId = :oid AND deleted = 0 ORDER BY position")
    suspend fun items(oid: String): List<OrderItem>
    @Query(
        """SELECT i.* FROM order_items i INNER JOIN orders o ON o.id = i.orderId
           WHERE o.businessId = :bid AND o.deleted = 0 AND o.status = 'ACTIVE' AND i.deleted = 0
           AND o.date BETWEEN :from AND :to ORDER BY o.date DESC, i.position"""
    )
    fun observeItemsInRange(bid: String, from: Long, to: Long): Flow<List<OrderItem>>
    @Upsert suspend fun upsertItems(items: List<OrderItem>)
    @Upsert suspend fun upsertItem(item: OrderItem)
    @Query("SELECT * FROM order_items WHERE dirty = 1") suspend fun dirtyItems(): List<OrderItem>

    // ---- Payments
    @Query("SELECT * FROM payments WHERE businessId = :bid AND deleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun observePayments(bid: String, from: Long, to: Long): Flow<List<Payment>>
    @Query("SELECT * FROM payments WHERE customerId = :cid AND deleted = 0 ORDER BY date")
    fun observeCustomerPayments(cid: String): Flow<List<Payment>>
    @Upsert suspend fun upsertPayment(p: Payment)
    @Query("SELECT * FROM payments WHERE dirty = 1") suspend fun dirtyPayments(): List<Payment>

    // ---- Expenses
    @Query("SELECT * FROM expenses WHERE businessId = :bid AND deleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeExpenses(bid: String, from: Long, to: Long): Flow<List<Expense>>
    @Upsert suspend fun upsertExpense(e: Expense)
    @Query("SELECT * FROM expenses WHERE dirty = 1") suspend fun dirtyExpenses(): List<Expense>

    // Sync helpers (get by id for conflict checks)
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun payment(id: String): Payment?
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun expense(id: String): Expense?
    @Query("SELECT * FROM order_items WHERE id = :id") suspend fun item(id: String): OrderItem?
}
