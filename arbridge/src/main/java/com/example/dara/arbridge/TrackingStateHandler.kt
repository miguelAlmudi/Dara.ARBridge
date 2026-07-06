package com.example.dara.arbridge

import android.os.Handler
import android.os.Looper
import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

class TrackingStateHandler(
    private val callback: Callback? = null,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    private var lastUiState: UiState? = null

    fun handle(camera: Camera): Boolean {
        val uiState = when (camera.trackingState) {
            TrackingState.TRACKING -> UiState.Tracking
            TrackingState.PAUSED,
            TrackingState.STOPPED -> {
                val warning = warningFor(camera.trackingFailureReason)
                UiState.NotTracking(
                    trackingState = camera.trackingState,
                    failureReason = camera.trackingFailureReason,
                    title = warning.title,
                    message = warning.message
                )
            }
        }

        dispatchIfChanged(uiState)
        return camera.trackingState == TrackingState.TRACKING
    }

    private fun dispatchIfChanged(uiState: UiState) {
        if (uiState == lastUiState) return
        lastUiState = uiState

        mainHandler.post {
            when (uiState) {
                UiState.Tracking -> callback?.onCameraTracking()
                is UiState.NotTracking -> callback?.onCameraTrackingProblem(
                    trackingState = uiState.trackingState,
                    failureReason = uiState.failureReason,
                    title = uiState.title,
                    message = uiState.message
                )
            }
        }
    }

    interface Callback {
        fun onCameraTracking()

        fun onCameraTrackingProblem(
            trackingState: TrackingState,
            failureReason: TrackingFailureReason,
            title: String,
            message: String
        )
    }

    data class Warning(
        val title: String,
        val message: String
    )

    private sealed interface UiState {
        data object Tracking : UiState

        data class NotTracking(
            val trackingState: TrackingState,
            val failureReason: TrackingFailureReason,
            val title: String,
            val message: String
        ) : UiState
    }

    companion object {
        fun warningFor(reason: TrackingFailureReason): Warning {
            return when (reason) {
                TrackingFailureReason.NONE -> Warning(
                    title = "Inicializando AR",
                    message = "Mova o celular lentamente para mapear o ambiente."
                )
                TrackingFailureReason.INSUFFICIENT_FEATURES -> Warning(
                    title = "Poucos detalhes na superfície",
                    message = "Aponte a câmera para uma área com mais textura ou objetos visíveis."
                )
                TrackingFailureReason.INSUFFICIENT_LIGHT -> Warning(
                    title = "Ambiente muito escuro",
                    message = "Vá para uma área mais iluminada."
                )
                TrackingFailureReason.EXCESSIVE_MOTION -> Warning(
                    title = "Movimento muito rápido",
                    message = "Mova a câmera mais devagar."
                )
                TrackingFailureReason.CAMERA_UNAVAILABLE -> Warning(
                    title = "Câmera indisponível",
                    message = "Feche outros apps que possam estar usando a câmera."
                )
                TrackingFailureReason.BAD_STATE -> Warning(
                    title = "Erro no ARCore",
                    message = "Reinicie a sessão AR ou abra o aplicativo novamente."
                )
            }
        }
    }
}
