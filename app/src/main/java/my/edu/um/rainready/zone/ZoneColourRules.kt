package my.edu.um.rainready.zone

object ZoneColourRules {
    private const val STALE_REPORT_MS = 6 * 60 * 60 * 1000L
    private const val STALE_WEATHER_MS = 15 * 60 * 1000L
    private const val MAX_OVERRIDE_MS = 12 * 60 * 60 * 1000L

    fun compute(
        input: ZoneInput,
        nowMillis: Long,
        previousState: ZoneState? = null
    ): ZoneState {
        val missing = mutableListOf<String>()
        if (input.rainMmPerHour == null) missing += "rain"
        if (input.groundLevel == null) missing += "groundLevel"
        if (input.weatherFetchedAtMillis == null) missing += "weatherFetchedAt"

        val isWeatherFresh = input.rainMmPerHour != null &&
                input.weatherFetchedAtMillis != null &&
                (nowMillis - input.weatherFetchedAtMillis) <= STALE_WEATHER_MS
        val staleWeatherBanner = !isWeatherFresh

        // 1. Active staff override (cleared when blank/null/expired). DATA_LAYOUT caps an override at
        //    12h, so an end time more than 12h away is treated as a bad value and ignored.
        val override = parseColour(input.staffOverride)
        val until = input.staffOverrideUntilMillis
        if (override != null && until != null && nowMillis < until && until - nowMillis <= MAX_OVERRIDE_MS) {
            return ZoneState(
                zoneId = input.zoneId,
                colour = override,
                reason = ZoneReason.STAFF_OVERRIDE,
                message = "Set by staff",
                staleWeatherBanner = staleWeatherBanner,
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // 2. Confirmed, unfixed report within 6h of createdAt; unconfirmed never triggers RED
        val activeReport = input.reports
            .filter {
                it.status.equals("confirmed", ignoreCase = true) &&
                        it.fixedAtMillis == null &&
                        it.createdAtMillis != null &&
                        (nowMillis - it.createdAtMillis) < STALE_REPORT_MS
            }
            .maxByOrNull { it.createdAtMillis!! }

        if (activeReport != null) {
            return ZoneState(
                zoneId = input.zoneId,
                colour = ZoneColour.RED,
                reason = ZoneReason.CONFIRMED_REPORT,
                message = "${activeReport.category.replaceFirstChar { it.uppercase() }} confirmed",
                reportCategory = activeReport.category,
                staleWeatherBanner = staleWeatherBanner,
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // 3. Fresh weather rain check (yellow at most; never issue fresh rain warning from stale/missing weather)
        if (isWeatherFresh && isRainYellow(input.groundLevel, input.rainMmPerHour)) {
            val isLow = input.groundLevel?.equals("low", ignoreCase = true) == true
            return ZoneState(
                zoneId = input.zoneId,
                colour = ZoneColour.YELLOW,
                reason = ZoneReason.RAIN_ON_GROUND,
                message = if (isLow) {
                    "Heavy rain forecast on low ground, forecast only"
                } else {
                    "Heavy rain forecast on medium ground, forecast only"
                },
                forecastOnly = true,
                staleWeatherBanner = false,
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // 4. Stale or missing weather without override/report: keep a previous rain YELLOW instead of
        //    forcing GREEN. Only a rain result is kept: overrides and reports were checked again above,
        //    so a previous RED from an override that has ended or a report that was fixed must not stay.
        //    The message is kept as it was; staleWeatherBanner tells the screen the weather is old
        //    (adding text here would add it again on every recompute).
        if (!isWeatherFresh && previousState != null &&
            previousState.zoneId == input.zoneId &&
            previousState.reason == ZoneReason.RAIN_ON_GROUND
        ) {
            return previousState.copy(
                forecastOnly = false,
                staleWeatherBanner = true,
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // 5. Default green when data is fresh and no active override/report/rain warning applies
        return ZoneState(
            zoneId = input.zoneId,
            colour = ZoneColour.GREEN,
            reason = ZoneReason.NO_PROBLEMS_REPORTED,
            message = if (staleWeatherBanner) {
                "Weather data stale or unavailable; no active incidents reported"
            } else {
                "No problems reported"
            },
            staleWeatherBanner = staleWeatherBanner,
            missingInputs = missing,
            computedAtMillis = nowMillis
        )
    }

    // Assumption per Plan 6.2: remove "medium" branch if team decides yellow applies to low ground only
    internal fun isRainYellow(ground: String?, mm: Double?): Boolean {
        if (ground == null || mm == null) return false
        return when (ground.trim().lowercase()) {
            "low" -> mm >= 5.0
            "medium" -> mm > 15.0
            else -> false
        }
    }

    private fun parseColour(s: String?): ZoneColour? = when (s?.trim()?.lowercase()) {
        "green" -> ZoneColour.GREEN
        "yellow" -> ZoneColour.YELLOW
        "red" -> ZoneColour.RED
        else -> null
    }
}