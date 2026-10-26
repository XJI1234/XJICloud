package com.xjicloud.native3dgs

import org.junit.Assert.*
import org.junit.Test

class SdkErrorsTest {
    @Test fun mergedSurfaceSnapshotCannotBeDiagnosedAsUnsupportedModel() {
        val message = sdkFailureMessage(4, stateErrorOrigin(4))
        assertEquals(ErrorOrigin.State, stateErrorOrigin(4))
        assertFalse(message.contains("不受支持"))
        assertTrue(message.contains("4"))
    }
    @Test fun mergedDeviceSnapshotCannotBeDiagnosedAsOutOfMemory() {
        val message = sdkFailureMessage(5, stateErrorOrigin(5))
        assertFalse(message.contains("内存"))
        assertTrue(message.contains("关闭"))
        assertTrue(message.contains("5"))
    }
    @Test fun mergedUploadSnapshotCannotBeDiagnosedAsSourceReadFailure() {
        val message = sdkFailureMessage(3, stateErrorOrigin(3))
        assertFalse(message.contains("读取"))
        assertTrue(message.contains("3"))
    }
    @Test fun unambiguousDecoderLimitsAndProcessExitKeepSpecificGuidance() {
        assertEquals(ErrorOrigin.Decoder, stateErrorOrigin(10))
        assertEquals(ErrorOrigin.Decoder, stateErrorOrigin(15))
        assertTrue(sdkFailureMessage(10, stateErrorOrigin(10)).contains("资源"))
        assertTrue(sdkFailureMessage(15, stateErrorOrigin(15)).contains("解码进程"))
    }
    @Test fun synchronousCommandResultsRetainTheirOwnNamespace() {
        assertTrue(sdkFailureMessage(3, ErrorOrigin.Command).contains("读取"))
        assertTrue(sdkFailureMessage(4, ErrorOrigin.Command).contains("不受支持"))
        assertTrue(sdkFailureMessage(5, ErrorOrigin.Command).contains("内存"))
    }
}
