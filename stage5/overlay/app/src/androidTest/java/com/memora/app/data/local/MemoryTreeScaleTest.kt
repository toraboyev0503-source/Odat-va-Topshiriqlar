package com.memora.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.memora.app.data.repository.MemoryTreeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MemoryTreeScaleTest {
    @Test fun countsAndQueueAtFourScales()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val sql=db.openHelper.writableDatabase
            val tree=MemoryTreeRepository(db)
            var prior=0
            for(n in listOf(100,1000,10000,100000)) {
                sql.execSQL("""WITH RECURSIVE seq(x) AS (SELECT ${prior+1} UNION ALL SELECT x+1 FROM seq WHERE x<$n)
                    INSERT INTO notes(id,type,title,contentHtml,plainText,createdAt,updatedAt,wordCount,blocksJson,hasImages,hasAudio,mood,hashtagsJson)
                    SELECT 'stress-'||x,CASE WHEN x%2=0 THEN 'SHORT' ELSE 'LONG' END,'t','','body',1000,1000,1,'',0,0,NULL,'[]' FROM seq""")
                prior=n
                if(n==100) {
                    for(i in 1..15) assertTrue(tree.deliver("stress-event-$i","NOTE:stress-$i",2000,3000L+i))
                    for(i in 1..8) assertTrue(tree.complete("stress-event-$i",4000))
                }
                val start=System.nanoTime()
                val counts=tree.observeVisualCounts().first()
                val elapsed=(System.nanoTime()-start)/1_000_000
                assertEquals(n.toLong(),counts.total)
                assertEquals(7L,counts.pending);assertEquals(8L,counts.treasure)
                assertEquals(n.toLong()-15,counts.onTree)
                assertEquals(counts.total,counts.onTree+counts.pending+counts.treasure)
                assertEquals(5,tree.observeVisualLeaves().first().size)
                assertEquals(2L,counts.counts.queued)
                android.util.Log.i("MemoryTreeScale","n=$n visualCountMs=$elapsed")
            }
            assertEquals(8,tree.returnTreasure(5000))
            assertEquals(99993L,tree.observeCounts().first().onTree)
            assertEquals(7L,tree.observeCounts().first().pending)
        } finally {db.close()}
    }

    @Test fun delayedPlansRespectDailyCap()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val tree=MemoryTreeRepository(db)
            val zone=ZoneId.of("Asia/Tashkent")
            val today=LocalDate.of(2026,11,28)
            val now=today.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
            for(i in 1..6) db.noteDao().upsert(NoteEntity("n$i","SHORT","t","","body",1L,1L,1))
            db.memoryRecallDao().insert((1..6).map { i ->
                MemoryRecallEventEntity("tree:late:$i",today.minusDays(1).toString(),"NOTE:n$i",now-86400000)
            })
            for(i in 1..2) assertNotNull(tree.deliverPlanned("tree:late:$i",now,2,zone))
            for(i in 3..6) assertNull(tree.deliverPlanned("tree:late:$i",now,2,zone))
            assertEquals(2L,tree.observeCounts().first().pending)
            assertNotNull(tree.deliverPlanned("tree:late:3",now+86400000,2,zone))
        } finally{db.close()}
    }
}
