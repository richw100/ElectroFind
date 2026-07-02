package com.richwatson.electrofind.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargerDao {
    @Query("SELECT * FROM cached_chargers WHERE pk IN (:pks)")
    suspend fun getByPks(pks: List<Long>): List<CachedChargerEntity>

    @Query("SELECT * FROM cached_chargers WHERE pk IN (:pks)")
    fun observeByPks(pks: List<Long>): Flow<List<CachedChargerEntity>>

    @Upsert
    suspend fun upsert(entity: CachedChargerEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CachedChargerEntity>)
}
