package com.xjicloud.native3dgs

internal class ViewVisibility {
    var activityResumed = true
    var selected = true
    var flutterAttached = true
    val active: Boolean get() = activityResumed && selected && flutterAttached
}
