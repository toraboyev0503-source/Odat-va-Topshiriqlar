package com.memora.app.ui.components

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.memora.app.data.local.TreeVisibleLeaf
import com.memora.app.data.local.TreeVisualCounts
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Home-only presentation. It never schedules or mutates memories. */
@Composable
fun MemoryTreeScene(
    counts: TreeVisualCounts,
    visibleLeaves: List<TreeVisibleLeaf>,
    dark: Boolean,
    uzbek: Boolean,
    onLeaf: ((TreeVisibleLeaf) -> Unit)?,
    onTreasure: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val active=rememberTreeResumed()
    val reduce=rememberReducedMotion()
    val stage=counts.counts.growthStage
    val glyphs=remember(stage,counts.shortOnTree,counts.longOnTree,counts.photoOnTree) {
        MemoryTreeGeometry.glyphs(stage,counts.shortOnTree,counts.longOnTree,counts.photoOnTree)
    }
    val growthTarget=MemoryTreeGeometry.growth(counts.total)
    val growth=remember { Animatable(growthTarget) }
    val bloom=remember { Animatable(1f) }
    var previousTotal by remember { mutableStateOf(counts.total) }
    LaunchedEffect(growthTarget,active,reduce) {
        if(!active || reduce) growth.snapTo(growthTarget)
        else growth.animateTo(growthTarget,tween(650,easing=FastOutSlowInEasing))
    }
    LaunchedEffect(counts.total,active,reduce) {
        val added=counts.total>previousTotal
        previousTotal=counts.total
        if(added && active && !reduce && glyphs.isNotEmpty()) {
            bloom.snapTo(0f)
            bloom.animateTo(1f,tween(700,easing=FastOutSlowInEasing))
        } else bloom.snapTo(1f)
    }
    val phase=remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active,reduce,glyphs.isNotEmpty()) {
        if(!active || reduce || glyphs.isEmpty()) { phase.floatValue=0f; return@LaunchedEffect }
        // At most 10 updates/s. Only the tiny foreground layer reads this state.
        while(isActive) {
            delay(100)
            phase.floatValue=(phase.floatValue+.12f)%(2f*PI.toFloat())
        }
    }
    val displayed=visibleLeaves.distinctBy { it.id }.take(5)
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var newIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(displayed.map { it.id },active,reduce) {
        val ids=displayed.map { it.id }.toSet()
        newIds=if(active && !reduce) seen?.let { ids-it }.orEmpty() else emptySet()
        seen=ids
    }
    val staticGlyphs=remember(glyphs) { glyphs.dropLast(minOf(4,glyphs.size)) }
    val movingGlyphs=remember(glyphs) { glyphs.takeLast(minOf(4,glyphs.size)) }
    val plaqueColor=if(dark) Color(0xffe7d7b5) else Color(0xff473e2d)
    val jarColor=if(dark) Color(0xffe6d4aa) else Color(0xff453727)

    BoxWithConstraints(modifier) {
        val sceneWidth=maxWidth
        val sceneHeight=maxHeight
        val leafSize=(sceneWidth*.14f).coerceIn(48.dp,58.dp)
        val jarWidth=(sceneWidth*.24f).coerceIn(72.dp,108.dp)
        val jarHeight=(sceneHeight*.29f).coerceIn(96.dp,150.dp)
        val plaqueWidth=(sceneWidth*.41f).coerceIn(124.dp,192.dp)
        val plaqueHeight=(sceneHeight*.13f).coerceIn(53.dp,72.dp)
        Canvas(Modifier.fillMaxSize()) { normalized { drawLandscape(dark) } }

        // Separate static canopy from the few animated leaves. Growth uses a layer transform.
        val treeLayer=Modifier.fillMaxSize().graphicsLayer {
            scaleX=growth.value; scaleY=growth.value
            transformOrigin=TransformOrigin(.5f,MemoryTreeGeometry.ROOT_Y/MemoryTreeGeometry.HEIGHT)
        }
        Canvas(treeLayer) {
            normalized {
                drawWood(stage,dark)
                staticGlyphs.forEach { g -> drawGlyph(g,dark) }
            }
        }
        Canvas(treeLayer) {
            normalized {
                movingGlyphs.forEachIndexed { index,g ->
                    val sway=sin(phase.floatValue+index*1.7f)*3.4f
                    val opening=if(index==movingGlyphs.lastIndex) bloom.value else 1f
                    if(opening>.02f) drawGlyph(g.copy(angle=g.angle+sway,length=g.length*opening),dark)
                }
            }
        }

        // Brass & glass treasure vessel; action is supplied by the stage-4 route.
        val jarDescription=if(uzbek) "Xotiralar xazinasi: ${counts.treasure}" else "Memory treasure: ${counts.treasure}"
        Box(
            Modifier.offset(x=place(sceneWidth,.15f,jarWidth),y=place(sceneHeight,.75f,jarHeight))
                .width(jarWidth).height(jarHeight)
                .then(if(onTreasure!=null) Modifier.clickable(role=Role.Button,onClick=onTreasure) else Modifier)
                .semantics { contentDescription=jarDescription },
            contentAlignment=Alignment.TopCenter
        ) {
            Canvas(Modifier.fillMaxSize()) { drawJar(counts.treasure,dark) }
            Text(counts.treasure.toString(),fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,
                color=jarColor,fontSize=if(counts.treasure.toString().length>5) 12.sp else 17.sp,maxLines=1,
                modifier=Modifier.offset(y=4.dp))
        }

        val plaqueLabel=if(uzbek) "Daraxt barglari" else "Tree leaves"
        Box(
            Modifier.offset(x=place(sceneWidth,.51f,plaqueWidth),y=place(sceneHeight,.80f,plaqueHeight))
                .width(plaqueWidth).height(plaqueHeight),
            contentAlignment=Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) { drawPlaque(dark) }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(plaqueLabel,color=plaqueColor,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Medium,fontSize=12.sp,maxLines=1)
                Text(counts.onTree.toString(),color=plaqueColor,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,
                    fontSize=if(counts.onTree.toString().length>6) 19.sp else 25.sp,maxLines=1)
            }
        }
        displayed.forEachIndexed { index,leaf ->
            key(leaf.id) {
                val slot=MemoryTreeGeometry.groundSlots[index]
                val x=place(sceneWidth,slot.first,leafSize)
                val y=place(sceneHeight,slot.second,leafSize)
                FallingLeafControl(
                    leaf=leaf,index=index,dark=dark,uzbek=uzbek,
                    active=active,reduce=reduce,animate=leaf.id in newIds,
                    phase=phase,onClick=onLeaf?.let { callback -> { callback(leaf) } },
                    sceneWidth=sceneWidth,sceneHeight=sceneHeight,x=x,y=y,diameter=leafSize
                )
            }
        }
        if(counts.counts.queued>0L) {
            val label=if(uzbek) "Navbatda: ${counts.counts.queued}" else "Waiting: ${counts.counts.queued}"
            Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp,maxLines=1,
                modifier=Modifier.align(Alignment.BottomEnd).offset(x=(-5).dp,y=(-2).dp))
        }
    }
}

private fun place(maximum: Dp,ratio: Float,diameter: Dp): Dp =
    (maximum*ratio-diameter/2f).coerceIn(0.dp,(maximum-diameter).coerceAtLeast(0.dp))

@Composable
private fun FallingLeafControl(
    leaf: TreeVisibleLeaf,index: Int,dark: Boolean,uzbek: Boolean,active: Boolean,reduce: Boolean,animate: Boolean,
    phase: androidx.compose.runtime.FloatState,onClick: (() -> Unit)?,
    sceneWidth: Dp,sceneHeight: Dp,x: Dp,y: Dp,diameter: Dp
) {
    val progress=remember(leaf.id) { Animatable(1f) }
    var played by remember(leaf.id) { mutableStateOf(false) }
    LaunchedEffect(animate,active,reduce) {
        if(!animate || !active || reduce) {
            if(animate) played=true
            progress.snapTo(1f)
        } else if(animate && !played) {
            played=true; progress.snapTo(0f)
            progress.animateTo(1f,tween(2600,easing=FastOutSlowInEasing))
        }
    }
    val density=LocalDensity.current
    val dx=with(density) { (sceneWidth*.54f-x-diameter/2f).toPx() }
    val dy=with(density) { (sceneHeight*.32f-y-diameter/2f).toPx() }
    val drift=with(density) { 19.dp.toPx() }
    val kind=when(leaf.kind) {"SHORT"->0;"LONG"->1;else->2}
    val kindLabel=if(uzbek) when(kind){0->"Qisqa yozuv";1->"Uzun yozuv";else->"Kun izi"} else
        when(kind){0->"Short note";1->"Long note";else->"Day trace"}
    val label=if(uzbek) "$kindLabel, eslatma ${index+1}" else "$kindLabel, memory ${index+1}"
    Box(
        Modifier.offset(x=x,y=y).size(diameter)
            .graphicsLayer {
                val p=progress.value
                translationX=dx*(1f-p)+sin(p*PI.toFloat()*3f)*drift*(1f-p)
                translationY=dy*(1f-p)
                rotationZ=sin(p*PI.toFloat()*2f)*28f
            }
            .then(if(onClick!=null) Modifier.clickable(role=Role.Button,enabled=progress.value>.98f,onClick=onClick) else Modifier)
            .semantics { contentDescription=label }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius=size.minDimension*.48f
            val pulse=if(reduce || !active) .5f else .5f+.14f*sin(phase.floatValue+index*.7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xfff8dfa0).copy(alpha=pulse),
                Color(0xffdcb766).copy(alpha=.18f),Color.Transparent),center=center,radius=radius),radius,center)
            drawOval(Color.Black.copy(alpha=if(dark) .20f else .10f),
                topLeft=Offset(size.width*.20f,size.height*.73f),size=Size(size.width*.60f,size.height*.09f))
            drawLeaf(center.x,center.y,if(index%2==0) 56f else -58f,size.minDimension*.57f,kind,.5f,dark)
        }
    }
}

@Composable
private fun rememberTreeResumed(): Boolean {
    val owner=LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ -> resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun current(): Boolean =
        (Build.VERSION.SDK_INT>=26 && !ValueAnimator.areAnimatorsEnabled()) ||
            runCatching { Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }.getOrDefault(false)
    var reduced by remember(resolver) { mutableStateOf(current()) }
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced=current() }
        }
        val registered=runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        }.isSuccess
        onDispose { if(registered) resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun DrawScope.normalized(block: DrawScope.() -> Unit) {
    val sx=size.width/MemoryTreeGeometry.WIDTH
    val sy=size.height/MemoryTreeGeometry.HEIGHT
    withTransform({ scale(sx,sy,Offset.Zero) }) { block() }
}

private fun DrawScope.drawLandscape(dark: Boolean) {
    val earth=if(dark) Color(0xff3f4c3d) else Color(0xffc3c4a1)
    drawOval(Brush.radialGradient(listOf(earth.copy(alpha=.72f),earth.copy(alpha=.28f),Color.Transparent),
        center=Offset(500f,924f),radius=480f),Offset(12f,790f),Size(976f,300f))
    drawOval(Color(0xff423b2b).copy(alpha=if(dark) .25f else .11f),Offset(214f,814f),Size(572f,97f))
    val r=Random(912)
    repeat(64) {
        val x=36f+r.nextFloat()*928f
        val y=865f+r.nextFloat()*125f
        val h=7f+r.nextFloat()*18f
        val c=if(dark) Color(0xff839779) else Color(0xff899773)
        drawLine(c.copy(alpha=.50f),Offset(x,y),Offset(x-5f,y-h),strokeWidth=2.2f,cap=StrokeCap.Round)
        drawLine(c.copy(alpha=.42f),Offset(x,y),Offset(x+6f,y-h*.72f),strokeWidth=1.7f,cap=StrokeCap.Round)
    }
    repeat(10) {
        val x=82f+r.nextFloat()*836f; val y=900f+r.nextFloat()*82f
        drawOval(if(dark) Color(0xff697267) else Color(0xffa4a89a),Offset(x,y),Size(12f+r.nextFloat()*18f,7f+r.nextFloat()*6f))
    }
}

private fun DrawScope.drawWood(stage: Int,dark: Boolean) {
    val shadow=if(dark) Color(0xff4b3929) else Color(0xff705437)
    val middle=if(dark) Color(0xff876b48) else Color(0xff92704a)
    val light=if(dark) Color(0xffb39b70) else Color(0xffbfa478)
    val trunk=Path().apply {
        moveTo(345f,838f); cubicTo(424f,815f,455f,772f,463f,689f)
        cubicTo(474f,582f,487f,530f,477f,420f)
        cubicTo(480f,340f,497f,248f,510f,156f)
        cubicTo(516f,269f,521f,352f,520f,425f)
        cubicTo(515f,530f,550f,617f,548f,685f)
        cubicTo(550f,775f,593f,811f,652f,838f)
        cubicTo(585f,832f,545f,817f,529f,798f)
        cubicTo(523f,821f,517f,834f,503f,849f)
        cubicTo(487f,837f,478f,817f,472f,801f)
        cubicTo(437f,823f,391f,835f,345f,838f); close()
    }
    val twigs=MemoryTreeGeometry.twigs.filter { it.stage<=stage }
    twigs.asReversed().forEach { b ->
        val p=Path().apply { moveTo(b.x0,b.y0); quadraticBezierTo(b.cx,b.cy,b.x1,b.y1) }
        drawPath(p,shadow,style=Stroke(width=b.width,cap=StrokeCap.Round))
        drawPath(p,middle,style=Stroke(width=b.width*.66f,cap=StrokeCap.Round))
        val highlight=Path().apply { moveTo(b.x0-2,b.y0-2); quadraticBezierTo(b.cx-2,b.cy-2,b.x1,b.y1) }
        drawPath(highlight,light.copy(alpha=.52f),style=Stroke(width=b.width*.15f,cap=StrokeCap.Round))
    }
    drawPath(trunk,Brush.horizontalGradient(listOf(shadow,middle,light,middle,shadow),startX=345f,endX=652f))
    drawPath(trunk,shadow.copy(alpha=.5f),style=Stroke(2.8f))
    repeat(12) { i ->
        val x=465f+i*6f
        val p=Path().apply {
            moveTo(x+(i-6)*7f,827f); cubicTo(x+(i-6)*2f,774f,x-8f,710f,x,642f)
            quadraticBezierTo(490f+i*3f,544f,492f+i*2f,444f+(i%4)*23f)
        }
        drawPath(p,if(i%3==0) light.copy(alpha=.52f) else shadow.copy(alpha=.47f),style=Stroke(if(i%3==0) 2.7f else 2f,cap=StrokeCap.Round))
    }
    // Fine offshoots support the sampled canopy rather than floating leaf circles.
    twigs.filter { it.stage<=stage && it.stage>=1 }.forEachIndexed { index,b ->
        val direction=if(index%2==0) 1f else -1f
        val branch=Path().apply {
            moveTo(b.x1*.72f+b.cx*.28f,b.y1*.72f+b.cy*.28f)
            quadraticBezierTo(b.x1+direction*19f,b.y1+18f,b.x1+direction*40f,b.y1-24f)
        }
        drawPath(branch,middle.copy(alpha=.8f),style=Stroke(3.4f,cap=StrokeCap.Round))
    }
}

private fun DrawScope.drawGlyph(g: TreeGlyph,dark: Boolean) =
    drawLeaf(g.x,g.y,g.angle,g.length,g.kind,g.tone,dark)

private fun DrawScope.drawLeaf(x: Float,y: Float,angle: Float,length: Float,kind: Int,tone: Float,dark: Boolean) {
    val base=when(kind){0->Color(0xff4e7c73);1->Color(0xff91a875);else->Color(0xffb4c4b0)}
    val mid=lerp(base,if(dark) Color(0xff203f39) else Color(0xffd4d4aa),tone*.22f)
    val high=lerp(mid,Color(0xffedf0d4),if(dark) .18f else .34f)
    val low=lerp(mid,Color(0xff2e5048),.34f)
    withTransform({
        translate(x,y); rotate(angle,Offset.Zero); scale(length/100f,length/100f,Offset.Zero)
    }) {
        val p=Path().apply {
            moveTo(0f,47f)
            cubicTo(-26f,29f,-44f,-3f,-27f,-26f)
            cubicTo(-17f,-43f,-2f,-50f,0f,-61f)
            cubicTo(10f,-46f,38f,-25f,33f,0f)
            cubicTo(30f,23f,13f,39f,0f,47f); close()
        }
        drawPath(p,Brush.linearGradient(listOf(low,mid,high),Offset(-34f,28f),Offset(28f,-45f)))
        drawPath(p,low.copy(alpha=.55f),style=Stroke(1.25f))
        val vein=Path().apply { moveTo(-2f,59f); quadraticBezierTo(3f,0f,0f,-57f) }
        drawPath(vein,high.copy(alpha=.70f),style=Stroke(1.7f,cap=StrokeCap.Round))
        for(i in 0..3){
            val yy=29f-i*16f
            drawLine(high.copy(alpha=.42f),Offset(1f,yy),Offset(-20f+i*2f,yy-13f),1.2f,cap=StrokeCap.Round)
            drawLine(high.copy(alpha=.40f),Offset(1f,yy),Offset(21f-i*2f,yy-15f),1.2f,cap=StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawPlaque(dark: Boolean) {
    val w=size.width; val h=size.height
    val brass=if(dark) Color(0xff9f8050) else Color(0xff987748)
    val face=if(dark) Color(0xff5c4e39) else Color(0xffe4d4b2)
    drawRoundRect(Color.Black.copy(alpha=.15f),Offset(2f,h*.08f),Size(w-4f,h*.92f),CornerRadius(h*.18f))
    drawRoundRect(brass,Offset(0f,0f),Size(w,h*.94f),CornerRadius(h*.17f))
    drawRoundRect(Brush.verticalGradient(listOf(lerp(face,Color.White,.12f),face),endY=h),Offset(w*.025f,h*.06f),Size(w*.95f,h*.82f),CornerRadius(h*.14f))
    drawRoundRect(brass.copy(alpha=.80f),Offset(w*.055f,h*.12f),Size(w*.89f,h*.70f),CornerRadius(h*.12f),style=Stroke(1.3f))
}

private fun DrawScope.drawJar(count: Long,dark: Boolean) {
    val w=size.width; val h=size.height
    val metal=if(dark) Color(0xff8b7047) else Color(0xff987240)
    val gold=if(dark) Color(0xffc8ae77) else Color(0xffc7ab71)
    val glass=if(dark) Color(0xff78958b) else Color(0xffa0b7a7)
    val plaque=if(dark) Color(0xff65563d) else Color(0xffe5d5b2)
    drawRoundRect(metal,Offset(w*.18f,0f),Size(w*.64f,h*.22f),CornerRadius(w*.08f))
    drawRoundRect(plaque,Offset(w*.21f,h*.025f),Size(w*.58f,h*.17f),CornerRadius(w*.05f))
    drawRect(metal,Offset(w*.46f,h*.21f),Size(w*.08f,h*.07f))
    val body=Path().apply {
        moveTo(w*.25f,h*.34f); cubicTo(w*.25f,h*.43f,w*.09f,h*.42f,w*.09f,h*.56f)
        lineTo(w*.09f,h*.86f); quadraticBezierTo(w*.10f,h*.97f,w*.29f,h*.98f)
        lineTo(w*.71f,h*.98f); quadraticBezierTo(w*.90f,h*.97f,w*.91f,h*.86f)
        lineTo(w*.91f,h*.56f); cubicTo(w*.91f,h*.42f,w*.75f,h*.43f,w*.75f,h*.34f); close()
    }
    drawPath(body,Brush.horizontalGradient(listOf(glass.copy(alpha=.48f),glass.copy(alpha=.12f),gold.copy(alpha=.12f),glass.copy(alpha=.40f)),endX=w))
    drawPath(body,metal.copy(alpha=.75f),style=Stroke(w*.025f))
    val fill=if(count<=0L) 0 else (3+count.coerceAtMost(32L).toInt()/2).coerceAtMost(19)
    val r=Random(44)
    repeat(fill) { i ->
        val col=i%3
        val yy=h*.87f-(i/3)*h*.058f
        val xx=w*(.28f+col*.21f)+(r.nextFloat()-.5f)*w*.08f
        drawLeaf(xx,yy,(r.nextFloat()-.5f)*130f,w*.24f,i%3,.4f,dark)
    }
    drawRoundRect(Brush.verticalGradient(listOf(gold,metal,gold),startY=h*.26f,endY=h*.39f),Offset(w*.16f,h*.27f),Size(w*.68f,h*.12f),CornerRadius(w*.08f))
    drawRoundRect(Brush.verticalGradient(listOf(gold,metal),startY=h*.90f,endY=h),Offset(w*.09f,h*.91f),Size(w*.82f,h*.085f),CornerRadius(w*.05f))
    drawLine(Color.White.copy(alpha=.40f),Offset(w*.20f,h*.53f),Offset(w*.20f,h*.82f),w*.035f,cap=StrokeCap.Round)
    drawLine(Color.White.copy(alpha=.22f),Offset(w*.78f,h*.55f),Offset(w*.78f,h*.74f),w*.02f,cap=StrokeCap.Round)
    repeat(7){i->
        val xx=w*(.20f+i*.10f)
        drawCircle(gold,w*.018f,Offset(xx,h*.365f))
    }
}
