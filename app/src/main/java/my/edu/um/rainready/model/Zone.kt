package my.edu.um.rainready.model

/**
 * The three zone states from FR2.1. Always shown as colour + symbol + word.
 * Keep the order from least to most serious: worstStatus() relies on it.
 */
enum class ZoneStatus { CLEAR, CAUTION, FLOODED }

/**
 * Turns the colour the data side stores for a zone (green, yellow or red, in any case) into a
 * [ZoneStatus]. How each status is drawn (symbol, outline) is up to the UI, not the data.
 * Returns null for anything else, such as the empty staffOverride that means "no override".
 */
fun zoneStatusOf(colour: String?): ZoneStatus? = when (colour?.trim()?.lowercase()) {
    "green" -> ZoneStatus.CLEAR
    "yellow" -> ZoneStatus.CAUTION
    "red" -> ZoneStatus.FLOODED
    else -> null
}

/** One campus zone as the Home screen and the map show it. */
data class ZoneSummary(
    val id: String,
    val name: String,
    /** Why the zone has this status, e.g. "Confirmed by 3 people · 12 min ago". */
    val detail: String,
    val status: ZoneStatus,
)

/** Data older than this shows the grey "can't get new reports" banner (FR2.1). */
const val STALE_AFTER_MILLIS = 15 * 60 * 1000L

/** The worst status in a list of zones: Flooded beats Caution beats Clear. Clear when the list is empty. */
fun List<ZoneSummary>.worstStatus(): ZoneStatus = maxOfOrNull { it.status } ?: ZoneStatus.CLEAR

/** True when the data was last refreshed more than 15 minutes before [nowMillis]. */
fun HomeState.isStale(nowMillis: Long): Boolean = nowMillis - updatedAtMillis > STALE_AFTER_MILLIS

/**
 * How long until data refreshed at [updatedAtMillis] counts as stale, in milliseconds;
 * 0 once it already is. The screens' timer sleeps this long instead of checking every second.
 */
fun millisUntilStale(updatedAtMillis: Long, nowMillis: Long): Long =
    (updatedAtMillis + STALE_AFTER_MILLIS + 1 - nowMillis).coerceAtLeast(0L)

/** Everything the Home screen needs. */
data class HomeState(
    /** One line about the rain, e.g. "Heavy rain now, until about 11 AM". */
    val weatherLine: String,
    /** The zones the user follows, or every zone if they follow none (FR1.2). */
    val zones: List<ZoneSummary>,
    /** When the zone data was last refreshed, in epoch milliseconds. */
    val updatedAtMillis: Long,
)
