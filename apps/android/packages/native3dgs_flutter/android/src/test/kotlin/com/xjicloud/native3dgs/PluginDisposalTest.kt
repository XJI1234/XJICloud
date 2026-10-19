package com.xjicloud.native3dgs

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.*
import org.junit.Test

class PluginDisposalTest {
    private class Reply : MethodChannel.Result {
        var value: Any? = null
        override fun success(result: Any?) { value = result }
        override fun error(code: String, message: String?, details: Any?) { fail("Unexpected channel error") }
        override fun notImplemented() { fail("Dispose must be implemented") }
    }
    @Test fun bridgeDisposeIsIdempotentAfterAutomaticPlatformViewRemoval() {
        val plugin = Native3dgsPlugin()
        val arguments = mapOf("protocolVersion" to 1, "viewId" to 17,
            "sessionId" to "already-removed", "operationId" to 0)
        repeat(2) {
            val result = Reply()
            plugin.onMethodCall(MethodCall("dispose", arguments), result)
            assertEquals(true, (result.value as Map<*, *>)["ok"])
        }
    }
}
