# kotlinx.serialization belt-and-braces rules for @Serializable types in this app.
# kotlinx-serialization-runtime ships consumer-rules.pro that *should* cover the
# generated KSerializer plumbing, but explicit -keep guards against runtime
# surprises when R8 chooses an aggressive optimization pass on a first-time enable.
#
# Pin the @Serializable companion-object accessor on the outer class (the field
# that kotlinx.serialization reads via getDeclaredField at descriptor build time).
-if @kotlinx.serialization.Serializable class de.docgerdsoft.pantrytracker.**
-keepclassmembers class <1> {
    *** Companion;
}

# Keep the Companion CLASS itself, not just the field accessor. Without this,
# R8 may strip or rename the inner $Companion type, leaving the kept field
# pointing at a missing class. Pin every $Companion under the app package.
-keep class de.docgerdsoft.pantrytracker.**$Companion { *; }

# Keep the @Serializable data classes themselves intact — their fields are read
# reflectively by the generated KSerializer descriptor. `OffApiEnvelope` is
# `internal` and would otherwise be a prime inline-and-rename target.
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

# Pin OffLookupCacheEntry's primary constructor. The data class has an
# `init { require(name.isNotBlank()) }` invariant (PR C polish) — if R8
# inlines or simplifies the constructor body, the IllegalArgumentException
# may not unwind to the catch in ProductRepositoryImpl.lookupForPreview.
-keepclassmembers class de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry {
    <init>(...);
}
