package com.richwatson.electrofind.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripLogDao {

    // ── Receipt sessions ────────────────────────────────────────────────────
    @Query("SELECT * FROM receipt_sessions WHERE startEpochMillis BETWEEN :from AND :to ORDER BY startEpochMillis DESC")
    fun sessionsInRange(from: Long, to: Long): Flow<List<ReceiptSessionEntity>>

    @Query("SELECT * FROM receipt_sessions ORDER BY startEpochMillis DESC")
    suspend fun allSessions(): List<ReceiptSessionEntity>

    @Query("SELECT sourceFileName FROM receipt_sessions")
    suspend fun importedFileNames(): List<String>

    // IGNORE: a receipt number already present (re-imported file, restored backup) is silently
    // skipped. Returned longs are row ids; -1 marks a row that conflicted and was not inserted.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<ReceiptSessionEntity>): List<Long>

    @Query("UPDATE receipt_sessions SET excluded = :excluded WHERE id = :id")
    suspend fun setSessionExcluded(id: Long, excluded: Boolean)

    @Query("DELETE FROM receipt_sessions WHERE receiptNumber IN (:receiptNumbers)")
    suspend fun deleteSessionsByReceiptNumber(receiptNumbers: List<String>)

    @Query("DELETE FROM receipt_sessions")
    suspend fun clearSessions()

    // ── Manual charges ──────────────────────────────────────────────────────
    @Query("SELECT * FROM custom_charges WHERE dateEpochMillis BETWEEN :from AND :to ORDER BY dateEpochMillis DESC")
    fun customChargesInRange(from: Long, to: Long): Flow<List<CustomChargeEntity>>

    @Query("SELECT * FROM custom_charges ORDER BY dateEpochMillis DESC")
    suspend fun allCustomCharges(): List<CustomChargeEntity>

    @Upsert
    suspend fun upsertCustomCharge(charge: CustomChargeEntity)

    @Query("UPDATE custom_charges SET excluded = :excluded WHERE id = :id")
    suspend fun setCustomChargeExcluded(id: String, excluded: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomChargesIgnore(charges: List<CustomChargeEntity>)

    @Query("DELETE FROM custom_charges WHERE id = :id")
    suspend fun deleteCustomCharge(id: String)

    @Query("DELETE FROM custom_charges")
    suspend fun clearCustomCharges()
}
