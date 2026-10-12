package com.cse.dashnav.ui.map

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cse.dashnav.core.TrackPoint
import org.osmdroid.api.IGeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme

/** A MapView tied to the current lifecycle. Callers add overlays from effects. */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(14.0)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    return mapView
}

@Composable
fun MapBox(mapView: MapView, modifier: Modifier = Modifier) {
    // osmdroid draws tiles past its own bounds unless clipped.
    AndroidView(factory = { mapView }, modifier = modifier.clipToBounds())
}

fun TrackPoint.toGeoPoint() = GeoPoint(lat, lon)

fun MapView.routeLine(points: List<GeoPoint>, color: Color, widthPx: Float = 10f): Polyline =
    Polyline(this).apply {
        setPoints(points)
        outlinePaint.color = color.toArgb()
        outlinePaint.strokeWidth = widthPx
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        infoWindow = null
    }

fun dotsOverlay(points: List<GeoPoint>, color: Color, radiusPx: Float = 6f): SimpleFastPointOverlay {
    val paint = Paint().apply {
        style = Paint.Style.FILL
        this.color = color.toArgb()
        isAntiAlias = true
    }
    val options = SimpleFastPointOverlayOptions.getDefaultStyle()
        .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
        .setRadius(radiusPx)
        .setPointStyle(paint)
        .setIsClickable(false)
    return SimpleFastPointOverlay(SimplePointTheme(points.map<GeoPoint, IGeoPoint> { it }), options)
}

/** Zooms to show all [points], waiting for the first layout if the view hasn't been measured yet. */
fun MapView.fitTo(points: List<GeoPoint>) {
    if (points.isEmpty()) return
    val apply = {
        val box = BoundingBox.fromGeoPointsSafe(points)
        if (points.size == 1 || box.latitudeSpan < 1e-5 && box.longitudeSpanWithDateLine < 1e-5) {
            controller.setZoom(17.0)
            controller.setCenter(points.first())
        } else {
            zoomToBoundingBox(box.increaseByScale(1.25f), false)
        }
    }
    if (width > 0 && height > 0) apply() else addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}
