package com.richwatson.electrofind.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface OcmDao {
    @Query("SELECT * FROM cached_ocm_chargers")
    suspend fun getAll(): List<CachedOcmEntity>

    @Upsert
    suspend fun upsert(entity: CachedOcmEntity)
}
