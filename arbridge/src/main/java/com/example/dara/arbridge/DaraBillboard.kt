package com.example.dara.arbridge

import io.github.sceneview.math.Position
import kotlin.math.sqrt

object DaraBillboard {
    val DEFAULT_OFFSET = Position(0f, 0.35f, 0f)
    val AUGMENTED_IMAGE_OFFSET = Position(0f, 0.002f, 0f)

    fun labelText(objectId: String, offset: Position = DEFAULT_OFFSET): String {
        return "$objectId - ${formatDistance(distanceFromObject(offset))}"
    }

    fun labelTextBetween(objectId: String, from: Position, to: Position): String {
        return "$objectId - ${formatDistance(distanceBetween(from, to))}"
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

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters < 1f) {
            "${(distanceMeters * 100f).toInt()} cm"
        }
        else {
            String.format(java.util.Locale.US, "%.2f m", distanceMeters)
        }
    }
}
