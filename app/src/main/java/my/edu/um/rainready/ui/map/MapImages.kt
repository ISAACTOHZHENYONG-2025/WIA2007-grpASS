package my.edu.um.rainready.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import my.edu.um.rainready.model.ZoneStatus
import my.edu.um.rainready.ui.components.style
import my.edu.um.rainready.ui.theme.PinConfirmed
import my.edu.um.rainready.ui.theme.PinUnconfirmed
import my.edu.um.rainready.ui.theme.ZoneCaution
import my.edu.um.rainready.ui.theme.ZoneCautionOutline
import my.edu.um.rainready.ui.theme.ZoneClear
import my.edu.um.rainready.ui.theme.ZoneFlooded

/**
 * Bitmaps the map draws as icons: zone labels (name tag + status badge) and report pins.
 * They are drawn in device pixels to match the Figma parts, so MapLibre shows them at the right dp size.
 */
internal class MapImages(context: Context) {
    private val dm = context.resources.displayMetrics
    private val d = dm.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val labelInk = 0xFF4A4F55.toInt()   // Figma map/label
    private val tagFill = 0xE6FFFFFF.toInt()    // white at 90%

    /** Zone label as on the Figma map: the zone name in a white tag, the status badge under it. */
    fun zoneLabel(name: String, status: ZoneStatus, word: String): Bitmap {
        val style = status.style()
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(12f); typeface = medium; color = labelInk }
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(14f); typeface = medium; color = style.onContainer.toArgb() }
        val nameFm = namePaint.fontMetrics
        val wordFm = wordPaint.fontMetrics

        val tagW = namePaint.measureText(name) + 12 * d
        val tagH = (nameFm.descent - nameFm.ascent) + 4 * d
        val symbol = 16 * d
        val badgeW = 8 * d + symbol + 6 * d + wordPaint.measureText(word) + 12 * d
        val badgeH = maxOf(symbol, wordFm.descent - wordFm.ascent) + 12 * d
        val gap = 4 * d
        val w = maxOf(tagW, badgeW)
        val h = tagH + gap + badgeH

        val bmp = createBitmap(kotlin.math.ceil(w).toInt(), kotlin.math.ceil(h).toInt())
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Name tag, centred over the badge
        val tagX = (w - tagW) / 2
        fill.color = tagFill
        c.drawRoundRect(RectF(tagX, 0f, tagX + tagW, tagH), 6 * d, 6 * d, fill)
        c.drawText(name, tagX + 6 * d, 2 * d - nameFm.ascent, namePaint)

        // Status badge: symbol + word on the status container colour
        val bx = (w - badgeW) / 2
        val by = tagH + gap
        fill.color = style.container.toArgb()
        c.drawRoundRect(RectF(bx, by, bx + badgeW, by + badgeH), 8 * d, 8 * d, fill)
        drawSymbol(c, status, bx + 8 * d, by + (badgeH - symbol) / 2, symbol)
        val textH = wordFm.descent - wordFm.ascent
        c.drawText(word, bx + 8 * d + symbol + 6 * d, by + (badgeH - textH) / 2 - wordFm.ascent, wordPaint)
        return bmp
    }

    /** Teardrop pin from the Figma "Map pin" part: grey = unconfirmed, red = confirmed. The tip is the bottom centre. */
    fun pin(confirmed: Boolean): Bitmap {
        val m = 3 * d // room for the shadow on the sides and top
        val w = 28 * d + 2 * m
        val h = 36 * d + m
        val bmp = createBitmap(kotlin.math.ceil(w).toInt(), kotlin.math.ceil(h).toInt())
        val c = Canvas(bmp)
        c.translate(m, m)
        val k = d
        val body = Path().apply {
            moveTo(14 * k, 0f)
            cubicTo(6.27f * k, 0f, 0f, 6.27f * k, 0f, 14 * k)
            cubicTo(0f, 24.5f * k, 14 * k, 36 * k, 14 * k, 36 * k)
            cubicTo(14 * k, 36 * k, 28 * k, 24.5f * k, 28 * k, 14 * k)
            cubicTo(28 * k, 6.27f * k, 21.73f * k, 0f, 14 * k, 0f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (if (confirmed) PinConfirmed else PinUnconfirmed).toArgb()
            setShadowLayer(3 * d, 0f, 1 * d, 0x4D000000)
        }
        c.drawPath(body, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = 0xFFFFFFFF.toInt() }
        c.save()
        c.clipPath(body)
        c.drawPath(body, ring) // clipped, so the 2 dp white edge sits inside the shape as in Figma
        c.restore()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawCircle(14 * k, 14 * k, 5 * k, dot)
        return bmp
    }

    /** Same shapes as the Compose ZoneSymbol, drawn in a size x size box. */
    private fun drawSymbol(c: Canvas, status: ZoneStatus, left: Float, top: Float, size: Float) {
        val k = size / 20f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.translate(left, top)
        when (status) {
            ZoneStatus.CLEAR -> { p.color = ZoneClear.toArgb(); c.drawCircle(10 * k, 10 * k, 9 * k, p) }
            ZoneStatus.CAUTION -> {
                val tri = Path().apply { moveTo(10 * k, 2 * k); lineTo(19 * k, 18 * k); lineTo(1 * k, 18 * k); close() }
                p.color = ZoneCaution.toArgb(); c.drawPath(tri, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * k; p.strokeJoin = Paint.Join.ROUND
                p.color = ZoneCautionOutline.toArgb(); c.drawPath(tri, p)
            }
            ZoneStatus.FLOODED -> {
                val pts = floatArrayOf(6.27f, 1f, 13.73f, 1f, 19f, 6.27f, 19f, 13.73f, 13.73f, 19f, 6.27f, 19f, 1f, 13.73f, 1f, 6.27f)
                val oct = Path().apply {
                    moveTo(pts[0] * k, pts[1] * k)
                    for (i in 2 until pts.size step 2) lineTo(pts[i] * k, pts[i + 1] * k)
                    close()
                }
                p.color = ZoneFlooded.toArgb(); c.drawPath(oct, p)
            }
        }
        c.restore()
    }
}
