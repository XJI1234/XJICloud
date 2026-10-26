# Aliyun Fusion Authentication Android SDK 1.2.16

Original `SDK/fusionauth-1.2.16-online-release.aar` copied unchanged from the user-provided Alibaba Cloud download `fusionAuthSDK_APP_Android_v1.2.16_operator_ui_log_static.zip` on 2026-10-26. No demo signing material, configuration, local tokens or fake-success code is included. Use is subject to Alibaba Cloud's SDK/service terms; obtain the original from the PNVS console rather than third-party mirrors.

SHA-256: `286efa126634c92da7fa0b6c22808ed8e27b7a485418ebdc2c4d64534ad33fc4`. Android `verifyFusionSdk` checks this original AAR on every `preBuild`; embedded consumer ProGuard rules remain intact. The supplied AAR exposes `getSDKVersion()`/Manifest metadata as 1.2.8 even though its native library and download are 1.2.16. Do not modify the binary to fix upstream metadata.

Only this AAR is required for the current number-authentication login node. Umeng risk-score/device-risk plugins are intentionally absent, so those scheme nodes must remain disabled. Supported native ABIs in this artifact: arm64-v8a, armeabi-v7a, x86; no x86_64. Current app release targets arm64-v8a.

Official integration: https://help.aliyun.com/zh/pnvs/developer-reference/the-android-client-access-3

Server APIs: https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-getfusionauthtoken and https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-verifywithfusionauthtoken

Privacy initialization: https://help.aliyun.com/zh/pnvs/security-and-compliance/converged-certification-services-compliance-guide

The Flutter channel `com.xjicloud.mobile/fusion_auth` accepts `authenticate` with `authToken`, `schemeCode`, `templateId=100001`, `privacyAccepted=true`, and returns a unified verification token string. Backend verification is mandatory before calling `complete` with the actual `success` result; `cancel` closes a request. SDK initialization is gated by explicit privacy acceptance. A 90-second timeout, Activity/engine detachment and cancellation release SDK state. Errors contain stable codes and no SDK tokens or phone numbers. Operator policies open their SDK-provided HTTPS URLs in an app-owned nonexported Activity; no product-policy URL is invented. Product privacy disclosure and consent occur in Flutter before invoking this channel.

`canAuthenticate` checks only telephony/SIM availability after consent, before server token initialization; it does not read the phone number or request runtime phone permissions. Unknown SIM state is left to SDK detection. The app's own privacy policy is bundled Flutter content. `openPrivacyDocument` accepts only fixed provider-document identifiers (numberAuthentication, smsAuthentication, chinaMobile, chinaUnicom, chinaTelecom); the native host resolves their official HTTPS URLs. Reading either policy does not initialize the SDK or grant consent.

Package remains `com.xjicloud.mobile`. The current local debug APK certificate MD5 is `07fb12fb110de1fe5f9eb7625c81a6f3` (32 lowercase hexadecimal characters without colons). **Release still uses the pre-existing debug signing configuration; this is a test certificate, not production signing readiness.** Register the actual production certificate separately before release.

Real operator/SIM/privacy-page/navigation behavior requires an arm64 device and configured backend; JVM unit tests do not establish carrier acceptance or device lifecycle correctness.
