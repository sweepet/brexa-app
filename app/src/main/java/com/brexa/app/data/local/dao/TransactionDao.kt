package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "total_usd") val totalUsd: Double
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date_timestamp DESC, created_at DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date_timestamp DESC, created_at DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date_timestamp BETWEEN :startDate AND :endDate ORDER BY date_timestamp DESC, created_at DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date_timestamp BETWEEN :startDate AND :endDate ORDER BY date_timestamp DESC, created_at DESC")
    suspend fun getTransactionsByDateRangeSync(startDate: Long, endDate: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date_timestamp DESC, created_at DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type AND date_timestamp BETWEEN :startDate AND :endDate ORDER BY date_timestamp DESC, created_at DESC")
    fun getTransactionsByTypeAndDateRange(type: String, startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId OR destination_account_id = :accountId ORDER BY date_timestamp DESC, created_at DESC")
    fun getTransactionsByAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionByIdSync(id: String): TransactionEntity?

    @Query("SELECT COALESCE(SUM(usd_equivalent), 0.0) FROM transactions WHERE type = :type AND date_timestamp BETWEEN :startDate AND :endDate")
    fun getTotalUsdByTypeAndDateRange(type: String, startDate: Long, endDate: Long): Flow<Double>

    @Query("""
        SELECT category_id, SUM(usd_equivalent) AS total_usd 
        FROM transactions 
        WHERE type = :type AND date_timestamp BETWEEN :startDate AND :endDate 
        GROUP BY category_id 
        ORDER BY total_usd DESC
    """)
    fun getCategoryTotals(type: String, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions ORDER BY date_timestamp DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :accountId OR destination_account_id = :accountId")
    suspend fun countTransactionsByAccount(accountId: String): Int

    @Query("DELETE FROM transactions WHERE account_id = :accountId OR destination_account_id = :accountId")
    suspend fun deleteTransactionsByAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countTransactionsByCategory(categoryId: String): Int

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactionsSync()
}
