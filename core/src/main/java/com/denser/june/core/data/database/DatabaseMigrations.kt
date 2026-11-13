package com.denser.june.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.denser.june.core.data.database.converters.JournalTypeConverters
import com.denser.june.core.domain.logging.AppLogger
import com.denser.june.core.domain.model.PeopleUnification

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 1 to 2..."
            )
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `tags` (
                    `tagId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `name` TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `journal_tag_cross_ref` (
                    `id` INTEGER NOT NULL, 
                    `tagId` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`, `tagId`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_tag_cross_ref_tagId` ON `journal_tag_cross_ref` (`tagId`)")
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 1 to 2."
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 2 to 3..."
            )
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `journals_new` (
                    `id` TEXT NOT NULL, 
                    `title` TEXT NOT NULL, 
                    `content` TEXT NOT NULL, 
                    `emoji` TEXT, 
                    `images` TEXT NOT NULL, 
                    `location` TEXT, 
                    `songDetails` TEXT, 
                    `tags` TEXT NOT NULL, 
                    `createdAt` INTEGER NOT NULL DEFAULT 0, 
                    `updatedAt` INTEGER, 
                    `dateTime` INTEGER NOT NULL, 
                    `isBookmarked` INTEGER NOT NULL, 
                    `isArchived` INTEGER NOT NULL, 
                    `isDraft` INTEGER NOT NULL, 
                    `deletedAt` INTEGER, 
                    `syncedAt` INTEGER DEFAULT NULL, 
                    `cloudId` TEXT DEFAULT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            
            db.execSQL("""
                INSERT INTO journals_new (id, title, content, emoji, images, location, songDetails, tags, createdAt, updatedAt, dateTime, isBookmarked, isArchived, isDraft)
                SELECT CAST(id AS TEXT), title, content, emoji, images, location, songDetails, tags, createdAt, updatedAt, dateTime, isBookmarked, isArchived, isDraft FROM journals
            """.trimIndent())
            
            db.execSQL("DROP TABLE journals")
            db.execSQL("ALTER TABLE journals_new RENAME TO journals")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `journal_tag_cross_ref_new` (
                    `id` TEXT NOT NULL, 
                    `tagId` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`, `tagId`)
                )
            """.trimIndent())
            
            db.execSQL("""
                INSERT INTO journal_tag_cross_ref_new (id, tagId)
                SELECT CAST(id AS TEXT), tagId FROM journal_tag_cross_ref
            """.trimIndent())
            
            db.execSQL("DROP TABLE journal_tag_cross_ref")
            db.execSQL("ALTER TABLE journal_tag_cross_ref_new RENAME TO journal_tag_cross_ref")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_tag_cross_ref_tagId` ON `journal_tag_cross_ref` (`tagId`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `deleted_journal_tombstones` (
                    `id` TEXT NOT NULL, 
                    `deletedAt` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 2 to 3."
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 3 to 4..."
            )
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `tags_new` (
                    `name` TEXT NOT NULL,
                    PRIMARY KEY(`name`)
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `journal_tag_cross_ref_new` (
                    `id` TEXT NOT NULL,
                    `tagName` TEXT NOT NULL,
                    PRIMARY KEY(`id`, `tagName`)
                )
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO journal_tag_cross_ref_new (id, tagName)
                SELECT ref.id, t.name
                FROM journal_tag_cross_ref ref
                INNER JOIN tags t ON ref.tagId = t.tagId
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO tags_new (name)
                SELECT DISTINCT name FROM tags
            """.trimIndent())

            db.execSQL("DROP TABLE journal_tag_cross_ref")
            db.execSQL("ALTER TABLE journal_tag_cross_ref_new RENAME TO journal_tag_cross_ref")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_tag_cross_ref_tagName` ON `journal_tag_cross_ref` (`tagName`)")

            db.execSQL("DROP TABLE tags")
            db.execSQL("ALTER TABLE tags_new RENAME TO tags")
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 3 to 4."
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 4 to 5..."
            )
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `song_library` (
                    `contentHash` TEXT NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `localArtPath` TEXT,
                    `sourceUrl` TEXT,
                    `sourceType` TEXT NOT NULL DEFAULT 'LINK',
                    `title` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `albumName` TEXT,
                    `genre` TEXT,
                    `thumbnailUrl` TEXT,
                    `addedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`contentHash`)
                )
            """.trimIndent())
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 4 to 5."
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 5 to 6..."
            )
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `isLocked` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `theme` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `font` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `fontVariation` TEXT DEFAULT NULL")
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 5 to 6."
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 6 to 7..."
            )
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `audios` TEXT NOT NULL DEFAULT '[]'")
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 6 to 7."
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 7 to 8..."
            )
            db.execSQL("ALTER TABLE `journals` ADD COLUMN `people` TEXT NOT NULL DEFAULT '[]'")
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 7 to 8."
            )
        }
    }

    /**
     * People used to exist twice: as `@name` tags and as the people on an entry, each entry
     * with its own copy. From here on a person is one thing with one id ([PeopleUnification]).
     * Each `@name` tag becomes a person on its entry, and every person is indexed under the
     * label `@<id>`, so people filter and count like any other label.
     *
     * The table layout is unchanged. An entry whose tags or people change counts as edited, so
     * sync uploads it: otherwise its content would differ from the synced copy with nothing to
     * say which side is newer, and sync would put the old copy back.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Running database migration from version 8 to 9..."
            )
            // Whatever goes wrong here, the journal has to open: the old data stays readable
            // as it is, and entries are unified again whenever they are saved.
            try {
                unifyPeople(db)
            } catch (e: Exception) {
                AppLogger.d(
                    AppLogger.Category.DATABASE,
                    "DatabaseMigrations",
                    "Could not unify people: ${e.message}"
                )
            }
            AppLogger.d(
                AppLogger.Category.DATABASE,
                "DatabaseMigrations",
                "Successfully completed database migration from version 8 to 9."
            )
        }

        private fun unifyPeople(db: SupportSQLiteDatabase) {
            val converters = JournalTypeConverters()
            val now = System.currentTimeMillis()
            data class Row(val id: String, val tags: String, val people: String)

            val rows = mutableListOf<Row>()
            db.query(
                "SELECT `id`, `tags`, `people` FROM `journals` " +
                    "WHERE `tags` LIKE '%\"@%' OR `people` != '[]' ORDER BY `dateTime` DESC, `createdAt` DESC"
            ).use { c ->
                while (c.moveToNext()) rows += Row(c.getString(0), c.getString(1), c.getString(2))
            }

            val before = rows.map { row ->
                PeopleUnification.Entry(row.id, converters.toImagesList(row.tags), converters.toPeopleList(row.people))
            }
            val after = PeopleUnification.unify(before)

            db.execSQL("DELETE FROM `journal_tag_cross_ref` WHERE `tagName` LIKE '@%'")
            db.execSQL("DELETE FROM `tags` WHERE `name` LIKE '@%'")
            before.zip(after).forEachIndexed { index, (old, new) ->
                // people that could not be read are left exactly as they are
                val unreadable = old.people.isEmpty() && rows[index].people.trim() != "[]"
                if (!unreadable && (old.tags != new.tags || old.people != new.people)) {
                    db.execSQL(
                        "UPDATE `journals` SET `tags` = ?, `people` = ?, `updatedAt` = ? WHERE `id` = ?",
                        arrayOf<Any>(
                            converters.fromImagesList(new.tags),
                            converters.fromPeopleList(new.people),
                            now,
                            new.id
                        )
                    )
                }
                for (person in new.people) {
                    db.execSQL("INSERT OR IGNORE INTO `tags` (`name`) VALUES (?)", arrayOf<Any>(person.key))
                    db.execSQL(
                        "INSERT OR IGNORE INTO `journal_tag_cross_ref` (`id`, `tagName`) VALUES (?, ?)",
                        arrayOf<Any>(new.id, person.key)
                    )
                }
            }
        }
    }
}
