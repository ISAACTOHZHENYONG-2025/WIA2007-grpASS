package my.edu.um.rainready.zone

object ZoneSamples {
    fun sampleStates(nowMillis: Long = 1_700_000_000_000L): List<ZoneState> = listOf(
        ZoneState(
            zoneId = "Z1",
            colour = ZoneColour.RED,
            reason = ZoneReason.STAFF_OVERRIDE,
            message = "Set by staff",
            missingInputs = emptyList(),
            computedAtMillis = nowMillis
        ),
        ZoneState(
            zoneId = "Z2",
            colour = ZoneColour.RED,
            reason = ZoneReason.CONFIRMED_REPORT,
            message = "Flood confirmed",
            reportCategory = "flood",
            missingInputs = emptyList(),
            computedAtMillis = nowMillis
        ),
        ZoneState(
            zoneId = "Z3",
            colour = ZoneColour.YELLOW,
            reason = ZoneReason.RAIN_ON_GROUND,
            message = "Heavy rain forecast on low ground, forecast only",
            forecastOnly = true,
            missingInputs = emptyList(),
            computedAtMillis = nowMillis
        ),
        ZoneState(
            zoneId = "Z4",
            colour = ZoneColour.GREEN,
            reason = ZoneReason.NO_PROBLEMS_REPORTED,
            message = "No problems reported",
            missingInputs = listOf("rain"),
            computedAtMillis = nowMillis
        )
    )
}

