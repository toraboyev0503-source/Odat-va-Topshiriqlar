package com.memora.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        SavedTitleEntity::class,
        ReminderEntity::class,
        TimeTracePhotoEntity::class,
        TopilmaSourceEntity::class,
        TopilmaCardEntity::class,
        TopilmaCommentEntity::class,
        TopilmaImageEntity::class,
        TopilmaTagEntity::class,
        TopilmaCardTagEntity::class,
        TopilmaSourceTagEntity::class,
        TopilmaReviewEventEntity::class,
        TopilmaReviewSessionEntity::class,
        TopilmaReviewSessionItemEntity::class,
        ObsidianSyncRecordEntity::class,
        MemoryAlbumPhotoEntity::class,
        MemoryRecallEventEntity::class,
        TreeRecallEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun savedTitleDao(): SavedTitleDao
    abstract fun reminderDao(): ReminderDao
    abstract fun timeTraceDao(): TimeTraceDao
    abstract fun topilmaSourceDao(): TopilmaSourceDao
    abstract fun topilmaCardDao(): TopilmaCardDao
    abstract fun topilmaCommentDao(): TopilmaCommentDao
    abstract fun topilmaImageDao(): TopilmaImageDao
    abstract fun topilmaTagDao(): TopilmaTagDao
    abstract fun topilmaReviewDao(): TopilmaReviewDao
    abstract fun obsidianSyncDao(): ObsidianSyncDao
    abstract fun memoryAlbumDao(): MemoryAlbumDao
    abstract fun memoryRecallDao(): MemoryRecallDao
    abstract fun memoryTreeDao(): MemoryTreeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN blocksJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN hasImages INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN hasAudio INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_titles ADD COLUMN pinnedAt INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN mood TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `time_trace_photos` (
                        `dateKey` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        `imported` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trace_photos_capturedAt` ON `time_trace_photos` (`capturedAt`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_sources` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `normalizedTitle` TEXT NOT NULL, `author` TEXT, `normalizedAuthor` TEXT, `coverPath` TEXT, `canonicalUri` TEXT, `originAppPackage` TEXT, `originAppName` TEXT, `originAppIconPath` TEXT, `sourceType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastAddedAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `excludedFromReview` INTEGER NOT NULL, `userEdited` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topilma_sources_canonicalUri` ON `topilma_sources` (`canonicalUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_sources_normalizedTitle_normalizedAuthor` ON `topilma_sources` (`normalizedTitle`, `normalizedAuthor`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_sources_archived_lastAddedAt` ON `topilma_sources` (`archived`, `lastAddedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_sources_sourceType` ON `topilma_sources` (`sourceType`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_cards` (`id` TEXT NOT NULL, `sourceId` TEXT, `text` TEXT NOT NULL, `sourceUri` TEXT, `originAppPackage` TEXT, `originAppName` TEXT, `createdAt` INTEGER NOT NULL, `originalSourceDate` INTEGER, `updatedAt` INTEGER NOT NULL, `lastEditedAt` INTEGER, `favorite` INTEGER NOT NULL, `excludedFromReview` INTEGER NOT NULL, `deletedAt` INTEGER, `contentFingerprint` TEXT NOT NULL, `firstReviewAt` INTEGER NOT NULL, `lastReviewedAt` INTEGER, `nextReviewAt` INTEGER NOT NULL, `reviewIntervalDays` INTEGER NOT NULL, `reviewCount` INTEGER NOT NULL, `lastRating` TEXT, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_sourceId_createdAt` ON `topilma_cards` (`sourceId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_createdAt` ON `topilma_cards` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_updatedAt` ON `topilma_cards` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_nextReviewAt` ON `topilma_cards` (`nextReviewAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_favorite` ON `topilma_cards` (`favorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_contentFingerprint` ON `topilma_cards` (`contentFingerprint`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_cards_deletedAt` ON `topilma_cards` (`deletedAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_comments` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `editedAt` INTEGER, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_comments_cardId_createdAt` ON `topilma_comments` (`cardId`, `createdAt`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_images` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_images_cardId_position` ON `topilma_images` (`cardId`, `position`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topilma_tags_normalizedName` ON `topilma_tags` (`normalizedName`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_card_tags` (`cardId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`cardId`, `tagId`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_card_tags_tagId` ON `topilma_card_tags` (`tagId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_source_tags` (`sourceId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `tagId`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_source_tags_tagId` ON `topilma_source_tags` (`tagId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_review_events` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `rating` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, `previousIntervalDays` INTEGER NOT NULL, `nextIntervalDays` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_review_events_cardId_reviewedAt` ON `topilma_review_events` (`cardId`, `reviewedAt`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_review_sessions` (`id` TEXT NOT NULL, `dayKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `targetCount` INTEGER NOT NULL, `currentIndex` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topilma_review_sessions_dayKey` ON `topilma_review_sessions` (`dayKey`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `topilma_review_session_items` (`sessionId` TEXT NOT NULL, `cardId` TEXT NOT NULL, `position` INTEGER NOT NULL, `ratedAt` INTEGER, `rating` TEXT, PRIMARY KEY(`sessionId`, `cardId`))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topilma_review_session_items_sessionId_position` ON `topilma_review_session_items` (`sessionId`, `position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topilma_review_session_items_cardId` ON `topilma_review_session_items` (`cardId`)")
            }
        }


        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE topilma_cards ADD COLUMN likeCount INTEGER NOT NULL DEFAULT 0")
            }
        }


        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN hashtagsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `obsidian_sync_records` (
                    `key` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `relativePath` TEXT,
                    `state` TEXT NOT NULL,
                    `lastSyncedAt` INTEGER,
                    `lastError` TEXT,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )""".trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_obsidian_sync_records_entityType_entityId` ON `obsidian_sync_records` (`entityType`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_obsidian_sync_records_state_updatedAt` ON `obsidian_sync_records` (`state`, `updatedAt`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep both the legacy topilma tables and old Time Trace photos intact.
                db.execSQL("""CREATE TABLE IF NOT EXISTS `memory_album_photos` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `capturedAt` INTEGER NOT NULL,
                    `caption` TEXT NOT NULL,
                    `effect` TEXT NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_album_photos_capturedAt` ON `memory_album_photos` (`capturedAt`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `memory_recall_events` (
                    `id` TEXT NOT NULL,
                    `dayKey` TEXT NOT NULL,
                    `noteId` TEXT NOT NULL,
                    `scheduledAt` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `deliveredAt` INTEGER,
                    PRIMARY KEY(`id`)
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_recall_events_dayKey` ON `memory_recall_events` (`dayKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_recall_events_noteId` ON `memory_recall_events` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_recall_events_scheduledAt` ON `memory_recall_events` (`scheduledAt`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Auxiliary FTS4 index: the notes table is still the single source of truth.
                // No note text, timestamps, blocks, media paths or IDs are changed here.
                db.execSQL("""CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(
                    noteId, title, plainText, tokenize=unicode61
                )""")
                db.execSQL("""INSERT INTO notes_fts (noteId, title, plainText)
                    SELECT id, title, plainText FROM notes""")
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS notes_fts_insert AFTER INSERT ON notes BEGIN
                    INSERT INTO notes_fts(noteId,title,plainText)
                    VALUES(new.id,new.title,new.plainText); END""")
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS notes_fts_update AFTER UPDATE OF id,title,plainText ON notes BEGIN
                    DELETE FROM notes_fts WHERE noteId=old.id;
                    INSERT INTO notes_fts(noteId,title,plainText)
                    VALUES(new.id,new.title,new.plainText); END""")
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS notes_fts_delete AFTER DELETE ON notes BEGIN
                    DELETE FROM notes_fts WHERE noteId=old.id; END""")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS memory_tree_recall (
                    id TEXT NOT NULL PRIMARY KEY, sourceKey TEXT NOT NULL, currentKey TEXT,
                    scheduledAt INTEGER NOT NULL, deliveredAt INTEGER, state TEXT NOT NULL,
                    completedAt INTEGER, returnedAt INTEGER, likedAt INTEGER, impression TEXT,
                    impressionCreatedAt INTEGER, impressionEditedAt INTEGER)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_tree_recall_sourceKey ON memory_tree_recall(sourceKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_memory_tree_recall_currentKey ON memory_tree_recall(currentKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_tree_recall_state_deliveredAt ON memory_tree_recall(state, deliveredAt)")
                // Existing completed recall history has no exact completion timestamp.
                // Preserve the old records as historical; never manufacture a timestamp.
                db.execSQL("""INSERT OR IGNORE INTO memory_tree_recall
                    (id, sourceKey, scheduledAt, deliveredAt, state)
                    SELECT r.id, 'NOTE:' || r.noteId, r.scheduledAt, r.deliveredAt, 'RETURNED'
                    FROM memory_recall_events r JOIN notes n ON n.id = r.noteId WHERE r.state = 'READ'""")
                // Keep one oldest outstanding delivery per existing note.
                db.execSQL("""INSERT OR IGNORE INTO memory_tree_recall
                    (id, sourceKey, currentKey, scheduledAt, deliveredAt, state)
                    SELECT r.id, 'NOTE:' || r.noteId, 'NOTE:' || r.noteId, r.scheduledAt, r.deliveredAt, 'PENDING'
                    FROM memory_recall_events r JOIN notes n ON n.id = r.noteId WHERE r.state = 'DELIVERED'
                    ORDER BY COALESCE(r.deliveredAt,r.scheduledAt), r.id""")
                installTreeDeleteTriggers(db)
            }
        }

        private fun installTreeDeleteTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS memory_tree_note_delete AFTER DELETE ON notes BEGIN
                DELETE FROM memory_tree_recall WHERE sourceKey = 'NOTE:' || old.id;
                DELETE FROM memory_recall_events WHERE (substr(id,1,5)='tree:' AND noteId='NOTE:' || old.id)
                    OR (substr(id,1,5)!='tree:' AND noteId=old.id); END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS memory_tree_photo_delete AFTER DELETE ON memory_album_photos BEGIN
                DELETE FROM memory_tree_recall WHERE sourceKey = 'PHOTO:' || old.id;
                DELETE FROM memory_recall_events WHERE substr(id,1,5)='tree:' AND noteId='PHOTO:' || old.id; END""")
        }

        // Shared list of real migrations for production and instrumented upgrade tests.
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
        )

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memora.db"
                ).addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // The migration creates the same auxiliary index for upgraded DBs.
                            // Fresh installations must also get it before the first note write.
                            MIGRATION_9_10.migrate(db)
                            installTreeDeleteTriggers(db)
                        }
                    })
                    .build().also { INSTANCE = it }
            }
    }
}
