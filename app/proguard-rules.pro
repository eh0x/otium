# Release shrinking rules (R8 full mode).
#
# Retrofit, OkHttp, kotlinx.serialization, Room, Hilt, WorkManager and ML Kit ship their own
# consumer rules, so nothing is kept here by default. CI runs assembleRelease on every push.
#
# Add a rule only for a failure seen on a release build (a crash, or an R8 missing-class error),
# with a comment giving the class and the reason, as the style guide asks for suppressions.

# ML Kit finds its component registrars (CommonComponentRegistrar, VisionCommonRegistrar,
# BarcodeRegistrar) through manifest meta-data and creates them by reflection. ML Kit ships no rule
# for this, so R8 full mode removed their no-arg constructors: discovery logged "Could not
# instantiate ... NoSuchMethodException <init> []", nothing was registered, and opening the scanner
# crashed with a NullPointerException in BarcodeScanning.getClient (seen 2026-10-09 on a release
# build).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
