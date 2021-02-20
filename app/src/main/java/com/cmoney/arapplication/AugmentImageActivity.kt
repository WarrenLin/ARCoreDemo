package com.cmoney.arapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.cmoney.arapplication.view.CustomArFragment
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.io.IOException
import java.util.function.Consumer
import java.util.function.Function


class AugmentImageActivity : AppCompatActivity() {
    private var arFragment: ArFragment? = null
    var shouldAddModel = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_augment_image)
        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as CustomArFragment
        arFragment!!.arSceneView.scene.addOnUpdateListener(this::onUpdateFrame)

        arFragment!!.planeDiscoveryController.hide()
        arFragment!!.planeDiscoveryController.setInstructionView(null)
        arFragment!!.arSceneView.planeRenderer.isEnabled = false
    }

    fun setupAugmentedImagesDb(config: Config, session: Session?): Boolean {
        val bitmap: Bitmap = loadAugmentedImage() ?: return false
        val augmentedImageDatabase = AugmentedImageDatabase(session)
        augmentedImageDatabase.addImage("logo", bitmap)
        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    private fun loadAugmentedImage(): Bitmap? {
        try {
            assets.open("logo.png").use { input -> return BitmapFactory.decodeStream(input) }
        } catch (e: IOException) {
            Log.e("ImageLoad", "IO Exception", e)
        }
        return null
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun onUpdateFrame(frameTime: FrameTime) {
        val frame: Frame = arFragment!!.arSceneView.arFrame!!
        val augmentedImages: Collection<AugmentedImage> = frame.getUpdatedTrackables(
            AugmentedImage::class.java
        )
        for (augmentedImage in augmentedImages) {
            if (augmentedImage.trackingState == TrackingState.TRACKING) {
                if (augmentedImage.name == "logo" && shouldAddModel) {
                    placeObject(
                        arFragment!!,
                        augmentedImage.createAnchor(augmentedImage.centerPose),
                        Uri.parse("android.resource://" + packageName + "/raw/" + R.raw.model)
                    )
                    shouldAddModel = false
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun placeObject(arFragment: ArFragment, anchor: Anchor, uri: Uri) {
        ModelRenderable.builder()
            .setSource(arFragment.context, uri)
//            .setSource(
//                arFragment.context, RenderableSource.builder().setSource(
//                    arFragment.context,
//                    uri,
//                    RenderableSource.SourceType.GLB
//                ).setScale(0.5f).build()
//            )
            .build()
            .thenAccept(Consumer { modelRenderable: ModelRenderable? ->
                addNodeToScene(
                    arFragment,
                    anchor,
                    modelRenderable!!
                )
            })
            .exceptionally(
                Function<Throwable, Void?> { throwable: Throwable ->
                    Toast.makeText(
                        arFragment.context,
                        "Error:" + throwable.message,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    null
                }
            )
    }

    private fun addNodeToScene(arFragment: ArFragment, anchor: Anchor, renderable: Renderable) {
        val anchorNode = AnchorNode(anchor)
        val node = TransformableNode(arFragment.transformationSystem)
        node.renderable = renderable
        node.setParent(anchorNode)
        arFragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }
}