package com.example.dara.arbridge

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object DaraBillboard {
    /**
     * Spec visual para o ViewNode. Nao existe mais tamanho de Bitmap aqui.
     * size continua em metros, porque esse e o tamanho do plano 3D do ViewNode/Billboard.
     */
    data class AugmentedImageBillboardSpec(
        val size: Size,
        val fontSizeSp: Float,
        val minFontSizeSp: Float = 8f
    )

    val DEFAULT_OFFSET = Position(0f, 0.35f, 0f)
    val AUGMENTED_IMAGE_OFFSET = Position(0f, 0.04f, 0f)
    val AUGMENTED_IMAGE_BACKGROUND_COLOR = AndroidColor.argb(210, 32, 32, 32)

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
        val safeWidth = max(extentX ?: 0.28f, 0.26f)
        val safeHeight = max(extentZ ?: 0.20f, 0.20f)
        val smallerEdge = min(safeWidth, safeHeight)

        return AugmentedImageBillboardSpec(
            size = Size(x = safeWidth, y = safeHeight),
            fontSizeSp = max(12f, min(34f, smallerEdge * 120f)),
            minFontSizeSp = 8f
        )
    }

    /**
     * Escala opcional para manter legibilidade conforme a distancia muda.
     * Use no scale do node/pai, nao em Bitmap.
     */
    fun scaleForDistance(
        distanceMeters: Float,
        minScale: Float = 0.75f,
        maxScale: Float = 1.45f,
        referenceDistanceMeters: Float = 1.0f
    ): Float {
        val safeDistance = distanceMeters.coerceAtLeast(0.1f)
        return (safeDistance / referenceDistanceMeters).coerceIn(minScale, maxScale)
    }

    @Composable
    fun BillboardContent(
        text: String,
        fontSizeSp: Float,
        textColor: Color = Color.White,
        backgroundColor: Color = Color.Transparent,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = (fontSizeSp * 1.12f).sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = max(1f, fontSizeSp * 0.18f)
                    )
                )
            )
        }
    }

    fun androidColorToComposeColor(color: Int): Color {
        return Color(
            red = AndroidColor.red(color),
            green = AndroidColor.green(color),
            blue = AndroidColor.blue(color),
            alpha = AndroidColor.alpha(color)
        )
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
