package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.PendingExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingExpenseDao {

    @Query("SELECT * FROM pending_expenses ORDER BY is_paid ASC, due_date_timestamp ASC")
    fun getAllPendingExpenses(): Flow<List<PendingExpenseEntity>>

    @Query("SELECT * FROM pending_expenses WHERE is_paid = 0 ORDER BY due_date_timestamp ASC")
    fun getActivePendingExpenses(): Flow<List<PendingExpenseEntity>>

    @Query("SELECT * FROM pending_expenses WHERE is_paid = 1 ORDER BY paid_at_timestamp DESC, due_date_timestamp DESC")
    fun getPaidPendingExpenses(): Flow<List<PendingExpenseEntity>>

    @Query("SELECT * FROM pending_expenses WHERE id = :id LIMIT 1")
    suspend fun getPendingExpenseById(id: String): PendingExpenseEntity?

    @Query("SELECT * FROM pending_expenses ORDER BY due_date_timestamp ASC")
    fun getAllPendingExpensesSync(): List<PendingExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<PendingExpenseEntity>)

    @Update
    suspend fun update(item: PendingExpenseEntity)

    @Delete
    suspend fun delete(item: PendingExpenseEntity)

    @Query("DELETE FROM pending_expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_expenses")
    fun deleteAllPendingExpensesSync()
}
