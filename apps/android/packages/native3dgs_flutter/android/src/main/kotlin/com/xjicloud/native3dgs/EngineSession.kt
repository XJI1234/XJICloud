package com.xjicloud.native3dgs

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RenderState(val phase: String = "Empty", val requestId: Long = 0,
    val activeRequestId: Long = 0, val sceneCount: Long = 0,
    val surfaceGeneration: Long = 0, val error: Int = 0, val message: String? = null,
    val errorOrigin: ErrorOrigin? = null)
data class SessionState(val operationId: Long = 0, val render: RenderState = RenderState())
enum class ErrorOrigin { Command, State, Decoder }
fun stateErrorOrigin(code: Int): ErrorOrigin = if (code in setOf(8, 10, 12, 13, 14, 15, 16))
    ErrorOrigin.Decoder else ErrorOrigin.State
open class EngineFailure(val code: Int, cause: Throwable, val origin: ErrorOrigin = ErrorOrigin.Command) : Exception("SDK error $code", cause)
class EngineInitializationFailure(code: Int, cause: Throwable) : EngineFailure(code, cause)
fun sdkFailureMessage(code: Int, origin: ErrorOrigin = ErrorOrigin.Command): String {
    // SDK0.2.2 merges private decodeError and native snapshot.error. Overlapping
    // state values cannot establish a cause; C-result codes apply only to commands.
    if (origin == ErrorOrigin.State) return "渲染或解码状态异常 (SDK 状态 $code)，请取消或关闭模型后重试。"
    if (origin == ErrorOrigin.Decoder) return when (code) {
        10 -> "模型超出解码资源限制 (SDK 解码 $code)，请尝试更小模型。"
        15 -> "隔离解码进程已退出 (SDK 解码 $code)，请关闭后重新选择模型。"
        12 -> "解码已取消 (SDK 解码 $code)，可重新选择模型。"
        13 -> "解码超时 (SDK 解码 $code)，请关闭后尝试更小模型。"
        else -> "模型解码失败 (SDK 解码 $code)，请重新选择有效模型或关闭后重试。"
    }
    return when (code) {
    5 -> "模型超出设备内存或资源限制 (SDK $code)，请关闭后尝试更小模型。"
    4 -> "模型版本或设备能力不受支持 (SDK $code)，请更换模型或设备。"
    3 -> "无法读取模型 (SDK $code)，请重新选择文件并检查读取授权。"
    else -> "模型或 GPU 操作失败 (SDK $code)，可取消或关闭后重试。"
    }
}
interface EnginePort {
    val state: StateFlow<RenderState>
    suspend fun open(uri: String): Long
    fun cancel(requestId: Long)
    fun attach(surface: Any): Long
    fun resize(generation: Long, width: Int, height: Int)
    fun detach(generation: Long)
    fun closeScene()
    fun camera(command: String, values: List<Double>)
    suspend fun closeAndWait()
}
data class Capability(val supported: Boolean, val message: String) {
    companion object {
        fun check(api: Int, abis: List<String>, vulkanVersion: Int, pageSize: Long = 4096): Capability = when {
            api < 29 -> Capability(false, "需要 Android 10 / API 29 或更高版本。")
            abis.firstOrNull() != "arm64-v8a" -> Capability(false, "需要 arm64-v8a 设备；当前架构不支持原生渲染。")
            vulkanVersion < 0x00401000 -> Capability(false, "需要 Vulkan 1.1；当前设备未声明支持。")
            pageSize > 4096 -> Capability(false, "SDK 0.2.2 解码库不支持 16KB 内存页设备；请等待上游兼容版本。")
            else -> Capability(true, "平台预检通过；GPU 兼容性仍需实际打开模型验证。")
        }
    }
}

/** Main-thread confined transaction and Surface ownership; no Android/JNI in tests. */
class EngineSession(private val engine: EnginePort, private val scope: CoroutineScope,
    private val closeDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val mutable = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutable
    val activeRequestId: Long get() = engine.state.value.activeRequestId
    private var operation = 0L
    private var request: Long? = null
    private var openJob: Job? = null
    private var generation: Long? = null
    var closing = false; private set
    private var cancelled = false
    private val observer = scope.launch {
        engine.state.collect { value ->
            if (!closing && !cancelled && (operation == 0L || request != null)) {
                val id = request
                if (id == null || value.requestId == id ||
                    (value.phase == "Ready" && value.activeRequestId == id)) {
                    mutable.value = SessionState(operation, value)
                }
            }
        }
    }
    fun attach(surface: Any, width: Int, height: Int) {
        check(!closing) { "Session is closing" }
        // Surface creation/visibility can precede its first buffer allocation.
        if (width <= 0 || height <= 0) return
        if (generation == null) generation = engine.attach(surface)
        // A retained View may return with a new buffer size before another
        // surfaceChanged callback. Reconcile without allocating a generation.
        resize(width, height)
    }
    fun resize(width: Int, height: Int) {
        if (!closing && width > 0 && height > 0) generation?.let { engine.resize(it, width, height) }
    }
    fun detach() {
        val current = generation ?: return
        // Clear before SDK call: repeated callbacks cannot reuse an invalid generation.
        generation = null
        engine.detach(current)
    }
    private fun cancelCurrent(): Exception? {
        openJob?.cancel(); openJob = null
        val previous = request
        request = null
        if (previous != null && !(engine.state.value.phase == "Ready" && engine.state.value.activeRequestId == previous)) {
            try { engine.cancel(previous) } catch (error: Exception) { failure(error); return error }
        }
        return null
    }
    fun open(id: Long, uri: String) {
        check(!closing) { "Session is closing" }
        require(id > operation) { "Stale operation" }
        cancelCurrent()
        operation = id; cancelled = false
        mutable.value = SessionState(id, mutable.value.render.copy(phase = "Loading", requestId = 0,
            error = 0, errorOrigin = null, message = null))
        openJob = scope.launch {
            try {
                val nativeRequest = engine.open(uri)
                if (closing || operation != id || cancelled) {
                    engine.cancel(nativeRequest)
                } else {
                    request = nativeRequest
                    val value = engine.state.value
                    val currentSnapshot = value.requestId == nativeRequest ||
                        (value.phase == "Ready" && value.activeRequestId == nativeRequest)
                    mutable.value = SessionState(id, if (currentSnapshot) value
                        else value.copy(phase = "Uploading", requestId = nativeRequest))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!closing && operation == id && !cancelled) failure(error)
                else System.err.println("Native3dgs superseded open cleanup failed: ${error.javaClass.name}")
            }
        }
    }
    fun cancel(target: Long) {
        if (closing || target != operation) return
        cancelled = true
        cancelCurrent()?.let { throw it }
        mutable.value = SessionState(operation, mutable.value.render.copy(phase = "Cancelled"))
    }
    fun invalidate(id: Long) {
        require(id > operation)
        operation = id
        mutable.value = mutable.value.copy(operationId = id)
    }
    fun closeScene(id: Long) {
        check(!closing)
        require(id > operation) { "Stale operation" }
        cancelled = true; cancelCurrent()
        engine.closeScene()
        invalidate(id)
        mutable.value = SessionState(id)
    }
    fun camera(command: String, values: List<Double>) {
        check(!closing && generation != null) { "No active surface" }
        engine.camera(command, values)
    }
    fun failure(error: Throwable) {
        // Numeric SDK errors remain in state; exception class is diagnostic without URI disclosure.
        mutable.value = SessionState(operation, mutable.value.render.copy(phase = "Failed",
            error = (error as? EngineFailure)?.code ?: mutable.value.render.error,
            errorOrigin = (error as? EngineFailure)?.origin ?: mutable.value.render.errorOrigin,
            message = if (error is EngineFailure) sdkFailureMessage(error.code, error.origin)
                else "原生操作失败 (${error.javaClass.simpleName})，请关闭后重试。"))
        System.err.println("Native3dgs operation failed: ${error.javaClass.name}")
    }
    fun shutdown(): Job {
        check(!closing) { "Already closing" }
        closing = true
        openJob?.cancel(); observer.cancel()
        try { detach() } catch (error: Exception) { failure(error) }
        mutable.value = SessionState(operation, mutable.value.render.copy(phase = "Stopping"))
        return scope.launch {
            val diagnostic = launch {
                delay(10_000)
                mutable.value = SessionState(operation, mutable.value.render.copy(message = "GPU 资源仍在关闭，请等待；停止接收新命令。"))
            }
            try {
                openJob?.join()
                withContext(closeDispatcher) { engine.closeAndWait() }
                mutable.value = SessionState(operation, RenderState(phase = "Stopped"))
            } catch (error: Exception) { failure(error) }
            finally { diagnostic.cancel() }
        }
    }
}
