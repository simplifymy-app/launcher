package app.simplifymy.launcher.core.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onDragMove: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onDragEnd: ((cancelled: Boolean) -> Unit)? = null
    var editable: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val longPress = Runnable {
        dragging = true

        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).also { cancel ->
            for (i in 0 until childCount) getChildAt(i).dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        onLongPress?.invoke(lastX, lastY)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return dragging || super.onTouchEvent(event)
    }

    private fun track(event: MotionEvent) {
        lastX = event.rawX
        lastY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; dragging = false
                if (editable) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) onDragMove?.invoke(event.rawX, event.rawY)
                else if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) handler.removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (dragging) onDragEnd?.invoke(event.actionMasked == MotionEvent.ACTION_CANCEL)
                dragging = false
            }
        }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        handler.removeCallbacks(longPress)
    }
}

class LauncherWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetHostView(context)

    companion object {
        const val HOST_ID = 1024
    }
}

class Widgets(context: Context) {
    private val app = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    val host = LauncherWidgetHost(app)

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bind(id: Int, info: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)

    private val views = HashMap<Int, AppWidgetHostView>()
    private val sizes = HashMap<Int, Pair<Int, Int>>()

    fun delete(id: Int) {
        if (id < 0) return
        host.deleteAppWidgetId(id)
        views.remove(id)
        sizes.remove(id)
    }

    fun view(context: Context, id: Int): View {
        views[id]?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val info = info(id) ?: return View(context)
        return host.createView(app, id, info).also { views[id] = it }
    }

    fun resized(id: Int, widthDp: Int, heightDp: Int) {
        if (sizes[id] == widthDp to heightDp) return
        sizes[id] = widthDp to heightDp
        val view = views[id] ?: return
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        runCatching { manager.updateAppWidgetOptions(view.appWidgetId, options) }
    }
}
