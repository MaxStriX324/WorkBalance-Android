package ru.maxstrix.workbalance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    @Query("SELECT * FROM work_events ORDER BY localDateTime")
    fun observeEvents(): Flow<List<WorkEventEntity>>

    @Insert
    suspend fun insertEvent(event: WorkEventEntity): Long

    @Insert
    suspend fun insertEvents(events: List<WorkEventEntity>)

    @Update
    suspend fun updateEvent(event: WorkEventEntity)

    @Delete
    suspend fun deleteEvent(event: WorkEventEntity)

    @Query("SELECT * FROM day_overrides")
    fun observeOverrides(): Flow<List<DayOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: DayOverrideEntity)

    @Query("DELETE FROM day_overrides WHERE epochDay = :epochDay")
    suspend fun deleteOverride(epochDay: Long)

    @Query("SELECT * FROM settings")
    fun observeSettings(): Flow<List<SettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(settings: List<SettingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOverrides(overrides: List<DayOverrideEntity>)

    @Query("DELETE FROM work_events")
    suspend fun clearEvents()

    @Query("DELETE FROM day_overrides")
    suspend fun clearOverrides()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Transaction
    suspend fun replaceAll(
        events: List<WorkEventEntity>,
        overrides: List<DayOverrideEntity>,
        settings: List<SettingEntity>
    ) {
        clearEvents()
        clearOverrides()
        clearSettings()
        if (events.isNotEmpty()) insertEvents(events)
        if (overrides.isNotEmpty()) putOverrides(overrides)
        if (settings.isNotEmpty()) putSettings(settings)
    }
}
