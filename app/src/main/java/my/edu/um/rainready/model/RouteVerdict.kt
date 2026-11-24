package my.edu.um.rainready.model

/** The four "Can I get to class?" answers from FR2.3. */
enum class Verdict {
    /** Usual path all green: "Go." */
    GO,
    /** Usual path has yellow: "Go, take care." */
    GO_TAKE_CARE,
    /** Usual path has red and another path avoids red: "Use safer route". */
    USE_SAFER_ROUTE,
    /** Every path has red: "No safe route reported". */
    NO_SAFE_ROUTE,
}

/**
 * The route-check answer for one trip. The verdict engine fills this in; the screens only show it,
 * so wording, colours and the map highlight stay out of the engine.
 */
data class RouteVerdict(
    val verdict: Verdict,
    val startId: String,
    val destinationId: String,
    /** The usual path between the two buildings. */
    val usualPath: CampusPath,
    /** The path to walk: the usual one, or the safer one for [Verdict.USE_SAFER_ROUTE]. Null for [Verdict.NO_SAFE_ROUTE]. */
    val recommendedPath: CampusPath?,
    /** Paths shown grey and dashed, such as the usual path when a safer one is recommended. */
    val otherPaths: List<CampusPath>,
    /** Extra walking minutes of the safer route compared with the usual path. */
    val extraMinutes: Int = 0,
    /** Heavy rain is forecast within the next hour on low ground along the path: add "Leave 10 minutes early." */
    val leaveEarly: Boolean = false,
    /** The zone that decided the verdict (the worst zone on the usual path), if any. */
    val causeZoneId: String? = null,
    /** What was reported there: "flood", "fallen tree" or "blocked drain". Null when it is rain, or nothing. */
    val causeCategory: String? = null,
    /** How many people confirmed that report. */
    val causeConfirmations: Int? = null,
    /** When the reports behind this verdict were last updated, in epoch milliseconds. */
    val updatedAtMillis: Long,
)
