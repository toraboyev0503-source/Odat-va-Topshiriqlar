package com.memora.app.export

import com.memora.app.data.local.TreeBackupSnapshot
import com.memora.app.data.local.TreeRecallEntity
import com.memora.app.data.local.MemoryRecallEventEntity
import org.json.JSONArray
import org.json.JSONObject

/** Versioned manifest section; source identities are remapped only after source import. */
object MemoryTreeBackupCodec {
    private const val LIMIT=200_000
    fun encode(snapshot:TreeBackupSnapshot):JSONObject = JSONObject().apply {
        require(snapshot.recalls.size<=LIMIT && snapshot.operational.size<=LIMIT)
        put("version",1)
        put("recalls",JSONArray().apply {
            snapshot.recalls.forEach { r -> put(JSONObject().apply {
                put("id",r.id);put("sourceKey",r.sourceKey);put("currentKey",r.currentKey ?: JSONObject.NULL)
                put("scheduledAt",r.scheduledAt);put("deliveredAt",r.deliveredAt ?: JSONObject.NULL)
                put("state",r.state);put("completedAt",r.completedAt ?: JSONObject.NULL)
                put("returnedAt",r.returnedAt ?: JSONObject.NULL);put("likedAt",r.likedAt ?: JSONObject.NULL)
                put("impression",r.impression ?: JSONObject.NULL)
                put("impressionCreatedAt",r.impressionCreatedAt ?: JSONObject.NULL)
                put("impressionEditedAt",r.impressionEditedAt ?: JSONObject.NULL)
            }) }
        })
        put("operational",JSONArray().apply {
            snapshot.operational.forEach { r -> put(JSONObject().apply {
                put("id",r.id);put("dayKey",r.dayKey);put("noteId",r.noteId);put("scheduledAt",r.scheduledAt)
                put("state",r.state);put("deliveredAt",r.deliveredAt ?: JSONObject.NULL)
            }) }
        })
    }
    private fun JSONObject.optionalLong(key:String):Long? = if(isNull(key)) null else getLong(key)
    private fun JSONObject.optionalText(key:String):String? = if(isNull(key)) null else getString(key)
    private fun validKey(key:String):Boolean = key.length in 6..512 &&
        (key.startsWith("NOTE:") || key.startsWith("PHOTO:") && (key.removePrefix("PHOTO:").toLongOrNull() ?: 0L)>0)
    fun decode(obj:JSONObject):TreeBackupSnapshot {
        require(obj.getInt("version")==1) { "Unsupported memory tree backup" }
        val a=obj.getJSONArray("recalls"); val b=obj.getJSONArray("operational")
        require(a.length()<=LIMIT && b.length()<=LIMIT)
        val seen=mutableSetOf<String>();val current=mutableSetOf<String>()
        val recalls=(0 until a.length()).map { i ->
            val r=a.getJSONObject(i)
            val id=r.getString("id");val key=r.getString("sourceKey")
            val state=r.getString("state");val active=r.optionalText("currentKey")
            require(id.isNotBlank() && id.length<=1024 && seen.add(id) && validKey(key))
            require(state in setOf("PENDING","TREASURE","RETURNED"))
            require(if(state=="RETURNED") active==null else active==key && current.add(key))
            val row=TreeRecallEntity(id,key,active,r.getLong("scheduledAt"),r.optionalLong("deliveredAt"),state,
                r.optionalLong("completedAt"),r.optionalLong("returnedAt"),r.optionalLong("likedAt"),
                r.optionalText("impression"),r.optionalLong("impressionCreatedAt"),r.optionalLong("impressionEditedAt"))
            require(row.scheduledAt>0 && listOfNotNull(row.deliveredAt,row.completedAt,row.returnedAt,row.likedAt,
                row.impressionCreatedAt,row.impressionEditedAt).all{it>0})
            require(state!="TREASURE" || row.completedAt!=null)
            require(state!="PENDING" || row.completedAt==null)
            require((row.impression==null)==(row.impressionCreatedAt==null))
            require(row.impression==null || row.impression.isNotBlank())
            require(row.impressionEditedAt==null || row.impressionCreatedAt!=null && row.impressionEditedAt>=row.impressionCreatedAt)
            row
        }
        val operationalIds=mutableSetOf<String>()
        val operational=(0 until b.length()).map { i ->
            val r=b.getJSONObject(i)
            val id=r.getString("id");val note=r.getString("noteId");val day=r.getString("dayKey")
            val state=r.getString("state")
            require(id.isNotBlank() && id.length<=1024 && operationalIds.add(id))
            require(validKey(if(id.startsWith("tree:"))note else "NOTE:"+note))
            require(java.time.LocalDate.parse(day).toString()==day)
            require(state in setOf("PLANNED","DELIVERED","READ","DISMISSED","CANCELLED"))
            val at=r.getLong("scheduledAt");require(at>0)
            MemoryRecallEventEntity(id,day,note,at,state,r.optionalLong("deliveredAt"))
        }
        return TreeBackupSnapshot(recalls,operational)
    }
}
