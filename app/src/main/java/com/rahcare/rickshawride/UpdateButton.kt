package com.rahcare.rickshawride

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ভাসমান আপডেট বোতাম (AutoReply-এর মতো): নতুন সংস্করণ থাকলেই দেখায়, চাপলে ডাউনলোড ও ইনস্টল — বোতামটাই প্রগ্রেস বার হয়ে যায়। */
@Composable
fun UpdateButton(modifier: Modifier = Modifier) {
    val info = UpdState.info ?: return
    val ctx = LocalContext.current
    val busy = UpdState.busy
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .height(52.dp)
            .widthIn(min = if (busy) 250.dp else 0.dp)
            .background(if (busy) Color(0xFF0F4A2D) else Green)
            .clickable(enabled = !busy) { Updater.install(ctx, info.url) },
        contentAlignment = Alignment.Center,
    ) {
        if (busy && UpdState.pct >= 0) {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight()
                    .fillMaxWidth(UpdState.pct / 100f).background(Green),
            )
        }
        Text(
            UpdState.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
    }
}
