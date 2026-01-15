package com.example.vibemoney.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 提升版本号到 3，解决 schema 变更导致的闪退
@Database(entities = [Ledger::class, Transaction::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibe_money_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }

        // 处理旧版本新增列，避免升级清空数据
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureLedgerColumn(database, "isActive", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureLedgerColumn(database, "isClosed", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun ensureLedgerColumn(
            database: SupportSQLiteDatabase,
            columnName: String,
            columnDefinition: String
        ) {
            val cursor = database.query("PRAGMA table_info(ledgers)")
            cursor.use {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        return
                    }
                }
            }
            database.execSQL("ALTER TABLE ledgers ADD COLUMN $columnName $columnDefinition")
        }
    }
}
