# R8 keeps members annotated with @JavascriptInterface by default, but the
# bridges are reached by name from the WebView, so the rules are made explicit
# instead of relying on that default.
-keepattributes *Annotation*

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the app's own classes: the bridges, the widget provider and the
# BroadcastReceiver are all instantiated by the Android framework by name.
-keep class com.supertipsters.oscar.** { *; }
