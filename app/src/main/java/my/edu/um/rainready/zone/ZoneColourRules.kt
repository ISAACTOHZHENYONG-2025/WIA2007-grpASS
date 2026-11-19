package my.edu.um.rainready.zone

object ZoneColourRules {
    private const val STALE_MS = 6 * 60 * 60 * 1000L

    fun compute(input: ZoneInput, nowMillis: Long): ZoneState {
        val missing = mutableListOf<String>()
        if (input.rainMmPerHour == null) missing += "rain"
        if (input.groundLevel == null) missing += "groundLevel"

        // Precedence 1: Active staff override takes top priority
        val override = parseColour(input.staffOverride)
        val until = input.staffOverrideUntilMillis
        if (override != null && until != null && nowMillis < until) {
            return ZoneState(
                zoneId = input.zoneId,
                colour = override,
                reason = ZoneReason.STAFF_OVERRIDE,
                message = "Set by staff",
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // Precedence 2: Fresh, unfixed, confirmed reports make the zone red
        val active = input.reports
            .filter {
                it.status == "confirmed" &&
                        it.fixedAtMillis == null &&
                        it.createdAtMillis != null &&
                        nowMillis - it.createdAtMillis < STALE_MS
            }
            .maxByOrNull { it.createdAtMillis!! }
        if (active != null) {
            return ZoneState(
                zoneId = input.zoneId,
                colour = ZoneColour.RED,
                reason = ZoneReason.CONFIRMED_REPORT,
                message = "${active.category.replaceFirstChar { it.uppercase() }} confirmed",
                reportCategory = active.category,
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // Precedence 3: Rain threshold by ground level (yellow at most)
        if (isRainYellow(input.groundLevel, input.rainMmPerHour)) {
            val isLow = input.groundLevel?.lowercase() == "low"
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
                missingInputs = missing,
                computedAtMillis = nowMillis
            )
        }

        // Precedence 4: Default green state (never "All clear")
        return ZoneState(
            zoneId = input.zoneId,
            colour = ZoneColour.GREEN,
            reason = ZoneReason.NO_PROBLEMS_REPORTED,
            message = "No problems reported",
            missingInputs = missing,
            computedAtMillis = nowMillis
        )
    }

    // Assumption per Plan 6.2: remove "medium" branch if team decides yellow applies to low ground only
    internal fun isRainYellow(ground: String?, mm: Double?): Boolean {
        return !(ground == null || mm == null) && when (ground.lowercase()) {
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