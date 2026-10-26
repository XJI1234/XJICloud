package com.xjicloud.native3dgs
import org.junit.Assert.*
import org.junit.Test
class ViewVisibilityTest {
    @Test fun activityResumeAndFlutterReattachCannotReactivateHiddenTab() {
        val visibility = ViewVisibility()
        visibility.selected = false
        visibility.activityResumed = false
        visibility.flutterAttached = false
        visibility.activityResumed = true
        visibility.flutterAttached = true
        assertFalse(visibility.active)
        visibility.selected = true
        assertTrue(visibility.active)
    }
    @Test fun selectedTabCannotRenderBeforeActivityAndSurfaceResume() {
        val visibility = ViewVisibility()
        visibility.activityResumed = false
        visibility.selected = true
        assertFalse(visibility.active)
        visibility.activityResumed = true
        visibility.flutterAttached = false
        assertFalse(visibility.active)
    }
}
