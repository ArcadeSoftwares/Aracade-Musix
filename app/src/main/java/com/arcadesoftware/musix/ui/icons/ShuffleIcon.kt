package com.arcadesoftware.musix.ui.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/**
 * Professional custom shuffle icon — two crossing arrows with arrow-heads,
 * drawn as an SVG-style ImageVector for crisp rendering at all sizes.
 *
 * Uses currentColor-friendly tinting: pass a tint at render time via
 * Icon(imageVector = ShuffleIcon, tint = MaterialTheme.colorScheme.onSurface, ...)
 * rather than baking Color.Black into the vector.
 */
val ShuffleIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ShuffleCustom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Top strand: starts left, curves up and crosses to bottom-right
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 7f)
            lineTo(8f, 7f)
            curveTo(10f, 7f, 11f, 8f, 12f, 9f)
            curveTo(13f, 10f, 14f, 15f, 16f, 15.5f)
            curveTo(13f, 16f, 12.9f, 16f, 12f, 15f)
            curveTo(11f, 16f, 10f, 17f, 8f, 17f)
            lineTo(3f, 17f)
        }

        // Second strand: starts left-bottom, curves and crosses to top-right
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 9f)
            curveTo(13f, 8f, 14f, 7f, 16f, 7f)
            lineTo(21f, 7f)
        }

        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 15f)
            curveTo(13f, 16f, 14f, 17f, 16f, 17f)
            lineTo(21f, 17f)
        }

        // Arrow head — top right
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17f, 3f)
            lineTo(21f, 7f)
            lineTo(17f, 11f)
        }

        // Arrow head — bottom right
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17f, 13f)
            lineTo(21f, 17f)
            lineTo(17f, 21f)
        }
    }.build()