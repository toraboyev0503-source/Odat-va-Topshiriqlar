package com.memora.app.data.repository

import androidx.room.withTransaction
import com.memora.app.data.local.AppDatabase
import com.memora.app.data.local.TreeRecallEntity
import com.memora.app.data.local.MemoryRecallEventEntity
import com.memora.app.memories.MemoryRecallPlanner
import com.memora.app.memories.RecallCandidate
import java.time.LocalDate
import java.time.ZoneId

/** Stage 1 durable state engine; stage 3 connects the existing MemoryScheduler and reader. */
class MemoryTreeRepository(private val db: AppDatabase) {
    private val dao = db.memoryTreeDao()
    fun observeCounts() = dao.observeCounts()
    fun observeVisualCounts() = dao.observeVisualCounts()
    fun observeVisualLeaves() = dao.observeVisualLeaves()
    fun observeVisible() = dao.observeVisible()
    fun observeTreasure() = dao.observeTreasure()
    fun observeTreasureCount() = dao.observeTreasureCount()
    fun pagedTreasure(ascending:Boolean) = androidx.paging.Pager(
        config=androidx.paging.PagingConfig(pageSize=32,initialLoadSize=64,maxSize=192,enablePlaceholders=false),
        pagingSourceFactory={dao.treasurePage(ascending)}
    ).flow
    suspend fun snapshotForBackup(keys:Set<String>) = db.withTransaction {
        com.memora.app.data.local.TreeBackupSnapshot(
            dao.allForBackup().filter { it.sourceKey in keys },
            dao.allOperationalForBackup().filter {
                (if(it.id.startsWith("tree:")) it.noteId else "NOTE:"+it.noteId) in keys
            }
        )
    }
    suspend fun matchingNoteIds(type:String,title:String,plain:String,at:Long)=dao.matchingNoteIds(type,title,plain,at)
    suspend fun matchingPhotos(at:Long,caption:String,effect:String)=dao.matchingPhotos(at,caption,effect)
    suspend fun photosAtPath(path:String)=dao.photosAtPath(path)

    /** Never overwrite an unrelated occurrence or a divergent local history. */
    suspend fun restoreTree(snapshot:com.memora.app.data.local.TreeBackupSnapshot,sourceMap:Map<String,String>) = db.withTransaction {
        val ids=mutableMapOf<String,String>()
        for(old in snapshot.recalls) {
            val key=sourceMap[old.sourceKey] ?: error("Missing tree source: "+old.sourceKey)
            check(dao.sourceExists(key))
            val id=if(key==old.sourceKey) old.id else remappedId(old.id,key)
            ids[old.id]=id
            val row=old.copy(id=id,sourceKey=key,currentKey=if(old.currentKey!=null)key else null)
            val present=dao.byId(id)
            if(present!=null) require(present==row) { "Conflicting memory history; existing data preserved" }
            else {
                if(row.currentKey!=null) require(dao.currentOccurrence(key)==null) { "Conflicting current memory leaf" }
                check(dao.insert(row)!=-1L)
            }
        }
        val operational=db.memoryRecallDao()
        for(old in snapshot.operational) {
            val oldKey=if(old.id.startsWith("tree:"))old.noteId else "NOTE:"+old.noteId
            val key=sourceMap[oldKey] ?: error("Missing planned memory source")
            val id=ids[old.id] ?: if(key==oldKey)old.id else remappedId(old.id,key)
            val row=old.copy(id=id,noteId=if(id.startsWith("tree:"))key else key.removePrefix("NOTE:"))
            val present=operational.byId(id)
            if(present==null) {
                if(row.state!="PLANNED" || dao.currentOccurrence(key)==null) operational.insert(listOf(row))
            } else require(present==row) { "Conflicting reminder history; existing data preserved" }
        }
    }
    private fun remappedId(oldId:String,key:String):String =
        "tree:restored:"+java.util.UUID.nameUUIDFromBytes((oldId+"\u0000"+key).toByteArray(Charsets.UTF_8)).toString()
    fun observeHistory(sourceKey: String) = dao.observeHistory(sourceKey)
    suspend fun candidates(cutoff: Long, limit: Int) = dao.candidates(cutoff,limit.coerceIn(1,1000))

    /** Use the original scheduler occurrence ID, not a fresh ID on every retry. */
    suspend fun deliver(id: String, sourceKey: String, scheduledAt: Long, deliveredAt: Long): Boolean = db.withTransaction {
        require(id.isNotBlank() && sourceKey.isNotBlank())
        val prior = dao.byId(id)
        if (prior != null) {
            require(prior.sourceKey == sourceKey) { "Occurrence identity collision" }
            false
        } else if (!dao.sourceExists(sourceKey)) {
            false
        } else {
            dao.insert(TreeRecallEntity(id,sourceKey,sourceKey,scheduledAt,deliveredAt,"PENDING")) != -1L
        }
    }

    fun observeEvent(id: String) = dao.observeEvent(id)
    suspend fun currentPendingForNote(noteId: String) = dao.currentPending("NOTE:"+noteId)
    fun observeLikeCount(key: String) = dao.observeLikeCount(key)
    fun observeImpressions(key: String) = dao.observeImpressions(key)
    fun observePhoto(id: Long) = dao.observePhoto(id)
    suspend fun getEvent(id: String) = dao.byId(id)

    /** Uses the existing daily reminder table and planner. One transactional plan per day. */
    suspend fun prepareDay(day: LocalDate,zone: ZoneId,count: Int): List<MemoryRecallEventEntity> = db.withTransaction {
        val legacy = db.memoryRecallDao()
        val dayKey = day.toString()
        if(legacy.forDay(dayKey).isEmpty()) {
            val cutoff=day.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val fresh=dao.planningCandidates(cutoff,true,1000)
            val all=if(fresh.size>=count) fresh else
                (fresh+dao.planningCandidates(cutoff,false,1000)).distinctBy { it.sourceKey }
            val first=MemoryRecallPlanner.choose(day,zone,fresh.map { RecallCandidate(it.sourceKey,it.createdAt) },emptySet(),count)
            val missing=count-first.size
            val chosen=if(missing>0) first+MemoryRecallPlanner.choose(day,zone,
                all.filterNot { item -> first.any { it.id==item.sourceKey } }.map { RecallCandidate(it.sourceKey,it.createdAt) },
                emptySet(),missing) else first
            val selectedIds=chosen.map { it.id }.toSet()
            // Re-plan the selected set together to retain the existing time spacing rule.
            val slots=MemoryRecallPlanner.choose(day,zone,all.filter { it.sourceKey in selectedIds }
                .map { RecallCandidate(it.sourceKey,it.createdAt) },emptySet(),count)
            legacy.insert(slots.map { MemoryRecallEventEntity("tree:$dayKey:${it.id}",dayKey,it.id,it.scheduledAt) })
        }
        dao.plannedEvents()
    }

    suspend fun pruneOperational(cutoff: String) = dao.pruneOperational(cutoff)

    /** Alarm receiver and missed-alarm catch-up share one atomic, idempotent delivery path. */
    suspend fun deliverPlanned(id: String,now: Long): TreeRecallEntity? = db.withTransaction {
        val event=db.memoryRecallDao().byId(id) ?: return@withTransaction null
        if(event.state!="PLANNED" || event.scheduledAt>now) return@withTransaction null
        val key=if(id.startsWith("tree:")) event.noteId else "NOTE:"+event.noteId
        val inserted=deliver(id,key,event.scheduledAt,now)
        if(!inserted) {
            dao.resolvePlan(id,"CANCELLED",null)
            return@withTransaction null
        }
        check(dao.resolvePlan(id,"DELIVERED",now)==1)
        dao.byId(id)
    }

    suspend fun complete(id: String, at: Long): Boolean = db.withTransaction {
        val done=dao.complete(id,at)==1
        if(done) dao.markOperationalRead(id)
        done
    }
    suspend fun setLike(id: String, liked: Boolean, at: Long) = dao.setLike(id,liked,at) == 1
    suspend fun setImpression(id: String, body: String, at: Long): Boolean = db.withTransaction {
        val clean=body.trim()
        require(clean.isNotBlank())
        val existing=dao.byId(id) ?: return@withTransaction false
        if(existing.state!="PENDING") return@withTransaction false
        if(existing.impression==clean) return@withTransaction true
        dao.setImpression(id,clean,at)==1
    }
    suspend fun returnTreasure(at: Long) = dao.returnTreasure(at)
}
