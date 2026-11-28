package com.memora.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Query sanitization and SQL shape: bound parameters, no string injection. */
class NoteSearchQueryTest {
    @Test fun unicodeSearchUsesPrefixTokens() {
        assertEquals("\"O\"* \"zbek\"* \"matn\"*", NoteSearchQuery.safeFtsQuery("O'zbek matn"))
    }

    @Test fun operatorsCannotEscapeQuotedFtsTerms() {
        assertEquals("\"OR\"* \"DROP\"*", NoteSearchQuery.safeFtsQuery("OR; DROP"))
    }

    @Test fun listQuerySelectsOnlyPreviewProjection() {
        val sql = NoteSearchQuery.build(NoteListFilter(
            type = "LONG", search = "xotira", mood = "GOOD", media = "IMAGE",
            from = 123L, untilExclusive = 456L
        )).sql
        assertTrue(sql.contains("substr(n.plainText, 1, 360)"))
        assertTrue(!sql.contains("n.contentHtml"))
        assertTrue(!sql.contains("SELECT *") && !sql.contains("n.blocksJson AS"))
        assertTrue(sql.contains("notes_fts MATCH ?"))
        assertTrue(sql.contains("n.createdAt >= ?"))
        assertTrue(sql.contains("n.createdAt < ?"))
    }

    @Test fun countQueryDoesNotLoadAnyRows() {
        val sql = NoteSearchQuery.buildCount(NoteListFilter(search = "muhim")).sql
        assertTrue(sql.startsWith("SELECT COUNT(*) FROM notes AS n"))
        assertTrue(!sql.contains("ORDER BY"))
    }
}
