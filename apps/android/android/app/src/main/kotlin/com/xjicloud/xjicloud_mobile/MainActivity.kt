package com.xjicloud.xjicloud_mobile

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent

class MainActivity : FlutterActivity() {
    private var files: PlatformFilesBridge? = null
    private var fusionAuth: FusionAuthBridge? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        files = PlatformFilesBridge(this, MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "xjicloud/platform_files"))
        fusionAuth?.dispose()
        fusionAuth = FusionAuthBridge(this, MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.xjicloud.mobile/fusion_auth"))
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (files?.onActivityResult(requestCode, resultCode, data) != true) super.onActivityResult(requestCode, resultCode, data)
    }
    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        fusionAuth?.dispose(); fusionAuth = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
    override fun onDestroy() {
        fusionAuth?.dispose(); fusionAuth = null
        files?.dispose()
        super.onDestroy()
    }
}
