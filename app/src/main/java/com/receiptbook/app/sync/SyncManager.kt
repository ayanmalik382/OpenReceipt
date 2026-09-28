package com.receiptbook.app.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.receiptbook.app.R
import com.receiptbook.app.ReceiptBookApp
import com.receiptbook.app.data.*
import com.receiptbook.app.net.ApiClient
import com.receiptbook.app.net.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** errorText is already translated (from the server); errorRes is a string resource for local errors. */
data class SyncState(val running: Boolean = false, val errorRes: Int? = null, val errorText: String? = null, val lastOk: Long = 0L)

/**
 * Offline-first sync. Rows changed on this device have dirty = 1. We push them in batches and pull
 * everything newer than our cursor. Conflicts: the row with the newest updatedAt wins.
 */
class SyncManager(
    private val db: AppDatabase,
    private val api: ApiClient,
    private val session: SessionStore
) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SyncState(lastOk = session.lastSyncTime))
    val state: StateFlow<SyncState> = _state

    private class Pending(val id: String, val updatedAt: Long, val deleted: Boolean, val data: String)
    private class Table(
        val entity: String,
        val table: String,
        val pending: suspend () -> List<Pending>,
        val upsertJson: suspend (String) -> Unit
    )

    private inline fun <reified T : Syncable> table(
        entity: String,
        table: String,
        crossinline dirty: suspend () -> List<T>,
        crossinline get: suspend (String) -> T?,
        crossinline put: suspend (T) -> Unit,
        crossinline clean: (T) -> T
    ): Table = Table(
        entity, table,
        pending = { dirty().map { Pending(it.id, it.updatedAt, it.deleted, json.encodeToString(it)) } },
        upsertJson = { data ->
            val incoming = json.decodeFromString<T>(data)
            val existing = get(incoming.id)
            if (existing == null || !existing.dirty || incoming.updatedAt >= existing.updatedAt) put(clean(incoming))
        }
    )

    private val tables: List<Table> = listOf(
        table<Business>("business", "businesses", { dao.dirtyBusinesses() }, { dao.business(it) }, { dao.upsertBusiness(it) }, { it.copy(dirty = false) }),
        table<Supplier>("supplier", "suppliers", { dao.dirtySuppliers() }, { dao.supplier(it) }, { dao.upsertSupplier(it) }, { it.copy(dirty = false) }),
        table<Product>("product", "products", { dao.dirtyProducts() }, { dao.product(it) }, { dao.upsertProduct(it) }, { it.copy(dirty = false) }),
        table<Customer>("customer", "customers", { dao.dirtyCustomers() }, { dao.customer(it) }, { dao.upsertCustomer(it) }, { it.copy(dirty = false) }),
        table<SaleOrder>("order", "orders", { dao.dirtyOrders() }, { dao.order(it) }, { dao.upsertOrder(it) }, { it.copy(dirty = false) }),
        table<OrderItem>("orderItem", "order_items", { dao.dirtyItems() }, { dao.item(it) }, { dao.upsertItem(it) }, { it.copy(dirty = false) }),
        table<Payment>("payment", "payments", { dao.dirtyPayments() }, { dao.payment(it) }, { dao.upsertPayment(it) }, { it.copy(dirty = false) }),
        table<Expense>("expense", "expenses", { dao.dirtyExpenses() }, { dao.expense(it) }, { dao.upsertExpense(it) }, { it.copy(dirty = false) })
    )

    fun requestNow() {
        if (session.token != null) scope.launch { sync() }
    }

    /** Returns true on success. Never throws. */
    suspend fun sync(): Boolean = mutex.withLock {
        if (session.token == null || session.email.value == null) return@withLock false
        _state.value = _state.value.copy(running = true, errorRes = null, errorText = null)
        try {
            var cursor = session.cursor
            val pending = tables.flatMap { t -> t.pending().map { t to it } }
            val chunks: List<List<Pair<Table, Pending>>> = if (pending.isEmpty()) listOf(emptyList()) else pending.chunked(20)

            var resp: ApiClient.SyncResponse? = null
            for (chunk in chunks) {
                val body = chunk.map { (t, p) -> ApiClient.Change(t.entity, p.id, p.updatedAt, p.deleted, p.data) }
                val r = api.sync(cursor, body)
                applyIncoming(r.changes)
                markClean(chunk)
                cursor = r.cursor
                session.cursor = cursor
                resp = r
            }
            var more = resp?.hasMore == true
            while (more) {
                val r = api.sync(cursor, emptyList())
                applyIncoming(r.changes)
                cursor = r.cursor
                session.cursor = cursor
                more = r.hasMore
            }
            val now = System.currentTimeMillis()
            session.lastSyncTime = now
            _state.value = SyncState(false, null, null, now)
            true
        } catch (e: ApiClient.ApiException) {
            _state.value = _state.value.copy(running = false, errorText = e.message)
            false
        } catch (e: IOException) {
            _state.value = _state.value.copy(running = false, errorRes = R.string.sync_offline)
            false
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, errorRes = R.string.sync_failed)
            false
        }
    }

    private suspend fun applyIncoming(changes: List<ApiClient.Change>) {
        if (changes.isEmpty()) return
        db.withTransaction {
            for (c in changes) tables.firstOrNull { it.entity == c.entity }?.upsertJson?.invoke(c.data)
        }
    }

    /** Only clears the dirty flag if the row was not edited again while we were syncing. */
    private fun markClean(chunk: List<Pair<Table, Pending>>) {
        val w = db.openHelper.writableDatabase
        for ((t, p) in chunk) {
            w.execSQL("UPDATE ${t.table} SET dirty = 0 WHERE id = ? AND updatedAt = ?", arrayOf<Any?>(p.id, p.updatedAt))
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ReceiptBookApp).container
        if (c.session.token == null) return Result.success()
        return if (c.sync.sync()) Result.success() else Result.retry()
    }
}
