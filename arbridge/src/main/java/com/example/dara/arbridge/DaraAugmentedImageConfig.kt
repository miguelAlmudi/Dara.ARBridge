package com.example.dara.arbridge

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session
import io.github.sceneview.math.Position

data class DaraAugmentedImageConfig(
    val imageAssetPath: String? = null,
    val imageName: String = DEFAULT_IMAGE_NAME,
    val physicalWidthMeters: Float? = null,
    val offset: Position = Position(0f, 0f, 0f),
    val modelScaleToUnits: Float = DEFAULT_MODEL_SCALE_TO_UNITS
) {
    val isEnabled: Boolean
        get() = !imageAssetPath.isNullOrBlank()

    fun createDatabase(context: Context, session: Session, logTag: String): AugmentedImageDatabase? {
        val assetPath = imageAssetPath
            ?.trim()
            ?.removePrefix("file:///android_asset/")
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return try {
            val bitmap = context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }

            if (bitmap == null) {
                Log.e(logTag, "Augmented image bitmap decode failed: $assetPath")
                return null
            }

            AugmentedImageDatabase(session).apply {
                val width = physicalWidthMeters
                if (width != null && width > 0f) {
                    addImage(imageName, bitmap, width)
                }
                else {
                    addImage(imageName, bitmap)
                }
            }
        }
        catch (ex: Exception) {
            Log.e(logTag, "Error loading augmented image asset: $assetPath", ex)
            null
        }
    }

    companion object {
        const val DEFAULT_IMAGE_NAME = "dara_reference_image"
        const val DEFAULT_MODEL_SCALE_TO_UNITS = 0.5f

        fun from(bundle: Bundle?, intent: Intent?): DaraAugmentedImageConfig {
            val imagePath = bundle?.getString(DaraArContract.EXTRA_AUGMENTED_IMAGE_ASSET_PATH)
                ?: intent?.getStringExtra(DaraArContract.EXTRA_AUGMENTED_IMAGE_ASSET_PATH)

            val imageName = bundle?.getString(DaraArContract.EXTRA_AUGMENTED_IMAGE_NAME)
                ?: intent?.getStringExtra(DaraArContract.EXTRA_AUGMENTED_IMAGE_NAME)
                ?: DEFAULT_IMAGE_NAME

            val physicalWidth = readOptionalFloat(
                bundle = bundle,
                intent = intent,
                key = DaraArContract.EXTRA_AUGMENTED_IMAGE_WIDTH_METERS
            )

            val offset = Position(
                x = readFloat(bundle, intent, DaraArContract.EXTRA_MODEL_OFFSET_X_METERS, 0f),
                y = readFloat(bundle, intent, DaraArContract.EXTRA_MODEL_OFFSET_Y_METERS, 0f),
                z = readFloat(bundle, intent, DaraArContract.EXTRA_MODEL_OFFSET_Z_METERS, 0f)
            )

            val scaleToUnits = readFloat(
                bundle = bundle,
                intent = intent,
                key = DaraArContract.EXTRA_MODEL_SCALE_TO_UNITS,
                defaultValue = DEFAULT_MODEL_SCALE_TO_UNITS
            ).takeIf { it > 0f } ?: DEFAULT_MODEL_SCALE_TO_UNITS

            return DaraAugmentedImageConfig(
                imageAssetPath = imagePath,
                imageName = imageName,
                physicalWidthMeters = physicalWidth,
                offset = offset,
                modelScaleToUnits = scaleToUnits
            )
        }

        private fun readOptionalFloat(bundle: Bundle?, intent: Intent?, key: String): Float? {
            if (bundle?.containsKey(key) == true) {
                return bundle.getFloat(key)
            }

            if (intent?.hasExtra(key) == true) {
                return intent.getFloatExtra(key, 0f)
            }

            return null
        }

        private fun readFloat(
            bundle: Bundle?,
            intent: Intent?,
            key: String,
            defaultValue: Float
        ): Float {
            if (bundle?.containsKey(key) == true) {
                return bundle.getFloat(key)
            }

            if (intent?.hasExtra(key) == true) {
                return intent.getFloatExtra(key, defaultValue)
            }

            return defaultValue
        }
    }
}
