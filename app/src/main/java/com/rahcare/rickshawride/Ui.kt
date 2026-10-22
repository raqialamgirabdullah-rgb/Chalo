package com.rahcare.rickshawride

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

val Green = Color(0xFF1B7F4B)
val Red = Color(0xFFC62828)
val Amber = Color(0xFFE08A00)

@Composable
fun ChaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = Amber,
            error = Red,
            background = Color(0xFFF7F8F5),
            surface = Color.White,
        ),
        content = content,
    )
}

// ---------- বড় বাটন ----------
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    secondary: Boolean = false,
) {
    val m = modifier.fillMaxWidth().heightIn(min = 60.dp)
    if (secondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (danger) Red else Green)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (danger) Red else Green),
        ) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- নিচের প্যানেল ----------
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        color = Color.White,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun Title(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
}

@Composable
fun Body(text: String, color: Color = Color(0xFF333333)) {
    Text(text, fontSize = 18.sp, color = color)
}

/** মানচিত্রের ঠিক মাঝখানের পিন — এই বিন্দুটাই নির্বাচিত জায়গা। */
@Composable
fun CenterPin(color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.White, radius = 20.dp.toPx(), center = c)
            drawCircle(color, radius = 15.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 5.dp.toPx(), center = c)
        }
    }
}

@Composable
fun CenterMessage(title: String, body: String, buttonText: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            body,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF333333),
            modifier = Modifier.padding(vertical = 18.dp),
        )
        BigButton(buttonText, onClick)
    }
}

// ---------- অনুমতি ও অবস্থান ----------
fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** লোকেশন (ও নোটিফিকেশন) অনুমতি চাওয়ার ফাংশন ফেরত দেয়। */
@Composable
fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onResult(ok)
    }
    return {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        launcher.launch(perms.toTypedArray())
    }
}

@SuppressLint("MissingPermission")
suspend fun fetchLocation(ctx: Context): Pt? {
    return try {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val l = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: client.lastLocation.await()
        if (l != null) Pt(l.latitude, l.longitude) else null
    } catch (e: Exception) {
        null
    }
}

fun dial(ctx: Context, phone: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    } catch (e: Exception) {
        // ডায়াল করার অ্যাপ নেই
    }
}
