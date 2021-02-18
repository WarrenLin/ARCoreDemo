package com.cmoney.arapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    /**
     * 使用 Intent googlequicksearchbox 打開
     */
    fun onIntentQuickSearchClick(view: View) {
        val sceneViewerIntent = Intent(Intent.ACTION_VIEW)
        sceneViewerIntent.data =
            Uri.parse("https://arvr.google.com/scene-viewer/1.0?file=https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Avocado/glTF/Avocado.gltf")
        sceneViewerIntent.setPackage("com.google.android.googlequicksearchbox")
        startActivity(sceneViewerIntent)
    }

    /**
     * 使用 Intent google ar core 打開
     */
    fun onIntentARCoreClick(view: View) {
        val sceneViewerIntent = Intent(Intent.ACTION_VIEW)
        val intentUri = Uri.parse("https://arvr.google.com/scene-viewer/1.0").buildUpon()
            .appendQueryParameter(
                "file",
                "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Avocado/glTF/Avocado.gltf"
            )
            .appendQueryParameter("mode", "ar_only")
            .build()
        sceneViewerIntent.data = intentUri
        sceneViewerIntent.setPackage("com.google.ar.core")
        startActivity(sceneViewerIntent)
    }

    fun onRenderNetworkModel(view: View) {
        startActivity(Intent(this, RenderNetworkActivity::class.java))
    }

    fun onRenderLocalModel(view: View) {
        startActivity(Intent(this, RenderLocalActivity::class.java))
    }

    fun onLocalSceneClick(view: View) {
        startActivity(Intent(this, LocalSceneActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}