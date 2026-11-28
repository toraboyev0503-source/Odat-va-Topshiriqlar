package com.memora.app

import com.memora.app.data.repository.MemoraRepository
import com.memora.app.util.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TextUtilsTest {
    @Test
    fun wordCounterHandlesWhitespace() {
        assertEquals(0, TextUtils.countWords("   "))
        assertEquals(4, TextUtils.countWords("one  two\nthree\tfour"))
        assertEquals(4, MemoraRepository.countWords("one  two\nthree\tfour"))
    }

    @Test
    fun previewNormalizesWhitespace() {
        assertEquals("Hello world", TextUtils.preview("Hello   \n world"))
    }

    @Test
    fun xmlEscapeProtectsDocxXml() {
        assertEquals("&lt;a&amp;b&gt;", TextUtils.xmlEscape("<a&b>"))
    }
}
