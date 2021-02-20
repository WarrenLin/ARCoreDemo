package com.cmoney.arapplication.view

import android.util.Log
import com.cmoney.arapplication.AugmentImageActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment


class CustomArFragment: ArFragment() {

    override fun getSessionConfiguration(session: Session): Config? {
        planeDiscoveryController.setInstructionView(null)
        val config = Config(session)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        session.configure(config)
        arSceneView.setupSession(session)

        if ((activity as AugmentImageActivity?)!!.setupAugmentedImagesDb(config, session)) {
            Log.d("SetupAugImgDb", "Success")
        } else {
            Log.e("SetupAugImgDb", "Faliure setting up db")
        }

        return config
    }
}