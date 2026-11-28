package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.MemoraDesign

private data class DataAction(val icon:ImageVector,val title:String,val subtitle:String,val onClick:()->Unit)

@Composable
fun DataBackupScreen(language:AppLanguage,onBack:()->Unit,onExport:()->Unit,onImport:()->Unit,onTopilmalarHtml:(Boolean)->Unit,onTopilmalarWord:(Boolean)->Unit){
    var pendingTopilmalarFormat by remember { mutableStateOf<String?>(null) }
    var includeComments by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()){
        MemoraTopBar(title=dataTitle(language),navigation={IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,null)}})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=MemoraDesign.screenPadding,vertical=10.dp)){
            Text(backupSection(language),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=8.dp))
            ActionGroup(listOf(
                DataAction(Icons.Rounded.Backup,backupCreate(language),backupCreateSub(language),onExport),
                DataAction(Icons.Rounded.Restore,restore(language),restoreSub(language),onImport)
            ))
            Text(exportSection(language),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=22.dp,bottom=8.dp))
            ActionGroup(listOf(
                DataAction(Icons.Rounded.Html,"HTML",htmlSub(language),onExport),
                DataAction(Icons.Rounded.Description,"Word",wordSub(language),onExport)
            ))

        }
    }

}
@Composable private fun ActionGroup(actions:List<DataAction>){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(MemoraDesign.radiusMedium),color=MaterialTheme.colorScheme.surface,shadowElevation=MemoraDesign.shadowSubtle){Column{actions.forEachIndexed{i,a->Row(Modifier.fillMaxWidth().clickable(onClick=a.onClick).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(a.icon,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(start=16.dp)){Text(a.title,style=MaterialTheme.typography.titleMedium);Text(a.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};if(i!=actions.lastIndex)HorizontalDivider(Modifier.padding(start=58.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.06f))}}}}
private fun dataTitle(l:AppLanguage)=if(l==AppLanguage.UZ)"Ma’lumotlar va backup" else "Data & backup"
private fun backupSection(l:AppLanguage)=if(l==AppLanguage.UZ)"Qayta tiklash uchun" else "For restore"
private fun exportSection(l:AppLanguage)=if(l==AppLanguage.UZ)"Ko‘rish va ulashish uchun" else "For viewing & sharing"
private fun backupCreate(l:AppLanguage)=if(l==AppLanguage.UZ)"Memora backup yaratish" else "Create Memora backup"
private fun backupCreateSub(l:AppLanguage)=if(l==AppLanguage.UZ)"Yozuvlar, media va Vaqt izi ma’lumotlari" else "Notes, media and Time Trace data"
private fun restore(l:AppLanguage)=if(l==AppLanguage.UZ)"Backup tiklash" else "Restore backup"
private fun restoreSub(l:AppLanguage)=if(l==AppLanguage.UZ)"Oldingi Memora ma’lumotlarini qaytarish" else "Restore previous Memora data"
private fun htmlSub(l:AppLanguage)=if(l==AppLanguage.UZ)"Brauzerda ochiladigan arxiv" else "Browser-friendly archive"
private fun wordSub(l:AppLanguage)=if(l==AppLanguage.UZ)"Hujjat va alohida audio papkasi" else "Document with linked audio folder"
