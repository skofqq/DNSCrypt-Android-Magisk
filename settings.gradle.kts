// One build for the whole repository: the Magisk module lives in the root, the
// Shieldy app in shieldy/ (its own Gradle build, included here). Open this folder
// in Android Studio to work on both; `./gradlew release` builds the signed APK and
// the module zip as two files.
rootProject.name = "DNSCrypt-Android-Magisk"

includeBuild("shieldy")
