package com.example.dara.arbridge

import com.google.ar.core.Pose
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DaraWorldAlignmentManagerTest {
    private val earth = DaraAugmentedImageMarker(
        imageAssetPath = "markers/earth2.jpg",
        imageName = "earth2",
        markerPoseInDaraWorld = Pose.IDENTITY
    )
    private val onca = DaraAugmentedImageMarker(
        imageAssetPath = "markers/onca2.jpg",
        imageName = "onca2",
        markerPoseInDaraWorld = Pose.makeTranslation(-1f, 2f, 0f)
    )

    @Test
    fun cameraKeepsUpdatingAfterMarkerLeavesView() {
        val manager = DaraWorldAlignmentManager(listOf(earth, onca))
        manager.observeMarker("earth2", Pose.makeTranslation(5f, 0f, 0f))

        assertArrayEquals(
            floatArrayOf(2f, 0f, 0f),
            manager.positionInDaraWorld(Pose.makeTranslation(7f, 0f, 0f)),
            0.0001f
        )
        assertEquals("earth2", manager.lastReferenceName)
    }

    @Test
    fun knownSecondMarkerCorrectsAlignmentWithoutChangingItsDaraPose() {
        val manager = DaraWorldAlignmentManager(listOf(earth, onca))
        manager.observeMarker("earth2", Pose.makeTranslation(5f, 0f, 0f))

        val knownPose = manager.observeMarker("onca2", Pose.makeTranslation(8f, 0f, 0f))
        assertArrayEquals(floatArrayOf(-1f, 2f, 0f), knownPose!!.translation, 0.0001f)

        assertArrayEquals(
            floatArrayOf(0f, 2f, 0f),
            manager.positionInDaraWorld(Pose.makeTranslation(9f, 0f, 0f)),
            0.0001f
        )
        assertEquals("onca2", manager.lastReferenceName)
    }

    @Test
    fun unknownMarkerDoesNotChangeAlignment() {
        val manager = DaraWorldAlignmentManager(listOf(earth))
        manager.observeMarker("earth2", Pose.makeTranslation(5f, 0f, 0f))
        val transformBefore = manager.daraFromArCore

        val result = manager.observeMarker("unknown", Pose.makeTranslation(20f, 0f, 0f))

        assertEquals(null, result)
        assertEquals(transformBefore, manager.daraFromArCore)
        assertEquals("earth2", manager.lastReferenceName)
    }
}
