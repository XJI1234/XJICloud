package com.xjicloud.xjicloud_mobile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import com.mobile.auth.gatewayauth.Constant
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel

/** Owns one SDK scene from explicit consent until backend verification or cancellation. */
internal class FusionAuthBridge(activity: Activity, private val channel: MethodChannel) {
    private val main = Handler(Looper.getMainLooper())
    private val session = FusionAuthSession(
        { AliyunFusionSdkSession(activity, main) },
        FusionTimeout { action ->
            val task = Runnable(action)
            main.postDelayed(task, 90_000)
            val cancel: () -> Unit = { main.removeCallbacks(task) }
            cancel
        },
    )

    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "canAuthenticate" -> {
                    // No phone-number permission or subscription identifiers are read.
                    try {
                        val telephony = activity.getSystemService(TelephonyManager::class.java)
                        val hasTelephony = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                        val simState = if (hasTelephony) telephony?.simState else null
                        result.success(hasTelephony && simState != null && simState != TelephonyManager.SIM_STATE_ABSENT)
                    } catch (_: SecurityException) {
                        result.error("fusion_unavailable", "SIM capability cannot be checked", null)
                    }
                }
                "openPrivacyDocument" -> {
                    val document = (call.arguments as? Map<*, *>)?.get("document") as? String
                    val url = when (document) {
                        "numberAuthentication" -> "https://terms.aliyun.com/legal-agreement/terms/suit_bu1_ali_cloud/suit_bu1_ali_cloud202112211045_86198.html"
                        "smsAuthentication" -> "https://terms.alicdn.com/legal-agreement/terms/privacy_policy_full/20230922101800634/20230922101800634.html"
                        "chinaMobile" -> "https://wap.cmpassport.com/resources/html/contract.html"
                        "chinaUnicom" -> "https://msv6.wosms.cn/html/oauth/protocol2.html"
                        "chinaTelecom" -> "https://e.189.cn/sdk/agreement/detail.do?appKey=E_189&hidetop=true&returnUrl="
                        else -> null
                    }
                    if (url == null) {
                        result.error("policy_unavailable", "Unknown policy document", null)
                        return@setMethodCallHandler
                    }
                    try {
                        activity.startActivity(Intent(activity, FusionProtocolActivity::class.java)
                            .putExtra(Constant.PROTOCOL_WEB_VIEW_NAME, activity.getString(R.string.fusion_data_policy))
                            .putExtra(Constant.PROTOCOL_WEB_VIEW_URL, url))
                        result.success(null)
                    } catch (_: RuntimeException) {
                        result.error("policy_unavailable", "Policy cannot be opened", null)
                    }
                }
                "authenticate" -> {
                    val args = call.arguments as? Map<*, *>
                    session.authenticate(FusionAuthRequest(
                        args?.get("authToken") as? String ?: "",
                        args?.get("schemeCode") as? String ?: "",
                        args?.get("templateId") as? String ?: "",
                        args?.get("privacyAccepted") == true,
                    ), object : FusionAuthReply {
                        override fun success(verifyToken: String) { result.success(verifyToken) }
                        override fun error(code: String) { result.error(code, message(code), null) }
                    })
                }
                "complete" -> {
                    val args = call.arguments as? Map<*, *>
                    session.complete(args?.get("success") == true)
                    result.success(null)
                }
                "cancel" -> { session.cancel(); result.success(null) }
                else -> result.notImplemented()
            }
        }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
        session.dispose()
    }

    private fun message(code: String): String = when (code) {
        "cancelled" -> "Authentication cancelled"
        "privacy_required" -> "Privacy consent is required before authentication"
        "fusion_busy" -> "Authentication is already in progress"
        else -> "Use phone verification to continue"
    }
}
