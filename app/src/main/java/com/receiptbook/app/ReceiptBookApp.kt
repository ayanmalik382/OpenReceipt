package com.receiptbook.app

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.receiptbook.app.data.AppDatabase
import com.receiptbook.app.data.Repository
import com.receiptbook.app.i18n.L
import com.receiptbook.app.net.ApiClient
import com.receiptbook.app.net.SessionStore
import com.receiptbook.app.sync.SyncManager
import com.receiptbook.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class AppContainer(ctx: Context) {
    val session = SessionStore(ctx)
    val db = AppDatabase.build(ctx)
    val repo = Repository(db)
    val api = ApiClient(BuildConfig.API_BASE_URL, session)
    val sync = SyncManager(db, api, session)
}

class ReceiptBookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        L.init(this) // must run before any screen/string is used
        container = AppContainer(this)
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("receiptbook-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        container.sync.requestNow()
    }
}
