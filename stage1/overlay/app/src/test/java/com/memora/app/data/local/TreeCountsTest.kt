package com.memora.app.data.local
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeCountsTest {
    @Test fun allGrowthBoundaries() {
        listOf(0L to 0,1L to 1,10L to 1,11L to 2,50L to 2,51L to 3,200L to 3,201L to 4,1000L to 4,1001L to 5,100000L to 5).forEach { (n,stage) ->
            assertEquals(stage,TreeCounts(n,n,0,0).growthStage)
        }
    }
    @Test fun queueAndGrowthAreIndependentOfState() {
        val counts=TreeCounts(171,150,9,12)
        assertEquals(5L,counts.visible)
        assertEquals(4L,counts.queued)
        assertEquals(3,counts.growthStage)
        assertEquals(counts.total,counts.onTree+counts.pending+counts.treasure)
    }
}
