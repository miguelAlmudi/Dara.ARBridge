package com.example.dara.arbridge

import com.google.ar.core.Pose

/** Keeps the ARCore -> DaraWorld transform alive after an image leaves the camera. */
class DaraWorldAlignmentManager(markers: List<DaraAugmentedImageMarker>) {
    private val markerPosesInDara = markers
        .mapNotNull { marker -> marker.markerPoseInDaraWorld?.let { marker.imageName to it } }
        .toMap()
        .toMutableMap()

    var daraFromArCore: Pose? = null
        private set

    var lastReferenceName: String? = null
        private set

    fun observeMarker(imageName: String, markerPoseInArCore: Pose): Pose? {
        val markerPoseInDara = markerPosesInDara[imageName] ?: return null
        daraFromArCore = CameraPoseManager.transformFromArCoreToDara(
            markerPoseInDaraWorld = markerPoseInDara,
            markerPoseInArCore = markerPoseInArCore
        )
        lastReferenceName = imageName
        return markerPoseInDara
    }

    fun updateMarkerPose(
        imageName: String,
        markerPoseInDaraWorld: Pose,
        markerPoseInArCore: Pose
    ) {
        markerPosesInDara[imageName] = markerPoseInDaraWorld
        daraFromArCore = CameraPoseManager.transformFromArCoreToDara(
            markerPoseInDaraWorld = markerPoseInDaraWorld,
            markerPoseInArCore = markerPoseInArCore
        )
        lastReferenceName = imageName
    }

    fun markerPose(imageName: String): Pose? = markerPosesInDara[imageName]

    fun poseInDaraWorld(poseInArCore: Pose): Pose? = daraFromArCore?.compose(poseInArCore)

    fun positionInDaraWorld(poseInArCore: Pose): FloatArray? =
        poseInDaraWorld(poseInArCore)?.translation
}
