package com.memora.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.BuildConfig
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun AboutScreen(language: AppLanguage, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = if (language == AppLanguage.UZ) "Memora haqida" else "About Memora",
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MemoraDesign.screenPadding, vertical = 12.dp)
        ) {
            Text("Memora", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (language == AppLanguage.UZ) "Yozish, xotiralarni saqlash va vaqt o‘tishini sokin tarzda qayta ko‘rish uchun shaxsiy makon."
                else "A private space for writing, keeping memories and revisiting time calmly.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(22.dp))
            InfoCard(
                title = if (language == AppLanguage.UZ) "Maxfiylik" else "Privacy",
                text = if (language == AppLanguage.UZ)
                    "Yozuvlar, media va Vaqt izi suratlari ilovaning shaxsiy saqlash hududida saqlanadi. Vaqt izi suratlari Galereyaga avtomatik chiqarilmaydi. Backup va eksportni faqat siz boshlaysiz."
                else
                    "Notes, media and Time Trace photos are kept in the app's private storage. Time Trace photos are not automatically added to Gallery. Backup and export only run when you start them."
            )
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = if (language == AppLanguage.UZ) "Vaqt izi" else "Time Trace",
                text = if (language == AppLanguage.UZ)
                    "Vaqt izi yuz yoki tashqi ko‘rinishni baholamaydi. Kadrni bir xil ko‘rsatish uchun suratlar neytral 4:5 formatga joylanadi; beautify yoki tashqi ko‘rinishni o‘zgartirish qo‘llanmaydi."
                else
                    "Time Trace does not score or judge appearance. Photos are normalized to a consistent 4:5 frame; no beautification or appearance-changing filter is applied."
            )
            Spacer(Modifier.height(20.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
        }
    }
}
