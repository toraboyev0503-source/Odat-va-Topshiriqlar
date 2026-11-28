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
class MemoryTreeOccurrenceTest {
    @Test fun dailyPlanIsIdempotentAndPrioritizesUnseenMemories()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val tree=MemoryTreeRepository(db)
            val day=LocalDate.of(2026,11,28)
            val zone=ZoneId.of("Asia/Tashkent")
            val created=day.minusDays(20).atTime(10,0).atZone(zone).toInstant().toEpochMilli()
            for(i in 1..3) db.noteDao().upsert(NoteEntity("n$i","SHORT","t","","body",created,created,1))
            val photoId=db.memoryAlbumDao().insert(MemoryAlbumPhotoEntity(relativePath="memory_album/p.jpg",capturedAt=created))
            assertTrue(tree.deliver("old","NOTE:n1",created+1,created+2))
            assertTrue(tree.complete("old",created+3))
            assertEquals(1,tree.returnTreasure(created+4))
            val first=tree.prepareDay(day,zone,3)
            val second=tree.prepareDay(day,zone,3)
            assertEquals(first,second)
            assertEquals(setOf("NOTE:n2","NOTE:n3","PHOTO:$photoId"),first.map{it.noteId}.toSet())
            val occurrence=first.first()
            assertNull(tree.deliverPlanned(occurrence.id,occurrence.scheduledAt-1))
            val event=tree.deliverPlanned(occurrence.id,occurrence.scheduledAt+1)
            assertNotNull(event)
            assertNull(tree.deliverPlanned(occurrence.id,occurrence.scheduledAt+2))
            assertTrue(tree.setLike(occurrence.id,true,occurrence.scheduledAt+3))
            assertTrue(tree.setLike(occurrence.id,true,occurrence.scheduledAt+4))
            assertEquals(1L,tree.observeLikeCount(occurrence.noteId).first())
            assertTrue(tree.setImpression(occurrence.id,"first",occurrence.scheduledAt+5))
            assertTrue(tree.setImpression(occurrence.id,"first",occurrence.scheduledAt+6))
            assertNull(tree.getEvent(occurrence.id)?.impressionEditedAt)
            assertTrue(tree.setImpression(occurrence.id,"edited",occurrence.scheduledAt+7))
            assertEquals(1,tree.observeImpressions(occurrence.noteId).first().size)
            assertTrue(tree.complete(occurrence.id,occurrence.scheduledAt+8))
            assertFalse(tree.complete(occurrence.id,occurrence.scheduledAt+9))
            assertFalse(tree.setLike(occurrence.id,false,occurrence.scheduledAt+10))
            tree.pruneOperational(day.plusDays(200).toString())
            assertEquals("TREASURE",tree.getEvent(occurrence.id)?.state)
            assertEquals(1L,tree.observeLikeCount(occurrence.noteId).first())
            assertEquals("edited",tree.observeImpressions(occurrence.noteId).first().single().impression)
        } finally { db.close() }
    }
}
