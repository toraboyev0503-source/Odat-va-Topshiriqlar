package com.memora.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the original 3.json fixture and Room's generated version 10 schema. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test fun upgradeThreeToTenPreservesLegacyNoteAndIndexesSearch() {
        val name = "memora-migration-3-to-10"
        helper.createDatabase(name, 3).apply {
            execSQL(
                """INSERT INTO notes
                (id, type, title, contentHtml, plainText, createdAt, updatedAt,
                 wordCount, blocksJson, hasImages, hasAudio)
                VALUES ('legacy-1', 'LONG', 'Legacy note', '<p>Original</p>',
                        'Original', 1000, 1000, 1, '', 0, 0)""".trimIndent()
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 11, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT title, plainText, mood, hashtagsJson FROM notes WHERE id = 'legacy-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy note", cursor.getString(0))
                assertEquals("Original", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertEquals("[]", cursor.getString(3))
            }
            db.query("SELECT noteId FROM notes_fts WHERE notes_fts MATCH 'Original'").use { cursor ->
                assertTrue("Existing note must be FTS indexed", cursor.moveToFirst())
                assertEquals("legacy-1", cursor.getString(0))
            }
            db.execSQL("UPDATE notes SET plainText='Updated content' WHERE id='legacy-1'")
            db.query("SELECT noteId FROM notes_fts WHERE notes_fts MATCH 'Updated'").use { cursor ->
                assertTrue("FTS UPDATE trigger must work", cursor.moveToFirst())
            }
            db.execSQL("INSERT INTO memory_tree_recall (id,sourceKey,currentKey,scheduledAt,deliveredAt,state) VALUES ('test','NOTE:legacy-1','NOTE:legacy-1',1000,1000,'PENDING')")
            db.execSQL("DELETE FROM notes WHERE id='legacy-1'")
            db.query("SELECT COUNT(*) FROM memory_tree_recall").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0,cursor.getInt(0))
            }
            db.query("SELECT noteId FROM notes_fts WHERE notes_fts MATCH 'Updated'").use { cursor ->
                assertTrue("FTS DELETE trigger must work", !cursor.moveToFirst())
            }
        }
    }
}
