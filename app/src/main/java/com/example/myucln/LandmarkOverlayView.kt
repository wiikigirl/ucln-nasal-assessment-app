package com.example.myucln

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LandmarkOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var landmarks: FloatArray? = null

    // 1. Calculate a responsive radius based on the screen density (e.g., 5dp)
    private val density = context.resources.displayMetrics.density
    private val dotRadius = 5f * density

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateLandmarks(newLandmarks: FloatArray) {
        this.landmarks = newLandmarks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentLandmarks = landmarks ?: return

        for (i in currentLandmarks.indices step 2) {
            if (i + 1 < currentLandmarks.size) {
                val x = currentLandmarks[i] * width
                val y = currentLandmarks[i + 1] * height

                // 2. Use the density-independent radius here
                canvas.drawCircle(x, y, dotRadius, pointPaint)
            }
        }
    }
}