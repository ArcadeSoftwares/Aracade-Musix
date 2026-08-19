package com.arcadesoftware.musix.components

import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun test() {
    val b = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val s = SweepGradient(0f, 0f, intArrayOf(Color.Red.toArgb(), Color.Blue.toArgb()), null)
            val m = Matrix()
            m.postRotate(90f)
            s.setLocalMatrix(m)
            return s
        }
    }
}
