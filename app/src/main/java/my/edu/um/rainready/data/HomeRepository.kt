package my.edu.um.rainready.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.edu.um.rainready.model.HomeState
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.model.ZoneSummary

/** Where the Home screen gets its data. */
interface HomeRepository {
    val home: Flow<HomeState>
}

/**
 * Sample data that matches the Figma "01 Home · rain" screen, used until the real zone data
 * (Jingying's zone colour rules on Firestore) and the rain forecast (Open-Meteo) are connected.
 * Swap it for the real repository in RainReadyApp; the screens do not need to change.
 */
class SampleHomeRepository(rainy: Boolean = true) : HomeRepository {
    override val home: Flow<HomeState> = flowOf(if (rainy) rainyDay() else calmDay())

    companion object {
        fun rainyDay(now: Long = System.currentTimeMillis()) = HomeState(
            weatherLine = "Heavy rain now, until about 11 AM",
            zones = listOf(
                ZoneSummary("Z7", "Engineering", "Confirmed by 3 people · 12 min ago", ZoneStatus.FLOODED),
                ZoneSummary("Z6", "Main Library and DTC", "Heavy rain on low ground until 11 AM", ZoneStatus.CAUTION),
                ZoneSummary("Z1", "FSKTM", "No problems reported", ZoneStatus.CLEAR),
            ),
            updatedAtMillis = now,
        )

        fun calmDay(now: Long = System.currentTimeMillis()) = HomeState(
            weatherLine = "No rain expected in the next 3 hours",
            zones = listOf(
                ZoneSummary("Z7", "Engineering", "No problems reported", ZoneStatus.CLEAR),
                ZoneSummary("Z6", "Main Library and DTC", "No problems reported", ZoneStatus.CLEAR),
                ZoneSummary("Z1", "FSKTM", "No problems reported", ZoneStatus.CLEAR),
            ),
            updatedAtMillis = now,
        )
    }
}
