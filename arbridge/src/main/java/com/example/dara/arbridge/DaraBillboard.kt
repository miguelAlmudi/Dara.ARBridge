package com.example.dara.arbridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object DaraBillboard {
    data class AugmentedImageBillboardSpec(
        val size: Size,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val fontSize: Float
    )

    val DEFAULT_OFFSET = Position(0f, 0.35f, 0f)
    val AUGMENTED_IMAGE_OFFSET = Position(0f, 0.002f, 0f)
    val AUGMENTED_IMAGE_BACKGROUND_COLOR = Color.argb(51, 72, 72, 72)

    fun labelText(objectId: String, offset: Position = DEFAULT_OFFSET): String {
        return "$objectId - ${formatDistance(distanceFromObject(offset))}"
    }

    fun labelTextBetween(objectId: String, from: Position, to: Position): String {
        return "$objectId - ${formatTrackingDistance(distanceBetween(from, to))}"
    }

    fun unknownDistanceText(objectId: String): String {
        return "$objectId - distancia desconhecida"
    }

    fun augmentedImageDistanceText(imageName: String, from: Position, to: Position): String {
        return "$imageName\n${formatDistanceCentimeters(distanceBetween(from, to))}"
    }

    fun augmentedImageMeasurementProblemText(imageName: String): String {
        return "$imageName\n(Problemas de medição)"
    }

    fun distanceFromObject(offset: Position): Float {
        return sqrt(offset.x * offset.x + offset.y * offset.y + offset.z * offset.z)
    }

    fun distanceBetween(from: Position, to: Position): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun augmentedImageBillboardSpec(
        extentX: Float?,
        extentZ: Float?
    ): AugmentedImageBillboardSpec {
        val safeWidth = max(extentX ?: 0.20f, 0.12f)
        val safeHeight = max(extentZ ?: 0.20f, 0.12f)
        val aspectRatio = safeWidth / safeHeight
        val longBitmapEdge = 1536
        val minBitmapEdge = 512
        val bitmapWidth: Int
        val bitmapHeight: Int

        if (aspectRatio >= 1f) {
            bitmapWidth = longBitmapEdge
            bitmapHeight = max(minBitmapEdge, (longBitmapEdge / aspectRatio).roundToInt())
        }
        else {
            bitmapWidth = max(minBitmapEdge, (longBitmapEdge * aspectRatio).roundToInt())
            bitmapHeight = longBitmapEdge
        }

        return AugmentedImageBillboardSpec(
            size = Size(x = safeWidth, y = safeHeight),
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            fontSize = max(64f, min(260f, min(bitmapWidth, bitmapHeight) * 0.36f))
        )
    }

    fun renderTextBitmapInto(
        bitmap: Bitmap,
        text: String,
        fontSize: Float,
        textColor: Int,
        backgroundColor: Int,
        typeface: Typeface = Typeface.DEFAULT_BOLD
    ) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (Color.alpha(backgroundColor) > 0) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                style = Paint.Style.FILL
            }
            val cornerRadius = 0f
            canvas.drawRoundRect(
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                cornerRadius,
                cornerRadius,
                bgPaint
            )
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 235, 235, 235)
            style = Paint.Style.STROKE
            strokeWidth = max(3f, bitmap.height * 0.02f)
        }
        val inset = borderPaint.strokeWidth / 2f
        val borderRadius = 0f
        canvas.drawRoundRect(
            RectF(inset, inset, bitmap.width.toFloat() - inset, bitmap.height.toFloat() - inset),
            borderRadius,
            borderRadius,
            borderPaint
        )

        val lines = text
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("") }
        val maxTextWidth = bitmap.width * 0.90f
        val maxTextHeight = bitmap.height * 0.80f
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
        }
        val referenceFontSize = 100f
        measurePaint.textSize = referenceFontSize
        val referenceMetrics = measurePaint.fontMetrics
        val referenceLineHeight = (referenceMetrics.descent - referenceMetrics.ascent) * 1.12f
        val referenceTotalHeight = referenceLineHeight * lines.size
        val referenceWidestLine = max(1f, lines.maxOf { line -> measurePaint.measureText(line) })
        val fontSizeByWidth = referenceFontSize * (maxTextWidth / referenceWidestLine)
        val fontSizeByHeight = referenceFontSize * (maxTextHeight / referenceTotalHeight)
        val fittedFontSize = min(fontSize, min(fontSizeByWidth, fontSizeByHeight))
            .coerceAtLeast(8f)
        val opaqueTextColor = Color.rgb(
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )

        val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = opaqueTextColor
            textSize = fittedFontSize
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            setShadowLayer(fittedFontSize * 0.10f, 0f, 0f, Color.BLACK)
        }
        val textStrokePaint = Paint(textFillPaint).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(1f, fittedFontSize * 0.10f)
            clearShadowLayer()
        }
        val xPos = bitmap.width / 2f
        val fontMetrics = textFillPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val lineHeight = textHeight * 1.12f
        val totalTextHeight = textHeight + (lineHeight * (lines.size - 1))
        var yPos = (bitmap.height / 2f) - (totalTextHeight / 2f) - fontMetrics.ascent

        lines.forEach { line ->
            canvas.drawText(line, xPos, yPos, textStrokePaint)
            canvas.drawText(line, xPos, yPos, textFillPaint)
            yPos += lineHeight
        }
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters < 1f) {
            "${(distanceMeters * 100f).toInt()} cm"
        }
        else {
            String.format(java.util.Locale.US, "%.2f m", distanceMeters)
        }
    }

    private fun formatTrackingDistance(distanceMeters: Float): String {
        return if (distanceMeters < 1f) {
            val snappedCentimeters = (distanceMeters * 100f).roundToInt()
            "$snappedCentimeters cm"
        }
        else {
            String.format(java.util.Locale.US, "%.2f m", distanceMeters)
        }
    }

    private fun formatDistanceCentimeters(distanceMeters: Float): String {
        val snappedCentimeters = (distanceMeters * 100f).roundToInt()
        return "$snappedCentimeters cm"
    }
}
