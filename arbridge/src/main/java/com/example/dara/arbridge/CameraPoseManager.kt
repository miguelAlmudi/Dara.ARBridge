package com.example.dara.arbridge

import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import java.util.Locale
import kotlin.math.sqrt

object CameraPoseManager {
    fun cameraPoseInDaraWorld(
        camera: Camera?,
        markerPoseInArCore: Pose?,
        markerPoseInDaraWorld: Pose
    ): Pose? {
        val cameraPoseInArCore = camera?.pose ?: return null
        markerPoseInArCore ?: return null

        val daraFromArCore = transformFromArCoreToDara(
            markerPoseInDaraWorld = markerPoseInDaraWorld,
            markerPoseInArCore = markerPoseInArCore
        )

        return daraFromArCore.compose(cameraPoseInArCore)
    }

    fun cameraPositionInDaraWorldMeters(
        camera: Camera?,
        markerPoseInArCore: Pose?,
        markerPoseInDaraWorld: Pose
    ): FloatArray? {
        val cameraPoseInDara = cameraPoseInDaraWorld(
            camera = camera,
            markerPoseInArCore = markerPoseInArCore,
            markerPoseInDaraWorld = markerPoseInDaraWorld
        ) ?: return null

        return floatArrayOf(
            cameraPoseInDara.tx(),
            cameraPoseInDara.ty(),
            cameraPoseInDara.tz()
        )
    }

    fun transformFromArCoreToDara(
        markerPoseInDaraWorld: Pose,
        markerPoseInArCore: Pose
    ): Pose {
        return markerPoseInDaraWorld.compose(markerPoseInArCore.inverse())
    }

    fun relativeCameraPositionMeters(
        camera: Camera?,
        originAnchor: Anchor?
    ): FloatArray? {
        val anchor = originAnchor
            ?.takeIf { it.trackingState == TrackingState.TRACKING }
            ?: return null
        return relativeCameraPositionMeters(camera, anchor.pose)
    }

    fun relativeCameraPositionMeters(
        camera: Camera?,
        originPose: Pose?
    ): FloatArray? {
        val cameraPose = camera?.displayOrientedPose ?: return null
        originPose ?: return null
        // Express the display-oriented camera pose in the origin anchor coordinate space.
        val relativePose = originPose
            .inverse()
            .compose(cameraPose)

        return floatArrayOf(
            relativePose.tx(),
            relativePose.ty(),
            relativePose.tz()
        )
    }

    fun formatPositionMeters(position: FloatArray?): String {
        return if (position == null) {
            "aguardando marcador"
        }
        else {
            String.format(
                Locale.US,
                "x=%.3f y=%.3f z=%.3f m",
                position[0],
                position[1],
                position[2]
            )
        }
    }

    fun distanceFromRelativePositionMeters(position: FloatArray?): Float? {
        position ?: return null
        return sqrt(
            position[0] * position[0] +
                position[1] * position[1] +
                position[2] * position[2]
        )
    }

    fun formatDistanceMeters(position: FloatArray?): String {
        val distance = distanceFromRelativePositionMeters(position)
            ?: return "aguardando marcador"

        return String.format(Locale.US, "%.3f m", distance)
    }
}
