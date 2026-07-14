package com.example.dara.arbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity(), DaraArRenderFragment.Callback {
//test update changes
    companion object {
        private const val REQUEST_3D = 2001
        private const val AR_FRAGMENT_BACK_STACK_NAME = "DaraArRenderFragment"
    }

    private lateinit var menuLayout: LinearLayout
    private lateinit var fragmentContainer: FrameLayout
    private var fragmentContainerId: Int = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fragmentContainerId = View.generateViewId()

        val open3dButton = Button(this).apply {
            text = "3D Render"

            setOnClickListener {
                val intent = Intent(
                    this@MainActivity,
                    Dara3dRenderActivity::class.java
                ).apply {
                    putExtra(DaraArContract.EXTRA_MODEL_ID, "lixo-123")
                    putExtra(DaraArContract.EXTRA_MODEL_ASSET_PATH, "lixo.glb")
                    putExtra(DaraArContract.EXTRA_MODE, "view")
                }

                startActivityForResult(intent, REQUEST_3D)
            }
        }

        val openArButton = Button(this).apply {
            text = "AR Render"

            setOnClickListener {
                openArRenderFragment(
                    modelId = "lixo-123",
                    modelAssetPath = "lixo.glb",
                    augmentedImageAssetPath = "markers/earth2.jpg",
                    augmentedImageWidthMeters = 0.20f,
                    modelOffsetXMeters = 0f,
                    modelOffsetYMeters = 0.05f,
                    modelOffsetZMeters = 0f,
                    modelScaleToUnits = 0.25f
                )
            }
        }

        menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)

            addView(open3dButton)
            addView(openArButton)
        }

        fragmentContainer = FrameLayout(this).apply {
            id = fragmentContainerId
            visibility = View.GONE
        }

        val rootLayout = FrameLayout(this).apply {
            addView(
                menuLayout,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            addView(
                fragmentContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        setContentView(rootLayout)
        configureBackButton()
    }

    private fun openArRenderFragment(
        modelId: String,
        modelAssetPath: String?,
        augmentedImageAssetPath: String? = null,
        augmentedImageWidthMeters: Float? = null,
        modelOffsetXMeters: Float = 0f,
        modelOffsetYMeters: Float = 0f,
        modelOffsetZMeters: Float = 0f,
        modelScaleToUnits: Float = DaraAugmentedImageConfig.DEFAULT_MODEL_SCALE_TO_UNITS,
        markerDaraPositionXMeters: Float = 0f,
        markerDaraPositionYMeters: Float = 0f,
        markerDaraPositionZMeters: Float = 0f,
        markerDaraRotationQx: Float = 0f,
        markerDaraRotationQy: Float = 0f,
        markerDaraRotationQz: Float = 0f,
        markerDaraRotationQw: Float = 1f
    ) {
        val currentFragment = supportFragmentManager.findFragmentById(fragmentContainerId)
        if (currentFragment is DaraArRenderFragment) {
            return
        }

        menuLayout.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(
                fragmentContainerId,
                DaraArRenderFragment.newInstance(
                    modelId = modelId,
                    modelAssetPath = modelAssetPath,
                    augmentedImageAssetPath = augmentedImageAssetPath,
                    augmentedImageWidthMeters = augmentedImageWidthMeters,
                    modelOffsetXMeters = modelOffsetXMeters,
                    modelOffsetYMeters = modelOffsetYMeters,
                    modelOffsetZMeters = modelOffsetZMeters,
                    modelScaleToUnits = modelScaleToUnits,
                    markerDaraPositionXMeters = markerDaraPositionXMeters,
                    markerDaraPositionYMeters = markerDaraPositionYMeters,
                    markerDaraPositionZMeters = markerDaraPositionZMeters,
                    markerDaraRotationQx = markerDaraRotationQx,
                    markerDaraRotationQy = markerDaraRotationQy,
                    markerDaraRotationQz = markerDaraRotationQz,
                    markerDaraRotationQw = markerDaraRotationQw
                )
            )
            .addToBackStack(AR_FRAGMENT_BACK_STACK_NAME)
            .commit()
    }

    private fun closeArRenderFragment() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }

        fragmentContainer.visibility = View.GONE
        menuLayout.visibility = View.VISIBLE
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        closeArRenderFragment()
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    override fun onDaraArObjectClicked(objectId: String) {
        Toast.makeText(
            this,
            "source=AR\nevent=${DaraArContract.EVENT_OBJECT_CLICKED}\nobjectId=$objectId",
            Toast.LENGTH_LONG
        ).show()

        closeArRenderFragment()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_3D) {
            return
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(
                this,
                "exit 3D",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val event = data?.getStringExtra(DaraArContract.RESULT_EVENT)
        val objectId = data?.getStringExtra(DaraArContract.RESULT_OBJECT_ID)

        Toast.makeText(
            this,
            "source=3D\nevent=$event\nobjectId=$objectId",
            Toast.LENGTH_LONG
        ).show()
    }
}
