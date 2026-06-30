package com.example.dara.arbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

class Dara3dRenderActivity : ComponentActivity() {

    private val tag = "Dara3dRenderActivity"

    private var modelId: String = "sem-model-id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelId = intent.getStringExtra(DaraArContract.EXTRA_MODEL_ID) ?: "sem-model-id"

        val modelAssetPath =
            intent.getStringExtra(DaraArContract.EXTRA_MODEL_ASSET_PATH)
                ?: "models/vaso.glb"

        val assetOk = assetExists(modelAssetPath)

        Log.d(tag, "onCreate")
        Log.d(tag, "modelId=$modelId")
        Log.d(tag, "modelAssetPath=$modelAssetPath")
        Log.d(tag, "assetExists=$assetOk")
        Log.d(tag, "assets=${assets.list("models")?.joinToString()}")

        setContent {
            val engine = rememberEngine()
            val modelLoader = rememberModelLoader(engine)
            val cameraManipulator = rememberCameraManipulator()

            val modelInstance = rememberModelInstance(
                modelLoader = modelLoader,
                assetFileLocation = modelAssetPath
            )

            LaunchedEffect(modelInstance) {
                Log.d(tag, "modelInstance=$modelInstance")
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            ) {
                SceneView(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    cameraManipulator = cameraManipulator
                ) {
                    if (modelInstance != null) {
                        Log.d(tag, "Creating ModelNode")

                        ModelNode(
                            modelInstance = modelInstance,
                            scaleToUnits = 2.0f,
                            autoAnimate = true,
                            position = Position(z = -2.0f)
                        )
                    } else {
                        Log.e(tag, "modelInstance is null")
                    }
                }

                Text(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    color = Color.White,
                    text = """
                        modelAssetPath=$modelAssetPath
                        assetExists=$assetOk
                        modelInstance=${modelInstance != null}
                    """.trimIndent()
                )

                Button(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    onClick = {
                        returnObjectClicked(modelId)
                    }
                ) {
                    Text("Simular clique no objeto 3D")
                }
            }
        }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            assets.open(path).close()
            true
        } catch (ex: Exception) {
            Log.e(tag, "Asset not found: $path", ex)
            false
        }
    }

    private fun returnObjectClicked(objectId: String) {
        val result = Intent().apply {
            putExtra(DaraArContract.RESULT_EVENT, DaraArContract.EVENT_OBJECT_CLICKED)
            putExtra(DaraArContract.RESULT_OBJECT_ID, objectId)
        }

        setResult(Activity.RESULT_OK, result)
        finish()
    }
}