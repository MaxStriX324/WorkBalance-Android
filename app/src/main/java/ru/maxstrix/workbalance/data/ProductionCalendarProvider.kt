package ru.maxstrix.workbalance.data

import android.content.Context
import org.json.JSONObject
import ru.maxstrix.workbalance.domain.CalendarDayType
import ru.maxstrix.workbalance.domain.CalendarPack
import ru.maxstrix.workbalance.domain.CalendarRegion
import ru.maxstrix.workbalance.domain.CalendarRule
import ru.maxstrix.workbalance.domain.ProductionCalendar
import ru.maxstrix.workbalance.domain.ProductionCalendarSettings
import java.time.LocalDate

class ProductionCalendarProvider(context: Context) {
    private val appContext = context.applicationContext
    private val bundledPacks: List<CalendarPack> by lazy {
        listOf(
            readPack("calendars/ru-2026.json"),
            readPack("calendars/ru-saratov-2026.json")
        )
    }

    fun calendar(settings: ProductionCalendarSettings): ProductionCalendar {
        val active = bundledPacks.filter { pack ->
            when {
                pack.regionCode == null -> settings.federalEnabled
                !settings.regionalEnabled -> false
                else -> pack.regionCode == settings.region.code
            }
        }
        return ProductionCalendar(settings, active)
    }

    fun availableYears(): Set<Int> = bundledPacks
        .filter { it.regionCode == null && it.official }
        .mapTo(sortedSetOf()) { it.year }

    fun availableRegions(): List<CalendarRegion> = listOf(CalendarRegion.SARATOV)

    private fun readPack(assetPath: String): CalendarPack {
        val json = appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parsePack(json)
    }

    internal fun parsePack(json: String): CalendarPack {
        val root = JSONObject(json)
        val rulesJson = root.getJSONArray("rules")
        val rules = buildList {
            for (index in 0 until rulesJson.length()) {
                val item = rulesJson.getJSONObject(index)
                add(
                    CalendarRule(
                        date = LocalDate.parse(item.getString("date")),
                        type = CalendarDayType.valueOf(item.getString("type")),
                        name = item.optString("name"),
                        reductionMinutes = item.optInt("reductionMinutes", 0)
                    )
                )
            }
        }
        return CalendarPack(
            id = root.getString("id"),
            year = root.getInt("year"),
            regionCode = root.optString("regionCode").takeIf { it.isNotBlank() },
            title = root.getString("title"),
            revision = root.getString("revision"),
            official = root.optString("status") == "OFFICIAL",
            source = root.optString("source"),
            rules = rules
        )
    }
}
