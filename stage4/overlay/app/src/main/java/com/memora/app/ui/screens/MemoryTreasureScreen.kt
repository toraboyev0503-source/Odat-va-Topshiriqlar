package com.memora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.memora.app.data.repository.MemoryTreeRepository
import com.memora.app.prefs.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MemoryTreasureScreen(tree:MemoryTreeRepository,language:AppLanguage,onBack:()->Unit,
    onOpen:(String)->Unit,onReturned:(Int)->Unit) {
    val uz=language==AppLanguage.UZ
    val scope=rememberCoroutineScope()
    var ascending by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val count by remember(tree) { tree.observeTreasureCount() }.collectAsStateWithLifecycle(0L)
    val rows=remember(tree,ascending) { tree.pagedTreasure(ascending) }.collectAsLazyPagingItems()
    BackHandler { if(!busy) onBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,enabled=!busy) { Icon(Icons.Rounded.ArrowBack,if(uz)"Orqaga" else "Back") }
            Column(Modifier.weight(1f)) {
                Text(if(uz)"Xotiralar xazinasi" else "Memory treasure",style=MaterialTheme.typography.titleLarge)
                Text(count.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={ascending=!ascending},enabled=!busy) {
                Icon(Icons.Rounded.Sort,if(ascending) { if(uz)"Yangilari avval" else "Newest first" }
                    else { if(uz)"Eskilari avval" else "Oldest first" })
            }
            IconButton(onClick={confirm=true},enabled=count>0 && !busy) {
                Icon(Icons.Rounded.Refresh,if(uz)"Barglarni daraxtga qaytarish" else "Return leaves to tree")
            }
        }
        if(error) Text(if(uz)"Amal bajarilmadi. Qayta urinib ko‘ring." else "Could not complete. Try again.",
            color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
        when {
            rows.loadState.refresh is LoadState.Error -> TextButton(onClick={rows.retry()}) { Text(if(uz)"Qayta yuklash" else "Retry") }
            rows.loadState.refresh is LoadState.Loading && rows.itemCount==0 -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){ CircularProgressIndicator() }
            rows.itemCount==0 -> Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                Text(if(uz)"Hozircha xazinada barg yo‘q." else "The treasure is empty.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(rows.itemCount,key={i->rows.peek(i)?.id ?: "placeholder:$i"}) { index ->
                    val item=rows[index] ?: return@items
                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,
                        modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){onOpen(item.id)}) {
                        Column(Modifier.padding(15.dp)) {
                            val kind=when(item.kind) {"SHORT"->if(uz)"Qisqa" else "Short"; "LONG"->if(uz)"Uzun" else "Long";else->if(uz)"Kun izi" else "Day trace"}
                            Text(kind,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            Text(item.title.ifBlank {kind},style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text((if(uz)"Asl sana: " else "Original: ")+treasureDate(item.originalAt),style=MaterialTheme.typography.labelSmall)
                            Text((if(uz)"Eslatilgan: " else "Recalled: ")+treasureDate(item.recalledAt),style=MaterialTheme.typography.labelSmall)
                            Row(Modifier.padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FavoriteBorder,null,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(5.dp));Text(item.likes.toString())
                                Spacer(Modifier.width(18.dp));Icon(Icons.Rounded.ChatBubbleOutline,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text(item.impressions.toString())
                            }
                        }
                    }
                }
                if(rows.loadState.append is LoadState.Error) item {TextButton(onClick={rows.retry()}){Text(if(uz)"Qayta yuklash" else "Retry")}}
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={if(!busy)confirm=false},
        title={Text(if(uz)"Barglarni qaytarish" else "Return leaves")},
        text={Text(if(uz)"Barcha eslatilgan barglarni daraxtga qaytarasizmi? Xotiralaringiz, likelar va taassurotlaringiz saqlanib qoladi."
            else "Return all recalled leaves to the tree? Memories, likes and reflections will remain.")},
        confirmButton={TextButton(enabled=!busy,onClick={
            busy=true;error=false
            scope.launch {
                try { val n=tree.returnTreasure(System.currentTimeMillis());confirm=false;onReturned(n) }
                catch(c:CancellationException){throw c}
                catch(_:Exception){error=true;confirm=false}
                finally{busy=false}
            }
        }){Text(if(uz)"Qaytarish" else "Return")}},
        dismissButton={TextButton(enabled=!busy,onClick={confirm=false}){Text(if(uz)"Bekor qilish" else "Cancel")}})
}
private fun treasureDate(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
