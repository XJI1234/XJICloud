package com.xjicloud.native3dgs

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.TextView
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.platform.PlatformView
import kotlinx.coroutines.*

internal class Native3dgsPlatformView(context: Context, val viewId: Int, val sessionId: String,
    messenger: BinaryMessenger, private val activity: () -> Activity?,
    private val onDisposed: () -> Unit) : PlatformView, SurfaceHolder.Callback,
    EventChannel.StreamHandler, Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app = context.applicationContext as Application
    val capability: Capability
    val session: EngineSession?
    val initializationError: Int
    private val view: View
    private val channel = EventChannel(messenger, "native3dgs/events/$viewId")
    private var collector: Job? = null
    private var disposed = false
    private var paused = false
    private var flutterAttached = true
    private var free = false
    private var lastX = 0f
    private var lastY = 0f
    private lateinit var input: FrameInput
    private val choreographer = Choreographer.getInstance()
    private val frame = Choreographer.FrameCallback { input.flush() }
    companion object { private val engineInUse = java.util.concurrent.atomic.AtomicBoolean(false) }
    init {
        val vulkan = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version ?: 0
        val checked = Capability.check(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), vulkan,
            android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE))
        capability = if (checked.supported && !engineInUse.compareAndSet(false, true))
            Capability(false, "上一引擎仍在使用或关闭中，请等待；若持续失败请重启应用。") else checked
        // Keep unsupported hosts entirely outside SdkEngine construction / JNI loading.
        var constructionError: Throwable? = null
        session = if (capability.supported) try { EngineSession(SdkEngine(context, scope), scope) }
            catch (error: Exception) { constructionError = error; null }
            catch (error: LinkageError) { constructionError = error; null }
        else null
        initializationError = (constructionError as? EngineFailure)?.code ?: 0
        input = FrameInput({ choreographer.postFrameCallback(frame) },
            { choreographer.removeFrameCallback(frame) }) { name, values ->
            try { command(name, values) } catch (error: Exception) { session?.failure(error) }
        }
        view = if (session != null) SurfaceView(context).apply {
            holder.addCallback(this@Native3dgsPlatformView)
            val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    gesture("dolly", listOf(kotlin.math.ln(detector.scaleFactor.toDouble()) * 8))
                    return true
                }
            })
            setOnTouchListener { touched, event ->
                if (paused || disposed) return@setOnTouchListener false
                scale.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                        lastX = if (event.pointerCount > 1) scale.focusX else event.x
                        lastY = if (event.pointerCount > 1) scale.focusY else event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val x = if (event.pointerCount > 1) scale.focusX else event.x
                        val y = if (event.pointerCount > 1) scale.focusY else event.y
                        gesture(if (event.pointerCount > 1) "pan" else if (free) "look" else "orbit",
                            listOf((x - lastX).toDouble(), (y - lastY).toDouble()))
                        lastX = x; lastY = y
                    }
                    MotionEvent.ACTION_UP -> touched.performClick()
                    MotionEvent.ACTION_CANCEL -> input.clear()
                }
                true
            }
        } else TextView(context).apply {
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            text = constructionError?.let {
                android.util.Log.e("Native3dgs", "Engine construction failed: ${it.javaClass.name}")
                "原生引擎初始化失败 (${it.javaClass.simpleName})。"
            } ?: capability.message
        }
        channel.setStreamHandler(this)
        app.registerActivityLifecycleCallbacks(this)
    }
    fun command(name: String, values: List<Double>) {
        check(!disposed && !paused && flutterAttached) { "View is inactive" }
        if (name == "free" || name == "fixed") input.clear()
        session?.camera(name, values) ?: error("Unsupported view")
        if (name == "free") free = true
        if (name == "fixed") free = false
    }
    private fun gesture(name: String, values: List<Double>) {
        if (!disposed && !paused && flutterAttached) input.add(name, values)
    }
    fun stopInput() = input.clear()
    override fun getView() = view
    private fun attach() {
        val surface = view as? SurfaceView ?: return
        if (!disposed && !paused && flutterAttached && surface.holder.surface.isValid) {
            try { session?.attach(surface.holder.surface, surface.width, surface.height) }
            catch (error: Exception) { session?.failure(error) }
        }
    }
    private fun detach() {
        input.clear()
        try { session?.detach() } catch (error: Exception) { session?.failure(error) }
    }
    override fun surfaceCreated(holder: SurfaceHolder) = attach()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        try { session?.resize(width, height) } catch (error: Exception) { session?.failure(error) }
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) = detach()
    override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
        collector?.cancel()
        collector = scope.launch {
            session?.state?.collect { state ->
                val s = state.render
                sink.success(mapOf("protocolVersion" to 1, "viewId" to viewId, "sessionId" to sessionId,
                    "operationId" to state.operationId, "requestId" to s.requestId,
                    "activeRequestId" to s.activeRequestId, "phase" to s.phase,
                    "sceneCount" to s.sceneCount, "error" to s.error,
                    "surfaceGeneration" to s.surfaceGeneration, "message" to s.message))
            }
        }
    }
    override fun onCancel(arguments: Any?) { collector?.cancel(); collector = null }
    override fun dispose() {
        if (disposed) return
        disposed = true
        input.clear()
        onDisposed()
        collector?.cancel(); collector = null
        channel.setStreamHandler(null)
        app.unregisterActivityLifecycleCallbacks(this)
        (view as? SurfaceView)?.holder?.removeCallback(this)
        val closing = session?.shutdown()
        if (closing == null) scope.cancel()
        else closing.invokeOnCompletion {
            if (session.state.value.render.phase == "Stopped") engineInUse.set(false)
            scope.cancel()
        }
    }
    fun pause() { paused = true; detach() }
    fun resume() { paused = false; attach() }
    override fun onFlutterViewAttached(flutterView: View) { flutterAttached = true; attach() }
    override fun onFlutterViewDetached() { flutterAttached = false; detach() }
    override fun onActivityPaused(a: Activity) { if (a == activity()) pause() }
    override fun onActivityResumed(a: Activity) { if (a == activity()) resume() }
    override fun onActivityDestroyed(a: Activity) { if (a == activity()) dispose() }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
}
