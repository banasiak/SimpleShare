# R8 is enabled for release builds. Everything below exists because the failure it prevents is
# silent -- the app builds, installs and only misbehaves at runtime -- so each rule notes what breaks
# without it. Library rules (Hilt, OkHttp, DataStore, Compose, Play Review) arrive as consumer rules
# from their own artifacts and are deliberately not duplicated here.

# SanitizeState is @Parcelize and round-trips through a Bundle when the process is killed. The
# framework only ever reaches CREATOR reflectively, so losing it would fail at restore time rather
# than at build time. proguard-android-optimize.txt already carries this rule; it is repeated here
# deliberately, because the cost is nothing and the failure it guards against is invisible until a
# user loses their work.
-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}
# review-ktx carries a Play Services build-time annotation that is not on the runtime classpath.
# It has no bearing on behaviour, but R8 treats a missing referenced class as a hard error.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
