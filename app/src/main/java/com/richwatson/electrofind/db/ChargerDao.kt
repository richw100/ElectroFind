package com.richwatson.electrofind.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChargerDao {
    @Query("SELECT * FROM cached_chargers WHERE pk IN (:pks)")
    suspend fun getByPks(pks: List<Long>): List<CachedChargerEntity>

    @Upsert
    suspend fun upsert(entity: CachedChargerEntity)
}
