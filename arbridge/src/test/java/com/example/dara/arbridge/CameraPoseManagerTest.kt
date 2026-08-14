package com.example.dara.arbridge

import com.google.ar.core.Pose
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CameraPoseManagerTest {
    @Test
    fun transformFromArCoreToDara_mapsArCorePoseIntoDaraWorld() {
        val markerPoseInArCore = Pose.makeTranslation(2f, 0f, 0f)
        val markerPoseInDaraWorld = Pose.makeTranslation(10f, 0f, 0f)

        val daraFromArCore = CameraPoseManager.transformFromArCoreToDara(
            markerPoseInDaraWorld = markerPoseInDaraWorld,
            markerPoseInArCore = markerPoseInArCore
        )
        val cameraPoseInDara = daraFromArCore.compose(Pose.makeTranslation(3f, 4f, 5f))

        assertArrayEquals(
            floatArrayOf(11f, 4f, 5f),
            cameraPoseInDara.translation,
            0.0001f
        )
    }

    @Test
    fun cameraPoseInDaraWorld_honorsMarkerRotationAndComposeOrder() {
        val halfTurnAroundY = Pose.makeRotation(0f, 1f, 0f, 0f)
        val markerArPose = Pose.makeTranslation(2f, 0f, 0f)
        val markerDaraPose = Pose.makeTranslation(-1f, 2f, 0f).compose(halfTurnAroundY)
        val arToDara = CameraPoseManager.calculateArToDaraTransform(
            markerArPose = markerArPose,
            markerDaraPose = markerDaraPose
        )

        val cameraDaraPose = CameraPoseManager.cameraPoseInDaraWorld(
            cameraArPose = Pose.makeTranslation(3f, 0f, 0f),
            arToDaraTransform = arToDara
        )

        assertArrayEquals(floatArrayOf(-2f, 2f, 0f), cameraDaraPose.translation, 0.0001f)
    }
}
