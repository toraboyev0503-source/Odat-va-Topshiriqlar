package com.memora.app.ui.components

import kotlin.math.ln
import kotlin.math.min
import kotlin.random.Random

/** Normalized drawing coordinates. No Android objects, no dependence on wall clock. */
internal data class TreeTwig(
    val x0: Float,val y0: Float,val cx: Float,val cy: Float,
    val x1: Float,val y1: Float,val width: Float,val stage: Int
)
internal data class TreeGlyph(val x: Float,val y: Float,val angle: Float,val length: Float,val kind: Int,val tone: Float)

internal object MemoryTreeGeometry {
    const val MAX_GLYPHS = 180
    const val WIDTH = 1000f
    const val HEIGHT = 1140f
    const val ROOT_X = 500f
    const val ROOT_Y = 825f
    val twigs = listOf(
        TreeTwig(490f,710f,420f,570f,283f,481f,30f,0),
        TreeTwig(506f,670f,585f,539f,727f,422f,28f,0),
        TreeTwig(496f,576f,478f,392f,510f,156f,25f,0),
        TreeTwig(418f,570f,324f,479f,157f,383f,16f,1),
        TreeTwig(570f,565f,715f,490f,874f,339f,17f,1),
        TreeTwig(465f,480f,382f,358f,282f,243f,18f,1),
        TreeTwig(534f,480f,619f,334f,693f,173f,16f,1),
        TreeTwig(373f,471f,245f,396f,85f,475f,12f,2),
        TreeTwig(287f,425f,196f,298f,107f,268f,10f,2),
        TreeTwig(352f,378f,260f,207f,175f,175f,10f,2),
        TreeTwig(464f,354f,393f,215f,346f,106f,12f,2),
        TreeTwig(525f,300f,561f,167f,614f,89f,10f,2),
        TreeTwig(655f,329f,740f,234f,835f,191f,11f,2),
        TreeTwig(686f,459f,811f,472f,932f,429f,11f,2),
        TreeTwig(427f,612f,293f,598f,171f,557f,13f,3),
        TreeTwig(576f,614f,705f,580f,830f,556f,13f,3),
        TreeTwig(277f,483f,179f,510f,117f,527f,7f,3),
        TreeTwig(705f,422f,769f,362f,944f,285f,8f,3),
        TreeTwig(283f,243f,268f,157f,224f,103f,7f,3),
        TreeTwig(510f,156f,483f,111f,482f,44f,8f,3),
        TreeTwig(693f,173f,758f,124f,801f,93f,7f,4),
        TreeTwig(171f,557f,114f,617f,71f,603f,7f,4),
        TreeTwig(830f,556f,906f,580f,947f,527f,7f,4),
        TreeTwig(346f,106f,386f,69f,410f,49f,5f,5),
        TreeTwig(614f,89f,664f,54f,704f,68f,5f,5)
    )
    // Fixed positions with at least ~50dp separation at ordinary phone widths.
    val groundSlots = listOf(.88f to .66f,.88f to .81f,.78f to .94f,.51f to .94f,.24f to .94f)

    fun growth(total: Long): Float {
        val n=total.coerceAtLeast(0L)
        fun part(low: Long,high: Long,start: Float,end: Float): Float =
            start + (end-start)*((n-low).toDouble()/(high-low).toDouble()).coerceIn(0.0,1.0).toFloat()
        return when {
            n==0L -> .20f
            n<=10L -> part(0,10,.20f,.40f)
            n<=50L -> part(10,50,.40f,.63f)
            n<=200L -> part(50,200,.63f,.91f)
            n<=1000L -> part(200,1000,.91f,.98f)
            else -> (.98f + .02f*(ln(n.toDouble()/1000.0)/ln(100.0)).coerceIn(0.0,1.0).toFloat())
        }
    }
    /** Exact small counts; proportional bounded visual samples for large trees. */
    fun distribution(short: Long,long: Long,photo: Long): IntArray {
        val values=listOf(short,long,photo).map { it.coerceAtLeast(0L) }
        val total=values.sumOf { it.toDouble() }
        if(total==0.0) return IntArray(3)
        val target=min(MAX_GLYPHS.toDouble(),total).toInt()
        val result=IntArray(3)
        val positive=values.indices.filter { values[it]>0L }
        positive.forEach { result[it]=1 }
        val remaining=target-positive.size
        val weights=values.map { (it-1L).coerceAtLeast(0L).toDouble() }
        val weightSum=weights.sum()
        if(remaining>0 && weightSum>0.0){
            val desired=weights.map { it/weightSum*remaining }
            desired.forEachIndexed { i,d -> result[i]+=d.toInt() }
            val order=desired.indices.sortedByDescending { desired[it]-desired[it].toInt() }
            repeat(target-result.sum()){ i -> result[order[i%order.size]]++ }
        }
        return result
    }
    fun glyphs(stage: Int,short: Long,long: Long,photo: Long): List<TreeGlyph> {
        val counts=distribution(short,long,photo)
        val kinds=counts.flatMapIndexed { kind,n -> List(n){kind} }.shuffled(Random(7321))
        val tips=twigs.filter { it.stage<=stage.coerceIn(0,5) }.shuffled(Random(4912))
        val r=Random(1779)
        return kinds.mapIndexed { i,kind ->
            // Spread the canopy evenly among stable branch tips, not independent random dots.
            val tip=tips[i%tips.size]
            val ring=(i/tips.size).coerceAtMost(12)
            val radius=16f+ring*5f
            val x=(tip.x1+(r.nextFloat()-.5f)*radius*2f).coerceIn(43f,957f)
            val y=(tip.y1+(r.nextFloat()-.5f)*radius*1.5f).coerceIn(38f,665f)
            val length=57f+r.nextFloat()*38f
            TreeGlyph(x,y,(r.nextFloat()-.5f)*155f,length,kind,r.nextFloat())
        }.sortedBy { it.y }
    }
}
