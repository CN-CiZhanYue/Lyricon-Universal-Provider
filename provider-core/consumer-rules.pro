# YukiHookAPI/KavaRef contains optional JVM type-annotation inspection paths.
# Android does not ship java.lang.reflect.AnnotatedType, and Provider hooks do
# not invoke those optional paths.
-dontwarn java.lang.reflect.AnnotatedType
