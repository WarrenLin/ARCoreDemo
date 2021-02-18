package com.cmoney.arapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_local_scene.*
import kotlinx.android.synthetic.main.location_layout_renderable.view.*
import uk.co.appoly.arcorelocation.LocationMarker
import uk.co.appoly.arcorelocation.LocationScene
import java.util.concurrent.CompletableFuture

class LocalSceneActivity : AppCompatActivity() {
    private var locationScene: LocationScene? = null

    private var arSceneView: ArSceneView? = null

    private var arHandler = Handler(Looper.getMainLooper())

    private var venuesSet: MutableSet<VenuesModel.VenuesData> = mutableSetOf()

    private var areAllMarkersLoaded = false

    private var arCoreInstallRequested = false

    private val resumeArElementsTask = Runnable {
        locationScene?.resume()
        arSceneView?.resume()
    }

    private val INVALID_MARKER_SCALE_MODIFIER = -1F
    private val INITIAL_MARKER_SCALE_MODIFIER = 0.5f

    private val RENDER_MARKER_MIN_DISTANCE = 2//meters
    private val RENDER_MARKER_MAX_DISTANCE = 7000//meters

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_scene)
        arSceneView = findViewById(R.id.arSceneView)

        setupSession()

        renderVenues()
    }

    private fun setupSession() {
        if (arSceneView == null) {
            return
        }

        if (arSceneView?.session == null) {
            try {
                val session = setupSession(this, arCoreInstallRequested)
                if (session == null) {
                    arCoreInstallRequested = true
                    return
                } else {
                    arSceneView!!.setupSession(session)
                }
            } catch (e: UnavailableException) {
                e.printStackTrace()
            }
        }

        if (locationScene == null) {
            locationScene = LocationScene(this, arSceneView)
            locationScene!!.setMinimalRefreshing(true)
            locationScene!!.setOffsetOverlapping(true)
//            locationScene!!.setRemoveOverlapping(true)
            locationScene!!.anchorRefreshInterval = 2000
        }

        try {
            resumeArElementsTask.run()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Unable to get camera", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val venues = getVenuesModel()
        venuesSet.addAll(venues.data)
    }

    @Throws(UnavailableException::class)
    fun setupSession(activity: Activity, installRequested: Boolean): Session? {
        when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                return null
            }
            ArCoreApk.InstallStatus.INSTALLED -> {
                //just continue with session setup
            }
            else -> {
                //just continue with session setup
            }
        }

        val session = Session(activity)
        val config = Config(session)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        // IMPORTANT!!!  ArSceneView requires the `LATEST_CAMERA_IMAGE` non-blocking update mode.

        session.configure(config)
        return session
    }

    override fun onResume() {
        super.onResume()
        setupSession()
    }

    override fun onPause() {
        super.onPause()
        arSceneView?.session?.let {
            locationScene?.pause()
            arSceneView?.pause()
        }
    }

    private fun renderVenues() {
        setupAndRenderVenuesMarkers()
        updateVenuesMarkers()
    }

    private fun updateVenuesMarkers() {
        arSceneView?.scene?.addOnUpdateListener()
        {
            if (!areAllMarkersLoaded) {
                return@addOnUpdateListener
            }

            locationScene?.mLocationMarkers?.forEach { locationMarker ->
                locationMarker.height =
                    generateRandomHeightBasedOnDistance(
                        locationMarker?.anchorNode?.distance ?: 0
                    )
            }

            val frame = arSceneView!!.arFrame ?: return@addOnUpdateListener
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@addOnUpdateListener
            }
            locationScene!!.processFrame(frame)
        }
    }

    private fun setupAndRenderVenuesMarkers() {
        venuesSet.forEach { venue ->
            val completableFutureViewRenderable = ViewRenderable.builder()
                .setView(this, R.layout.location_layout_renderable)
                .build()

            CompletableFuture.anyOf(completableFutureViewRenderable)
                .handle { t, throwable ->
                    //here we know the renderable was built or not
                    when {
                        throwable != null -> {
                            // handle renderable load fail
                            throwable.printStackTrace()
                            return@handle null
                        }
                        else -> {
                            try {
                                val venueMarker = LocationMarker(
                                    venue.lng,
                                    venue.lat,
                                    setVenueNode(venue, completableFutureViewRenderable)
                                )
                                arHandler.postDelayed({
                                    attachMarkerToScene(
                                        venueMarker,
                                        completableFutureViewRenderable.get().view
                                    )
                                    if (venuesSet.indexOf(venue) == venuesSet.size - 1) {
                                        areAllMarkersLoaded = true
                                    }
                                }, 200)

                            } catch (ex: Exception) {
                                // handle exception
                                ex.printStackTrace()
                            }
                            null
                        }
                    }
                }

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setVenueNode(
        venue: VenuesModel.VenuesData,
        completableFuture: CompletableFuture<ViewRenderable>
    ): Node {
        val node = Node()
        node.renderable = completableFuture.get()

        val nodeLayout = completableFuture.get().view
        val venueName = nodeLayout.name
        val markerLayoutContainer = nodeLayout.pinContainer
        venueName.text = venue.title
        markerLayoutContainer.visibility = View.GONE
        nodeLayout.setOnTouchListener { _, _ ->
            Toast.makeText(this, venue.title, Toast.LENGTH_SHORT).show()
            false
        }

        return node
    }

    private fun attachMarkerToScene(
        locationMarker: LocationMarker,
        layoutRendarable: View
    ) {
        resumeArElementsTask.run {
            locationMarker.scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN
            locationMarker.scaleModifier = INITIAL_MARKER_SCALE_MODIFIER

            locationScene?.mLocationMarkers?.add(locationMarker)
            locationMarker.anchorNode?.isEnabled = true

            arHandler.post {
                locationScene?.refreshAnchors()
                layoutRendarable.pinContainer.visibility = View.VISIBLE
            }
        }
        locationMarker.setRenderEvent { locationNode ->
            layoutRendarable.distance.text = showDistance(locationNode.distance)
            resumeArElementsTask.run {
                computeNewScaleModifierBasedOnDistance(locationMarker, locationNode.distance)
            }
        }
    }

    private fun computeNewScaleModifierBasedOnDistance(
        locationMarker: LocationMarker,
        distance: Int
    ) {
        val scaleModifier = getScaleModifierBasedOnRealDistance(distance)
        return if (scaleModifier == INVALID_MARKER_SCALE_MODIFIER) {
            detachMarker(locationMarker)
        } else {
            locationMarker.scaleModifier = scaleModifier
        }
    }

    private fun detachMarker(locationMarker: LocationMarker) {
        locationMarker.anchorNode?.anchor?.detach()
        locationMarker.anchorNode?.isEnabled = false
        locationMarker.anchorNode = null
    }

    fun generateRandomHeightBasedOnDistance(distance: Int): Float {
        return when (distance) {
            in 0..1000 -> (1..3).random().toFloat()
            in 1001..1500 -> (4..6).random().toFloat()
            in 1501..2000 -> (7..9).random().toFloat()
            in 2001..3000 -> (10..12).random().toFloat()
            in 3001..RENDER_MARKER_MAX_DISTANCE -> (12..13).random().toFloat()
            else -> 0f
        }
    }

    fun getScaleModifierBasedOnRealDistance(distance: Int): Float {
        return when (distance) {
            in Integer.MIN_VALUE..RENDER_MARKER_MIN_DISTANCE -> INVALID_MARKER_SCALE_MODIFIER
            in RENDER_MARKER_MIN_DISTANCE + 1..20 -> 0.8f
            in 21..40 -> 0.75f
            in 41..60 -> 0.7f
            in 61..80 -> 0.65f
            in 81..100 -> 0.6f
            in 101..1000 -> 0.5f
            in 1001..1500 -> 0.45f
            in 1501..2000 -> 0.4f
            in 2001..2500 -> 0.35f
            in 2501..3000 -> 0.3f
            in 3001..RENDER_MARKER_MAX_DISTANCE -> 0.25f
            in RENDER_MARKER_MAX_DISTANCE + 1..Integer.MAX_VALUE -> 0.15f
            else -> -1f
        }
    }

    fun showDistance(distance: Int): String {
        return if (distance >= 1000)
            String.format("%.2f", (distance.toDouble() / 1000)) + " km"
        else
            "$distance m"
    }

    data class VenuesModel(
        val data: List<VenuesData>
    ) {
        data class VenuesData(
            val id: String,
            val lat: Double,
            val lng: Double,
            val title: String,
            val sourceUrl: String,
            val avgPrice: Int,
            val source: String
        )
    }

    private fun getVenuesModel(): VenuesModel {
        val sourceString = "{\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef8c\",\n" +
                "      \"lat\": 25.0206384751,\n" +
                "      \"lng\": 121.467145119,\n" +
                "      \"title\": \"田明文化金融大樓\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL4e21165013120\",\n" +
                "      \"avgPrice\": 537000,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef97\",\n" +
                "      \"lat\": 25.020788861,\n" +
                "      \"lng\": 121.4663352798,\n" +
                "      \"title\": \"文化京華\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL57a2227919be0\",\n" +
                "      \"avgPrice\": 495600,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aefa9\",\n" +
                "      \"lat\": 25.0203318083,\n" +
                "      \"lng\": 121.4658319386,\n" +
                "      \"title\": \"心站地帶\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLda63876239799\",\n" +
                "      \"avgPrice\": 491900,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef99\",\n" +
                "      \"lat\": 25.0214864616,\n" +
                "      \"lng\": 121.4659792337,\n" +
                "      \"title\": \"陽明大廈\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL0f59848079a89\",\n" +
                "      \"avgPrice\": 376000,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef9f\",\n" +
                "      \"lat\": 25.0192821307,\n" +
                "      \"lng\": 121.4668067804,\n" +
                "      \"title\": \"上品匯\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL0ad98039676f5\",\n" +
                "      \"avgPrice\": 559000,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef98\",\n" +
                "      \"lat\": 25.0220391,\n" +
                "      \"lng\": 121.4658141,\n" +
                "      \"title\": \"捷運陽明\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLa7f18752acb1d\",\n" +
                "      \"avgPrice\": 615200,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef5e\",\n" +
                "      \"lat\": 25.0214714119,\n" +
                "      \"lng\": 121.4685718049,\n" +
                "      \"title\": \"捷和藍京\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL9cb45682e9377\",\n" +
                "      \"avgPrice\": 545900,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aefbb\",\n" +
                "      \"lat\": 25.0197917182,\n" +
                "      \"lng\": 121.4652581963,\n" +
                "      \"title\": \"健華新城\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLe0d118297c1df\",\n" +
                "      \"avgPrice\": 413200,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef95\",\n" +
                "      \"lat\": 25.0189401557,\n" +
                "      \"lng\": 121.4675762767,\n" +
                "      \"title\": \"三輝歌劇苑\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLfdd101209a3379\",\n" +
                "      \"avgPrice\": 610000,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef5b\",\n" +
                "      \"lat\": 25.02064821,\n" +
                "      \"lng\": 121.4691897442,\n" +
                "      \"title\": \"新板PLUS+\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL5bb105002015ba\",\n" +
                "      \"avgPrice\": 635800,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef4b\",\n" +
                "      \"lat\": 25.0216298962,\n" +
                "      \"lng\": 121.4693576325,\n" +
                "      \"title\": \"民生大道\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLa8211579b74af\",\n" +
                "      \"avgPrice\": 483600,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef4d\",\n" +
                "      \"lat\": 25.0224876069,\n" +
                "      \"lng\": 121.4688517884,\n" +
                "      \"title\": \"國王大廈\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL11e78962a19ad\",\n" +
                "      \"avgPrice\": 473200,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef4a\",\n" +
                "      \"lat\": 25.0212473512,\n" +
                "      \"lng\": 121.4696453914,\n" +
                "      \"title\": \"民生千囍\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dL53f11702b5cd5\",\n" +
                "      \"avgPrice\": 538900,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef49\",\n" +
                "      \"lat\": 25.0210337269,\n" +
                "      \"lng\": 121.469875046,\n" +
                "      \"title\": \"康和新賞\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLb3c1159869403\",\n" +
                "      \"avgPrice\": 592800,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a8fff783e3f29707aef4c\",\n" +
                "      \"lat\": 25.0207946099,\n" +
                "      \"lng\": 121.4699259,\n" +
                "      \"title\": \"早安公園\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLcf611681c32fa\",\n" +
                "      \"avgPrice\": 479500,\n" +
                "      \"source\": \"leju\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"600a9000783e3f29707afd9e\",\n" +
                "      \"lat\": 25.0189979698,\n" +
                "      \"lng\": 121.4699375841,\n" +
                "      \"title\": \"潤泰鼎峯\",\n" +
                "      \"sourceUrl\": \"https://www.leju.com.tw/page_search_result?oid\\u003dLc3b113194ee838\",\n" +
                "      \"source\": \"leju\"\n" +
                "    }\n" +
                "  ]\n" +
                "} "

        return Gson().fromJson(sourceString, VenuesModel::class.java)
    }
}