# The original AAR's consumer rules remain embedded and are applied by AGP.
# Additional public API/UI keeps follow the supplied 1.2.16 demo.
-keep class com.alicom.fusion.auth.** { *; }
-keep class com.mobile.auth.gatewayauth.** { *; }
-keep class androidx.core.content.ContextCompat { *; }
-keep class androidx.appcompat.app.AppCompatActivity { *; }
-keep public class * extends android.widget.ImageView { *; }
-keep class androidx.activity.OnBackPressedDispatcher { *; }
-keep class androidx.activity.OnBackPressedCallback { *; }
-keep class androidx.lifecycle.LifecycleOwner { *; }
