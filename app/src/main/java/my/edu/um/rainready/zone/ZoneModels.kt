package my.edu.um.rainready.zone

enum class ZoneColour {
    GREEN,
    YELLOW,
    RED
}

enum class ZoneReason {
    STAFF_OVERRIDE,
    CONFIRMED_REPORT,
    RAIN_ON_GROUND,
    NO_PROBLEMS_REPORTED
}

data class ReportInput(
    val status: String,
    val category: String,
    val createdAtMillis: Long?,
    val fixedAtMillis: Long?
)

data class ZoneInput(
    val zoneId: String,
    val groundLevel: String?,
    val staffOverride: String?,
    val staffOverrideUntilMillis: Long?,
    val reports: List<ReportInput>,
    val rainMmPerHour: Double?,
    val weatherFetchedAtMillis: Long?=null
)

data class ZoneState(
    val zoneId: String,
    val colour: ZoneColour,
    val reason: ZoneReason,
    val message: String,
    val reportCategory: String? = null,
    val forecastOnly: Boolean = false,
    val staleWeatherBanner: Boolean = false,
    val missingInputs: List<String> = emptyList(),
    val computedAtMillis: Long
)