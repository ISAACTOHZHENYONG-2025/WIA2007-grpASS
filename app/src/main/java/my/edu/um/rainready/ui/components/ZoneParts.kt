package my.edu.um.rainready.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import my.edu.um.rainready.R
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.ui.theme.OnZoneCautionContainer
import my.edu.um.rainready.ui.theme.OnZoneClearContainer
import my.edu.um.rainready.ui.theme.OnZoneFloodedContainer
import my.edu.um.rainready.ui.theme.RainReadyTheme
import my.edu.um.rainready.ui.theme.ZoneCaution
import my.edu.um.rainready.ui.theme.ZoneCautionContainer
import my.edu.um.rainready.ui.theme.ZoneCautionOutline
import my.edu.um.rainready.ui.theme.ZoneClear
import my.edu.um.rainready.ui.theme.ZoneClearContainer
import my.edu.um.rainready.ui.theme.ZoneFlooded
import my.edu.um.rainready.ui.theme.ZoneFloodedContainer

/** Container and text colours for a zone status, from the Figma "zone/..." variables. */
data class ZoneStyle(val container: Color, val onContainer: Color, @param:StringRes val word: Int)

fun ZoneStatus.style(): ZoneStyle = when (this) {
    ZoneStatus.CLEAR -> ZoneStyle(ZoneClearContainer, OnZoneClearContainer, R.string.zone_clear)
    ZoneStatus.CAUTION -> ZoneStyle(ZoneCautionContainer, OnZoneCautionContainer, R.string.zone_caution)
    ZoneStatus.FLOODED -> ZoneStyle(ZoneFloodedContainer, OnZoneFloodedContainer, R.string.zone_flooded)
}

/**
 * Colour-blind-safe zone symbol: circle = Clear, triangle = Caution, octagon = Flooded.
 * Decorative only; always show the word next to it.
 */
@Composable
fun ZoneSymbol(status: ZoneStatus, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        // Shapes are drawn in a 20 x 20 box, as in Figma, then scaled to the requested size.
        val k = this.size.minDimension / 20f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        when (status) {
            ZoneStatus.CLEAR -> drawCircle(ZoneClear, radius = 9f * k, center = p(10f, 10f))
            ZoneStatus.CAUTION -> {
                val tri = Path().apply {
                    moveTo(10f * k, 2f * k); lineTo(19f * k, 18f * k); lineTo(1f * k, 18f * k); close()
                }
                drawPath(tri, ZoneCaution)
                drawPath(tri, ZoneCautionOutline, style = Stroke(width = 1.5f * k, join = StrokeJoin.Round))
            }
            ZoneStatus.FLOODED -> {
                val pts = listOf(6.27f to 1f, 13.73f to 1f, 19f to 6.27f, 19f to 13.73f, 13.73f to 19f, 6.27f to 19f, 1f to 13.73f, 1f to 6.27f)
                val oct = Path().apply {
                    pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * k, y * k) else lineTo(x * k, y * k) }
                    close()
                }
                drawPath(oct, ZoneFlooded)
            }
        }
    }
}

/** Status pill: symbol + word on the status container colour. */
@Composable
fun ZoneBadge(status: ZoneStatus, modifier: Modifier = Modifier) {
    val style = status.style()
    Row(
        modifier = modifier
            .background(style.container, RoundedCornerShape(8.dp))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ZoneSymbol(status, size = 16.dp)
        Text(stringResource(style.word), style = MaterialTheme.typography.labelLarge, color = style.onContainer)
    }
}

/** One zone on the Home screen: name, why, and the status badge. */
@Composable
fun ZoneCard(name: String, detail: String, status: ZoneStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ZoneBadge(status)
    }
}

@Preview(showBackground = true)
@Composable
private fun ZonePartsPreview() {
    RainReadyTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ZoneStatus.entries.forEach { ZoneSymbol(it, size = 24.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneStatus.entries.forEach { ZoneBadge(it) }
            }
            ZoneCard("Engineering", "Confirmed by 3 people · 12 min ago", ZoneStatus.FLOODED)
            ZoneCard("Main Library and DTC", "Heavy rain on low ground until 11 AM", ZoneStatus.CAUTION)
            ZoneCard("FSKTM and Science", "No problems reported", ZoneStatus.CLEAR)
        }
    }
}
