package com.tbtechs.focusflow.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Defense tab icons, built from the Ionicons "shield-checkmark" / "shield-checkmark-outline"
 * glyphs the React Native tab bar renders.
 *
 *  Active   -> solid shield, the check mark is a real cut-out (fill only, no stroke at all)
 *  Inactive -> outlined shield + check, drawn as two stroked paths with round caps/joins
 *
 * Both are black here on purpose: the colour comes from Icon(tint = ...).
 *   active   tint = BrandPrimary (#6366F1)
 *   inactive tint = RefMuted     (same tint as the other four inactive tabs)
 */
object DefenseTabIcons {

    /**
     * Outline weight in the 512 viewport. 42.67 = 2/24, the weight of the Material outlined
     * Schedule/Stats/Settings icons, so Defense sits at the same visual weight as its neighbours.
     * Ionicons' own weight is 32f (closest to the RN app) if you ever want the thinner look.
     */
    private const val OUTLINE_STROKE = 42.67f

    /** Active (focused) Defense icon. */
    val Active: ImageVector by lazy {
        ImageVector.Builder(
            name = "DefenseShieldActive",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(479.07f, 111.36f)
                curveTo(478.92f, 108.99f, 478.24f, 106.68f, 477.09f, 104.61f)
                curveTo(475.94f, 102.53f, 474.34f, 100.73f, 472.41f, 99.35f)
                curveTo(470.47f, 97.97f, 468.26f, 97.04f, 465.92f, 96.62f)
                curveTo(379.42f, 81.1f, 343.31f, 69.88f, 262.59f, 33.42f)
                curveTo(260.52f, 32.48f, 258.27f, 32f, 256f, 32f)
                curveTo(253.73f, 32f, 251.48f, 32.48f, 249.41f, 33.42f)
                curveTo(168.69f, 69.88f, 132.58f, 81.1f, 46.08f, 96.62f)
                curveTo(43.74f, 97.04f, 41.53f, 97.97f, 39.59f, 99.35f)
                curveTo(37.66f, 100.73f, 36.06f, 102.53f, 34.91f, 104.61f)
                curveTo(33.76f, 106.68f, 33.08f, 108.99f, 32.93f, 111.36f)
                curveTo(29.08f, 172.47f, 37.29f, 229.41f, 57.36f, 280.6f)
                curveTo(73.8f, 322.35f, 98.12f, 360.55f, 129f, 393.11f)
                curveTo(182.47f, 449.84f, 239.24f, 474.48f, 250.07f, 478.84f)
                curveTo(251.98f, 479.61f, 254.01f, 480.01f, 256.07f, 480.01f)
                curveTo(258.13f, 480.01f, 260.16f, 479.61f, 262.07f, 478.84f)
                curveTo(272.9f, 474.48f, 329.67f, 449.84f, 383.14f, 393.11f)
                curveTo(413.97f, 360.54f, 438.25f, 322.34f, 454.64f, 280.6f)
                curveTo(474.71f, 229.41f, 482.92f, 172.47f, 479.07f, 111.36f)
                close()
                moveTo(348.07f, 186.47f)
                lineTo(237.27f, 314.47f)
                curveTo(235.85f, 316.12f, 234.1f, 317.46f, 232.14f, 318.41f)
                curveTo(230.17f, 319.36f, 228.04f, 319.9f, 225.86f, 320f)
                lineTo(225.2f, 320f)
                curveTo(223.13f, 320f, 221.07f, 319.6f, 219.15f, 318.81f)
                curveTo(217.23f, 318.03f, 215.48f, 316.88f, 214f, 315.43f)
                lineTo(164.8f, 267.23f)
                curveTo(163.08f, 265.55f, 161.77f, 263.5f, 160.95f, 261.24f)
                curveTo(160.13f, 258.98f, 159.84f, 256.56f, 160.08f, 254.17f)
                curveTo(160.33f, 251.78f, 161.11f, 249.47f, 162.36f, 247.42f)
                curveTo(163.62f, 245.38f, 165.33f, 243.64f, 167.35f, 242.34f)
                curveTo(169.37f, 241.04f, 171.66f, 240.21f, 174.05f, 239.92f)
                curveTo(176.43f, 239.62f, 178.85f, 239.87f, 181.13f, 240.64f)
                curveTo(183.41f, 241.41f, 185.48f, 242.69f, 187.2f, 244.37f)
                lineTo(224.2f, 280.66f)
                lineTo(323.9f, 165.53f)
                curveTo(325.74f, 163.41f, 328.1f, 161.8f, 330.76f, 160.88f)
                curveTo(333.41f, 159.96f, 336.26f, 159.76f, 339.02f, 160.29f)
                curveTo(341.78f, 160.82f, 344.35f, 162.06f, 346.47f, 163.9f)
                curveTo(348.59f, 165.74f, 350.2f, 168.1f, 351.12f, 170.76f)
                curveTo(352.04f, 173.41f, 352.24f, 176.26f, 351.71f, 179.02f)
                curveTo(351.18f, 181.78f, 349.94f, 184.35f, 348.1f, 186.47f)
                close()
            }
        }.build()
    }

    /** Inactive Defense icon. */
    val Inactive: ImageVector by lazy {
        ImageVector.Builder(
            name = "DefenseShieldInactive",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            // check mark
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = OUTLINE_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(336f, 176f)
                lineTo(225.2f, 304f)
                lineTo(176f, 255.8f)
            }
            // shield outline (explicitly closed so the start/end point has a proper round join,
            // not two overlapping caps - that overlap is where a thin seam can appear)
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = OUTLINE_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(463.1f, 112.37f)
                curveTo(373.68f, 96.33f, 336.71f, 84.45f, 256f, 48f)
                curveTo(175.29f, 84.45f, 138.32f, 96.33f, 48.9f, 112.37f)
                curveTo(32.7f, 369.13f, 240.58f, 457.79f, 256f, 464f)
                curveTo(271.42f, 457.79f, 479.3f, 369.13f, 463.1f, 112.37f)
                close()
            }
        }.build()
    }
}
