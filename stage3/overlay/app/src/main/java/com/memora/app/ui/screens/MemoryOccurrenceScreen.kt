package com.memora.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.data.local.TreeRecallEntity
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.MemoryAlbumPhotoEntity
import com.memora.app.data.repository.MemoryTreeRepository
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.memories.MemoryAlbumStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The event ID, not the note ID, identifies the user's reaction and completion. */
@Composable
fun MemoryOccurrenceScreen(
    eventId: String,tree: MemoryTreeRepository,repository: MemoraRepository,
    language: AppLanguage,writingFont: FontChoice,onBack: () -> Unit,
    onCompleted: (TreeRecallEntity) -> Unit = {}
) {
    val uz=language==AppLanguage.UZ
    val scope=rememberCoroutineScope()
    val event by remember(tree,eventId) { tree.observeEvent(eventId) }.collectAsStateWithLifecycle(initialValue=null)
    val sourceKey=event?.sourceKey
    val noteId=sourceKey?.takeIf { it.startsWith("NOTE:") }?.removePrefix("NOTE:")
    val photoId=sourceKey?.takeIf { it.startsWith("PHOTO:") }?.removePrefix("PHOTO:")?.toLongOrNull()
    val note by remember(repository,noteId) { noteId?.let(repository::observeNote) ?: flowOf<NoteEntity?>(null) }
        .collectAsStateWithLifecycle(initialValue=null)
    val photo by remember(tree,photoId) { photoId?.let(tree::observePhoto) ?: flowOf<MemoryAlbumPhotoEntity?>(null) }
        .collectAsStateWithLifecycle(initialValue=null)
    val likeCount by remember(tree,sourceKey) { sourceKey?.let(tree::observeLikeCount) ?: flowOf(0L) }
        .collectAsStateWithLifecycle(0L)
    val impressions by remember(tree,sourceKey) { sourceKey?.let(tree::observeImpressions) ?: flowOf(emptyList<TreeRecallEntity>()) }
        .collectAsStateWithLifecycle(emptyList())
    var comments by rememberSaveable(eventId) { mutableStateOf(false) }
    var draft by rememberSaveable(eventId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activeEvent=event
    val canReact=activeEvent?.state=="PENDING"
    val context=LocalContext.current
    val storage=remember(context) { MemoryAlbumStorage(context.applicationContext) }
    val path=photo?.relativePath
    val bitmap by produceState<Bitmap?>(null,path) {
        value=if(path==null) null else withContext(Dispatchers.IO) { storage.load(path) }
    }
    val ready=note!=null || (photo!=null && bitmap!=null)
    val failure=if(uz) "Saqlanmadi. Qayta urinib ko‘ring." else "Could not save. Try again."
    fun mutate(action:suspend () -> Boolean,onSuccess:()->Unit={}) {
        if(busy) return
        busy=true; error=null
        scope.launch {
            try { if(action()) onSuccess() else error=failure }
            catch(c:CancellationException){ throw c }
            catch(_:Exception){ error=failure }
            finally { busy=false }
        }
    }
    fun exit() {
        if(busy) return
        if(comments) { comments=false; error=null; return }
        val current=event
        // No completion on disposal, rotation, backgrounding, failed media load, or comment back.
        if(current==null || current.state!="PENDING" || !ready) { onBack(); return }
        mutate(action={
            tree.complete(current.id,System.currentTimeMillis()) ||
                tree.getEvent(current.id)?.state=="TREASURE"
        },onSuccess={ onCompleted(current); onBack() })
    }
    BackHandler { exit() }
    val opening=remember(eventId) { Animatable(.96f) }
    LaunchedEffect(eventId) {
        if(android.os.Build.VERSION.SDK_INT>=26 && !android.animation.ValueAnimator.areAnimatorsEnabled())
            opening.snapTo(1f)
        else opening.animateTo(1f,tween(240))
    }
    if(comments) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={exit()},enabled=!busy) { Icon(Icons.Rounded.ArrowBack,if(uz)"Orqaga" else "Back") }
                Text(if(uz)"Taassurotlar" else "Reflections",style=MaterialTheme.typography.titleLarge)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(impressions.isEmpty()) item {
                    Text(if(uz)"Hozircha taassurot yozilmagan." else "No reflections yet.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(impressions,key={it.id}) { item ->
                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,
                        modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.impression.orEmpty(),style=MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(item.impressionCreatedAt?.let(::memoryDate).orEmpty() +
                                if(item.impressionEditedAt!=null) (if(uz)" · tahrirlangan" else " · edited") else "",
                                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=16.dp)) }
            if(canReact) Row(Modifier.fillMaxWidth().padding(12.dp).imePadding(),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),
                    enabled=!busy,label={Text(if(uz)"Bugungi taassurotingiz" else "Your reflection today")},maxLines=5)
                IconButton(enabled=!busy && draft.isNotBlank() && draft!=event?.impression,
                    onClick={
                        val body=draft.trim()
                        mutate(action={tree.setImpression(eventId,body,System.currentTimeMillis())},
                            onSuccess={draft=body})
                    }) { Icon(Icons.Rounded.Send,if(uz)"Saqlash" else "Save") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .graphicsLayer { scaleX=opening.value;scaleY=opening.value }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val currentNote=note
            val currentPhoto=photo
            if(currentNote!=null) ReaderScreen(
                note=currentNote,repository=repository,language=language,writingFont=writingFont,
                canEdit=false,onBack={exit()},onEdit={},onDelete={},onWriteRequired={},memoryMode=true
            ) else Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={exit()},enabled=!busy) { Icon(Icons.Rounded.ArrowBack,if(uz)"Orqaga" else "Back") }
                    Text(if(uz)"Xotira" else "Memory",style=MaterialTheme.typography.titleLarge)
                }
                if(currentPhoto!=null) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                        bitmap?.let { Image(it.asImageBitmap(),if(uz)"Kun izi surati" else "Day trace photo",
                            contentScale=ContentScale.Fit,colorFilter=albumFilter(currentPhoto.effect),
                            modifier=Modifier.fillMaxWidth().heightIn(max=560.dp)) }
                        if(bitmap==null) Text(if(uz)"Surat yuklanmoqda yoki mavjud emas." else "Photo loading or unavailable.")
                        if(currentPhoto.caption.isNotBlank()) Text(currentPhoto.caption,style=MaterialTheme.typography.bodyLarge,
                            modifier=Modifier.padding(top=12.dp))
                    }
                } else Text(if(uz)"Xotira yuklanmoqda yoki mavjud emas." else "Memory loading or unavailable.",
                    modifier=Modifier.padding(16.dp))
            }
        }
        val original=note?.createdAt ?: photo?.capturedAt
        if(original!=null) Surface(tonalElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp)) {
                val kind=if(note?.type=="SHORT") {if(uz)"Qisqa" else "Short"}
                    else if(note!=null) {if(uz)"Uzun" else "Long"}
                    else {if(uz)"Kun izi" else "Day trace"}
                val days=java.time.temporal.ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(original).atZone(ZoneId.systemDefault()).toLocalDate(),
                    java.time.LocalDate.now()).coerceAtLeast(0L)
                Text("$kind · "+memoryDate(original)+" · "+if(uz)"$days kun avval" else "$days days ago",
                    style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    val liked=event?.likedAt!=null
                    IconButton(enabled=canReact && ready && !busy,onClick={
                        mutate(action={tree.setLike(eventId,!liked,System.currentTimeMillis())})
                    }) { Icon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if(uz)"Like" else "Like",tint=if(liked) Color(0xffb95661) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(likeCount.toString())
                    Spacer(Modifier.width(16.dp))
                    IconButton(enabled=event!=null && !busy,onClick={
                        draft=event?.impression.orEmpty(); comments=true; error=null
                    }) { Icon(Icons.Rounded.ChatBubbleOutline,if(uz)"Taassurotlar" else "Reflections") }
                    Text(impressions.size.toString())
                }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        }
    }
}
private fun memoryDate(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
