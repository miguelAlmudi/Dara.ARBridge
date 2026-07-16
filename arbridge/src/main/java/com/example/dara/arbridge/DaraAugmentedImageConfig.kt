package com.example.dara.arbridge

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Pose
import com.google.ar.core.Session
import io.github.sceneview.math.Position

data class DaraAugmentedImageMarker(
    val imageAssetPath: String,
    val imageName: String,
    val physicalWidthMeters: Float? = null,
    /** Null delegates the Dara pose lookup to [DaraMarkerDefinitions]. */
    val markerPoseInDaraWorld: Pose? = null
)

data class DaraAugmentedImageConfig(
    val imageAssetPath: String? = null,
    val imageName: String = DEFAULT_IMAGE_NAME,
    val physicalWidthMeters: Float? = null,
    val offset: Position = Position(0f, 0f, 0f),
    val modelScaleToUnits: Float = DEFAULT_MODEL_SCALE_TO_UNITS,
    val markerPoseInDaraWorld: Pose = Pose.IDENTITY,
    val markers: List<DaraAugmentedImageMarker> = emptyList()
) {
    val effectiveMarkers: List<DaraAugmentedImageMarker>
        get() = markers.ifEmpty {
            val path = imageAssetPath?.takeIf { it.isNotBlank() } ?: return@ifEmpty emptyList()
            listOf(
                DaraAugmentedImageMarker(
                    imageAssetPath = path,
                    imageName = imageName,
                    physicalWidthMeters = physicalWidthMeters,
                    markerPoseInDaraWorld = markerPoseInDaraWorld
                )
            )
        }.map { marker ->
            if (marker.markerPoseInDaraWorld != null) marker
            else marker.copy(
                markerPoseInDaraWorld = DaraMarkerDefinitions.find(marker.imageName)?.daraPose
            )
        }

    val isEnabled: Boolean
        get() = effectiveMarkers.isNotEmpty()

    val displayName: String
        get() = imageAssetPath
            ?.trim()
            ?.removePrefix("file:///android_asset/")
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
            ?: imageName

    fun createDatabase(context: Context, session: Session, logTag: String): AugmentedImageDatabase? {
        val configuredMarkers = effectiveMarkers
        if (configuredMarkers.isEmpty()) return null

        return try {
            AugmentedImageDatabase(session).apply {
                configuredMarkers.forEach { marker ->
                    val assetPath = marker.imageAssetPath
                        .trim()
                        .removePrefix("file:///android_asset/")
                        .trim('/')
                    val bitmap = context.assets.open(assetPath).use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                        ?: error("Augmented image bitmap decode failed: $assetPath")
                    val width = marker.physicalWidthMeters
                    if (width != null && width > 0f) {
                        addImage(marker.imageName, bitmap, width)
                    } else {
                        addImage(marker.imageName, bitmap)
                    }
                }
            }
        }
        catch (ex: Exception) {
            Log.e(logTag, "Error loading augmented image database", ex)
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
            val markerPoseInDaraWorld = Pose(
                floatArrayOf(
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_POSITION_X_METERS, 0f),
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_POSITION_Y_METERS, 0f),
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_POSITION_Z_METERS, 0f)
                ),
                floatArrayOf(
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_ROTATION_QX, 0f),
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_ROTATION_QY, 0f),
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_ROTATION_QZ, 0f),
                    readFloat(bundle, intent, DaraArContract.EXTRA_MARKER_DARA_ROTATION_QW, 1f)
                )
            )

            val markerPaths = bundle?.getStringArray(DaraArContract.EXTRA_AUGMENTED_IMAGE_ASSET_PATHS)
                ?: intent?.getStringArrayExtra(DaraArContract.EXTRA_AUGMENTED_IMAGE_ASSET_PATHS)
            val markerNames = bundle?.getStringArray(DaraArContract.EXTRA_AUGMENTED_IMAGE_NAMES)
                ?: intent?.getStringArrayExtra(DaraArContract.EXTRA_AUGMENTED_IMAGE_NAMES)
            val markerWidths = bundle?.getFloatArray(DaraArContract.EXTRA_AUGMENTED_IMAGE_WIDTHS_METERS)
                ?: intent?.getFloatArrayExtra(DaraArContract.EXTRA_AUGMENTED_IMAGE_WIDTHS_METERS)
            val markerPositions = bundle?.getFloatArray(DaraArContract.EXTRA_MARKER_DARA_POSITIONS)
                ?: intent?.getFloatArrayExtra(DaraArContract.EXTRA_MARKER_DARA_POSITIONS)
            val markerRotations = bundle?.getFloatArray(DaraArContract.EXTRA_MARKER_DARA_ROTATIONS)
                ?: intent?.getFloatArrayExtra(DaraArContract.EXTRA_MARKER_DARA_ROTATIONS)
            val markerPoseKnown = bundle?.getBooleanArray(DaraArContract.EXTRA_MARKER_DARA_POSE_KNOWN)
                ?: intent?.getBooleanArrayExtra(DaraArContract.EXTRA_MARKER_DARA_POSE_KNOWN)

            val markers = markerPaths?.mapIndexedNotNull { index, path ->
                val name = markerNames?.getOrNull(index) ?: return@mapIndexedNotNull null
                val hasPose = markerPoseKnown?.getOrNull(index) == true
                DaraAugmentedImageMarker(
                    imageAssetPath = path,
                    imageName = name,
                    physicalWidthMeters = markerWidths?.getOrNull(index)?.takeUnless { it.isNaN() },
                    markerPoseInDaraWorld = if (hasPose) {
                        Pose(
                            floatArrayOf(
                                markerPositions?.getOrNull(index * 3) ?: 0f,
                                markerPositions?.getOrNull(index * 3 + 1) ?: 0f,
                                markerPositions?.getOrNull(index * 3 + 2) ?: 0f
                            ),
                            floatArrayOf(
                                markerRotations?.getOrNull(index * 4) ?: 0f,
                                markerRotations?.getOrNull(index * 4 + 1) ?: 0f,
                                markerRotations?.getOrNull(index * 4 + 2) ?: 0f,
                                markerRotations?.getOrNull(index * 4 + 3) ?: 1f
                            )
                        )
                    } else null
                )
            }.orEmpty()

            return DaraAugmentedImageConfig(
                imageAssetPath = imagePath,
                imageName = imageName,
                physicalWidthMeters = physicalWidth,
                offset = offset,
                modelScaleToUnits = scaleToUnits,
                markerPoseInDaraWorld = markerPoseInDaraWorld,
                markers = markers
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
