package com.brexa.app.data.local.dao

import androidx.room.*
import com.brexa.app.data.local.entity.ExchangeRateHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateHistoryDao {
    @Query("SELECT * FROM exchange_rate_history WHERE currency_id = :currencyId ORDER BY recorded_at DESC")
    fun getHistoryByCurrency(currencyId: String): Flow<List<ExchangeRateHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRateRecord(record: ExchangeRateHistoryEntity)
}
