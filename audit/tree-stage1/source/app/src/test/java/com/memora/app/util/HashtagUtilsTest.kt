package com.memora.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagUtilsTest {
    @Test
    fun extractsUnicodeNestedAndDashedTagsInOrderWithoutDuplicates() {
        val tags = HashtagUtils.extract("#Tahlil matn #SavolJavob #kitoblar/Taleb #ğoya_test #a-b #Tahlil")
        assertEquals(listOf("Tahlil", "SavolJavob", "kitoblar/Taleb", "ğoya_test", "a-b"), tags)
    }

    @Test
    fun ignoresHashInsideWord() {
        assertTrue(HashtagUtils.extract("abc#tag").isEmpty())
    }

    @Test
    fun writesObsidianNativeTagSyntax() {
        assertEquals("#Tahlil #SavolJavob", HashtagUtils.markdown(listOf("Tahlil", "SavolJavob")))
    }
}
