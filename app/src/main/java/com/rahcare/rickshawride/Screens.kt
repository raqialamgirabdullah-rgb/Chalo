package com.rahcare.rickshawride

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

// =====================================================================
// মূল পর্দা
// =====================================================================

@Composable
fun Root(vm: AppViewModel) {
    when (val s = vm.stage) {
        Stage.Loading -> LoadingScreen()
        is Stage.Failed -> CenterMessage("সমস্যা হয়েছে", s.msg, "আবার চেষ্টা করুন") { vm.start() }
        Stage.ChooseRole -> RoleScreen(vm)
        is Stage.Register -> RegisterScreen(vm, s.role)
        Stage.Passenger -> MainFrame(vm, driver = false)
        Stage.Driver -> MainFrame(vm, driver = true)
    }
    val msg = vm.message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { vm.message = null },
            confirmButton = {
                TextButton(onClick = { vm.message = null }) {
                    Text("ঠিক আছে", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(msg, fontSize = 20.sp) },
        )
    }
}

@Composable
fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("চলো", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Green)
        Box(Modifier.padding(24.dp)) { CircularProgressIndicator() }
        Text("একটু অপেক্ষা করুন...", fontSize = 20.sp)
    }
}

@Composable
fun RoleScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("চলো", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(
            "আপনি কে?",
            fontSize = 26.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        BigButton("আমি যাত্রী", { vm.chooseRole("passenger") })
        Box(Modifier.padding(8.dp))
        BigButton("আমি চালক", { vm.chooseRole("driver") }, secondary = true)
        Text(
            "পরে চাইলে এটি বদলাতে পারবেন।",
            fontSize = 16.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
fun RegisterScreen(vm: AppViewModel, role: String) {
    val old = vm.profile
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(old?.phone ?: "") }
    var vehicle by rememberSaveable { mutableStateOf(old?.vehicleType ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val isDriver = role == "driver"
    val field = TextStyle(fontSize = 20.sp)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title(if (isDriver) "চালক হিসেবে নিবন্ধন" else "যাত্রী হিসেবে নিবন্ধন")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("আপনার নাম") },
            singleLine = true,
            textStyle = field,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("ফোন নম্বর (যেমন ০১৭XXXXXXXX)") },
            singleLine = true,
            textStyle = field,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (isDriver) {
            Body("আপনার গাড়ির ধরন বাছুন:")
            for (v in VehicleType.values()) {
                val sel = vehicle == v.key
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vehicle = v.key },
                    colors = CardDefaults.cardColors(
                        containerColor = if (sel) Color(0xFFD7F0E1) else Color.White,
                    ),
                    border = if (sel) androidx.compose.foundation.BorderStroke(3.dp, Green) else null,
                ) {
                    Text(
                        (if (sel) "✔  " else "") + v.bn,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        val e = error
        if (e != null) Text(e, color = Red, fontSize = 18.sp)
        Box(Modifier.weight(1f))
        BigButton(
            "সংরক্ষণ করুন",
            {
                val p = phone.toEnDigits().replace(" ", "").replace("-", "")
                when {
                    name.isBlank() -> error = "আপনার নাম লিখুন।"
                    !isValidBdPhone(p) -> error = "সঠিক মোবাইল নম্বর লিখুন (যেমন ০১৭১২৩৪৫৬৭৮)।"
                    isDriver && VehicleType.fromKey(vehicle) == null -> error = "গাড়ির ধরন বাছুন।"
                    else -> {
                        error = null
                        vm.saveProfile(role, name, p, vehicle)
                    }
                }
            },
            enabled = !vm.busy,
        )
        if (old != null && old.role.isNotBlank()) {
            BigButton("পেছনে যান", { vm.start() }, secondary = true)
        }
    }
}

// =====================================================================
// কাঠামো: উপরের বার, ভেতরের পর্দা, নিচের ট্যাব
// =====================================================================

@Composable
fun MainFrame(vm: AppViewModel, driver: Boolean) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (driver) "চলো — চালক" else "চলো — যাত্রী",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
            )
            TextButton(onClick = { vm.switchRole() }) {
                Text("ভূমিকা বদলান", fontSize = 16.sp)
            }
        }
        if (AppConfig.LOCAL_MODE) {
            Text(
                "পরীক্ষামূলক মোড: তথ্য শুধু এই ফোনে থাকছে, অন্য ফোনের সাথে মিলবে না।",
                fontSize = 14.sp,
                color = Color(0xFF5D4000),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFE9B3)).padding(6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                if (driver) DriverHome(vm) else PassengerHome(vm)
            } else {
                HistoryScreen(vm, driver)
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White)) {
            TabButton(if (driver) "অনলাইন" else "রাইড", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabButton(if (driver) "আয় ও ইতিহাস" else "পুরোনো রাইড", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clickable { onClick() }
            .background(if (selected) Color(0xFFD7F0E1) else Color.White)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Green else Color(0xFF555555),
        )
    }
}

// =====================================================================
// যাত্রী
// =====================================================================

@Composable
fun PassengerHome(vm: AppViewModel) {
    val ride = vm.shownRide.collectAsState().value
    if (ride == null) PassengerBooking(vm) else PassengerRide(vm, ride)
}

@Composable
fun PassengerBooking(vm: AppViewModel) {
    val ctx = LocalContext.current
    val holder = remember { MapHolder() }
    var permOk by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val askPermission = rememberLocationPermissionRequest { permOk = it }

    LaunchedEffect(permOk) {
        if (permOk) {
            val p = fetchLocation(ctx)
            if (p != null) vm.setMyLocation(p, recenter = vm.pickup == null && vm.step == BookStep.PICKUP)
        } else {
            askPermission()
        }
    }
    BackHandler(enabled = vm.step != BookStep.PICKUP) { vm.backStep() }

    val markers = buildList {
        vm.myLoc?.let { add(MapDot(it, MapColors.ME)) }
        if (vm.step != BookStep.PICKUP) vm.pickup?.let { add(MapDot(it, MapColors.PICKUP)) }
        if (vm.step == BookStep.VEHICLE) vm.dest?.let { add(MapDot(it, MapColors.DEST)) }
    }
    val line = if (vm.step == BookStep.VEHICLE && vm.pickup != null && vm.dest != null) {
        Pair(vm.pickup!!, vm.dest!!)
    } else null

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                holder = holder,
                markers = markers,
                line = line,
                focus = vm.focus,
                allowTapMove = vm.step != BookStep.VEHICLE,
                initial = vm.focus?.pt ?: vm.myLoc ?: DEFAULT_CENTER,
            )
            if (vm.step == BookStep.PICKUP) CenterPin(Green)
            if (vm.step == BookStep.DEST) CenterPin(Red)
        }
        Panel(maxHeight = 400.dp) {
            when (vm.step) {
                BookStep.PICKUP -> {
                    Title("কোথা থেকে উঠবেন?")
                    Body(
                        if (!permOk) "অবস্থানের অনুমতি দেওয়া নেই। অনুমতি দিন, অথবা মানচিত্র সরিয়ে সবুজ পিন ঠিক জায়গায় আনুন।"
                        else if (vm.myLoc == null) "আপনার অবস্থান খোঁজা হচ্ছে... মানচিত্র সরিয়ে সবুজ পিন ঠিক জায়গায় আনতে পারেন।"
                        else "সবুজ পিনটি আপনার অবস্থান। ঠিক না হলে মানচিত্র সরিয়ে পিন ঠিক করুন।",
                    )
                    if (!permOk) BigButton("অনুমতি দিন", { askPermission() }, secondary = true)
                    vm.myLoc?.let { loc ->
                        BigButton("আমার অবস্থানে ফিরুন", { vm.focusOn(loc, 17.0) }, secondary = true)
                    }
                    BigButton("পিকআপ ঠিক আছে", { vm.confirmPickup(holder.center() ?: DEFAULT_CENTER) })
                }
                BookStep.DEST -> {
                    Title("কোথায় যাবেন?")
                    Body("মানচিত্রে গন্তব্যে ট্যাপ করুন অথবা মানচিত্র সরিয়ে লাল পিনটি গন্তব্যে আনুন।")
                    BigButton("গন্তব্য ঠিক আছে", { vm.confirmDest(holder.center() ?: DEFAULT_CENTER) })
                    BigButton("পেছনে", { vm.backStep() }, secondary = true)
                }
                BookStep.VEHICLE -> {
                    Title("গাড়ি বাছুন")
                    Body("দূরত্ব (আনুমানিক): ${vm.distanceKm.kmText()} কিমি")
                    for (v in VehicleType.values()) {
                        val sel = vm.vehicle == v
                        val fare = FareConfig.estimate(v, vm.distanceKm)
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { vm.vehicle = v },
                            colors = CardDefaults.cardColors(
                                containerColor = if (sel) Color(0xFFD7F0E1) else Color(0xFFF2F2F2),
                            ),
                            border = if (sel) androidx.compose.foundation.BorderStroke(3.dp, Green) else null,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(v.bn, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("আনুমানিক ${fare.bn()} টাকা", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    BigButton(
                        "রাইড ডাকুন",
                        { vm.requestRide() },
                        enabled = vm.vehicle != null && !vm.busy,
                    )
                    BigButton("পেছনে", { vm.backStep() }, secondary = true)
                }
            }
        }
    }
}

@Composable
fun PassengerRide(vm: AppViewModel, ride: Ride) {
    when (ride.status) {
        RideStatus.COMPLETED -> FareDone(ride, driver = false) { vm.dismissRide() }
        RideStatus.CANCELLED -> CancelledView(ride, driver = false) { vm.dismissRide() }
        else -> PassengerLive(vm, ride)
    }
}

@Composable
fun PassengerLive(vm: AppViewModel, ride: Ride) {
    val ctx = LocalContext.current
    val holder = remember { MapHolder() }
    val driverLoc = vm.driverLoc.collectAsState().value
    val contact = vm.contact.collectAsState().value
    var askCancel by remember { mutableStateOf(false) }

    LaunchedEffect(ride.id) { vm.focusOn(ride.pickup, 15.0) }

    val markers = buildList {
        add(MapDot(ride.pickup, MapColors.PICKUP))
        add(MapDot(ride.dest, MapColors.DEST))
        driverLoc?.let { add(MapDot(it, MapColors.DRIVER)) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                holder = holder,
                markers = markers,
                line = Pair(ride.pickup, ride.dest),
                focus = vm.focus,
                allowTapMove = false,
                initial = ride.pickup,
            )
        }
        Panel(maxHeight = 420.dp) {
            if (ride.status == RideStatus.REQUESTED) {
                var remaining by remember { mutableIntStateOf(180) }
                LaunchedEffect(ride.createdAtMs) {
                    while (true) {
                        remaining = max(
                            0,
                            ((ride.createdAtMs + REQUEST_TIMEOUT_MS - System.currentTimeMillis()) / 1000).toInt(),
                        )
                        delay(1000)
                    }
                }
                Title("কাছের চালক খোঁজা হচ্ছে...")
                Body("অপেক্ষার সময় বাকি: ${timeText(remaining)}")
                Body("৩ মিনিটে কেউ না নিলে রাইড নিজে থেকে বাতিল হবে।")
            } else {
                Title(
                    when (ride.status) {
                        RideStatus.ACCEPTED -> "চালক আপনার কাছে আসছেন"
                        RideStatus.ARRIVED -> "চালক পৌঁছে গেছেন"
                        else -> "যাত্রা চলছে"
                    },
                )
                Body("চালক: ${ride.driverName ?: contact?.name ?: ""}")
                Body("গাড়ি: ${VehicleType.fromKey(ride.vehicleType)?.bn ?: ""}")
                val phone = contact?.phone
                if (phone != null) {
                    Body("ফোন: ${phone.toBnDigits()}")
                    BigButton("চালককে ফোন করুন", { dial(ctx, phone) }, secondary = true)
                }
                if (driverLoc == null) Body("চালকের অবস্থান এখনো আসেনি...", Color(0xFF777777))
            }
            Body("দূরত্ব: ${ride.distanceKm.kmText()} কিমি  |  ভাড়া: ${ride.fare.bn()} টাকা")
            BigButton("রাইড বাতিল করুন", { askCancel = true }, danger = true, secondary = true, enabled = !vm.busy)
        }
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("রাইড বাতিল করবেন?", fontSize = 22.sp) },
            confirmButton = {
                TextButton(onClick = {
                    askCancel = false
                    vm.cancelRide()
                }) { Text("হ্যাঁ, বাতিল", fontSize = 20.sp, color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { askCancel = false }) { Text("না", fontSize = 20.sp) }
            },
        )
    }
}

// =====================================================================
// চালক
// =====================================================================

@Composable
fun DriverHome(vm: AppViewModel) {
    val ride = vm.shownRide.collectAsState().value
    if (ride == null) DriverIdle(vm) else DriverRide(vm, ride)
}

@Composable
fun DriverIdle(vm: AppViewModel) {
    val ctx = LocalContext.current
    val holder = remember { MapHolder() }
    val online = LocationHub.running.collectAsState().value
    val me = LocationHub.location.collectAsState().value
    val requests = vm.requests.collectAsState().value
    val askPermission = rememberLocationPermissionRequest { ok ->
        if (ok) {
            vm.goOnline()
        } else {
            vm.message = "অবস্থানের অনুমতি ছাড়া অনলাইন হওয়া যাবে না।"
        }
    }

    LaunchedEffect(me != null) {
        me?.let { vm.focusOn(it, 15.0) }
    }

    val markers = buildList {
        if (online) me?.let { add(MapDot(it, MapColors.ME)) }
        for (r in requests) add(MapDot(r.pickup, MapColors.PICKUP))
    }

    Column(Modifier.fillMaxSize()) {
        Card(
            Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (online) Color(0xFFD7F0E1) else Color(0xFFEDEDED),
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        if (online) "আপনি অনলাইন" else "আপনি অফলাইন",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (online) Green else Color(0xFF444444),
                    )
                    Text(
                        "গাড়ি: ${VehicleType.fromKey(vm.profile?.vehicleType)?.bn ?: ""}",
                        fontSize = 16.sp,
                    )
                }
                Switch(
                    checked = online,
                    onCheckedChange = { want ->
                        if (want) {
                            if (hasLocationPermission(ctx)) vm.goOnline() else askPermission()
                        } else {
                            vm.goOffline()
                        }
                    },
                    modifier = Modifier.scale(1.5f).padding(end = 12.dp),
                )
            }
        }

        if (!online) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "নতুন রাইডের রিকোয়েস্ট পেতে উপরের সুইচ চালু করে অনলাইন হোন।",
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF444444),
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                OsmMap(
                    modifier = Modifier.fillMaxSize(),
                    holder = holder,
                    markers = markers,
                    line = null,
                    focus = vm.focus,
                    allowTapMove = false,
                    initial = me ?: DEFAULT_CENTER,
                )
            }
            Panel(maxHeight = 340.dp) {
                if (me == null) {
                    Title("আপনার অবস্থান খোঁজা হচ্ছে...")
                } else if (requests.isEmpty()) {
                    Title("নতুন রিকোয়েস্টের অপেক্ষা...")
                    Body("৩ কিমির মধ্যে আপনার গাড়ির ধরনের রিকোয়েস্ট এলে এখানে দেখা যাবে।")
                    if (AppConfig.LOCAL_MODE) {
                        BigButton("ডেমো রিকোয়েস্ট আনুন (পরীক্ষা)", { vm.addDemoRequest() }, secondary = true)
                    }
                } else {
                    Title("নতুন রিকোয়েস্ট (${requests.size.bn()}টি)")
                    for (r in requests) RequestCard(vm, r, me)
                }
            }
        }
    }
}

@Composable
fun RequestCard(vm: AppViewModel, r: Ride, me: Pt) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7F2)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ভাড়া: ${r.fare.bn()} টাকা", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
            Body("পিকআপ আপনার থেকে: ${Geo.straightKm(me, r.pickup).kmText()} কিমি দূরে")
            Body("যাত্রার দূরত্ব: ${r.distanceKm.kmText()} কিমি")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { BigButton("গ্রহণ", { vm.acceptRequest(r) }, enabled = !vm.busy) }
                Box(Modifier.weight(1f)) { BigButton("প্রত্যাখ্যান", { vm.rejectRequest(r) }, secondary = true, danger = true) }
            }
        }
    }
}

@Composable
fun DriverRide(vm: AppViewModel, ride: Ride) {
    when (ride.status) {
        RideStatus.COMPLETED -> FareDone(ride, driver = true) { vm.dismissRide() }
        RideStatus.CANCELLED -> CancelledView(ride, driver = true) { vm.dismissRide() }
        else -> DriverLive(vm, ride)
    }
}

@Composable
fun DriverLive(vm: AppViewModel, ride: Ride) {
    val ctx = LocalContext.current
    val holder = remember { MapHolder() }
    val me = LocationHub.location.collectAsState().value
    val contact = vm.contact.collectAsState().value
    var askCancel by remember { mutableStateOf(false) }

    LaunchedEffect(ride.id) { vm.focusOn(ride.pickup, 15.0) }

    val markers = buildList {
        me?.let { add(MapDot(it, MapColors.ME)) }
        add(MapDot(ride.pickup, MapColors.PICKUP))
        add(MapDot(ride.dest, MapColors.DEST))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                holder = holder,
                markers = markers,
                line = Pair(ride.pickup, ride.dest),
                focus = vm.focus,
                allowTapMove = false,
                initial = ride.pickup,
            )
        }
        Panel(maxHeight = 420.dp) {
            Title(
                when (ride.status) {
                    RideStatus.ACCEPTED -> "যাত্রীর কাছে যান (সবুজ বিন্দু)"
                    RideStatus.ARRIVED -> "যাত্রীর জন্য অপেক্ষা করুন"
                    else -> "যাত্রী নিয়ে গন্তব্যে যান (লাল বিন্দু)"
                },
            )
            Body("যাত্রী: ${contact?.name ?: ride.passengerName}")
            val phone = contact?.phone
            if (phone != null) {
                Body("ফোন: ${phone.toBnDigits()}")
                BigButton("যাত্রীকে ফোন করুন", { dial(ctx, phone) }, secondary = true)
            }
            Body("দূরত্ব: ${ride.distanceKm.kmText()} কিমি  |  ভাড়া: ${ride.fare.bn()} টাকা")
            when (ride.status) {
                RideStatus.ACCEPTED -> BigButton("পৌঁছেছি", { vm.driverStep(RideStatus.ARRIVED) }, enabled = !vm.busy)
                RideStatus.ARRIVED -> BigButton("যাত্রা শুরু", { vm.driverStep(RideStatus.ONGOING) }, enabled = !vm.busy)
                else -> BigButton("যাত্রা শেষ", { vm.driverStep(RideStatus.COMPLETED) }, enabled = !vm.busy)
            }
            if (ride.status != RideStatus.ONGOING) {
                BigButton("রাইড বাতিল করুন", { askCancel = true }, danger = true, secondary = true, enabled = !vm.busy)
            }
        }
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("রাইড বাতিল করবেন?", fontSize = 22.sp) },
            confirmButton = {
                TextButton(onClick = {
                    askCancel = false
                    vm.cancelRide()
                }) { Text("হ্যাঁ, বাতিল", fontSize = 20.sp, color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { askCancel = false }) { Text("না", fontSize = 20.sp) }
            },
        )
    }
}

// =====================================================================
// শেষের পর্দা ও ইতিহাস
// =====================================================================

@Composable
fun FareDone(ride: Ride, driver: Boolean, onOk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("যাত্রা শেষ", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "ভাড়া: ${ride.fare.bn()} টাকা",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Green,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            if (driver) "নগদে নিন" else "নগদে দিন",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            "দূরত্ব: ${ride.distanceKm.kmText()} কিমি",
            fontSize = 18.sp,
            color = Color(0xFF555555),
            modifier = Modifier.padding(bottom = 32.dp),
        )
        BigButton("ঠিক আছে", onOk)
    }
}

@Composable
fun CancelledView(ride: Ride, driver: Boolean, onOk: () -> Unit) {
    val text = when (ride.cancelledBy) {
        "timeout" -> "৩ মিনিটে কোনো চালক পাওয়া যায়নি, তাই রাইড বাতিল হয়েছে। আবার চেষ্টা করুন।"
        "passenger" -> if (driver) "যাত্রী রাইড বাতিল করেছেন।" else "আপনি রাইড বাতিল করেছেন।"
        "driver" -> if (driver) "আপনি রাইড বাতিল করেছেন।" else "চালক রাইড বাতিল করেছেন।"
        else -> "রাইড বাতিল হয়েছে।"
    }
    CenterMessage("রাইড বাতিল", text, "ঠিক আছে", onOk)
}

fun statusText(s: String): String = when (s) {
    RideStatus.COMPLETED -> "সম্পন্ন"
    RideStatus.CANCELLED -> "বাতিল"
    else -> "চলমান"
}

fun timeText(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return (String.format(Locale.US, "%02d:%02d", m, s)).toBnDigits()
}

@Composable
fun HistoryScreen(vm: AppViewModel, driver: Boolean) {
    val all = vm.myRides.collectAsState().value
    val past = all.filter { !it.isActive }.sortedByDescending { it.createdAtMs }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale("bn", "BD")) }

    val done = past.filter { it.status == RideStatus.COMPLETED }
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis
    val todayTotal = done.filter { it.createdAtMs >= startOfToday }.sumOf { it.fare }
    val allTotal = done.sumOf { it.fare }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (driver) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD7F0E1))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("আজকের আয়: ${todayTotal.bn()} টাকা", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
                        Text("মোট আয়: ${allTotal.bn()} টাকা", fontSize = 20.sp)
                        Text("মোট সম্পন্ন রাইড: ${done.size.bn()}টি", fontSize = 18.sp)
                    }
                }
            }
        }
        if (past.isEmpty()) {
            item {
                Text(
                    "এখনো কোনো রাইড নেই।",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(24.dp),
                    color = Color(0xFF555555),
                )
            }
        }
        items(past, key = { it.id }) { r ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            VehicleType.fromKey(r.vehicleType)?.bn ?: "",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            statusText(r.status),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (r.status == RideStatus.COMPLETED) Green else Red,
                        )
                    }
                    Text(fmt.format(Date(r.createdAtMs)), fontSize = 16.sp, color = Color(0xFF555555))
                    Text("${r.distanceKm.kmText()} কিমি  |  ${r.fare.bn()} টাকা", fontSize = 18.sp)
                }
            }
        }
    }
}
