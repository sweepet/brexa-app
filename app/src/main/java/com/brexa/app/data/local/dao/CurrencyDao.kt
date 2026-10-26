package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY is_base DESC, sort_order ASC, name ASC")
    fun getAllCurrencies(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE is_active = 1 ORDER BY is_base DESC, sort_order ASC, name ASC")
    fun getActiveCurrencies(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE id = :id LIMIT 1")
    fun getCurrencyById(id: String): Flow<CurrencyEntity?>

    @Query("SELECT * FROM currencies WHERE id = :id LIMIT 1")
    suspend fun getCurrencyByIdSync(id: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE is_base = 1 LIMIT 1")
    fun getBaseCurrency(): Flow<CurrencyEntity?>

    @Query("SELECT COUNT(*) FROM currencies WHERE is_base = 0")
    suspend fun getCustomCurrenciesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Update
    suspend fun updateCurrency(currency: CurrencyEntity)

    @Query("UPDATE currencies SET rate_to_usd = :newRate, updated_at = :updatedAt WHERE id = :currencyId")
    suspend fun updateExchangeRate(currencyId: String, newRate: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE currencies SET code = :code, name = :name, symbol = :symbol, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateBaseCurrencyDetails(id: String, code: String, name: String, symbol: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCurrency(currency: CurrencyEntity)
}
