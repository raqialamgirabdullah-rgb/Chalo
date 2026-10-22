package com.rahcare.rickshawride

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface Stage {
    object Loading : Stage
    data class Failed(val msg: String) : Stage
    object ChooseRole : Stage
    data class Register(val role: String) : Stage
    object Passenger : Stage
    object Driver : Stage
}

enum class BookStep { PICKUP, DEST, VEHICLE }

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- সাধারণ অবস্থা ----------
    var stage by mutableStateOf<Stage>(Stage.Loading)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    private var uid = ""
    private val role = MutableStateFlow<String?>(null)
    private val vehicleKey = MutableStateFlow<String?>(null)
    private val trackedId = MutableStateFlow<String?>(null)
    private val hidden = MutableStateFlow<Set<String>>(emptySet())
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000)
        }
    }

    // ---------- আমার সব রাইড (যাত্রী হলে যাত্রী হিসেবে, চালক হলে চালক হিসেবে) ----------
    val myRides: StateFlow<List<Ride>> = role.flatMapLatest { r ->
        when (r) {
            "passenger" -> Repo.observeMyRides("passengerId", uid)
            "driver" -> Repo.observeMyRides("driverId", uid)
            else -> flowOf(emptyList<Ride>())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** এই মুহূর্তে পর্দায় যে রাইডটি দেখানো হচ্ছে। */
    val shownRide: StateFlow<Ride?> = combine(myRides, trackedId) { list, id ->
        if (id == null) null else list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** অন্য পক্ষের নাম ও ফোন — রাইড গৃহীত হওয়ার পরেই পাওয়া যায়। */
    val contact: StateFlow<Contact?> = shownRide
        .map { r -> if (r != null && r.driverId != null) r.id else null }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf<Contact?>(null)
            } else {
                Repo.observeContact(id, if (role.value == "driver") "passenger" else "driver")
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** যাত্রীর জন্য: চালকের লাইভ অবস্থান। */
    val driverLoc: StateFlow<Pt?> = shownRide
        .map { r ->
            if (r != null && r.driverId != null && role.value == "passenger" &&
                (r.status == RideStatus.ACCEPTED || r.status == RideStatus.ARRIVED || r.status == RideStatus.ONGOING)
            ) r.driverId else null
        }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf<Pt?>(null) else Repo.observeDriverLoc(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- চালকের নতুন রিকোয়েস্ট ----------
    private val requestsRaw = combine(LocationHub.running, trackedId, vehicleKey) { on, tracked, v ->
        if (on && tracked == null) v else null
    }.distinctUntilChanged().flatMapLatest { v ->
        if (v == null) flowOf(emptyList<Ride>()) else Repo.observeRequested(v)
    }

    val requests: StateFlow<List<Ride>> =
        combine(requestsRaw, LocationHub.location, ticker, hidden) { list, loc, now, hid ->
            if (loc == null) {
                emptyList<Ride>()
            } else {
                list.filter {
                    it.id !in hid &&
                        now - it.createdAtMs <= REQUEST_TIMEOUT_MS &&
                        Geo.straightKm(loc, it.pickup) <= DRIVER_RADIUS_KM
                }.sortedBy { Geo.straightKm(loc, it.pickup) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- যাত্রীর বুকিং ----------
    var step by mutableStateOf(BookStep.PICKUP)
        private set
    var pickup by mutableStateOf<Pt?>(null)
        private set
    var dest by mutableStateOf<Pt?>(null)
        private set
    var vehicle by mutableStateOf<VehicleType?>(null)
    var distanceKm by mutableStateOf(0.0)
        private set
    var myLoc by mutableStateOf<Pt?>(null)
        private set
    var focus by mutableStateOf<Focus?>(null)
        private set
    private var focusSeq = 0

    init {
        start()
        // অ্যাপ আবার খুললে চলমান রাইড নিজে থেকেই ফিরে আসে।
        viewModelScope.launch {
            myRides.collect { list ->
                if (trackedId.value == null) {
                    val active = list.filter { it.isActive }.maxByOrNull { it.createdAtMs }
                    if (active != null) trackedId.value = active.id
                }
            }
        }
        // ৩ মিনিটে কেউ না নিলে স্বয়ংক্রিয় বাতিল (যাত্রীর ফোন থেকে)।
        viewModelScope.launch {
            ticker.collect { now ->
                val r = shownRide.value
                if (r != null && role.value == "passenger" &&
                    r.status == RideStatus.REQUESTED &&
                    now - r.createdAtMs > REQUEST_TIMEOUT_MS
                ) {
                    Repo.cancelRide(r.id, "timeout", onlyIfRequested = true)
                }
            }
        }
    }

    // ---------- শুরু ও নিবন্ধন ----------
    fun start() {
        stage = Stage.Loading
        viewModelScope.launch {
            try {
                uid = withTimeout(20_000) { Repo.ensureSignedIn() }
                val p = withTimeout(20_000) { Repo.loadProfile(uid) }
                profile = p
                if (p == null || p.role.isBlank()) {
                    stage = Stage.ChooseRole
                } else {
                    enter(p)
                }
            } catch (e: Exception) {
                stage = Stage.Failed("সংযোগ হয়নি। ইন্টারনেট আছে কি না দেখে আবার চেষ্টা করুন।")
            }
        }
    }

    private fun enter(p: Profile) {
        trackedId.value = null
        hidden.value = emptySet()
        resetBooking()
        vehicleKey.value = if (p.role == "driver") p.vehicleType else null
        role.value = p.role
        stage = if (p.role == "driver") Stage.Driver else Stage.Passenger
        if (p.role == "driver" && !LocationHub.running.value) {
            viewModelScope.launch {
                try {
                    Repo.setDriverOffline(uid)
                } catch (e: Exception) {
                    // গুরুত্বপূর্ণ নয়
                }
            }
        }
    }

    fun chooseRole(r: String) {
        stage = Stage.Register(r)
    }

    fun saveProfile(roleName: String, name: String, phone: String, vehicleKeyValue: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val p = Profile(
                    name = name.trim(),
                    phone = phone,
                    role = roleName,
                    vehicleType = if (roleName == "driver") vehicleKeyValue else (profile?.vehicleType ?: ""),
                )
                withTimeout(20_000) { Repo.saveProfile(uid, p) }
                profile = p
                enter(p)
            } catch (e: Exception) {
                message = "সংরক্ষণ করা যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
            } finally {
                busy = false
            }
        }
    }

    fun switchRole() {
        if (LocationHub.running.value) {
            message = "ভূমিকা বদলানোর আগে অফলাইন হোন।"
            return
        }
        val r = shownRide.value
        if (r != null && r.isActive) {
            message = "আগে চলমান রাইড শেষ বা বাতিল করুন।"
            return
        }
        role.value = null
        vehicleKey.value = null
        trackedId.value = null
        stage = Stage.ChooseRole
    }

    // ---------- মানচিত্র ----------
    fun focusOn(pt: Pt, zoom: Double? = null) {
        focusSeq += 1
        focus = Focus(pt, zoom, focusSeq)
    }

    fun setMyLocation(pt: Pt, recenter: Boolean) {
        myLoc = pt
        if (recenter) focusOn(pt, 17.0)
    }

    // ---------- যাত্রী: বুকিংয়ের ধাপ ----------
    fun confirmPickup(center: Pt) {
        pickup = center
        step = BookStep.DEST
    }

    fun confirmDest(center: Pt) {
        val p = pickup ?: return
        viewModelScope.launch {
            val km = DistanceProvider.calculator.roadKm(p, center)
            if (km < 0.1) {
                message = "গন্তব্য পিকআপের খুব কাছে। মানচিত্র সরিয়ে অন্য জায়গা বাছুন।"
                return@launch
            }
            dest = center
            distanceKm = km
            step = BookStep.VEHICLE
        }
    }

    fun backStep() {
        when (step) {
            BookStep.VEHICLE -> {
                step = BookStep.DEST
                dest?.let { focusOn(it) }
            }
            BookStep.DEST -> {
                step = BookStep.PICKUP
                pickup?.let { focusOn(it) }
            }
            BookStep.PICKUP -> {}
        }
    }

    private fun resetBooking() {
        step = BookStep.PICKUP
        pickup = null
        dest = null
        vehicle = null
        distanceKm = 0.0
        myLoc?.let { focusOn(it, 17.0) }
    }

    fun requestRide() {
        val p = profile ?: return
        val pk = pickup ?: return
        val d = dest ?: return
        val v = vehicle ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val fare = FareConfig.estimate(v, distanceKm)
                val id = withTimeout(20_000) { Repo.createRide(uid, p, v, pk, d, distanceKm, fare) }
                trackedId.value = id
                resetBooking()
            } catch (e: Exception) {
                message = "রিকোয়েস্ট পাঠানো যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
            } finally {
                busy = false
            }
        }
    }

    // ---------- রাইড: দুই পক্ষের সাধারণ কাজ ----------
    fun cancelRide() {
        val r = shownRide.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val ok = Repo.cancelRide(r.id, role.value ?: "passenger")
            if (!ok) message = "বাতিল করা যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
            busy = false
        }
    }

    fun dismissRide() {
        trackedId.value = null
    }

    // ---------- চালক ----------
    fun goOnline() {
        val v = profile?.vehicleType ?: return
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, LocationService::class.java).putExtra(LocationService.EXTRA_VEHICLE, v),
        )
    }

    fun goOffline() {
        val r = shownRide.value
        if (r != null && r.isActive) {
            message = "চলমান রাইড থাকলে অফলাইন হওয়া যায় না।"
            return
        }
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, LocationService::class.java))
        LocationHub.running.value = false
        viewModelScope.launch {
            try {
                Repo.setDriverOffline(uid)
            } catch (e: Exception) {
                // গুরুত্বপূর্ণ নয়
            }
        }
    }

    fun acceptRequest(r: Ride) {
        val p = profile ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val ok = Repo.acceptRide(r.id, uid, p)
            if (ok) {
                trackedId.value = r.id
            } else {
                message = "দুঃখিত, রাইডটি আর খালি নেই (অন্য চালক নিয়েছেন বা যাত্রী বাতিল করেছেন)।"
                hidden.value = hidden.value + r.id
            }
            busy = false
        }
    }

    /** শুধু পরীক্ষার মোডে: কাছাকাছি একটি নকল যাত্রীর রিকোয়েস্ট আনে। */
    fun addDemoRequest() {
        val loc = LocationHub.location.value
        val v = profile?.vehicleType
        if (loc == null || v == null) {
            message = "আপনার অবস্থান পেতে একটু অপেক্ষা করুন, তারপর আবার চাপুন।"
            return
        }
        Repo.createDemoRequest(loc, v)
    }

    fun rejectRequest(r: Ride) {
        hidden.value = hidden.value + r.id
    }

    fun driverStep(status: String) {
        val r = shownRide.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withTimeout(20_000) { Repo.setRideStatus(r.id, status) }
            } catch (e: Exception) {
                message = "আপডেট হয়নি। ইন্টারনেট দেখে আবার চাপুন।"
            } finally {
                busy = false
            }
        }
    }
}
