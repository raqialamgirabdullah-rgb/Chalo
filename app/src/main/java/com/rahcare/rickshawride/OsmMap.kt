package com.rahcare.rickshawride

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

val DEFAULT_CENTER = Pt(23.8103, 90.4125) // ঢাকা

class MapDot(val pt: Pt, val color: Int)

/** মানচিত্রের বর্তমান কেন্দ্র (পিনের জায়গা) জানার জন্য। */
class MapHolder {
    var view: MapView? = null
    fun center(): Pt? {
        val c = view?.mapCenter ?: return null
        return Pt(c.latitude, c.longitude)
    }
}

/** মানচিত্র কোথায় নিয়ে যাবে। id বদলালেই নতুন করে সরে। */
class Focus(val pt: Pt, val zoom: Double?, val id: Int)

object MapColors {
    val ME: Int = Color.parseColor("#1565C0")
    val PICKUP: Int = Color.parseColor("#1B7F4B")
    val DEST: Int = Color.parseColor("#C62828")
    val DRIVER: Int = Color.parseColor("#E08A00")
}

private fun dot(ctx: Context, color: Int): Drawable {
    val size = (18 * ctx.resources.displayMetrics.density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((3 * ctx.resources.displayMetrics.density).toInt(), Color.WHITE)
        setSize(size, size)
    }
}

@Composable
fun OsmMap(
    modifier: Modifier,
    holder: MapHolder,
    markers: List<MapDot>,
    line: Pair<Pt, Pt>?,
    focus: Focus?,
    allowTapMove: Boolean,
    initial: Pt,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tapEnabled = rememberUpdatedState(allowTapMove)
    val lastFocus = remember { intArrayOf(-1) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.view?.onDetach()
            holder.view = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            val mv = MapView(c)
            mv.setTileSource(TileSourceFactory.MAPNIK)
            mv.setMultiTouchControls(true)
            mv.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            mv.controller.setZoom(16.0)
            mv.controller.setCenter(GeoPoint(initial.lat, initial.lng))
            // মানচিত্রে ট্যাপ করলে ওই জায়গা পিনের নিচে চলে আসে।
            mv.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null && tapEnabled.value) {
                            mv.controller.animateTo(p)
                            return true
                        }
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }),
            )
            mv.onResume()
            holder.view = mv
            mv
        },
        update = { view ->
            if (focus != null && lastFocus[0] != focus.id) {
                lastFocus[0] = focus.id
                val z = focus.zoom
                if (z != null) view.controller.setZoom(z)
                view.controller.animateTo(GeoPoint(focus.pt.lat, focus.pt.lng))
            }

            val old = view.overlays.filter { it is Marker || it is Polyline }
            for (o in old) view.overlays.remove(o)

            if (line != null) {
                val pl = Polyline()
                pl.setPoints(
                    listOf(
                        GeoPoint(line.first.lat, line.first.lng),
                        GeoPoint(line.second.lat, line.second.lng),
                    ),
                )
                pl.outlinePaint.color = Color.parseColor("#1B7F4B")
                pl.outlinePaint.strokeWidth = 8f
                view.overlays.add(pl)
            }
            for (m in markers) {
                val mk = Marker(view)
                mk.position = GeoPoint(m.pt.lat, m.pt.lng)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mk.icon = dot(ctx, m.color)
                mk.setOnMarkerClickListener { _, _ -> true }
                view.overlays.add(mk)
            }
            view.invalidate()
        },
    )
}
