package com.receiptbook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Business::class, Supplier::class, Product::class, Customer::class,
        SaleOrder::class, OrderItem::class, Payment::class, Expense::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "receiptbook.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: receipt language per business + receipt reference on payments (for translation). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE businesses ADD COLUMN receiptLang TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN refReceipt TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
