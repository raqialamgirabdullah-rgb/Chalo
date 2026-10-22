package com.rahcare.rickshawride

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** আমার বর্তমান অবস্থানের কার্ড (ঠিকানাসহ)। */
@Composable
fun MyLocationCard(vm: AppViewModel) {
    val loc = vm.myLoc
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F0FB)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("আমার বর্তমান অবস্থান", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            Text(
                when {
                    loc == null -> "অবস্থান খোঁজা হচ্ছে…"
                    vm.myAddress != null -> vm.myAddress!!
                    else -> "${loc.lat.coordText()}, ${loc.lng.coordText()}"
                },
                fontSize = 17.sp,
                color = Color(0xFF1B1B1B),
            )
        }
    }
}

private fun Double.coordText(): String = String.format(java.util.Locale.US, "%.5f", this).toBnDigits()

/** জায়গা লেখার ঘর: লিখে "খুঁজুন" বা কিবোর্ডের সার্চ চাপলে তালিকা আসে; তালিকার জায়গায় চাপলে মানচিত্রে পিন সেখানে যায়। */
@Composable
fun PlaceSearchField(label: String, value: String, onChange: (String) -> Unit, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(value) }),
            )
            TextButton(onClick = { vm.search(value) }, enabled = !vm.searching) {
                Text(if (vm.searching) "…" else "খুঁজুন", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        vm.searchMsg?.let { Text(it, fontSize = 15.sp, color = Red) }
        vm.suggestions.forEach { p ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.pickPlace(p) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
            ) {
                Text(p.name, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}
