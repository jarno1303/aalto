package fi.aalto.radio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import fi.aalto.radio.alarm.AlarmLog
import fi.aalto.radio.alarm.AlarmScheduler
import fi.aalto.radio.alarm.AlarmService
import fi.aalto.radio.alarm.AlarmStore
import fi.aalto.radio.alarm.rememberNotificationsEnabled
import fi.aalto.radio.audio.AudioSheet
import fi.aalto.radio.history.HistorySheet
import fi.aalto.radio.history.rememberTrackHistory
import fi.aalto.radio.alarm.dayShort
import fi.aalto.radio.alarm.formatClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import fi.aalto.radio.catalog.CatalogFreshness
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAB_RADIO = 0
private const val TAB_SEARCH = 1
private const val TAB_FAVORITES = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AaltoPerf.markAppStart()
        super.onCreate(savedInstanceState)
        AaltoThemePreferences.load(this)
        enableEdgeToEdge()
        setContent {
            val darkTheme = AaltoThemePreferences.mode.isDark()

            // Content draws behind the system bars; bar icons follow the app theme,
            // including when the user overrides the system light/dark setting.
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AaltoTheme(darkTheme = darkTheme) {
                AaltoApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AaltoAppContainer.syncCoordinator(this).onForeground()
    }

    override fun onStop() {
        AaltoAppContainer.syncCoordinator(this).onBackground()
        super.onStop()
    }
}

@Composable
private fun AaltoApp() {
    val context = LocalContext.current
    val repository = remember(context) {
        AaltoAppContainer.stationRepository(context)
    }
    val catalogRepository = remember(context) {
        AaltoAppContainer.stationCatalogRepository(context)
    }
    val coroutineScope = rememberCoroutineScope()
    val radioPlayer = rememberRadioPlayer()
    val syncCoordinator = remember(context) { AaltoAppContainer.syncCoordinator(context) }
    val syncState by syncCoordinator.state.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAlarm by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showAudio by rememberSaveable { mutableStateOf(false) }
    var showCountries by rememberSaveable { mutableStateOf(false) }
    var ownCountries by remember { mutableStateOf(OwnCountriesPreference.get(context)) }
    val trackHistory = rememberTrackHistory()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var alarmSettings by remember { mutableStateOf(AlarmStore.load(context)) }
    var nextAlarmMillis by remember { mutableStateOf(AlarmScheduler.nextRingMillis(context)) }
    // Refresh the top bar label after an alarm has rung or a snooze ended.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            alarmSettings = AlarmStore.load(context)
            nextAlarmMillis = AlarmScheduler.nextRingMillis(context)
        }
    }
    // The alarm rings either way; the permission only makes its screen visible.
    // The state is followed so the warning goes the moment permission is given.
    val notifications = rememberNotificationsEnabled()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notifications.refresh() }
    // Saveable so the Night Screen survives rotation (e.g. mounting the phone in a car holder).
    var nightScreenActive by rememberSaveable { mutableStateOf(false) }
    var catalogStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogSearchStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var recentStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }

    var selectedStationId by rememberSaveable {
        mutableStateOf(StationCatalog.DEFAULT_STATION_ID)
    }

    var favoriteIds by remember {
        mutableStateOf(repository.initialFavoriteIds())
    }

    var favoriteOrder by remember {
        mutableStateOf(repository.initialFavoriteIds().toList())
    }

    var selectedTab by rememberSaveable {
        mutableStateOf(TAB_RADIO)
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var radioCountryCode by rememberSaveable {
        mutableStateOf(RadioCountryPreference.get(context))
    }
    var allOwnCountries by rememberSaveable {
        mutableStateOf(RadioCountryPreference.allOwn(context))
    }
    // One country (the default), or every followed country at once.
    val browseCountries = if (allOwnCountries && ownCountries.size > 1) ownCountries else listOf(radioCountryCode)

    var catalogRetry by remember { mutableStateOf(0) }
    LaunchedEffect(catalogRepository, browseCountries, catalogRetry) {
        catalogLoading = true
        catalogError = null
        catalogStations = emptyList()

        val loaded = mutableListOf<CatalogStation>()
        browseCountries.forEach { code ->
            when (val result = catalogRepository.getStationsByCountry(code, limit = 100)) {
                is CatalogReadResult.Success -> {
                    var countryStations = result.snapshot.stations
                    if (result.snapshot.freshness == CatalogFreshness.STALE) {
                        when (val refresh = catalogRepository.refreshCountry(code, limit = 100)) {
                            is CatalogResult.Success -> countryStations = refresh.value
                            is CatalogResult.Failure -> Unit
                        }
                    }
                    loaded += countryStations
                    // Show each country as soon as it is there, not after the last one.
                    catalogStations = loaded.toList()
                }
                is CatalogReadResult.Failure -> {
                    if (loaded.isEmpty()) catalogError = result.error.message
                }
            }
        }
        catalogLoading = false
    }

    LaunchedEffect(catalogRepository, browseCountries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            catalogSearchStations = emptyList()
            return@LaunchedEffect
        }

        delay(300)
        catalogSearchStations = browseCountries.flatMap { code ->
            when (val result = catalogRepository.searchStations(query, code, limit = 100)) {
                is CatalogReadResult.Success -> result.snapshot.stations
                is CatalogReadResult.Failure -> emptyList()
            }
        }
    }

    val catalogRadioStations = remember(catalogStations, catalogSearchStations) {
        (catalogStations + catalogSearchStations)
            .mapNotNull { it.toPlayableRadioStationOrNull() }
            .distinctBy { it.stableId }
    }
    LaunchedEffect(catalogRadioStations) {
        repository.registerCatalogStations(catalogRadioStations)
    }
    val stations = remember(repository.stations, catalogRadioStations) {
        // Built-in stations first, so a catalog duplicate of one is dropped.
        (repository.stations + catalogRadioStations).distinctByListing()
    }

    val selectedStation = repository.stationById(selectedStationId) ?: run {
        val fallback = stations.first()
        stationTrace(
            stage = "selected_station_fallback",
            station = fallback,
            detail = "requestedId=$selectedStationId"
        )
        fallback
    }

    val favoriteStations = buildList {
        favoriteOrder.mapNotNullTo(this) { id -> repository.stationById(id)?.takeIf { it.id in favoriteIds } }
        stations.filter { it.id in favoriteIds && it.id !in favoriteOrder }.sortedBy { it.id }.forEach(::add)
    }
    val radioStations = stationsForRadioList(
        stations = favoriteStations + stations,
        selectedStation = selectedStation
    )
    val popularStations = remember(radioStations, selectedStation) {
        stationsForRadioHome(radioStations, selectedStation, maxCount = 10)
    }

    // Recently played, refreshed whenever the playing station changes.
    LaunchedEffect(repository, selectedStationId) {
        recentStations = runCatching { repository.recentStations() }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        withFrameNanos {
            AaltoPerf.reportMainUiReady()
        }
    }

    LaunchedEffect(repository) {
        repository.prepareLocalData()
        repository.observeFavoriteIds().collect { persistedFavoriteIds ->
            favoriteIds = persistedFavoriteIds
        }
    }

    LaunchedEffect(repository) {
        repository.observeFavoriteOrder().collect { favoriteOrder = it }
    }

    LaunchedEffect(syncCoordinator) {
        syncCoordinator.onForeground()
    }

    fun playStation(station: RadioStation, openNowPlaying: Boolean) {
        stationTrace("final_playback_callback", station)
        val trace = AaltoPerf.beginStationTap(station)
        radioPlayer.play(station, trace)
        if (StationCatalog.stationById(station.id) == null) {
            repository.registerCatalogStations(listOf(station))
        }
        selectedStationId = station.id
        stationTrace("ui_current_state_after_click", station)

        if (openNowPlaying) {
            selectedTab = TAB_RADIO
        }

        coroutineScope.launch {
            runCatching {
                repository.recordRecentlyPlayed(station.id)
            }
        }
    }

    // Previous/next follow the user's own station order (preset mental model).
    val canStepPresets = favoriteStations.size >= 2
    fun stepPreset(direction: Int) {
        if (favoriteStations.size < 2) return
        val currentIndex = favoriteStations.indexOfFirst { it.stableId == selectedStation.stableId }
        val nextIndex = if (currentIndex < 0) {
            if (direction > 0) 0 else favoriteStations.lastIndex
        } else {
            Math.floorMod(currentIndex + direction, favoriteStations.size)
        }
        playStation(favoriteStations[nextIndex], openNowPlaying = false)
    }
    val onPrevious: (() -> Unit)? = if (canStepPresets) ({ stepPreset(-1) }) else null
    val onNext: (() -> Unit)? = if (canStepPresets) ({ stepPreset(1) }) else null

    /**
     * Removing a station is destructive, so every route to it is undoable
     * instead of asking for confirmation first: the long press on the home
     * screen, the heart in the favorites list and the heart in Now Playing
     * all land here and all offer the same undo.
     */
    fun removeFavoriteWithUndo(station: RadioStation) {
        val previousOrder = favoriteOrder
        val previousIds = favoriteIds
        favoriteIds = favoriteIds - station.id
        coroutineScope.launch {
            val removed = runCatching { repository.removeFavorite(station.id) }.getOrDefault(false)
            if (!removed) {
                favoriteIds = previousIds
                return@launch
            }
            syncCoordinator.requestSync()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.home_removed, station.name),
                actionLabel = context.getString(R.string.action_undo),
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                runCatching {
                    repository.addFavorite(station.id)
                    // Put it back where it was.
                    repository.reorderFavorites(previousOrder)
                }
                syncCoordinator.requestSync()
            }
        }
    }

    fun commitFavoriteOrder(orderedIds: List<String>) {
        coroutineScope.launch {
            val committed = runCatching { repository.reorderFavorites(orderedIds) }.getOrDefault(false)
            if (committed) syncCoordinator.requestSync()
        }
    }

    /** "Move to top" from a station's menu: the one thing most reordering is. */
    fun moveFavoriteFirst(station: RadioStation) {
        val ordered = listOf(station.id) + favoriteStations.map { it.id }.filter { it != station.id }
        commitFavoriteOrder(ordered)
    }

    fun toggleFavorite(station: RadioStation) {
        if (station.id in favoriteIds) {
            removeFavoriteWithUndo(station)
            return
        }

        val previousFavoriteIds = favoriteIds
        favoriteIds = FavoriteIds.toggle(favoriteIds, station.id)

        coroutineScope.launch {
            runCatching { repository.addFavorite(station.id) }
                .onSuccess { committed ->
                    if (committed) {
                        syncCoordinator.requestSync()
                    } else {
                        favoriteIds = previousFavoriteIds
                    }
                }
                .onFailure {
                    favoriteIds = previousFavoriteIds
                }
        }
    }

    NightScreenSystemBars(active = nightScreenActive)
    BackHandler(enabled = nightScreenActive) {
        nightScreenActive = false
    }

    // Back on another tab returns to the radio instead of leaving the app,
    // the way Android expects a bottom bar to behave.
    BackHandler(enabled = !nightScreenActive && selectedTab != TAB_RADIO) {
        selectedTab = TAB_RADIO
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = selectedTab != TAB_RADIO,
                        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 100))
                    ) {
                        MiniPlayer(
                            station = selectedStation,
                            isPlaying = radioPlayer.isPlaying,
                            isConnecting = radioPlayer.isConnecting,
                            hasError = radioPlayer.playbackError != null,
                            onPlayPause = { radioPlayer.toggle(selectedStation) },
                            onOpen = { selectedTab = TAB_RADIO },
                            onNext = onNext,
                            trackTitle = radioPlayer.nowPlayingTrack
                        )
                    }
                    AaltoBottomNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { paddingValues ->
            when (selectedTab) {
                TAB_RADIO -> RadioScreen(
                    paddingValues = paddingValues,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    isConnecting = radioPlayer.isConnecting,
                    playbackError = radioPlayer.playbackError,
                    favoriteIds = favoriteIds,
                    favoriteStations = favoriteStations,
                    popularStations = popularStations,
                    onPlayPause = { radioPlayer.toggle(selectedStation) },
                    onRetry = { playStation(selectedStation, openNowPlaying = false) },
                    onFavorite = { toggleFavorite(selectedStation) },
                    onFind = { selectedTab = TAB_SEARCH },
                    onOpenAlarm = { showAlarm = true },
                    onRemoveOwnStation = { station -> removeFavoriteWithUndo(station) },
                    onMoveOwnStationFirst = { station -> moveFavoriteFirst(station) },
                    alarmLabel = nextAlarmMillis?.let(::alarmLabel),
                    onOpenHistory = { showHistory = true },
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onOpenSettings = { showSettings = true },
                    onNightScreen = { nightScreenActive = true },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    trackTitle = radioPlayer.nowPlayingTrack
                )

                TAB_SEARCH -> SearchScreen(
                    paddingValues = paddingValues,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    stations = stations,
                    recentStations = recentStations,
                    radioCountryCode = radioCountryCode,
                    ownCountries = ownCountries,
                    onEditCountries = { showCountries = true },
                    onRadioCountryChange = {
                        radioCountryCode = it
                        allOwnCountries = false
                        RadioCountryPreference.set(context, it)
                    },
                    allOwnCountries = allOwnCountries && ownCountries.size > 1,
                    onAllOwnCountries = {
                        allOwnCountries = true
                        RadioCountryPreference.setAllOwn(context, true)
                    },
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    favoriteIds = favoriteIds,
                    catalogStations = catalogStations,
                    catalogLoading = catalogLoading,
                    catalogError = catalogError,
                    onRetryCatalog = { catalogRetry += 1 },
                    onStationClick = { station ->
                        stationTrace(
                            stage = "search_lookup",
                            station = station,
                            detail = "requestedId=${station.id} found=true"
                        )
                        // Stay in search so the user can try several stations; the mini player shows what plays.
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite
                )

                TAB_FAVORITES -> FavoritesScreen(
                    paddingValues = paddingValues,
                    favoriteStations = favoriteStations,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onReorder = { orderedIds -> commitFavoriteOrder(orderedIds) },
                    onFind = { selectedTab = TAB_SEARCH }
                )
            }
        }

        AnimatedVisibility(
            visible = nightScreenActive,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            NightScreen(
                station = selectedStation,
                isPlaying = radioPlayer.isPlaying,
                isConnecting = radioPlayer.isConnecting,
                playbackError = radioPlayer.playbackError,
                onPlayPause = { radioPlayer.toggle(selectedStation) },
                onPrevious = onPrevious,
                onNext = onNext,
                onExit = { nightScreenActive = false },
                trackTitle = radioPlayer.nowPlayingTrack
            )
        }
    }

    // The car, steering wheel, notification or widget can switch station:
    // follow it so the app shows what is actually playing.
    val playerStationId = radioPlayer.currentStationId
    LaunchedEffect(playerStationId) {
        val id = playerStationId ?: return@LaunchedEffect
        if (id == selectedStationId) return@LaunchedEffect
        if (repository.stationById(id) == null) {
            fi.aalto.radio.playback.StationLookup(context).byId(id)
                ?.let { repository.registerCatalogStations(listOf(it)) }
        }
        if (repository.stationById(id) != null) selectedStationId = id
    }

    // Safety net: whenever an alarm rings while the app is open, all three
    // choices are here too, even if the system did not show the alarm view.
    if (fi.aalto.radio.alarm.AlarmRuntime.ringing) {
        fi.aalto.radio.alarm.AlarmRingingDialog(
            stationName = fi.aalto.radio.alarm.AlarmRuntime.stationName,
            snoozeMinutes = fi.aalto.radio.alarm.AlarmRuntime.snoozeMinutes,
            usingFallback = fi.aalto.radio.alarm.AlarmRuntime.usingFallback,
            onSnooze = { AlarmService.send(context, AlarmService.ACTION_SNOOZE) },
            onContinue = { AlarmService.send(context, AlarmService.ACTION_CONTINUE) },
            onDismiss = { AlarmService.send(context, AlarmService.ACTION_DISMISS) }
        )
    }

    if (showCountries) {
        CountryPickerDialog(
            selected = ownCountries,
            onSelectedChange = { updated ->
                ownCountries = updated
                OwnCountriesPreference.set(context, updated)
                // A country that is no longer followed should not stay open.
                if (radioCountryCode !in updated) {
                    updated.firstOrNull()?.let { code ->
                        RadioCountryPreference.set(context, code)
                        radioCountryCode = code
                    }
                }
            },
            onDismiss = { showCountries = false }
        )
    }

    if (showAudio) {
        AudioSheet(
            stationId = selectedStation.id,
            stationName = selectedStation.name,
            onDismiss = { showAudio = false }
        )
    }

    if (showHistory) {
        HistorySheet(
            tracks = trackHistory.items,
            onClear = trackHistory::clear,
            onDismiss = { showHistory = false }
        )
    }

    if (showAlarm) {
        val alarmStations = buildList {
            addAll(favoriteStations)
            add(selectedStation)
            alarmSettings.stationId?.let { repository.stationById(it) }?.let { add(it) }
        }.distinctBy { it.stableId }
        val isDebugBuild = remember {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
        AlarmSheet(
            settings = alarmSettings,
            nextRingMillis = nextAlarmMillis,
            stations = alarmStations,
            log = if (isDebugBuild) AlarmLog.read(context) else emptyList(),
            notificationsEnabled = notifications.enabled,
            onEnableNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Already asked once: open the app's notification settings.
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            },
            onTest = {
                showAlarm = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AlarmService.ringNow(context)
            },
            onChange = { updated ->
                val previous = alarmSettings
                // Every change is saved and scheduled at once (no Save button).
                AlarmStore.save(context, updated)
                if (!updated.enabled) AlarmScheduler.cancelSnooze(context)
                if (updated.hour != previous.hour || updated.minute != previous.minute ||
                    updated.days != previous.days || !updated.enabled
                ) {
                    AlarmStore.setSkipAt(context, 0L)
                }
                AlarmScheduler.reschedule(context)
                alarmSettings = updated
                nextAlarmMillis = AlarmScheduler.nextRingMillis(context)

                val timingChanged = !previous.enabled ||
                    updated.hour != previous.hour ||
                    updated.minute != previous.minute ||
                    updated.days != previous.days
                if (updated.enabled && timingChanged) {
                    // Confirms the time and day right away.
                    AlarmScheduler.nextRegularMillis(context)?.let { next ->
                        Toast.makeText(context, alarmCountdownText(context, next), Toast.LENGTH_SHORT).show()
                    }
                }

                if (updated.enabled && !previous.enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!AlarmScheduler.canScheduleExact(context) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        Toast.makeText(context, R.string.alarm_exact_permission, Toast.LENGTH_LONG).show()
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:" + context.packageName))
                            )
                        }
                    }
                }
            },
            onDismiss = { showAlarm = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            themeMode = AaltoThemePreferences.mode,
            onThemeModeChange = { mode -> AaltoThemePreferences.update(context, mode) },
            syncState = syncState,
            onSignIn = {
                (context as? ComponentActivity)?.let { activity ->
                    coroutineScope.launch { syncCoordinator.signIn(activity) }
                }
            },
            onSignOut = syncCoordinator::signOut,
            onOpenAudio = {
                showSettings = false
                showAudio = true
            },
            onOpenCountries = {
                showSettings = false
                showCountries = true
            },
            onOpenLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        )
                    }
                    Unit
                }
            } else {
                null
            },
            onDismiss = { showSettings = false }
        )
    }
}

/** "7.00" for today or tomorrow, otherwise "ma 7.00". */
private fun alarmLabel(epochMs: Long): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val clock = formatClock(time.hour, time.minute)
    val today = LocalDate.now()
    return when (time.toLocalDate()) {
        today -> clock
        today.plusDays(1) -> clock
        else -> "${dayShort(time.dayOfWeek)} $clock"
    }
}
