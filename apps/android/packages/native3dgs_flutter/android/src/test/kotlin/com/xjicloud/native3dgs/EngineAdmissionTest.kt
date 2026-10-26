package com.xjicloud.native3dgs

import org.junit.Assert.*
import org.junit.Test

class EngineAdmissionTest {
    @Test fun failedHandleZeroInitializationDoesNotConsumeSlotAfterViewDisposal() {
        val admission = EngineAdmission()
        val failedView = admission.reserve()!!
        failedView.initializationFailed(knownNoEngine = true)
        val nextView = admission.reserve()
        assertNotNull("No live engine exists, so initialization must be retryable", nextView)
        // A late callback from the old view must never free the next view's slot.
        failedView.closeCompleted(success = true)
        assertNull(admission.reserve())
        nextView!!.constructed(); nextView.beginClosing(); nextView.closeCompleted(success = true)
        assertNotNull(admission.reserve())
    }
    @Test fun realEngineKeepsSlotWhileClosingAndReleasesOnlyAfterSuccess() {
        val admission = EngineAdmission()
        val view = admission.reserve()!!
        view.constructed(); view.beginClosing()
        assertNull(admission.reserve())
        assertTrue(admission.blockedMessage().contains("关闭"))
        view.closeCompleted(success = true)
        assertNotNull(admission.reserve())
    }
    @Test fun failedCloseIsPoisonedAndExplicitlyRequiresProcessRestart() {
        val admission = EngineAdmission()
        val view = admission.reserve()!!
        view.constructed(); view.beginClosing(); view.closeCompleted(success = false)
        assertNull(admission.reserve())
        assertTrue(admission.blockedMessage().contains("关闭失败"))
        assertTrue(admission.blockedMessage().contains("重启应用"))
    }
    @Test fun indeterminateInitializationIsNotMisrepresentedAsActiveClosing() {
        val admission = EngineAdmission()
        admission.reserve()!!.initializationFailed(knownNoEngine = false)
        assertNull(admission.reserve())
        assertTrue(admission.blockedMessage().contains("初始化状态无法确认"))
        assertTrue(admission.blockedMessage().contains("重启应用"))
    }
}
