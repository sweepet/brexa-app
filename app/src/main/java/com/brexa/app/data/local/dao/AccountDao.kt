package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY is_default DESC, sort_order ASC, name ASC")
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY is_default DESC, is_archived ASC, sort_order ASC, name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 AND include_in_total = 1")
    fun getIncludedInTotalAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 AND include_in_total = 1")
    suspend fun getIncludedInTotalAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun getAccountById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountByIdSync(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_default = 1 LIMIT 1")
    fun getDefaultAccount(): Flow<AccountEntity?>

    @Query("SELECT COUNT(*) FROM accounts WHERE is_default = 0 AND is_archived = 0")
    suspend fun getCustomAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET current_balance = :newBalance, updated_at = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: String, newBalance: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET include_in_total = :include WHERE id = :accountId")
    suspend fun setIncludeInTotal(accountId: String, include: Boolean)

    @Query("UPDATE accounts SET is_archived = :isArchived WHERE id = :accountId")
    suspend fun setArchived(accountId: String, isArchived: Boolean)

    @Query("SELECT COUNT(*) FROM accounts WHERE currency_id = :currencyId")
    suspend fun countAccountsByCurrency(currencyId: String): Int

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: String)
}
