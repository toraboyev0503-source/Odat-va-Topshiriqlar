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

@RunWith(AndroidJUnit4::class)
class MemoryTreeRoomTest {
    @Test fun liveSourcesQueueAndHistory() = runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val tree=MemoryTreeRepository(db)
            for(i in 1..8) {
                db.noteDao().upsert(NoteEntity("n$i","SHORT","t","", "text",1L,1L,1))
            }
            val photo=db.memoryAlbumDao().insert(MemoryAlbumPhotoEntity(relativePath="test.jpg",capturedAt=1L))
            assertEquals(9L,tree.observeCounts().first().total)
            for(i in 1..8) assertTrue(tree.deliver("e$i","NOTE:n$i",2L,i+2L))
            assertFalse(tree.deliver("e1","NOTE:n1",2L,3L))
            assertFalse(tree.deliver("duplicate","NOTE:n1",2L,3L))
            assertEquals(5,tree.observeVisible().first().size)
            assertEquals(3L,tree.observeCounts().first().queued)
            assertTrue(tree.setLike("e1",true,15L))
            assertTrue(tree.setLike("e1",true,16L))
            assertTrue(tree.setImpression("e1","a",17L))
            assertTrue(tree.setImpression("e1","b",18L))
            assertTrue(tree.complete("e1",19L))
            assertFalse(tree.complete("e1",20L))
            assertEquals("e2",tree.observeVisible().first().first().id)
            assertEquals(1,tree.returnTreasure(21L))
            assertTrue(tree.deliver("e9","NOTE:n1",22L,22L))
            val old=tree.observeHistory("NOTE:n1").first().first()
            assertEquals(15L,old.likedAt)
            assertEquals("b",old.impression)
            assertEquals(17L,old.impressionCreatedAt)
            assertEquals(18L,old.impressionEditedAt)
            assertEquals(0L,tree.observeCounts().first().treasure)
            assertTrue(tree.deliver("photo","PHOTO:$photo",22L,22L))
            val counts=tree.observeCounts().first()
            assertEquals(counts.total,counts.onTree+counts.pending+counts.treasure)
        } finally { db.close() }
    }
}
