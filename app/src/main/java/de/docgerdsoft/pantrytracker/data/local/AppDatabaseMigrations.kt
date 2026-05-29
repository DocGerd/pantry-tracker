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

/**
 * v2 -> v3: adds the opt-in restock columns to `products` (#191). `lowLimit` is
 * nullable (null ⇒ untracked, never on the buying list). `defaultBuyAmount` is
 * NOT NULL DEFAULT 1 so existing rows stay valid and a single "Bought" tap has a
 * sensible amount. Two ADD COLUMN statements — verify against
 * `app/schemas/.../3.json` `products.createSql`. Room's `validateMigration`
 * checks column name + affinity + nullability, not the `DEFAULT` clause, so the
 * `DEFAULT 1` needed to back-fill existing rows doesn't break validation.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `lowLimit` INTEGER")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `defaultBuyAmount` INTEGER NOT NULL DEFAULT 1")
    }
}
