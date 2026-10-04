# Room, Hilt, WorkManager, Retrofit and kotlinx-serialization ship their own consumer rules.

# Keep line numbers for readable crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit reads each API method's response type from its generic signature. Responses the app never reads
# (MessageResponse, CreatedResponse) would otherwise be pruned and their signatures erased to Object, which
# fails every save with "Unable to create converter for class java.lang.Object".
-keep,allowobfuscation class dev.libinfaby.tasks.data.api.*Response
