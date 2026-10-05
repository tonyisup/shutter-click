# Garmin's companion SDK ships no consumer rules. It exchanges AIDL parcelables with
# Garmin Connect and deserializes watch messages, so keep its small API intact.
-keep class com.garmin.** { *; }
-dontwarn com.garmin.**
