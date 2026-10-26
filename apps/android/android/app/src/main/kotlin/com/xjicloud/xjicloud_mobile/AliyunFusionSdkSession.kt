package com.xjicloud.xjicloud_mobile

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionAuthUICallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.AlicomFusionConstant
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.numberauth.AlicomFusionSwitchLogin
import com.alicom.fusion.auth.numberauth.FusionNumberAuthModel
import com.alicom.fusion.auth.smsauth.AlicomFusionVerifyCodeView
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import com.alicom.fusion.auth.upsms.AlicomFusionUpSMSView
import com.mobile.auth.gatewayauth.AuthUIControlClickListener
import com.mobile.auth.gatewayauth.ResultCode

internal class AliyunFusionSdkSession(
    private val activity: Activity,
    private val main: Handler,
) : FusionSdkSession {
    private var business: AlicomFusionBusiness? = null
    private var events: FusionSdkEvents? = null
    @Volatile private var closed = false
    @Volatile private var initialAuthToken: String? = null
    private var initializedAt = 0L
    private var started = false
    private var templateId = "100001"

    override fun initialize(request: FusionAuthRequest, events: FusionSdkEvents) {
        check(request.privacyAccepted)
        this.events = events
        templateId = request.templateId
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a") || activity.isFinishing || activity.isDestroyed) {
            events.unavailable(); return
        }
        AlicomFusionLog.setLogEnable(false)
        initialAuthToken = request.authToken
        initializedAt = SystemClock.elapsedRealtime()
        val sdk = AlicomFusionBusiness()
        // Actual AAR creates authProxy in init; registering before init dereferences null.
        business = sdk
        sdk.initWithToken(activity.applicationContext, request.schemeCode,
            AlicomFusionAuthToken().apply { setAuthToken(request.authToken) })
        sdk.setProtocolAction(FusionProtocolActivity.ACTION)
        sdk.setAlicomFusionAuthCallBack(object : AlicomFusionAuthCallBack {
            override fun onSDKTokenUpdate(): AlicomFusionAuthToken? {
                // SDK can request this during initialization too. A freshly issued server
                // credential lasts >=900s and is usable for this bounded 90s scene only.
                val available = initialAuthToken
                if (!closed && available != null && SystemClock.elapsedRealtime() - initializedAt < 90_000) {
                    return AlicomFusionAuthToken().apply { setAuthToken(available) }
                }
                emit { it.unavailable() }
                return null
            }
            override fun onSDKTokenAuthSuccess() = emit { it.ready() }
            override fun onSDKTokenAuthFailure(token: AlicomFusionAuthToken?, event: AlicomFusionEvent?) = emit { it.unavailable() }
            override fun onVerifySuccess(token: String?, nodeName: String?, event: AlicomFusionEvent?) = emit { it.token(token ?: "") }
            override fun onHalfWayVerifySuccess(nodeName: String?, token: String?, event: AlicomFusionEvent?, result: HalfWayVerifyResult?) {
                // This bridge only supports login scene, never change/bind-number flows.
                emit { it.unavailable() }
            }
            override fun onVerifyFailed(event: AlicomFusionEvent?, nodeName: String?) {
                if (event?.errorCode == AlicomFusionConstant.ALICOMFUSIONAUTH_NUMBERAUTHPAGECANCELED) emit { it.cancelled() }
                else emit { it.unavailable() }
            }
            override fun onTemplateFinish(event: AlicomFusionEvent?) = emit { it.finished() }
            override fun onAuthEvent(event: AlicomFusionEvent?) {
                // UI events carry no authenticated identity. Back closes only this request.
                if (event?.errorCode in setOf(
                        AlicomFusionConstant.ALICOMFUSIONAUTH_NUMBERAUTHBACKCLICK,
                        AlicomFusionConstant.ALICOMFUSIONAUTH_SMSAUTHBACKCLICK,
                        AlicomFusionConstant.ALICOMFUSION_UPBACKCLICK,
                        AlicomFusionConstant.ALICOMFUSION_GRAPHBACK,
                    )) emit { it.cancelled() }
            }
            override fun onGetPhoneNumberForVerification(nodeId: String?, event: AlicomFusionEvent?): String = ""
            override fun onVerifyInterrupt(event: AlicomFusionEvent?) = Unit // unchecked consent/input/loading stay inside SDK
        })
    }

    override fun start() {
        if (closed || started) return
        if (activity.isFinishing || activity.isDestroyed) { events?.unavailable(); return }
        started = true
        business?.startSceneWithTemplateId(activity, templateId, object : AlicomFusionAuthUICallBack {
            override fun onPhoneNumberVerifyUICustomView(template: String?, node: String?, model: FusionNumberAuthModel) {
                model.builder.setPrivacyState(false)
                    .setProtocolAction(FusionProtocolActivity.ACTION)
                    .setPackageName(activity.packageName)
                model.setSwitchLoginBack(object : AlicomFusionSwitchLogin {
                    override fun switchLogin() = emit { it.unavailable() }
                })
                model.setUiClickListener(AuthUIControlClickListener { code, _, _ ->
                    when (code) {
                        ResultCode.CODE_ERROR_USER_CANCEL,
                        ResultCode.CODE_ERROR_USER_CONTROL_CANCEL_BYBTN,
                        ResultCode.CODE_ERROR_USER_CONTROL_CANCEL_BYKEY -> emit { it.cancelled() }
                        ResultCode.CODE_ERROR_USER_SWITCH -> emit { it.unavailable() }
                    }
                })
            }
            override fun onSMSCodeVerifyUICustomView(template: String?, node: String?, autoInput: Boolean, view: AlicomFusionVerifyCodeView?) = Unit
            override fun onSMSSendVerifyUICustomView(template: String?, node: String?, view: AlicomFusionUpSMSView?, phone: String?, code: String?) = Unit
        })
    }

    override fun complete(success: Boolean) {
        if (!closed && started && success) business?.continueSceneWithTemplateId(templateId, true)
        // Failure returns to Flutter's SMS path; do not open another native SMS flow.
        if (!closed && started && !success) business?.stopSceneWithTemplateId(templateId)
    }

    override fun close() {
        if (closed) return
        closed = true
        initialAuthToken = null
        events = null
        val sdk = business
        business = null
        // destory closes running nodes too; stopScene is invalid while a node is busy.
        sdk?.destory()
    }

    private fun emit(action: (FusionSdkEvents) -> Unit) {
        main.post { if (!closed) events?.let(action) }
    }
}
