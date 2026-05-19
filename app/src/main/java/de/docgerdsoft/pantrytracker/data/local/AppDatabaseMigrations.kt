package de.docgerdsoft.pantrytracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds the OFF lookup cache table (#48). DDL is copy-paste of Room's
 * generated `createSql` from `app/schemas/.../2.json` (with `${'$'}{TABLE_NAME}`
 * substituted) - must match byte-for-byte or the runtime `validateMigration`
 * check throws `IllegalStateException`.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `off_lookup_cache` " +
                "(`barcode` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`brand` TEXT, " +
                "`imageUrl` TEXT, " +
                "`resolvingHost` TEXT NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`barcode`))",
        )
    }
}
