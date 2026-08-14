package com.example.dara.arbridge

import com.google.ar.core.Pose

data class DaraMarkerDefinition(
    val imageName: String,
    val daraPose: Pose
)

object DaraMarkerDefinitions {
    private val definitions = listOf(
        DaraMarkerDefinition(
            imageName = "earth2",
            daraPose = Pose.IDENTITY
        ),
        DaraMarkerDefinition(
            imageName = "onca2",
            daraPose = Pose.makeTranslation(-1f, 2f, 0f)
        )
    ).associateBy(DaraMarkerDefinition::imageName)

    fun find(imageName: String): DaraMarkerDefinition? = definitions[imageName]
}
