package com.arcadesoftware.musix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Professional custom shuffle icon — two crossing arrows with arrow-heads,
 * drawn as an SVG-style ImageVector for crisp rendering at all sizes.
 */
val ShuffleIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ShuffleCustom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Top path: left → right crossing upward
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero
        ) {
            // Main diagonal line top: 3,7 → 9,7 → 18,17 → 18,17
            moveTo(3f, 7f)
            lineTo(8f, 7f)
            curveTo(10f, 7f, 11f, 8f, 12f, 9f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero
        ) {
            // Bottom diagonal continuation: 12,15 → 16,19 → 21,19
            moveTo(12f, 15f)
            curveTo(13f, 16f, 14f, 17f, 16f, 17f)
            lineTo(21f, 17f)
        }
        // Top-right arrow head
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(17f, 21f)
            lineTo(21f, 17f)
            lineTo(17f, 13f)
        }
        // Bottom path: left → right crossing downward
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(3f, 17f)
            lineTo(8f, 17f)
            curveTo(10f, 17f, 11f, 16f, 12f, 15f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 9f)
            curveTo(13f, 8f, 14f, 7f, 16f, 7f)
            lineTo(21f, 7f)
        }
        // Top-right arrow head (for upper path)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(17f, 3f)
            lineTo(21f, 7f)
            lineTo(17f, 11f)
        }
    }.build()
