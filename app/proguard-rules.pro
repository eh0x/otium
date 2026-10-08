# Release shrinking rules (R8 full mode).
#
# Retrofit, OkHttp, kotlinx.serialization, Room, Hilt, WorkManager and ML Kit ship their own
# consumer rules, so nothing is kept here by default. CI runs assembleRelease on every push.
#
# Add a rule only for a failure seen on a release build (a crash, or an R8 missing-class error),
# with a comment giving the class and the reason, as the style guide asks for suppressions.
