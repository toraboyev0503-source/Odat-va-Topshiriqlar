package com.memora.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class MemoryTreeGeometryTest {
    @Test fun growthIsMonotonicAndBounded() {
        var previous=0f
        for(n in 0L..100000L) {
            val g=MemoryTreeGeometry.growth(n)
            assertTrue(g>=previous && g<=1f)
            previous=g
        }
    }
    @Test fun exactSmallAndBoundedLargeDistribution() {
        assertArrayEquals(intArrayOf(0,0,0),MemoryTreeGeometry.distribution(0,0,0))
        assertArrayEquals(intArrayOf(139,26,6),MemoryTreeGeometry.distribution(139,26,6))
        val large=MemoryTreeGeometry.distribution(99998,1,1)
        assertEquals(180,large.sum())
        assertTrue(large.all { it>0 })
        val equal=MemoryTreeGeometry.distribution(100000,100000,100000)
        assertEquals(180,equal.sum())
        assertTrue(equal.maxOrNull()!!-equal.minOrNull()!!<=1)
    }
    @Test fun deterministicCanopyAndFiveGroundSlots() {
        val a=MemoryTreeGeometry.glyphs(5,70000,20000,10000)
        assertEquals(a,MemoryTreeGeometry.glyphs(5,70000,20000,10000))
        assertEquals(180,a.size)
        assertTrue(a.all { it.x in 0f..1000f && it.y in 0f..1140f })
        assertEquals(5,MemoryTreeGeometry.groundSlots.distinct().size)
    }
}
