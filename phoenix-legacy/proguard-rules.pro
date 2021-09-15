# http://developer.android.com/guide/developing/tools/proguard.html
-printconfiguration ~/tmp/full-r8-config.txt
-dontoptimize
-dontobfuscate
-dontpreverify

-keep class androidx.**
-keep class scala.**
-keep class akka.**
-keep class fr.acinq.**
-keep class org.greenrobot.**
-keepattributes *Annotation*, Signature, Exception

# greenrobot eventbus
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# logback-android (https://github.com/tony19/logback-android/wiki#proguard)
-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn ch.qos.logback.core.net.*
