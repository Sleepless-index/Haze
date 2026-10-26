package sleepless_index.haze.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Icons drawn from the approved mockup (24x24 viewport, rounded strokes).

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0 z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M ${x + r} $y h ${w - 2 * r} a $r $r 0 0 1 $r $r v ${h - 2 * r} " +
        "a $r $r 0 0 1 ${-r} $r h ${-(w - 2 * r)} a $r $r 0 0 1 ${-r} ${-r} " +
        "v ${-(h - 2 * r)} a $r $r 0 0 1 $r ${-r} z"


private fun f2(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

/** A rounded rectangle centred at (cx, cy), rotated by [deg] degrees. */
private fun tiltedRRect(cx: Double, cy: Double, w: Double, h: Double, r: Double, deg: Double): String {
    val t = Math.toRadians(deg)
    val ct = Math.cos(t)
    val st = Math.sin(t)
    fun p(x: Double, y: Double) = f2(cx + x * ct - y * st) + " " + f2(cy + x * st + y * ct)
    val a = w / 2
    val b = h / 2
    val rr = f2(r)
    return "M ${p(-a + r, -b)} L ${p(a - r, -b)} A $rr $rr 0 0 1 ${p(a, -b + r)} " +
        "L ${p(a, b - r)} A $rr $rr 0 0 1 ${p(a - r, b)} " +
        "L ${p(-a + r, b)} A $rr $rr 0 0 1 ${p(-a, b - r)} " +
        "L ${p(-a, -b + r)} A $rr $rr 0 0 1 ${p(-a + r, -b)} z"
}

/** Clockwise arc around (12, 12) from [a0] to [a1] degrees, ending in an open arrow head. */
private fun arcArrow(r: Double, a0: Double, a1: Double): String {
    fun pt(a: Double) = Pair(12 + r * Math.cos(Math.toRadians(a)), 12 + r * Math.sin(Math.toRadians(a)))
    val s = pt(a0)
    val e = pt(a1)
    val t = Math.toRadians(a1)
    val tx = -Math.sin(t)
    val ty = Math.cos(t)
    val len = 2.4
    fun barb(sign: Double): String {
        val ang = Math.toRadians(145.0) * sign
        val bx = tx * Math.cos(ang) - ty * Math.sin(ang)
        val by = tx * Math.sin(ang) + ty * Math.cos(ang)
        return f2(e.first + bx * len) + " " + f2(e.second + by * len)
    }
    return "M ${f2(s.first)} ${f2(s.second)} A ${f2(r)} ${f2(r)} 0 0 1 ${f2(e.first)} ${f2(e.second)} " +
        "M ${barb(1.0)} L ${f2(e.first)} ${f2(e.second)} L ${barb(-1.0)}"
}

private class IconBuilder(name: String) {
    private val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    fun s(d: String): IconBuilder {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        return this
    }

    fun f(d: String): IconBuilder {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = SolidColor(Color.Black),
        )
        return this
    }

    fun build(): ImageVector = b.build()
}

private const val HEART =
    "M 12 20.5 s -7.5 -4.6 -7.5 -10.4 A 4.2 4.2 0 0 1 12 7.6 a 4.2 4.2 0 0 1 7.5 2.5 c 0 5.8 -7.5 10.4 -7.5 10.4 z"

object GIcons {
    val Photos: ImageVector by lazy {
        IconBuilder("Photos").s(rrect(3f, 4f, 18f, 16f, 3.5f)).s(circle(9f, 10f, 1.6f))
            .s("M 21 16 l -5 -5 l -8 8").build()
    }
    val Albums: ImageVector by lazy {
        IconBuilder("Albums").s("M 6 4 h 12").s(rrect(3f, 7f, 18f, 13f, 3.5f)).build()
    }
    val Search: ImageVector by lazy {
        IconBuilder("Search").s(circle(11f, 11f, 6.5f)).s("M 20 20 l -4 -4").build()
    }
    val Settings: ImageVector by lazy {
        IconBuilder("Settings").s("M 4 7 h 10 M 18 7 h 2 M 4 17 h 2 M 10 17 h 10")
            .s(circle(16f, 7f, 2f)).s(circle(8f, 17f, 2f)).build()
    }
    val Heart: ImageVector by lazy { IconBuilder("Heart").s(HEART).build() }
    val HeartFilled: ImageVector by lazy { IconBuilder("HeartFilled").f(HEART).build() }
    val Share: ImageVector by lazy {
        IconBuilder("Share").s("M 12 15 V 4 M 8 8 l 4 -4 l 4 4")
            .s("M 5 12 v 6 a 2 2 0 0 0 2 2 h 10 a 2 2 0 0 0 2 -2 v -6").build()
    }
    val Trash: ImageVector by lazy {
        IconBuilder("Trash").s("M 5 7 h 14 M 10 7 V 4 h 4 v 3 M 7 7 l 1 13 h 8 l 1 -13").build()
    }
    val Back: ImageVector by lazy { IconBuilder("Back").s("M 15 5 l -7 7 l 7 7").build() }
    val More: ImageVector by lazy {
        IconBuilder("More").f(circle(12f, 5.5f, 1.7f)).f(circle(12f, 12f, 1.7f)).f(circle(12f, 18.5f, 1.7f)).build()
    }
    val CheckCircle: ImageVector by lazy {
        IconBuilder("CheckCircle").s(circle(12f, 12f, 8.5f)).s("M 8.5 12.3 l 2.4 2.4 l 4.6 -5").build()
    }
    val CheckSquare: ImageVector by lazy {
        IconBuilder("CheckSquare").s(rrect(4f, 4f, 16f, 16f, 4.5f)).s("M 8.5 12.3 l 2.4 2.4 l 4.6 -5").build()
    }
    val Check: ImageVector by lazy { IconBuilder("Check").s("M 5 12.5 l 4.5 4.5 L 19 7.5").build() }
    val Close: ImageVector by lazy { IconBuilder("Close").s("M 6 6 l 12 12 M 18 6 L 6 18").build() }
    val Scissors: ImageVector by lazy {
        IconBuilder("Scissors")
            .s("M 3 6 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 M 3 18 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 M 8.6 7.6 L 20 19 M 8.6 16.4 L 20 5")
            .build()
    }
    val Undo: ImageVector by lazy { IconBuilder("Undo").s("M 9 14 L 4 9 l 5 -5 M 4 9 h 10.5 a 5.5 5.5 0 0 1 0 11 H 11").build() }
    val Redo: ImageVector by lazy { IconBuilder("Redo").s("M 15 14 l 5 -5 l -5 -5 M 20 9 H 9.5 a 5.5 5.5 0 0 0 0 11 H 13").build() }
    val Plus: ImageVector by lazy { IconBuilder("Plus").s("M 12 5 v 14 M 5 12 h 14").build() }
    val Minus: ImageVector by lazy { IconBuilder("Minus").s("M 5 12 h 14").build() }
    val Play: ImageVector by lazy { IconBuilder("Play").f("M 7 4 v 16 l 13 -8 z").build() }
    val Pause: ImageVector by lazy {
        IconBuilder("Pause").f(rrect(6f, 5f, 4.2f, 14f, 1.2f)).f(rrect(13.8f, 5f, 4.2f, 14f, 1.2f)).build()
    }
    val Lock: ImageVector by lazy {
        IconBuilder("Lock").s(rrect(5f, 11f, 14f, 9f, 2.5f)).s("M 8 11 V 8 a 4 4 0 0 1 8 0 v 3").build()
    }
    val Edit: ImageVector by lazy {
        IconBuilder("Edit").s("M 4 20 l 1 -4.2 L 16.2 4.6 a 2.2 2.2 0 0 1 3.1 3.1 L 8.1 18.9 z M 14.4 6.4 l 3.2 3.2").build()
    }
    val Copy: ImageVector by lazy {
        IconBuilder("Copy").s(rrect(9f, 9f, 11f, 11f, 2.5f))
            .s("M 15 9 V 6.5 A 2.5 2.5 0 0 0 12.5 4 h -6 A 2.5 2.5 0 0 0 4 6.5 v 6 A 2.5 2.5 0 0 0 6.5 15 H 9").build()
    }
    val Clipboard: ImageVector by lazy {
        IconBuilder("Clipboard").s(rrect(5f, 5f, 14f, 16f, 2.5f)).s("M 9 3.5 h 6 v 3 H 9 z").build()
    }
    val Move: ImageVector by lazy {
        IconBuilder("Move")
            .s("M 3 7.5 A 2 2 0 0 1 5 5.5 h 4 l 2 2 h 8 a 2 2 0 0 1 2 2 v 8 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 z")
            .s("M 8.5 13.5 h 7 M 13 11 l 2.5 2.5 L 13 16").build()
    }
    val SdCard: ImageVector by lazy {
        IconBuilder("SdCard").s("M 8 3 h 6.5 L 19 7.5 V 19 a 2 2 0 0 1 -2 2 H 8 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 z")
            .s("M 10 7 v 3 M 13 7 v 3 M 16 9 v 1").build()
    }
    val SkipNext: ImageVector by lazy {
        IconBuilder("SkipNext").f("M 6 5 v 14 l 9.5 -7 z M 16 5 h 2.2 v 14 H 16 z").build()
    }
    val SkipPrevious: ImageVector by lazy {
        IconBuilder("SkipPrevious").f("M 18 5 v 14 l -9.5 -7 z M 5.8 5 H 8 v 14 H 5.8 z").build()
    }
    val ScreenRotate: ImageVector by lazy {
        IconBuilder("ScreenRotate")
            .s(tiltedRRect(12.0, 12.0, 6.0, 10.0, 1.6, 45.0))
            .s(arcArrow(9.2, 200.0, 250.0))
            .s(arcArrow(9.2, 20.0, 70.0))
            .build()
    }
    val FastForward: ImageVector by lazy {
        IconBuilder("FastForward").f("M 3.5 6 v 12 l 8 -6 z M 12.5 6 v 12 l 8 -6 z").build()
    }
    val FastRewind: ImageVector by lazy {
        IconBuilder("FastRewind").f("M 20.5 6 v 12 l -8 -6 z M 11.5 6 v 12 l -8 -6 z").build()
    }
    val Headphones: ImageVector by lazy {
        IconBuilder("Headphones").s("M 4 15 v -3 a 8 8 0 0 1 16 0 v 3")
            .s(rrect(3.5f, 14f, 4f, 6.5f, 1.6f)).s(rrect(16.5f, 14f, 4f, 6.5f, 1.6f)).build()
    }
    val Timer: ImageVector by lazy {
        IconBuilder("Timer").s(circle(12f, 13.5f, 7.5f)).s("M 12 13.5 V 9.5 M 10 3 h 4 M 18.6 6.4 l 1.4 -1.4").build()
    }
    val Playlist: ImageVector by lazy {
        IconBuilder("Playlist").s("M 4 7 h 11 M 4 12 h 7 M 4 17 h 7").f("M 15 11 v 8 l 6 -4 z").build()
    }
    val Repeat: ImageVector by lazy {
        IconBuilder("Repeat").f("M 7 7 h 10 v 3 l 4 -4 l -4 -4 v 3 H 5 v 6 h 2 z M 17 17 H 7 v -3 l -4 4 l 4 4 v -3 h 12 v -6 h -2 z").build()
    }
    val RepeatOne: ImageVector by lazy {
        IconBuilder("RepeatOne").f("M 7 7 h 10 v 3 l 4 -4 l -4 -4 v 3 H 5 v 6 h 2 z M 17 17 H 7 v -3 l -4 4 l 4 4 v -3 h 12 v -6 h -2 z M 13 15 V 9 h -1 l -2 1 v 1 h 1.5 v 4 z").build()
    }
    val Shuffle: ImageVector by lazy {
        IconBuilder("Shuffle").s("M 3 7 h 3 c 5 0 5 10 10 10 h 4 M 17 14 l 3 3 l -3 3")
            .s("M 3 17 h 3 c 2 0 3 -1.2 4 -2.5 M 14 9.5 C 15 8.2 16 7 18 7 h 2 M 17 4 l 3 3 l -3 3").build()
    }
    val Pin: ImageVector by lazy {
        IconBuilder("Pin").s("M 9 4 h 6 l -1 5 l 3 3 H 7 l 3 -3 z M 12 12 v 8").build()
    }
    val Eye: ImageVector by lazy {
        IconBuilder("Eye").s("M 2 12 s 3.5 -7 10 -7 s 10 7 10 7 s -3.5 7 -10 7 s -10 -7 -10 -7 z").s(circle(12f, 12f, 3f)).build()
    }
    val EyeOff: ImageVector by lazy {
        IconBuilder("EyeOff").s("M 3 12 s 3.5 -6 9 -6 s 9 6 9 6 s -3.5 6 -9 6 s -9 -6 -9 -6 z")
            .s(circle(12f, 12f, 2.6f)).s("M 4 4 L 20 20").build()
    }
    val ChevronDown: ImageVector by lazy { IconBuilder("ChevronDown").s("M 6 9 l 6 6 l 6 -6").build() }
    val Location: ImageVector by lazy {
        IconBuilder("Location").s("M 12 21 s -6.5 -5.6 -6.5 -11 a 6.5 6.5 0 0 1 13 0 c 0 5.4 -6.5 11 -6.5 11 z").s(circle(12f, 10f, 2.3f)).build()
    }
    val Unlock: ImageVector by lazy {
        IconBuilder("Unlock").s(rrect(5f, 11f, 14f, 9f, 2.5f)).s("M 8 11 V 8 a 4 4 0 0 1 7.5 -1.5").build()
    }
    val Info: ImageVector by lazy {
        IconBuilder("Info").s(circle(12f, 12f, 9f)).s("M 12 11 v 6 M 12 7.5 v 0.5").build()
    }
    val Rotate: ImageVector by lazy {
        IconBuilder("Rotate").s("M 20 12 a 8 8 0 1 1 -2.4 -5.7").s("M 20 4 v 5 h -5").build()
    }
    val RotateLeft: ImageVector by lazy {
        IconBuilder("RotateLeft").s("M 4 12 a 8 8 0 1 0 2.4 -5.7").s("M 4 4 v 5 h 5").build()
    }
    val Flip: ImageVector by lazy {
        IconBuilder("Flip").s("M 12 4 v 16").s("M 8 7 L 3 18 h 5 z M 16 7 l 5 11 h -5 z").build()
    }
    val Pip: ImageVector by lazy {
        IconBuilder("Pip").s(rrect(3f, 5f, 18f, 14f, 3f)).s(rrect(12f, 11f, 6f, 4f, 1f)).build()
    }
    val Subtitles: ImageVector by lazy {
        IconBuilder("Subtitles").s(rrect(3f, 5f, 18f, 14f, 3f)).s("M 7 11 h 3 M 13 11 h 4 M 7 15 h 5 M 15 15 h 2").build()
    }
    val Fit: ImageVector by lazy {
        IconBuilder("Fit").s("M 4 9 V 5 h 4 M 20 9 V 5 h -4 M 4 15 v 4 h 4 M 20 15 v 4 h -4").build()
    }
    /** Google Lens glyph. */
    val Lens: ImageVector by lazy {
        IconBuilder("Lens").f("M12 17.333q-1.667 0-2.833-1.166Q8 15 8 13.333q0-1.666 1.167-2.833Q10.333 9.333 12 9.333q1.667 0 2.833 1.167Q16 11.667 16 13.333q0 1.667-1.167 2.834-1.166 1.166-2.833 1.166Zm8 5.334q-1.1 0-1.883-.784-.784-.783-.784-1.883t.784-1.883q.783-.784 1.883-.784t1.883.784q.784.783.784 1.883t-.784 1.883q-.783.784-1.883.784ZM5.333 24q-2.2 0-3.766-1.567Q0 20.867 0 18.667V16h2.667v2.667q0 1.1.783 1.883.783.783 1.883.783H12V24Zm16-10.667V8q0-1.1-.783-1.883-.783-.784-1.883-.784H5.333q-1.1 0-1.883.784Q2.667 6.9 2.667 8v4H0V8q0-2.2 1.567-3.767 1.566-1.566 3.766-1.566H8L9.333 0h5.334L16 2.667h2.667q2.2 0 3.766 1.566Q24 5.8 24 8v5.333z").build()
    }
    val Audio: ImageVector by lazy {
        IconBuilder("Audio").s("M 4 10 v 4 M 8 7 v 10 M 12 4 v 16 M 16 8 v 8 M 20 11 v 2").build()
    }
    val VideoAlbum: ImageVector by lazy {
        IconBuilder("VideoAlbum").s(rrect(3f, 6f, 18f, 12f, 3f)).f("M 10.5 9.5 v 5 l 4 -2.5 z").build()
    }
    val Brightness: ImageVector by lazy {
        IconBuilder("Brightness").s(circle(12f, 12f, 4f))
            .s("M 12 3 v 2 M 12 19 v 2 M 3 12 h 2 M 19 12 h 2 M 5.6 5.6 l 1.4 1.4 M 17 17 l 1.4 1.4 M 18.4 5.6 L 17 7 M 7 17 l -1.4 1.4")
            .build()
    }
    val Volume: ImageVector by lazy {
        IconBuilder("Volume").s("M 4 9.5 h 3.5 L 12 5.5 v 13 L 7.5 14.5 H 4 z")
            .s("M 15.5 9 a 4 4 0 0 1 0 6 M 18 6.5 a 7.5 7.5 0 0 1 0 11").build()
    }
    val Restore: ImageVector by lazy {
        IconBuilder("Restore").s("M 4 12 a 8 8 0 1 0 2.4 -5.7").s("M 4 4 v 5 h 5").s("M 12 8 v 4 l 3 2").build()
    }
    val Image: ImageVector by lazy {
        IconBuilder("Image").s(rrect(3f, 4f, 18f, 16f, 3.5f)).s(circle(9f, 10f, 1.6f)).s("M 21 16 l -5 -5 l -8 8").build()
    }
}
