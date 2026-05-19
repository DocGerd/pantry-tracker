# Add project-specific ProGuard rules here.

# kotlinx.serialization belt-and-braces rules for @Serializable types in this app.
# kotlinx-serialization-runtime ships consumer-rules.pro that *should* cover these,
# but explicit -keep guards against runtime surprises when R8 chooses an aggressive
# optimization pass (this is a first-time R8 enable in v1.2).
#
# Pin the @Serializable companion-object .serializer() static accessor and the
# class members the generated serializer reads at runtime.
-if @kotlinx.serialization.Serializable class de.docgerdsoft.pantrytracker.**
-keepclassmembers class <1> {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class de.docgerdsoft.pantrytracker.**$Companion
-keepclassmembers class <1> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the @Serializable data classes themselves intact — their fields are read
# reflectively by the generated KSerializer descriptor.
-keep,includedescriptorclasses class de.docgerdsoft.pantrytracker.data.remote.OffProduct { *; }
-keep,includedescriptorclasses class de.docgerdsoft.pantrytracker.data.remote.OffApiEnvelope { *; }

# Room database, entities, DAOs, and type converters.
# Room's runtime ships consumer-rules.pro that already covers these in principle,
# but on first-time R8 the explicit declarations preempt the "generated _Impl class
# can't find its sibling" symptom that strikes Room when the optimization pass
# elides fields the Impl reads via reflection. The @Database-annotated class and
# @TypeConverter-bearing class are also Impl reflection targets — applying the
# belt-and-braces symmetrically.
-keep class de.docgerdsoft.pantrytracker.data.local.AppDatabase { *; }
-keep class de.docgerdsoft.pantrytracker.data.local.Converters { *; }
-keep class de.docgerdsoft.pantrytracker.data.local.Product { *; }
-keep class de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry { *; }
-keep interface de.docgerdsoft.pantrytracker.data.local.ProductDao { *; }
-keep interface de.docgerdsoft.pantrytracker.data.local.OffLookupCacheDao { *; }
