package com.brexa.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brexa.app.data.local.dao.*
import com.brexa.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        ExchangeRateHistoryEntity::class,
        AppSettingEntity::class,
        PendingExpenseEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class BrexaDatabase : RoomDatabase() {

    abstract fun currencyDao(): CurrencyDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun exchangeRateHistoryDao(): ExchangeRateHistoryDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun pendingExpenseDao(): PendingExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: BrexaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_expenses` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `due_date_timestamp` INTEGER NOT NULL,
                        `category_id` TEXT,
                        `account_id` TEXT,
                        `note` TEXT NOT NULL DEFAULT '',
                        `is_paid` INTEGER NOT NULL DEFAULT 0,
                        `paid_at_timestamp` INTEGER,
                        `transaction_id` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_expenses_due_date_timestamp` ON `pending_expenses` (`due_date_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_expenses_is_paid` ON `pending_expenses` (`is_paid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_expenses_category_id` ON `pending_expenses` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_expenses_account_id` ON `pending_expenses` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_expenses_transaction_id` ON `pending_expenses` (`transaction_id`)")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): BrexaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrexaDatabase::class.java,
                    "brexa_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(BrexaDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class BrexaDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            scope.launch(Dispatchers.IO) {
                try {
                    db.execSQL("UPDATE accounts SET name = 'Efectivo' WHERE name = 'Efectivo Principal'")
                } catch (_: Exception) { }
            }
        }

        private suspend fun populateInitialData(db: BrexaDatabase) {
            // 1. Moneda Base Principal (Preconfigurada como USD, pero 100% editable por el usuario)
            val baseCurrency = CurrencyEntity(
                id = "CUR_BASE",
                code = "USD",
                name = "Dólar Estadounidense",
                symbol = "$",
                rateToUsd = 1.0,
                iconId = "ic_account_cash",
                isBase = true,
                isActive = true,
                sortOrder = 0
            )
            db.currencyDao().insertCurrency(baseCurrency)

            // 2. Cuenta Predeterminada vinculada a la Moneda Base
            val defaultAccount = AccountEntity(
                id = "ACC_DEFAULT",
                name = "Efectivo",
                currencyId = "CUR_BASE",
                iconType = "CASH",
                colorHex = "#D4FF00",
                initialBalance = 0.0,
                currentBalance = 0.0,
                includeInTotal = true,
                isDefault = true,
                isArchived = false,
                sortOrder = 0
            )
            db.accountDao().insertAccount(defaultAccount)

            // 3. 5 Categorías de Gastos Predeterminadas
            val expenseCategories = listOf(
                CategoryEntity("CAT_EXP_1", "Alimentación", "EXPENSE", "ic_cat_food", "#D4FF00", isDefault = true, sortOrder = 0),
                CategoryEntity("CAT_EXP_2", "Vivienda y Servicios", "EXPENSE", "ic_cat_housing", "#D4FF00", isDefault = true, sortOrder = 1),
                CategoryEntity("CAT_EXP_3", "Transporte", "EXPENSE", "ic_cat_transport", "#D4FF00", isDefault = true, sortOrder = 2),
                CategoryEntity("CAT_EXP_4", "Salud y Cuidado Personal", "EXPENSE", "ic_cat_health", "#FF3366", isDefault = true, sortOrder = 3),
                CategoryEntity("CAT_EXP_5", "Recreación y Extras", "EXPENSE", "ic_cat_leisure", "#9D4EDD", isDefault = true, sortOrder = 4)
            )

            // 4. 5 Categorías de Ingresos Predeterminadas
            val incomeCategories = listOf(
                CategoryEntity("CAT_INC_1", "Salario Principal", "INCOME", "ic_account_cash", "#00FF88", isDefault = true, sortOrder = 0),
                CategoryEntity("CAT_INC_2", "Comisiones y Bonos", "INCOME", "ic_cat_general", "#00FF88", isDefault = true, sortOrder = 1),
                CategoryEntity("CAT_INC_3", "Servicios Freelance", "INCOME", "ic_account_crypto", "#9D4EDD", isDefault = true, sortOrder = 2),
                CategoryEntity("CAT_INC_4", "Ventas Eventuales", "INCOME", "ic_cat_food", "#00FF88", isDefault = true, sortOrder = 3),
                CategoryEntity("CAT_INC_5", "Transferencias Externas", "INCOME", "ic_account_bank", "#3A86FF", isDefault = true, sortOrder = 4)
            )

            db.categoryDao().insertAll(expenseCategories + incomeCategories)

            // 5. Ajustes Iniciales
            db.appSettingDao().setSetting(AppSettingEntity("incognito_mode", "false"))
            db.appSettingDao().setSetting(AppSettingEntity("currency_display", "USD"))
        }
    }
}
