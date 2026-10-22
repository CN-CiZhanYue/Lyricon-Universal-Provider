-dontwarn java.lang.reflect.AnnotatedType
# Preserve module/runtime names while allowing R8 to optimize the Compose UI.
-keep class !io.github.cn_cizhanyue.lyricon.universal.provider.ui.**,io.github.cn_cizhanyue.lyricon.universal.provider.** { *; }
-repackageclasses ''
