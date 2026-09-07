package ru.maxstrix.workbalance.domain

import java.time.LocalDate

enum class CalendarDayType { DAY_OFF, WORKDAY, SHORTENED }

enum class CalendarRegion(val code: String, val title: String) {
    NONE("NONE", "Не выбран"),
    SARATOV("RU-SAR", "Саратовская область");

    companion object {
        fun fromCode(code: String?): CalendarRegion = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class ShortenedDayMode(val title: String) {
    ASK("Спрашивать"),
    AUTOMATIC("Учитывать автоматически"),
    DISABLED("Не учитывать")
}

data class ProductionCalendarSettings(
    val federalEnabled: Boolean = true,
    val regionalEnabled: Boolean = false,
    val region: CalendarRegion = CalendarRegion.SARATOV,
    val shortenedDayMode: ShortenedDayMode = ShortenedDayMode.ASK
)

data class CalendarRule(
    val date: LocalDate,
    val type: CalendarDayType,
    val name: String,
    val reductionMinutes: Int = 0
)

data class CalendarPack(
    val id: String,
    val year: Int,
    val regionCode: String?,
    val title: String,
    val revision: String,
    val official: Boolean,
    val source: String,
    val rules: List<CalendarRule>
)

data class CalendarDayInfo(
    val date: LocalDate,
    val dayType: CalendarDayType?,
    val shortenedByMinutes: Int,
    val names: List<String>
) {
    val description: String get() = names.distinct().joinToString(" · ")
}

data class ProductionCalendar(
    val settings: ProductionCalendarSettings = ProductionCalendarSettings(),
    val packs: List<CalendarPack> = emptyList()
) {
    fun dayInfo(date: LocalDate): CalendarDayInfo? {
        val rules = packs.asSequence()
            .filter { it.year == date.year }
            .flatMap { it.rules.asSequence() }
            .filter { it.date == date }
            .toList()
        if (rules.isEmpty()) return null

        val dayType = rules.lastOrNull { it.type != CalendarDayType.SHORTENED }?.type
        val shortenedBy = rules
            .filter { it.type == CalendarDayType.SHORTENED }
            .maxOfOrNull { it.reductionMinutes }
            ?: 0
        return CalendarDayInfo(
            date = date,
            dayType = dayType,
            shortenedByMinutes = shortenedBy,
            names = rules.map { it.name }.filter { it.isNotBlank() }
        )
    }

    fun hasYear(year: Int): Boolean = packs.any {
        it.year == year && it.regionCode == null && it.official
    }

    fun activeDescription(): String {
        if (packs.isEmpty()) return "Производственный календарь отключён"
        return packs.joinToString(" + ") { it.title }
    }
}
