# Retrofit/Gson models are accessed via reflection — keep DTOs if minify is ever enabled.
-keep class com.ssmts.mobile.data.remote.** { *; }
