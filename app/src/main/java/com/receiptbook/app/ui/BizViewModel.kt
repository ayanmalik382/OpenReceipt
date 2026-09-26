package com.receiptbook.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptbook.app.AppContainer
import com.receiptbook.app.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Live data for one business. Everything comes from the local database, so it works offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class BizViewModel(val c: AppContainer, val bid: String) : ViewModel() {
    private val dao = c.db.dao()
    val repo = c.repo

    private fun <T> Flow<T>.st(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    val business = dao.observeBusiness(bid).st(null)
    val products = dao.observeProducts(bid).st(emptyList())
    val suppliers = dao.observeSuppliers(bid).st(emptyList())
    val customers = dao.observeCustomers(bid).st(emptyList())
    val balances = dao.observeBalances(bid).map { l -> l.associate { it.customerId to it.balance } }.st(emptyMap())

    val range = MutableStateFlow(DateRange.today())
    val orders = range.flatMapLatest { dao.observeOrders(bid, it.from, it.to) }.st(emptyList())
    val payments = range.flatMapLatest { dao.observePayments(bid, it.from, it.to) }.st(emptyList())
    val expenses = range.flatMapLatest { dao.observeExpenses(bid, it.from, it.to) }.st(emptyList())
    val items = range.flatMapLatest { dao.observeItemsInRange(bid, it.from, it.to) }.st(emptyList())

    private val t0 = Fmt.startOfDay(System.currentTimeMillis())
    private val t1 = Fmt.endOfDay(System.currentTimeMillis())
    val todayOrders = dao.observeOrders(bid, t0, t1).st(emptyList())
    val todayPayments = dao.observePayments(bid, t0, t1).st(emptyList())
    val todayExpenses = dao.observeExpenses(bid, t0, t1).st(emptyList())
}
