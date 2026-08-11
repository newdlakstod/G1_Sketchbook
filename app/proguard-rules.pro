# Firebase Realtime Database uses reflection on model classes.
# Keep our data model classes so field names survive minification.
-keepclassmembers class com.g1.sketchbook.data.model.** {
  <init>();
  <fields>;
}
-keep class com.g1.sketchbook.data.model.** { *; }
