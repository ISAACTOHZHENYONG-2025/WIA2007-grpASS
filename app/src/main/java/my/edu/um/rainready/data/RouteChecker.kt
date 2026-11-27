package my.edu.um.rainready.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.edu.um.rainready.model.CampusPath
import my.edu.um.rainready.model.MapState
import my.edu.um.rainready.model.RouteVerdict
import my.edu.um.rainready.model.Verdict
import my.edu.um.rainready.model.ZoneStatus

/**
 * Where the route-check screens get their answer (FR2.3). Jingying's verdict engine implements this
 * with the live zone colours; the screens do not need to change.
 */
interface RouteChecker {
    val campus: CampusData

    /**
     * The verdict for walking from [startId] to [destinationId], or null when no checked path joins
     * them. Emits again when the reports change, so the screen updates by itself (NFR1).
     */
    fun check(startId: String, destinationId: String): Flow<RouteVerdict?>
}

/**
 * A stand-in so the screens work before the real engine is ready. It applies the FR2.3 rules to the
 * same zone colours the map shows, so a new verdict follows every map update. Treats caution on
 * low ground as "heavy rain within the hour". Replace it with Jingying's engine.
 */
class SampleRouteChecker(
    override val campus: CampusData,
    private val map: Flow<MapState>,
) : RouteChecker {

    override fun check(startId: String, destinationId: String): Flow<RouteVerdict?> =
        map.map { verdictFor(it, startId, destinationId) }

    fun verdictFor(map: MapState, startId: String, destinationId: String): RouteVerdict? {
        val usual = campus.usualPath(startId, destinationId) ?: return null
        val others = campus.pathsBetween(startId, destinationId) - usual
        fun status(zoneId: String) = map.zoneStatus[zoneId] ?: ZoneStatus.CLEAR
        fun worst(path: CampusPath) = path.zoneIds.maxOfOrNull { status(it) } ?: ZoneStatus.CLEAR
        val cause = usual.zoneIds.filter { status(it) != ZoneStatus.CLEAR }.maxByOrNull { status(it) }

        fun verdict(v: Verdict, recommended: CampusPath?, grey: List<CampusPath>, extra: Int = 0, early: Boolean = false) =
            RouteVerdict(
                verdict = v,
                startId = startId,
                destinationId = destinationId,
                usualPath = usual,
                recommendedPath = recommended,
                otherPaths = grey,
                extraMinutes = extra,
                leaveEarly = early,
                causeZoneId = cause,
                causeCategory = cause?.let { map.causes[it]?.category },
                causeConfirmations = cause?.let { map.causes[it]?.confirmations },
                updatedAtMillis = map.updatedAtMillis,
            )

        return when (worst(usual)) {
            ZoneStatus.CLEAR -> verdict(Verdict.GO, usual, emptyList())
            ZoneStatus.CAUTION -> verdict(
                Verdict.GO_TAKE_CARE, usual, emptyList(),
                early = usual.zoneIds.any { status(it) == ZoneStatus.CAUTION && campus.groundLevel[it] == "low" },
            )
            ZoneStatus.FLOODED -> {
                val safer = others.filter { worst(it) != ZoneStatus.FLOODED }
                    .minWithOrNull(compareBy<CampusPath>({ worst(it) }, { it.walkingMinutes }))
                if (safer != null) {
                    verdict(Verdict.USE_SAFER_ROUTE, safer, listOf(usual), extra = safer.walkingMinutes - usual.walkingMinutes)
                } else {
                    verdict(Verdict.NO_SAFE_ROUTE, null, listOf(usual) + others)
                }
            }
        }
    }
}
