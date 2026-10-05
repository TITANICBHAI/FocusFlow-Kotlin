package com.tbtechs.focusflow.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Shield-checkmark icons based on the supplied Ionicons geometry.
 */
internal object DefenseIcons {
    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheckmark",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M479.07 111.36a16 16 0 00-13.15-14.74c-86.5-15.52-122.61-26.74-203.33-63.2a16 16 0 00-13.18 0C168.69 69.88 132.58 81.1 46.08 96.62a16 16 0 00-13.15 14.74c-3.85 61.11 4.36 118.05 24.43 169.24A349.47 349.47 0 00129 393.11c53.47 56.73 110.24 81.37 121.07 85.73a16 16 0 0012 0c10.83-4.36 67.6-29 121.07-85.73a349.47 349.47 0 0071.5-112.51c20.07-51.19 28.28-108.13 24.43-169.24zm-131 75.11l-110.8 128A16 16 0 01225.86 320h-.66a16 16 0 01-11.2-4.57l-49.2-48.2a16 16 0 1122.4-22.86l37 36.29L323.9 165.53a16 16 0 0124.2 20.94z"
                ).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build()
    }

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheckmarkOutline",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M336 176L225.2 304 176 255.8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 32f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M463.1 112.37C373.68 96.33 336.71 84.45 256 48c-80.71 36.45-117.68 48.33-207.1 64.37C32.7 369.13 240.58 457.79 256 464c15.42-6.21 223.3-94.87 207.1-351.63z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 32f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}