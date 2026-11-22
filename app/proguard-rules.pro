# JSch loads ciphers / KEX / signature classes by reflection using their class names,
# so the whole package must survive shrinking and obfuscation.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Our flow-control helper reaches package-private JSch fields.
-keep class com.payloadtunnel.core.FlowControlledInput { *; }

# JNI: the native library registers methods on this exact class/signature.
-keep class com.payloadtunnel.vpn.TProxyNative { *; }

# Keep enum names (we parse modes by name) and config model fields.
-keepclassmembers enum * { *; }
-keep class com.payloadtunnel.data.Profile { *; }
-keep class com.payloadtunnel.data.TunnelMode { *; }

# Start.io SDK (ships its own consumer rules too).
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**

# Keep BuildConfig (read at runtime).
-keep class com.payloadtunnel.BuildConfig { *; }

# Compose/Kotlin metadata is handled by default optimize rules.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
