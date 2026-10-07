-keepclassmembers class app.goimium.browser.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.goimium.browser.DesktopIdentity { *; }
-keepattributes SourceFile,LineNumberTable
