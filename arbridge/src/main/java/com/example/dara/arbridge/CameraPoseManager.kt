package com.example.dara.arbridge

import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import java.util.Locale

object CameraPoseManager {
    fun relativeCameraPositionMeters(
        camera: Camera?,
        originAnchor: Anchor?
    ): FloatArray? {
        val cameraPose = camera?.displayOrientedPose ?: return null
        val originPose = originAnchor?.pose ?: return null

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
}
