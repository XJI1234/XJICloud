package com.xjicloud.native3dgs

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.platform.PlatformViewFactory

class Native3dgsPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private val views = mutableMapOf<Int, Native3dgsPlatformView>()
    private var activityBinding: ActivityPluginBinding? = null
    private var picker: Pair<Native3dgsPlatformView, MethodChannel.Result>? = null
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "native3dgs/methods")
        channel.setMethodCallHandler(this)
        binding.platformViewRegistry.registerViewFactory("native3dgs/view", object : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
            override fun create(context: android.content.Context, viewId: Int, args: Any?): Native3dgsPlatformView {
                val parameters = args as Map<*, *>
                require(parameters["protocolVersion"] == 1)
                val sessionId = parameters["sessionId"] as String
                return Native3dgsPlatformView(context, viewId, sessionId, binding.binaryMessenger,
                    { activityBinding?.activity }, { views.remove(viewId) }).also { views[viewId] = it }
            }
        })
    }
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            require(call.argument<Int>("protocolVersion") == 1) { "Protocol mismatch" }
            val viewId = requireNotNull(call.argument<Int>("viewId"))
            val view = views[viewId]
            if (call.method == "dispose" && view == null) {
                require(!call.argument<String>("sessionId").isNullOrBlank()) { "Missing session" }
                result.success(mapOf("ok" to true))
                return
            }
            requireNotNull(view) { "Unknown view" }
            require(view.sessionId == call.argument<String>("sessionId")) { "Session mismatch" }
            val operation = call.argument<Number>("operationId")!!.toLong()
            if (call.method == "initialize") {
                result.success(mapOf("ok" to true, "supported" to (view.capability.supported && view.session != null),
                    "error" to view.initializationError,
                    "errorOrigin" to if (view.initializationError == 0) null else ErrorOrigin.Command.name,
                    "message" to (view.initializationMessage ?: view.capability.message)))
                return
            }
            if (call.method == "dispose") {
                view.dispose()
                view.whenClosed { success -> result.success(mapOf("ok" to success,
                    "message" to if (success) null else "原生资源未能确认关闭，请重启应用。")) }
                return
            }
            val session = view.session ?: error("Unsupported capability")
            check(!session.closing) { "Session is closing" }
            when (call.method) {
                "pickFile" -> {
                    require(operation == session.state.value.operationId) { "Stale operation" }
                    check(picker == null) { "A document picker is already active" }
                    val activity = activityBinding?.activity ?: error("No activity")
                    picker = view to result
                    try {
                        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }, 0x3d65)
                    } catch (error: Exception) { picker = null; throw error }
                    return
                }
                "visibility" -> view.setVisible(call.argument<Boolean>("visible") == true)
                "open" -> {
                    val uri = call.argument<String>("uri") ?: error("Missing URI")
                    require(android.net.Uri.parse(uri).scheme == "content") { "Only content URIs are supported" }
                    session.open(operation, uri)
                }
                "cancel" -> {
                    require(operation > session.state.value.operationId) { "Stale operation" }
                    view.stopInput()
                    session.cancel(call.argument<Number>("targetOperationId")!!.toLong())
                    session.invalidate(operation)
                }
                "close" -> { view.stopInput(); session.closeScene(operation) }
                "camera" -> {
                    require(operation == session.state.value.operationId) { "Stale operation" }
                    view.command(call.argument<String>("command")!!,
                        (call.argument<List<Number>>("values") ?: emptyList()).map { it.toDouble() })
                }
                else -> { result.notImplemented(); return }
            }
            result.success(mapOf("ok" to true, "operationId" to operation,
                "requestId" to session.state.value.render.requestId,
                "activeRequestId" to session.activeRequestId))
        } catch (error: Exception) {
            android.util.Log.e("Native3dgs", "Bridge operation failed: ${error.javaClass.name}")
            result.success(mapOf("ok" to false, "error" to ((error as? EngineFailure)?.code ?: 0),
                "errorOrigin" to (error as? EngineFailure)?.origin?.name,
                "message" to if (error is EngineFailure) sdkFailureMessage(error.code, error.origin)
                    else "操作失败 (${error.javaClass.simpleName})，请检查设备或重新打开查看器。"))
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != 0x3d65) return false
        val pending = picker ?: return true
        picker = null
        val (view, result) = pending
        if (views[view.viewId] !== view) { result.success(mapOf("ok" to false, "message" to "查看器已退出。")); return true }
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) { result.success(mapOf("ok" to true)); return true }
        var message: String? = null
        if ((data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try { activityBinding?.activity?.contentResolver?.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (error: SecurityException) {
                android.util.Log.w("Native3dgs", "Document provider declined persistent read grant")
                message = "文件提供者未保存长期授权，本次可读取；重启后需重新选择。"
            }
        }
        result.success(mapOf("ok" to true, "uri" to uri.toString(), "message" to message))
        return true
    }
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding; binding.addActivityResultListener(this)
        views.values.forEach { it.resume() }
    }
    private fun detachActivity() {
        views.values.forEach { it.pause() }
        activityBinding?.removeActivityResultListener(this); activityBinding = null
        picker?.second?.success(mapOf("ok" to false, "message" to "文件选择已随 Activity 关闭，请重试。")); picker = null
    }
    override fun onDetachedFromActivityForConfigChanges() = detachActivity()
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = onAttachedToActivity(binding)
    override fun onDetachedFromActivity() {
        views.values.toList().forEach { it.dispose() }
        detachActivity()
    }
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        views.values.toList().forEach { it.dispose() }; views.clear()
        channel.setMethodCallHandler(null); detachActivity()
    }
}
