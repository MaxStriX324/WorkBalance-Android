package ru.maxstrix.workbalance.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.maxstrix.workbalance.domain.DayKind
import ru.maxstrix.workbalance.domain.EventType
import ru.maxstrix.workbalance.domain.WorkEvent
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "work_events")
data class WorkEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localDateTime: String,
    val type: String
) {
    fun toDomain() = WorkEvent(
        id = id,
        at = LocalDateTime.parse(localDateTime),
        type = EventType.valueOf(type)
    )
}

@Entity(tableName = "day_overrides")
data class DayOverrideEntity(
    @PrimaryKey val epochDay: Long,
    val kind: String,
    val customWorkMinutes: Int? = null
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val dayKind: DayKind get() = DayKind.valueOf(kind)
}

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
