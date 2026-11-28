package com.memora.app.data.repository

import androidx.room.withTransaction
import com.memora.app.data.local.AppDatabase
import com.memora.app.data.local.TreeRecallEntity

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

    suspend fun complete(id: String, at: Long) = dao.complete(id,at) == 1
    suspend fun setLike(id: String, liked: Boolean, at: Long) = dao.setLike(id,liked,at) == 1
    suspend fun setImpression(id: String, body: String, at: Long): Boolean {
        require(body.isNotBlank())
        return dao.setImpression(id,body,at) == 1
    }
    suspend fun returnTreasure(at: Long) = dao.returnTreasure(at)
}
