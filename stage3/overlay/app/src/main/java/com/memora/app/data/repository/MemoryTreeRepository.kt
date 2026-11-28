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
    suspend fun setImpression(id: String, body: String, at: Long): Boolean {
        require(body.isNotBlank())
        return dao.setImpression(id,body,at) == 1
    }
    suspend fun returnTreasure(at: Long) = dao.returnTreasure(at)
}
