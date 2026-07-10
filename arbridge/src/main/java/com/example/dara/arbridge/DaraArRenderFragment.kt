package com.example.dara.arbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.scene.PlaneRendererBase
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.*
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import java.util.Locale
import kotlin.math.roundToInt

class DaraArRenderFragment : Fragment() {
    private var rootContainer: FrameLayout? = null
    private val logTag = "DaraArRenderFragment"
    private var modelId: String = "null"
    private var modelAssetPath: String? = null
    private var augmentedImageConfig: DaraAugmentedImageConfig = DaraAugmentedImageConfig()
    private var arSceneStarted = false
    private var userRequestedArInstall = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class ControlMode {
        GIZMO,
        GESTURE
    }

    private enum class GizmoAxis {
        X,
        Y,
        Z
    }

    private enum class GizmoAction {
        TRANSLATE,
        ROTATE
    }

    private enum class GizmoMode {
        TRANSLATION,
        ROTATION,
        SCALE
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startArCore() else showPermissionDenied()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(requireContext()).also {
            rootContainer = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadArguments()
        Log.d(logTag, "onViewCreated modelId=$modelId modelAssetPath=$modelAssetPath")

        if (hasCameraPermission()) startArCore()
        else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (rootContainer != null && !arSceneStarted && hasCameraPermission()) {
            startArCore()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        rootContainer?.removeAllViews()
        rootContainer = null
        arSceneStarted = false
    }

    private fun loadArguments() {
        modelId = arguments?.getString(DaraArContract.EXTRA_MODEL_ID)
            ?: activity?.intent?.getStringExtra(DaraArContract.EXTRA_MODEL_ID)
                    ?: "null"

        modelAssetPath = arguments?.getString(DaraArContract.EXTRA_MODEL_ASSET_PATH)
            ?: activity?.intent?.getStringExtra(DaraArContract.EXTRA_MODEL_ASSET_PATH)

        augmentedImageConfig = DaraAugmentedImageConfig.from(arguments, activity?.intent)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startArCore() {
        if (arSceneStarted)
            return

        when (val a = ArCoreApk.getInstance().checkAvailability(requireContext())) {
            ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                showStatus("Checking ARCore")
                mainHandler.postDelayed({ startArCore() }, 500)
                return
            }
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT,
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                showArUnavailable("ARCore unavailable: $a"); return
            }
            else -> {}
        }
        try {
            when (ArCoreApk.getInstance().requestInstall(requireActivity(), userRequestedArInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    userRequestedArInstall = false
                    showStatus("Google Play Services for AR not installed")
                }
                ArCoreApk.InstallStatus.INSTALLED -> showArScene()
            }
        }
        catch (ex: Exception) {
            Log.e(logTag, "Error on check ARCore", ex)
            showArUnavailable("Error: ${ex.message}")
        }
    }

    private fun findGltfInFolder(folderPath: String): String? {
        return try {
            requireContext().assets.list(folderPath)
                ?.firstOrNull { it.endsWith(".gltf", true) }
                ?.let { "$folderPath/$it" }
        }
        catch (e: Exception) {
            Log.e(logTag, "Error on get folder", e);
            null
        }
    }

    private fun resolveModelPath(rawPath: String?): String? {
        val path = rawPath
            ?.trim()
            ?.removePrefix("file:///android_asset/")
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        // Load from glb or gltf file
        if (isModelFilePath(path)) {
            if (assetFileExists(path)) {
                return path
            }
            else {
                Log.e(logTag, "Model file not found: $path")
                return null
            }
        }

        // Load as folder
        findModelInsideFolder(path)?.let { return it }

        Log.e(logTag, "Model not found for rawPath=$rawPath, normalizedPath=$path")
        return null
    }

    private fun isModelFilePath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.endsWith(".glb") || lower.endsWith(".gltf")
    }

    private fun findModelInsideFolder(folderPath: String): String? {
        try {
            val files = requireContext().assets.list(folderPath).orEmpty()

            val modelFile = files.firstOrNull {
                it.endsWith(".glb", ignoreCase = true) ||
                        it.endsWith(".gltf", ignoreCase = true)
            }

            return modelFile?.let { "$folderPath/$it" }
        }
        catch (ex: Exception) {
            Log.e(logTag, "Error on get model folder: $folderPath", ex)
            return null
        }
    }

    private fun assetFileExists(path: String): Boolean {
        try {
            requireContext().assets.open(path).close()
            return true
        }
        catch (ex: Exception) {
            Log.e(logTag, "Asset file not found: $path", ex)
            return false
        }
    }

    private fun showArScene() {
        arSceneStarted = true

        val rawPath = modelAssetPath
        val resolvedPath = resolveModelPath(rawPath)
        val imageTrackingEnabled = augmentedImageConfig.isEnabled
        Log.d(logTag, "showArScene raw=$rawPath resolved=$resolvedPath augmentedImage=$augmentedImageConfig")

        val container = rootContainer ?: return
        container.removeAllViews()

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val engine = rememberEngine()
                val modelLoader = rememberModelLoader(engine)
                val materialLoader = rememberMaterialLoader(engine)
                val viewNodeManager = rememberViewNodeManager()
                val rootView = LocalView.current

                data class Placed(
                    val anchor: Anchor,
                    val instanceKey: String,
                    val referenceImageName: String? = null,
                    val referenceImageExtentX: Float? = null,
                    val referenceImageExtentZ: Float? = null,
                    val referenceImagePosition: Position? = null,
                    val localPosition: Position = Position(0f, 0f, 0f),
                    val localRotation: Rotation = Rotation(x = 0f, y = 0f, z = 0f),
                    val scaleToUnits: Float = DaraAugmentedImageConfig.DEFAULT_MODEL_SCALE_TO_UNITS
                )
                data class ImageReference(
                    val anchor: Anchor,
                    val imageName: String,
                    val extentX: Float,
                    val extentZ: Float,
                    val position: Position
                )
                val placedKeys = remember { mutableStateListOf<Placed>() }
                val cachedInstances = remember { mutableMapOf<String, ModelInstance>() }
                var frame by remember { mutableStateOf<Frame?>(null) }
                var cameraPosition by remember { mutableStateOf(Position(0f, 0f, 0f)) }
                var imageReference by remember { mutableStateOf<ImageReference?>(null) }
                var markerCameraPosition by remember { mutableStateOf<FloatArray?>(null) }
                var cameraDistanceText by remember {
                    mutableStateOf(CameraPoseManager.formatDistanceMeters(null))
                }
                var debugLog by remember {
                    mutableStateOf(
                        "raw=$rawPath\nresolved=$resolvedPath\naugmentedImage=${augmentedImageConfig.imageAssetPath}"
                    )
                }
                var isAddMode by remember { mutableStateOf(true) }
                var controlMode by remember { mutableStateOf(ControlMode.GIZMO) }
                var showSettings by remember { mutableStateOf(false) }
                var isDraggingModel by remember { mutableStateOf(false) }
                var gizmoMode by remember { mutableStateOf(GizmoMode.TRANSLATION) }
                var activeGizmoAxis by remember { mutableStateOf<GizmoAxis?>(null) }
                var activeGizmoAction by remember { mutableStateOf<GizmoAction?>(null) }
                var lastGizmoTouchX by remember { mutableStateOf(0f) }
                var lastGizmoTouchY by remember { mutableStateOf(0f) }
                var sceneBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
                var capturedPreview by remember { mutableStateOf<Bitmap?>(null) }
                var isCameraTracking by remember { mutableStateOf(false) }
                var trackingWarningTitle by remember { mutableStateOf("Inicializando AR") }
                var trackingWarningMessage by remember {
                    mutableStateOf("Mova o celular lentamente para mapear o ambiente.")
                }
                val trackingStateHandler = remember {
                    TrackingStateHandler(
                        callback = object : TrackingStateHandler.Callback {
                            override fun onCameraTracking() {
                                isCameraTracking = true
                            }

                            override fun onCameraTrackingProblem(
                                trackingState: TrackingState,
                                failureReason: com.google.ar.core.TrackingFailureReason,
                                title: String,
                                message: String
                            ) {
                                isCameraTracking = false
                                trackingWarningTitle = title
                                trackingWarningMessage = message
                                debugLog = "Camera tracking=$trackingState\n reason=$failureReason\n $title\n $message"
                            }
                        },
                        mainHandler = mainHandler
                    )
                }

                // Bounding box material
                val yellowMaterial = remember(materialLoader) {
                    materialLoader.createUnlitColorInstance(Color.Yellow)
                }
                val markerCameraPositionText = CameraPoseManager.formatPositionMeters(markerCameraPosition)

                Box(Modifier.fillMaxSize()) {
                    ARSceneView(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                sceneBoundsInWindow = Rect(
                                    bounds.left.roundToInt(),
                                    bounds.top.roundToInt(),
                                    bounds.right.roundToInt(),
                                    bounds.bottom.roundToInt()
                                )
                            },
                        engine = engine,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        viewNodeWindowManager = viewNodeManager,
                        surfaceType = SurfaceType.TextureSurface,
                        planeRenderer = isAddMode,
                        planeRendererVersion = PlaneRendererBase.Version.V2,

                        sessionConfiguration = { session, config ->
                            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            config.depthMode = Config.DepthMode.DISABLED
                            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                            augmentedImageConfig
                                .createDatabase(requireContext(), session, logTag)
                                ?.let { database -> config.augmentedImageDatabase = database }
                        },
                        onSessionUpdated = onSessionUpdated@ { session, arFrame ->
                            frame = arFrame

                            // Gate AR processing until ARCore reports a valid camera track.
                            if (!trackingStateHandler.handle(arFrame.camera)) {
                                markerCameraPosition = null
                                val unknownDistanceText = CameraPoseManager.formatDistanceMeters(null)
                                if (cameraDistanceText != unknownDistanceText) {
                                    cameraDistanceText = unknownDistanceText
                                }
                                return@onSessionUpdated
                            }

                            val cameraPose = arFrame.camera.displayOrientedPose
                            val currentCameraPosition = Position(
                                x = cameraPose.tx(),
                                y = cameraPose.ty(),
                                z = cameraPose.tz()
                            )
                            cameraPosition = currentCameraPosition

                            val trackedImage = if (imageTrackingEnabled) {
                                session
                                    .getAllTrackables(AugmentedImage::class.java)
                                    .firstOrNull { image -> image.name == augmentedImageConfig.imageName }
                                    ?.takeIf { image ->
                                        image.trackingState == TrackingState.TRACKING
                                    }
                            }
                            else {
                                null
                            }

                            markerCameraPosition =
                                CameraPoseManager.relativeCameraPositionMeters(
                                    camera = arFrame.camera,
                                    originPose = trackedImage?.centerPose
                                )
                            val newCameraDistanceText = CameraPoseManager.formatDistanceMeters(markerCameraPosition)
                            if (cameraDistanceText != newCameraDistanceText) {
                                cameraDistanceText = newCameraDistanceText
                            }

                            if (imageTrackingEnabled) {
                                trackedImage
                                    ?.let { image ->
                                        val currentImagePosition = Position(
                                            x = image.centerPose.tx(),
                                            y = image.centerPose.ty(),
                                            z = image.centerPose.tz()
                                        )

                                        if (imageReference?.imageName != image.name) {
                                            imageReference?.anchor?.detach()
                                            imageReference = ImageReference(
                                                anchor = image.createAnchor(image.centerPose),
                                                imageName = image.name,
                                                extentX = image.extentX,
                                                extentZ = image.extentZ,
                                                position = currentImagePosition
                                            )
                                            debugLog = "Augmented image reference detected\n image=${image.name}\n waiting for Add surface tap"
                                            Log.d(logTag, "Augmented image reference detected image=${image.name}")
                                        }
                                        else {
                                            imageReference = imageReference?.copy(
                                                extentX = image.extentX,
                                                extentZ = image.extentZ,
                                                position = currentImagePosition
                                            )
                                        }

                                        val placedImageIndex = placedKeys.indexOfFirst { placed ->
                                            placed.referenceImageName == image.name
                                        }

                                        if (placedImageIndex >= 0) {
                                            val placed = placedKeys[placedImageIndex]
                                            placedKeys[placedImageIndex] = placed.copy(
                                                referenceImageExtentX = image.extentX,
                                                referenceImageExtentZ = image.extentZ,
                                                referenceImagePosition = currentImagePosition
                                            )
                                        }
                                    }
                            }
                        },
                        onTouchEvent = { event: MotionEvent, hitResult ->
                            // Edit Mode
                            if (!isAddMode) {
                                when (controlMode) {
                                    ControlMode.GIZMO -> {
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                val nodeName = hitResult
                                                    ?.nodeOrNull
                                                    ?.name
                                                    .orEmpty()

                                                activeGizmoAxis = when {
                                                    nodeName.startsWith("gizmo_x") -> GizmoAxis.X
                                                    nodeName.startsWith("gizmo_y") -> GizmoAxis.Y
                                                    nodeName.startsWith("gizmo_z") -> GizmoAxis.Z
                                                    else -> null
                                                }

                                                lastGizmoTouchX = event.x
                                                lastGizmoTouchY = event.y

                                                debugLog = "Gizmo down\n node=$nodeName\n axis=$activeGizmoAxis"

                                                return@ARSceneView true
                                            }

                                            MotionEvent.ACTION_MOVE -> {
                                                val axis = activeGizmoAxis

                                                if (axis != null && placedKeys.isNotEmpty()) {
                                                    val dx = event.x - lastGizmoTouchX
                                                    val dy = event.y - lastGizmoTouchY

                                                    lastGizmoTouchX = event.x
                                                    lastGizmoTouchY = event.y

                                                    val old = placedKeys[0]
                                                    val sensitivity = 0.0015f

                                                    val delta = when (axis) {
                                                        GizmoAxis.X -> Position(
                                                            x = dx * sensitivity,
                                                            y = 0f,
                                                            z = 0f
                                                        )

                                                        GizmoAxis.Y -> Position(
                                                            x = 0f,
                                                            y = -dy * sensitivity,
                                                            z = 0f
                                                        )

                                                        GizmoAxis.Z -> Position(
                                                            x = 0f,
                                                            y = 0f,
                                                            z = dy * sensitivity
                                                        )
                                                    }

                                                    val oldPos = old.localPosition
                                                    val newPos = Position(
                                                        x = oldPos.x + delta.x,
                                                        y = oldPos.y + delta.y,
                                                        z = oldPos.z + delta.z
                                                    )

                                                    placedKeys[0] = old.copy(
                                                        localPosition = newPos
                                                    )

                                                    debugLog = "Gizmo moving\n axis=$axis\n position=${newPos.x}, ${newPos.y}, ${newPos.z}"

                                                    return@ARSceneView true
                                                }

                                                return@ARSceneView true
                                            }

                                            MotionEvent.ACTION_UP,
                                            MotionEvent.ACTION_CANCEL -> {
                                                debugLog = "Gizmo finished\n axis=$activeGizmoAxis"
                                                activeGizmoAxis = null
                                                return@ARSceneView true
                                            }

                                            else -> {
                                                return@ARSceneView true
                                            }
                                        }
                                    }

                                    ControlMode.GESTURE -> {
                                        // One finger: drag, two fingers: rotate/scale
                                        if (event.pointerCount > 1) {
                                            return@ARSceneView false
                                        }

                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                isDraggingModel = hitResult != null
                                                lastGizmoTouchX = event.x
                                                lastGizmoTouchY = event.y

                                                if (isDraggingModel) {
                                                    debugLog = "Gesture drag started"
                                                    return@ARSceneView true
                                                }

                                                return@ARSceneView false
                                            }

                                            MotionEvent.ACTION_MOVE -> {
                                                if (!isDraggingModel) {
                                                    return@ARSceneView false
                                                }

                                                if (imageTrackingEnabled && placedKeys.isNotEmpty()) {
                                                    val dx = event.x - lastGizmoTouchX
                                                    val dy = event.y - lastGizmoTouchY
                                                    lastGizmoTouchX = event.x
                                                    lastGizmoTouchY = event.y

                                                    val old = placedKeys[0]
                                                    val sensitivity = 0.0015f
                                                    val oldPos = old.localPosition
                                                    val newLocalPosition = Position(
                                                        x = oldPos.x + (dx * sensitivity),
                                                        y = oldPos.y,
                                                        z = oldPos.z + (dy * sensitivity)
                                                    )

                                                    placedKeys[0] = old.copy(
                                                        localPosition = newLocalPosition
                                                    )

                                                    debugLog = "Dragging model on image\n position=${newLocalPosition.x}, ${newLocalPosition.y}, ${newLocalPosition.z}\n billboard=fixed-on-anchor"
                                                    return@ARSceneView true
                                                }

                                                val f = frame ?: run {
                                                    debugLog = "Drag move: frame=null"
                                                    return@ARSceneView true
                                                }

                                                val hits = f.hitTest(event.x, event.y)

                                                val planeHit = hits.firstOrNull { hr ->
                                                    val trackable = hr.trackable

                                                    trackable is Plane && trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING && trackable.isPoseInPolygon(hr.hitPose)
                                                }

                                                debugLog = "Drag move\n" + "hits=${hits.size}\n"

                                                if (planeHit != null && placedKeys.isNotEmpty()) {
                                                    try {
                                                        val old = placedKeys[0]
                                                        val newLocalPosition = worldHitToAnchorLocalPosition(
                                                            anchor = old.anchor,
                                                            hitPose = planeHit.hitPose
                                                        )

                                                        placedKeys[0] = old.copy(
                                                            localPosition = newLocalPosition
                                                        )

                                                        debugLog = "Dragging model\n position=${newLocalPosition.x}, ${newLocalPosition.y}, ${newLocalPosition.z}\n"
                                                        Log.d(logTag, "Dragging model localPosition=$newLocalPosition")
                                                    }
                                                    catch (ex: Exception) {
                                                        debugLog = "Error dragging model\n ${ex::class.java.simpleName}\n ${ex.message}"
                                                        Log.e(logTag, "Error dragging model", ex)
                                                    }
                                                }

                                                return@ARSceneView true
                                            }

                                            MotionEvent.ACTION_UP,
                                            MotionEvent.ACTION_CANCEL -> {
                                                if (isDraggingModel) {
                                                    isDraggingModel = false
                                                    debugLog = "Gesture drag finished"
                                                    return@ARSceneView true
                                                }

                                                return@ARSceneView false
                                            }

                                            else -> {
                                                return@ARSceneView false
                                            }
                                        }
                                    }
                                }
                            }

                            // Add mode
                            if (event.action == MotionEvent.ACTION_UP) {
                                val f = frame

                                when {
                                    f == null -> {
                                        debugLog = "No ARFrame"
                                        Log.w(logTag, "onTouchEvent: frame null")
                                    }

                                    resolvedPath == null -> {
                                        debugLog = "Model not found\n path=$rawPath\n"
                                        Log.e(logTag, "onTouchEvent: Model not found, path is null, path=$rawPath")
                                    }

                                    else -> {
                                        val hits = f.hitTest(event.x, event.y)

                                        Log.d(logTag, "hitTest: ${hits.size} hits at x=${event.x}, y=${event.y}")

                                        val hit = hits.firstOrNull { hitResult ->
                                            val trackable = hitResult.trackable
                                            val isValidPlane = trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)

                                            Log.d(logTag, "hit: trackable=${trackable::class.java.simpleName}, isValidPlane=$isValidPlane, distance=${hitResult.distance}")

                                            isValidPlane
                                        }

                                        if (hit == null) {
                                            debugLog = "No Plane hit\n hits=${hits.size}\n path=$resolvedPath"
                                            Log.w(logTag, "No plane found")
                                        }
                                        else {
                                            try {
                                                val imageRef = imageReference
                                                if (imageTrackingEnabled && imageRef == null) {
                                                    debugLog = "AR image not referenced yet\n scan marker before adding model"
                                                    Log.w(logTag, "Cannot add image-referenced model before AR image reference exists")
                                                    return@ARSceneView true
                                                }

                                                if (!imageTrackingEnabled) {
                                                    placedKeys.forEach { placed -> placed.anchor.detach() }
                                                }
                                                placedKeys.clear()
                                                cachedInstances.clear()

                                                val anchor = imageRef?.anchor ?: hit.createAnchor()
                                                val localPosition = imageRef?.let { reference ->
                                                    worldHitToAnchorLocalPosition(
                                                        anchor = reference.anchor,
                                                        hitPose = hit.hitPose
                                                    )
                                                } ?: Position(0f, 0f, 0f)
                                                val modelInstance = modelLoader.createModelInstance(resolvedPath)
                                                val key = "model_${System.nanoTime()}"

                                                cachedInstances[key] = modelInstance
                                                placedKeys.add(
                                                    Placed(
                                                        anchor = anchor,
                                                        instanceKey = key,
                                                        referenceImageName = imageRef?.imageName,
                                                        referenceImageExtentX = imageRef?.extentX,
                                                        referenceImageExtentZ = imageRef?.extentZ,
                                                        referenceImagePosition = imageRef?.position,
                                                        localPosition = localPosition,
                                                        scaleToUnits = if (imageTrackingEnabled)
                                                            augmentedImageConfig.modelScaleToUnits
                                                        else
                                                            DaraAugmentedImageConfig.DEFAULT_MODEL_SCALE_TO_UNITS
                                                    )
                                                )

                                                isAddMode = false
                                                debugLog = "Model added\n count=${placedKeys.size}\n path=$resolvedPath\n key=$key\n referencedBy=${imageRef?.imageName ?: "plane"}\n local=$localPosition"
                                                Log.d(logTag, "Model added key=$key path=$resolvedPath reference=${imageRef?.imageName ?: "plane"} total=${placedKeys.size}")
                                            }
                                            catch (ex: Exception) {
                                                debugLog = "Error on create model\n ${ex::class.java.simpleName}\n ${ex.message}\n path=$resolvedPath"
                                                Log.e(logTag, "Error on create model path=$resolvedPath", ex)
                                            }
                                        }
                                    }
                                }
                            }

                            false
                        }
                    ) {
                        val xGizmoMaterial = remember(materialLoader) {
                            materialLoader.createColorInstance(
                                color = Color(0xFFE53935), // red
                                metallic = 0f,
                                roughness = 0.45f
                            )
                        }

                        val yGizmoMaterial = remember(materialLoader) {
                            materialLoader.createColorInstance(
                                color = Color(0xFF43A047), // green
                                metallic = 0f,
                                roughness = 0.45f
                            )
                        }

                        val zGizmoMaterial = remember(materialLoader) {
                            materialLoader.createColorInstance(
                                color = Color(0xFF1E88E5), // blue
                                metallic = 0f,
                                roughness = 0.45f
                            )
                        }

                        if (imageTrackingEnabled) {
                            imageReference?.let { reference ->
                                AnchorNode(
                                    anchor = reference.anchor,
                                    onTrackingStateChanged = { state ->
                                        Log.d(logTag, "Image reference anchor tracking state=$state image=${reference.imageName}")
                                    }
                                ) {
                                    val billboardSpec = remember(
                                        reference.extentX,
                                        reference.extentZ
                                    ) {
                                        DaraBillboard.augmentedImageBillboardSpec(
                                            extentX = reference.extentX,
                                            extentZ = reference.extentZ
                                        )
                                    }
                                    val billboardTitle = augmentedImageConfig.displayName
                                    val billboardText = "$billboardTitle\n$cameraDistanceText"

                                    ViewNode(
                                        windowManager = viewNodeManager,
                                        unlit = true,
                                        position = DaraBillboard.AUGMENTED_IMAGE_OFFSET,
                                        rotation = Rotation(x = -90f),
                                        apply = {
                                            pxPerUnits = DaraBillboard.VIEW_NODE_PIXELS_PER_UNIT * billboardSpec.textureScale
                                            isTouchable = false
                                            isHittable = false
                                        }
                                    ) {
                                        DaraBillboard.BillboardContent(
                                            text = billboardText,
                                            fontSizeSp = billboardSpec.fontSizeSp,
                                            textColor = Color.White,
                                            backgroundColor = Color.Transparent,
                                            outlineColor = Color(0x96EBEBEB),
                                            outlineWidthDp = 1f,
                                            renderScale = billboardSpec.textureScale
                                        )
                                    }
                                }
                            }
                        }

                        placedKeys.forEach { placed ->
                            val instance: ModelInstance =
                                cachedInstances[placed.instanceKey] ?: return@forEach

                            AnchorNode(
                                anchor = placed.anchor,
                                onTrackingStateChanged = { state ->
                                    Log.d(logTag, "Anchor tracking state=$state key=${placed.instanceKey}")
                                }
                            ) {
                                val gestureControlEnabled = !isAddMode && controlMode == ControlMode.GESTURE

                                Node(
                                    position = placed.localPosition,
                                    rotation = placed.localRotation
                                ) {
                                    ModelNode(
                                        modelInstance = instance,
                                        scaleToUnits = placed.scaleToUnits,
                                        isEditable = gestureControlEnabled,

                                        apply = {
                                            isPositionEditable = false
                                            isRotationEditable = true
                                            isScaleEditable = true

                                            editableScaleRange = 0.1f..5.0f
                                            scaleGestureSensitivity = 1.0f

                                            isTouchable = true
                                            isHittable = true
                                        }
                                    )

                                    if (!imageTrackingEnabled) {
                                        ViewNode(
                                            windowManager = viewNodeManager,
                                            unlit = true,
                                            position = DaraBillboard.DEFAULT_OFFSET,
                                            apply = {
                                                isTouchable = false
                                                isHittable = false
                                            }
                                        ) {
                                            DaraBillboard.BillboardContent(
                                                text = DaraBillboard.labelTextBetween(
                                                    objectId = modelId,
                                                    from = cameraPosition,
                                                    to = Position(
                                                        x = placed.localPosition.x,
                                                        y = placed.localPosition.y,
                                                        z = placed.localPosition.z
                                                    )
                                                ),
                                                fontSizeSp = 18f,
                                                textColor = Color.White,
                                                backgroundColor = Color(0xCC111111)
                                            )
                                        }
                                    }

                                    Node(isVisible = !isAddMode) {
                                        val h = 0.26f

                                        val p000 = Position(-h, -h, -h)
                                        val p001 = Position(-h, -h, h)
                                        val p010 = Position(-h, h, -h)
                                        val p011 = Position(-h, h, h)

                                        val p100 = Position(h, -h, -h)
                                        val p101 = Position(h, -h, h)
                                        val p110 = Position(h, h, -h)
                                        val p111 = Position(h, h, h)

                                        LineNode(start = p000, end = p100, materialInstance = yellowMaterial)
                                        LineNode(start = p100, end = p101, materialInstance = yellowMaterial)
                                        LineNode(start = p101, end = p001, materialInstance = yellowMaterial)
                                        LineNode(start = p001, end = p000, materialInstance = yellowMaterial)

                                        LineNode(start = p010, end = p110, materialInstance = yellowMaterial)
                                        LineNode(start = p110, end = p111, materialInstance = yellowMaterial)
                                        LineNode(start = p111, end = p011, materialInstance = yellowMaterial)
                                        LineNode(start = p011, end = p010, materialInstance = yellowMaterial)

                                        LineNode(start = p000, end = p010, materialInstance = yellowMaterial)
                                        LineNode(start = p100, end = p110, materialInstance = yellowMaterial)
                                        LineNode(start = p101, end = p111, materialInstance = yellowMaterial)
                                        LineNode(start = p001, end = p011, materialInstance = yellowMaterial)

                                        if (controlMode == ControlMode.GIZMO) {
                                            val axisLength = 0.34f
                                            val shaftRadius = 0.012f
                                            val coneRadius = 0.04f
                                            val coneHeight = 0.09f

                                            val shaftCenter = axisLength / 2f
                                            val coneCenter = axisLength + coneHeight / 2f

                                            // X gizmo
                                            CylinderNode(
                                                radius = shaftRadius,
                                                height = axisLength,
                                                materialInstance = xGizmoMaterial,
                                                position = Position(shaftCenter, 0f, 0f),
                                                rotation = Rotation(z = -90f),
                                                apply = {
                                                    name = "gizmo_x"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )

                                            ConeNode(
                                                radius = coneRadius,
                                                height = coneHeight,
                                                materialInstance = xGizmoMaterial,
                                                position = Position(coneCenter, 0f, 0f),
                                                rotation = Rotation(z = -90f),
                                                apply = {
                                                    name = "gizmo_x_cone"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )

                                            // Y gizmo
                                            CylinderNode(
                                                radius = shaftRadius,
                                                height = axisLength,
                                                materialInstance = yGizmoMaterial,
                                                position = Position(0f, shaftCenter, 0f),
                                                apply = {
                                                    name = "gizmo_y"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )

                                            ConeNode(
                                                radius = coneRadius,
                                                height = coneHeight,
                                                materialInstance = yGizmoMaterial,
                                                position = Position(0f, coneCenter, 0f),
                                                apply = {
                                                    name = "gizmo_y_cone"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )

                                            // Z gizmo
                                            CylinderNode(
                                                radius = shaftRadius,
                                                height = axisLength,
                                                materialInstance = zGizmoMaterial,
                                                position = Position(0f, 0f, shaftCenter),
                                                rotation = Rotation(x = 90f),
                                                apply = {
                                                    name = "gizmo_z"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )

                                            ConeNode(
                                                radius = coneRadius,
                                                height = coneHeight,
                                                materialInstance = zGizmoMaterial,
                                                position = Position(0f, 0f, coneCenter),
                                                rotation = Rotation(x = 90f),
                                                apply = {
                                                    name = "gizmo_z_cone"
                                                    isTouchable = true
                                                    isHittable = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        onClick = {
                            showSettings = !showSettings
                        }
                    ) {
                        Text("⚙")
                    }

                    if (showSettings) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 64.dp)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Settings",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )

                                Text(
                                    text = "Control mode",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                )

                                Row {
                                    Button(
                                        onClick = {
                                            controlMode = ControlMode.GIZMO
                                            showSettings = false
                                            debugLog = "Control mode: GIZMO\n Gesture blocked\n"
                                        }
                                    ) {
                                        Text(
                                            if (controlMode == ControlMode.GIZMO)
                                                "✓ Gizmo"
                                            else
                                                "Gizmo"
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            controlMode = ControlMode.GESTURE
                                            showSettings = false
                                            debugLog = "Control mode: Gesture\n"
                                        }
                                    ) {
                                        Text(
                                            if (controlMode == ControlMode.GESTURE)
                                                "✓ Gesture"
                                            else
                                                "Gesture"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isAddMode && controlMode == ControlMode.GIZMO) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                        ) {
                            Button(
                                modifier = Modifier.size(48.dp),
                                onClick = {
                                    gizmoMode = GizmoMode.TRANSLATION
                                    debugLog = "Gizmo mode: Translation"
                                }
                            ) {
                                Text("T")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                modifier = Modifier.size(48.dp),
                                onClick = {
                                    gizmoMode = GizmoMode.ROTATION
                                    debugLog = "Gizmo mode: Rotation"
                                }
                            ) {
                                Text("R")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                modifier = Modifier.size(48.dp),
                                onClick = {
                                    gizmoMode = GizmoMode.SCALE
                                    debugLog = "Gizmo mode: Scale"
                                }
                            ) {
                                Text("S")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.90f))
                                .padding(12.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            text = buildString {
                                append("cameraPosition=$markerCameraPositionText")
                                //append("\ncameraDistance=$cameraDistanceText")
                            }
                        )

                        Text(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .background(Color.Black.copy(alpha = 0.80f))
                                .padding(12.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            text = buildString {
                                append("resolved=$resolvedPath")
                                //append("\nmodels=${placedKeys.size}")
                                append("\nmode=${if (isAddMode) "Add" else "Edit"}")
                                append("\nimageTracking=$imageTrackingEnabled")
                                append("\nimage=${augmentedImageConfig.imageAssetPath}")
                                append("\nplaneRenderer=$isAddMode")
                                append("\neditable=${!isAddMode && controlMode == ControlMode.GESTURE}")
                                append("\ncontrol=${controlMode.name}")
                                append("\ngizmoAxis=$activeGizmoAxis")
                                append("\nlocal=${placedKeys.firstOrNull()?.localPosition}")
                                append("\n$debugLog")
                            }
                        )
                    }

                    if (!isCameraTracking) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = trackingWarningTitle,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = trackingWarningMessage,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    capturedPreview?.let { preview ->
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    isAddMode = !isAddMode
                                    activeGizmoAxis = null
                                    activeGizmoAction = null
                                    isDraggingModel = false
                                    debugLog = "Mode changed: ${if (isAddMode) "Add" else "Edit"}"
                                }
                                catch (ex: Exception) {
                                    debugLog = "Error changing mode\n ${ex::class.java.simpleName}\n ${ex.message}"
                                    Log.e(logTag, "Error changing mode", ex)
                                }
                            }
                        ) {
                            Text(
                                if (isAddMode)
                                    "Add"
                                else
                                    "Edit"
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                captureScenePreview(
                                    sceneTextureView = rootView.findSceneTextureView(),
                                    sceneBounds = sceneBoundsInWindow,
                                    onCaptured = { bitmap ->
                                        capturedPreview = bitmap
                                        debugLog = "Photo captured\n ${bitmap.width}x${bitmap.height}"
                                    },
                                    onError = { message ->
                                        debugLog = message
                                        Log.w(logTag, message)
                                    }
                                )
                            }
                        ) {
                            Text("Foto")
                        }
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun captureScenePreview(
        sceneTextureView: TextureView?,
        sceneBounds: Rect?,
        onCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (sceneTextureView != null && !sceneTextureView.isAvailable) {
                onError("Scene texture not ready")
                return
            }

            val bounds = sceneBounds ?: run {
                onError("SceneView not ready")
                return
            }

            if (bounds.width() <= 0 || bounds.height() <= 0) {
                onError("SceneView bounds invalid")
                return
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                onError("Photo capture requires Android 8.0+")
                return
            }

            val activity = activity ?: run {
                onError("Activity not attached")
                return
            }
            val windowBounds = Rect(
                0,
                0,
                activity.window.decorView.width,
                activity.window.decorView.height
            )
            val copyBounds = Rect(bounds)
            if (!copyBounds.intersect(windowBounds) || copyBounds.width() <= 0 || copyBounds.height() <= 0) {
                onError("SceneView bounds outside window")
                return
            }

            val bitmap = Bitmap.createBitmap(copyBounds.width(), copyBounds.height(), Bitmap.Config.ARGB_8888)

            PixelCopy.request(
                activity.window,
                copyBounds,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        onCaptured(bitmap)
                    }
                    else {
                        onError("Photo capture failed: $result")
                    }
                },
                mainHandler
            )
        }
        catch (ex: Exception) {
            onError("Photo capture error: ${ex::class.java.simpleName} ${ex.message}")
            Log.e(logTag, "Photo capture error", ex)
        }
    }

    private fun View.findSceneTextureView(): TextureView? {
        if (this is TextureView) {
            return this
        }

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findSceneTextureView()?.let { return it }
            }
        }

        return null
    }

    private fun worldHitToAnchorLocalPosition(
        anchor: Anchor,
        hitPose: com.google.ar.core.Pose
    ): Position {
        val localPose = anchor.pose
            .inverse()
            .compose(hitPose)

        return Position(
            x = localPose.tx(),
            y = 0f,
            z = localPose.tz()
        )
    }
    private fun showStatus(msg: String) {
        showMessageView(msg, logAsError = false)
    }

    private fun showArUnavailable(msg: String) {
        showMessageView(msg, logAsError = true)
    }

    private fun showPermissionDenied() = showArUnavailable("Camera permission denied")

    private fun showMessageView(msg: String, logAsError: Boolean) {
        if (logAsError) Log.e(logTag, msg) else Log.d(logTag, msg)

        val container = rootContainer ?: return
        container.removeAllViews()
        container.addView(
            TextView(requireContext()).apply {
                text = msg
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    interface Callback {
        fun onDaraArObjectClicked(objectId: String)
    }

    private fun returnObjectClicked(objectId: String) {
        val callback = parentFragment as? Callback ?: activity as? Callback

        if (callback != null) {
            callback.onDaraArObjectClicked(objectId)
            return
        }

        requireActivity().setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(DaraArContract.RESULT_EVENT, DaraArContract.EVENT_OBJECT_CLICKED)
            putExtra(DaraArContract.RESULT_OBJECT_ID, objectId)
        })
        requireActivity().finish()
    }

    companion object {
        @JvmStatic
        fun newInstance(
            modelId: String? = null,
            modelAssetPath: String? = null,
            augmentedImageAssetPath: String? = null,
            augmentedImageWidthMeters: Float? = null,
            modelOffsetXMeters: Float = 0f,
            modelOffsetYMeters: Float = 0f,
            modelOffsetZMeters: Float = 0f,
            modelScaleToUnits: Float = DaraAugmentedImageConfig.DEFAULT_MODEL_SCALE_TO_UNITS
        ): DaraArRenderFragment {
            return DaraArRenderFragment().apply {
                arguments = Bundle().apply {
                    modelId?.let { putString(DaraArContract.EXTRA_MODEL_ID, it) }
                    modelAssetPath?.let { putString(DaraArContract.EXTRA_MODEL_ASSET_PATH, it) }
                    augmentedImageAssetPath?.let {
                        putString(DaraArContract.EXTRA_AUGMENTED_IMAGE_ASSET_PATH, it)
                    }
                    augmentedImageWidthMeters?.let {
                        putFloat(DaraArContract.EXTRA_AUGMENTED_IMAGE_WIDTH_METERS, it)
                    }
                    putFloat(DaraArContract.EXTRA_MODEL_OFFSET_X_METERS, modelOffsetXMeters)
                    putFloat(DaraArContract.EXTRA_MODEL_OFFSET_Y_METERS, modelOffsetYMeters)
                    putFloat(DaraArContract.EXTRA_MODEL_OFFSET_Z_METERS, modelOffsetZMeters)
                    putFloat(DaraArContract.EXTRA_MODEL_SCALE_TO_UNITS, modelScaleToUnits)
                }
            }
        }
    }
}
