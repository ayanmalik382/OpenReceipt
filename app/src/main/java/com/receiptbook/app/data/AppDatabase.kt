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
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "receiptbook.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v2: receipt language per business + receipt reference on payments (for translation). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE businesses ADD COLUMN receiptLang TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN refReceipt TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3: receipt template per business - a preset id, or "custom" with its own saved style. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE businesses ADD COLUMN templateId TEXT NOT NULL DEFAULT 'classic'")
                db.execSQL("ALTER TABLE businesses ADD COLUMN templateConfig TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4: an optional picture on a business (logo), customer, or supplier - a compressed
         * Base64 JPEG (see media/ImageStore.kt). Empty means "no picture set". */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE businesses ADD COLUMN logoBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE suppliers ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
