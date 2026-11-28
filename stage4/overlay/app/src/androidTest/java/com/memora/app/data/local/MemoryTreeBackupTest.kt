package com.memora.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.memora.app.data.repository.MemoryTreeRepository
import com.memora.app.export.MemoryTreeBackupCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryTreeBackupTest {
    @Test fun completeReturnRestorePreservesCountsAndHistory()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val source=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val a=MemoryTreeRepository(source);val b=MemoryTreeRepository(target)
            for(db in listOf(source,target)) {
                db.noteDao().upsert(NoteEntity("n","SHORT","t","","body",1L,1L,1))
                db.noteDao().upsert(NoteEntity("waiting","LONG","w","","body",1L,1L,1))
            }
            val oldPhoto=source.memoryAlbumDao().insert(MemoryAlbumPhotoEntity(relativePath="memory_album/source.jpg",capturedAt=1))
            target.memoryAlbumDao().insert(MemoryAlbumPhotoEntity(relativePath="memory_album/unrelated.jpg",capturedAt=1))
            val newPhoto=target.memoryAlbumDao().insert(MemoryAlbumPhotoEntity(relativePath="memory_album/source.jpg",capturedAt=1))
            assertTrue(a.deliver("old","NOTE:n",2,2))
            assertTrue(a.setLike("old",true,3))
            assertTrue(a.setImpression("old","first",4))
            assertTrue(a.complete("old",5))
            assertEquals(1,a.returnTreasure(6))
            assertTrue(a.deliver("current","NOTE:n",7,7))
            assertTrue(a.setLike("current",true,8))
            assertTrue(a.complete("current",9))
            assertTrue(a.deliver("photo","PHOTO:$oldPhoto",10,10))
            assertTrue(a.complete("photo",11))
            assertTrue(a.deliver("pending","NOTE:waiting",12,12))
            val keys=setOf("NOTE:n","NOTE:waiting","PHOTO:$oldPhoto")
            val snap=a.snapshotForBackup(keys)
            val decoded=MemoryTreeBackupCodec.decode(MemoryTreeBackupCodec.encode(snap))
            assertEquals(snap,decoded)
            val map=mapOf("NOTE:n" to "NOTE:n","NOTE:waiting" to "NOTE:waiting","PHOTO:$oldPhoto" to "PHOTO:$newPhoto")
            b.restoreTree(decoded,map);b.restoreTree(decoded,map)
            assertEquals(2L,b.observeTreasureCount().first())
            assertEquals(2L,b.observeLikeCount("NOTE:n").first())
            assertEquals("first",b.observeImpressions("NOTE:n").first().single().impression)
            assertEquals(1L,b.observeCounts().first().pending)
            assertEquals(2,b.returnTreasure(13))
            assertEquals(0L,b.observeTreasureCount().first())
            assertEquals(1L,b.observeCounts().first().pending)
            assertEquals(2L,b.observeLikeCount("NOTE:n").first())
            assertEquals(2,b.observeHistory("NOTE:n").first().size)
            assertEquals(0,b.returnTreasure(14))
        } finally {source.close();target.close()}
    }

    @Test fun divergentRestoreRollsBackWholeLedger()=runBlocking {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(ctx,AppDatabase::class.java).build()
        try {
            val r=MemoryTreeRepository(db)
            db.noteDao().upsert(NoteEntity("n","SHORT","t","","body",1L,1L,1))
            db.noteDao().upsert(NoteEntity("x","SHORT","t","","body",1L,1L,1))
            assertTrue(r.deliver("local","NOTE:n",2,2))
            val incoming=TreeBackupSnapshot(listOf(
                TreeRecallEntity("new","NOTE:x","NOTE:x",3,3,"PENDING"),
                TreeRecallEntity("other","NOTE:n","NOTE:n",3,3,"PENDING")
            ),emptyList())
            var rejected=false
            try {r.restoreTree(incoming,mapOf("NOTE:x" to "NOTE:x","NOTE:n" to "NOTE:n"))}
            catch(_:IllegalArgumentException){rejected=true}
            assertTrue(rejected)
            assertNull(r.getEvent("new"))
            assertNotNull(r.getEvent("local"))
        } finally {db.close()}
    }

    @Test fun malformedManifestWithDuplicateCurrentKeysRejected() {
        val invalid=TreeBackupSnapshot(listOf(
            TreeRecallEntity("a","NOTE:n","NOTE:n",1,1,"PENDING"),
            TreeRecallEntity("b","NOTE:n","NOTE:n",2,2,"PENDING")
        ),emptyList())
        var rejected=false
        try {MemoryTreeBackupCodec.decode(MemoryTreeBackupCodec.encode(invalid))}
        catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected)
    }
}
