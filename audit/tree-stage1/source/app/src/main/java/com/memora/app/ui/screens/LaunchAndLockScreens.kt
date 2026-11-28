package com.memora.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.R
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraBrand
import com.memora.app.ui.components.MemoraBrandBackdrop
import kotlinx.coroutines.delay

@Composable
fun BrandLaunchScreen(language: AppLanguage) {
    var stage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // A short calm opening lets the ivory canvas register before motion begins.
        delay(280)
        stage = 1          // exact Memora mark settles in
        delay(480)
        stage = 2          // a short breathing pause before the wordmark
        delay(280)
        stage = 3          // Memora wordmark settles
        delay(360)
        stage = 4          // tagline follows after the brand name has time to register
        delay(420)
        stage = 5          // Home's soft curves emerge after a brief breathing pause
    }

    val iconAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "launchIcon"
    )
    val iconOffset by animateDpAsState(
        targetValue = if (stage >= 1) 0.dp else 8.dp,
        animationSpec = tween(540, easing = FastOutSlowInEasing),
        label = "launchIconOffset"
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (stage >= 3) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "launchWordmark"
    )
    val wordmarkOffset by animateDpAsState(
        targetValue = if (stage >= 3) 0.dp else 8.dp,
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "launchWordmarkOffset"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (stage >= 4) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "launchTagline"
    )
    val taglineOffset by animateDpAsState(
        targetValue = if (stage >= 4) 0.dp else 7.dp,
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "launchTaglineOffset"
    )
    val backdropProgress by animateFloatAsState(
        targetValue = if (stage >= 5) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "launchBackdrop"
    )
    val backdropOffset by animateDpAsState(
        targetValue = if (stage >= 5) 0.dp else 62.dp,
        animationSpec = tween(540, easing = FastOutSlowInEasing),
        label = "launchBackdropOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoraBrand.Ivory)
    ) {
        MemoraBrandBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.BottomCenter)
                .offset(y = backdropOffset)
                .alpha(backdropProgress),
            primaryColor = MemoraBrand.Sage,
            secondaryColor = MemoraBrand.SoftStone,
            strength = 1f
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .offset(y = (-18).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.memora_brand_symbol_exact),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 146.dp, height = 125.dp)
                    .offset(y = iconOffset)
                    .alpha(iconAlpha)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Memora",
                color = MemoraBrand.Slate,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                modifier = Modifier
                    .offset(y = wordmarkOffset)
                    .alpha(wordmarkAlpha)
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 1.dp)
                    .background(MemoraBrand.Slate.copy(alpha = 0.34f * wordmarkAlpha))
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = L.t(language, S.HOME_TAGLINE).uppercase(),
                color = MemoraBrand.Slate.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.4.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = taglineOffset)
                    .alpha(taglineAlpha)
            )
        }
    }
}

@Composable
fun LockedScreen(language: AppLanguage, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null)
        Text(
            "Memora",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        Text(
            L.t(language, S.AUTH_SUBTITLE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Button(onClick = onUnlock) {
            Text(L.t(language, S.AUTH_TITLE))
        }
    }
}
