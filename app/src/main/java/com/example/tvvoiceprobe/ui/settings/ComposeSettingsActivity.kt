package com.example.tvvoiceprobe.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.tvvoiceprobe.ScreensaverCameraInput
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.tvvoiceprobe.ComposeHomeStatus
import com.example.tvvoiceprobe.CredentialStore
import com.example.tvvoiceprobe.HaDiscovery
import com.example.tvvoiceprobe.ScreensaverWatchService
import com.example.tvvoiceprobe.MicRoutePolicy
import com.example.tvvoiceprobe.ComposeSettingsInterop
import com.example.tvvoiceprobe.HaLayout
import com.example.tvvoiceprobe.UpdateChannel
import com.example.tvvoiceprobe.BuildConfig
import com.example.tvvoiceprobe.LiveCameraMode
import com.example.tvvoiceprobe.ScreensaverDashboard
import com.example.tvvoiceprobe.ScreensaverLiveMode
import com.example.tvvoiceprobe.ScreensaverPreviewActivity
import com.example.tvvoiceprobe.FirstRunSetupActivity
import com.example.tvvoiceprobe.TvAlertLayout
import com.example.tvvoiceprobe.OverlayPreferences
import com.example.tvvoiceprobe.TvEventsService
import com.example.tvvoiceprobe.WakeWordService
import com.example.tvvoiceprobe.ui.theme.KellieSkins
import com.example.tvvoiceprobe.ui.theme.KellieTheme
import com.example.tvvoiceprobe.ui.theme.LocalKellieSkin
import com.example.tvvoiceprobe.ui.theme.SkinStore

/**
 * Premium full-screen Jetpack Compose for TV Settings preview.
 * Original dialog Settings remain fully available via the SETTINGS toolbar button.
 * Controls reuse OverlayPreferences and existing service entry points.
 */
class ComposeSettingsActivity : ComponentActivity() {
    // Window focus can return after the soft keyboard closes even when the
    // Compose screen did not re-enter composition.
    private var focusEpoch by mutableIntStateOf(0)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusEpoch++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var skin by remember { mutableStateOf(SkinStore.getSelected(this)) }
            KellieTheme(skin = skin) {
                SettingsScreen(
                    windowFocusEpoch = focusEpoch,
                    onLegacySection = { section ->
                        ComposeSettingsInterop.requestLegacySection(section)
                        finish()
                    },
                    onSkinChange = { newSkin ->
                        SkinStore.setSelected(this, newSkin)
                        skin = newSkin
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.STYLE)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

data class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String
)

@Composable
fun SettingsScreen(
    windowFocusEpoch: Int,
    onLegacySection: (String) -> Unit,
    onSkinChange: (com.example.tvvoiceprobe.ui.theme.KellieSkin) -> Unit,
    onBack: () -> Unit
) {
    val skin = LocalKellieSkin.current
    var selected by rememberSaveable { mutableStateOf("home") }
    var editorOpen by remember { mutableStateOf(false) }
    val categoryFocus = remember {
        listOf("home", "voice", "overlay", "alerts", "screensaver", "updates")
            .associateWith { FocusRequester() }
    }
    val latestSelected by rememberUpdatedState(selected)
    val categoryListState = rememberLazyListState()
    // Editor state belongs to Screensaver. Don't leave its focus guard active
    // if the remote switches categories while an editor was open.
    LaunchedEffect(selected) {
        if (selected != "screensaver") editorOpen = false
    }

    // A one-shot onCreate focus request misses some onn keyboard/IME teardown
    // paths. Retry once after the actual Android window regains focus, and when
    // an inline editor closes. Never steal focus while the user is typing.
    LaunchedEffect(windowFocusEpoch, editorOpen) {
        if (!editorOpen) {
            val categoryOrder = listOf("home", "voice", "overlay", "alerts", "screensaver", "updates")
            val targetIndex = categoryOrder.indexOf(latestSelected).coerceAtLeast(0)
            // LazyColumn may not compose off-screen rows after a screen restore.
            // Bring the category into composition before requesting remote focus.
            if (categoryListState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                categoryListState.scrollToItem(targetIndex)
            }
            withFrameNanos { }
            categoryFocus[categoryOrder[targetIndex]]?.requestFocus()
        }
    }
    BackHandler(onBack = onBack)

    val categories = listOf(
        SettingsCategory("home", "Home Assistant", "Connection, pairing and conversation", "\uD83C\uDFE0"),
        SettingsCategory("voice", "Voice & microphone", "Wake word, sensitivity and input", "\uD83C\uDFA4"),
        SettingsCategory("overlay", "Overlay & appearance", "Popup, size, position and theme", "\uD83C\uDFA8"),
        SettingsCategory("alerts", "Alerts & cameras", "Notifications, speech and video", "\uD83D\uDD14"),
        SettingsCategory("screensaver", "Screensaver", "Frigate dashboard and cameras", "\uD83D\uDDA5\uFE0F"),
        SettingsCategory("updates", "Updates & account", "Versions and saved login", "\uD83D\uDC64")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(skin.colors.screen)
            .padding(32.dp)
    ) {
        Text(
            text = if (BuildConfig.IS_QA) "Kellie's Voice  \u00b7  Settings"
                else "Kellie's Voice  \u00b7  Compose Settings (preview)",
            color = skin.colors.accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Settings",
            color = skin.colors.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Row(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = categoryListState,
                modifier = Modifier
                    .weight(1.05f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { cat ->
                    CategoryCard(
                        category = cat,
                        selected = selected == cat.id,
                        onSelect = { selected = cat.id },
                        categoryFocus = categoryFocus[cat.id]
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1.6f)
                    .background(skin.colors.panel, skin.panelShape)
                    .border(1.dp, skin.colors.stroke, skin.panelShape)
                    .padding(16.dp)
            ) {
                LazyColumn {
                    item {
                        CategoryContent(
                            categoryId = selected,
                            onSkinChange = onSkinChange,
                            onLegacySection = onLegacySection,
                            onEditorStateChanged = { editorOpen = it }
                        )
                    }
                }
            }
        }

        Text(
            text = "D-pad to move  \u2022  OK to select  \u2022  Back returns to voice screen",
            color = skin.colors.textMuted,
            fontSize = 14.sp,
            // Instructional text is not a D-pad action. BackHandler above
            // handles Back without stealing initial focus from categories.
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun CategoryCard(
    category: SettingsCategory,
    selected: Boolean,
    onSelect: () -> Unit,
    categoryFocus: FocusRequester? = null
) {
    val skin = LocalKellieSkin.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) skin.colors.cardFocused else skin.colors.card,
                skin.cardShape
            )
            .border(
                width = if (focused) skin.focusBorderWidth else if (selected) 2.dp else 1.dp,
                color = if (focused) skin.colors.focus else if (selected) skin.colors.accentSecondary else skin.colors.stroke,
                shape = skin.cardShape
            )
            .then(if (categoryFocus == null) Modifier else Modifier.focusRequester(categoryFocus))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = category.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(text = category.title, color = skin.colors.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(text = category.subtitle, color = skin.colors.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun CategoryContent(
    categoryId: String,
    onSkinChange: (com.example.tvvoiceprobe.ui.theme.KellieSkin) -> Unit,
    onLegacySection: (String) -> Unit,
    onEditorStateChanged: (Boolean) -> Unit
) {
    val skin = LocalKellieSkin.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { OverlayPreferences(context) }

    Column {
        Text(
            text = when (categoryId) {
                "home" -> "Home Assistant"
                "voice" -> "Voice & microphone"
                "overlay" -> "Overlay & appearance"
                "alerts" -> "Alerts & cameras"
                "screensaver" -> "Screensaver"
                "updates" -> "Updates & account"
                else -> categoryId
            },
            color = skin.colors.accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (categoryId) {
            "overlay" -> {
                Text("Current skin: ${skin.displayName}", color = skin.colors.text, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select theme (shared with the voice overlay and Home Assistant):", color = skin.colors.textMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                KellieSkins.ALL.forEach { s ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (focused) skin.colors.cardFocused else skin.colors.card, skin.cardShape)
                            .border(
                                if (focused || s.id == skin.id) skin.focusBorderWidth else 1.dp,
                                if (focused || s.id == skin.id) skin.colors.focus else skin.colors.stroke,
                                skin.cardShape
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSkinChange(s) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (s.id == skin.id) "\u25cf  ${s.displayName}" else "\u25cb  ${s.displayName}",
                            color = if (s.id == skin.id) skin.colors.accent else skin.colors.text,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Community Themes", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Import JSON themes created by the community. Data only — never code.", color = skin.colors.textMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                var installed by remember { mutableStateOf(com.example.tvvoiceprobe.CommunityThemeStore.list(context)) }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val theme = com.example.tvvoiceprobe.CommunityThemeStore.importTheme(context, stream)
                                installed = com.example.tvvoiceprobe.CommunityThemeStore.list(context)
                                Toast.makeText(context, "Imported: ${theme.name}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                SettingsChoice(title = "Import Theme", selected = false, onSelect = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                })

                installed.forEach { theme ->
                    val selected = skin.id == theme.id
                    SettingsChoice(
                        title = if (selected) "● ${theme.name} (${theme.author})" else "○ ${theme.name} (${theme.author})",
                        selected = selected,
                        onSelect = {
                            val prefs = OverlayPreferences(context)
                            prefs.setSkin(theme.id)
                            onSkinChange(SkinStore.getSelected(context))
                            Toast.makeText(context, "Applied: ${theme.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (installed.isNotEmpty()) {
                    SettingsChoice(title = "Remove selected community theme", selected = false, onSelect = {
                        val current = OverlayPreferences(context).skin()
                        if (com.example.tvvoiceprobe.CommunityThemeStore.isId(current)) {
                            val file = java.io.File(context.filesDir, "community-themes/$current.json")
                            if (file.delete()) {
                                OverlayPreferences(context).setSkin(OverlayPreferences.SKIN_GLASS)
                                installed = com.example.tvvoiceprobe.CommunityThemeStore.list(context)
                                onSkinChange(SkinStore.getSelected(context))
                                Toast.makeText(context, "Removed theme", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }

                SettingsChoice(title = "Restore Default (Copper Glass)", selected = false, onSelect = {
                    OverlayPreferences(context).setSkin(OverlayPreferences.SKIN_GLASS)
                    onSkinChange(SkinStore.getSelected(context))
                    Toast.makeText(context, "Restored default theme", Toast.LENGTH_SHORT).show()
                })

                Spacer(modifier = Modifier.height(16.dp))
                Text("Popup position", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                val positions = listOf(
                    OverlayPreferences.POSITION_DEFAULT to "Default",
                    OverlayPreferences.POSITION_TOP_LEFT to "Top left",
                    OverlayPreferences.POSITION_TOP_RIGHT to "Top right",
                    OverlayPreferences.POSITION_BOTTOM_LEFT to "Bottom left",
                    OverlayPreferences.POSITION_BOTTOM_RIGHT to "Bottom right",
                    OverlayPreferences.POSITION_CENTER to "Center",
                    OverlayPreferences.POSITION_BOTTOM_CENTER to "Bottom center"
                )
                var currentPos by remember { mutableStateOf(prefs.position()) }
                positions.forEach { (id, label) ->
                    SettingsChoice(title = label, selected = id == currentPos, onSelect = {
                        prefs.setPosition(id)
                        currentPos = id
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.POSITION)
                        Toast.makeText(context, "Position saved: $label. Reopen overlay to apply.", Toast.LENGTH_SHORT).show()
                    })
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Popup size", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                val sizes = listOf(
                    OverlayPreferences.SIZE_SMALL to "Small",
                    OverlayPreferences.SIZE_MEDIUM to "Medium",
                    OverlayPreferences.SIZE_LARGE to "Large"
                )
                var currentSize by remember { mutableStateOf(prefs.size()) }
                sizes.forEach { (id, label) ->
                    SettingsChoice(title = label, selected = id == currentSize, onSelect = {
                        prefs.setSize(id)
                        currentSize = id
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.SIZE)
                        Toast.makeText(context, "Size saved: $label. Reopen overlay to apply.", Toast.LENGTH_SHORT).show()
                    })
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Auto-hide after answer", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                val hideOptions = listOf(5, 7, 10, 20, 0)
                var currentHide by remember { mutableStateOf(prefs.hideSeconds()) }
                hideOptions.forEach { secs ->
                    val label = if (secs == 0) "Never (manual close)" else "$secs seconds${if (secs == 7) " (original)" else ""}"
                    SettingsChoice(
                        title = label,
                        selected = secs == currentHide || (secs == 0 && !prefs.autoHide()),
                        onSelect = {
                            prefs.setHideSeconds(secs)
                            currentHide = secs
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.AUTO_HIDE)
                            Toast.makeText(context, "Auto-hide: ${prefs.hideLabel()}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Weather popup", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var weatherPos by remember { mutableStateOf(prefs.weatherPosition()) }
                Text("Position: ${com.example.tvvoiceprobe.WeatherLayout.positionLabel(weatherPos)}", color = skin.colors.text, fontSize = 14.sp)
                listOf("bottom_center", "top_left", "top_right", "bottom_left", "bottom_right").forEach { p ->
                    SettingsChoice(
                        title = com.example.tvvoiceprobe.WeatherLayout.positionLabel(p),
                        selected = p == weatherPos,
                        onSelect = {
                            prefs.setWeatherPosition(p)
                            weatherPos = p
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.WEATHER_POSITION)
                            Toast.makeText(context, "Weather position saved", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                var weatherSize by remember { mutableStateOf(prefs.weatherSize()) }
                Text("Size: $weatherSize", color = skin.colors.text, fontSize = 14.sp)
                listOf("small", "medium", "large").forEach { sz ->
                    SettingsChoice(title = sz.replaceFirstChar { it.uppercase() }, selected = sz == weatherSize, onSelect = {
                        prefs.setWeatherSize(sz)
                        weatherSize = sz
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.WEATHER_SIZE)
                        Toast.makeText(context, "Weather size saved", Toast.LENGTH_SHORT).show()
                    })
                }
                var weatherSecs by remember { mutableStateOf(prefs.weatherSeconds()) }
                Text("Duration: ${weatherSecs}s", color = skin.colors.text, fontSize = 14.sp)
                listOf(8, 20, 30, 45).forEach { s ->
                    SettingsChoice(title = "${s}s", selected = s == weatherSecs, onSelect = {
                        prefs.setWeatherSeconds(s)
                        weatherSecs = s
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.WEATHER_DURATION)
                        Toast.makeText(context, "Weather duration: ${s}s", Toast.LENGTH_SHORT).show()
                    })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Voice overlay launch", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var autoListen by remember { mutableStateOf(prefs.autoListen()) }
                SettingSwitch(
                    title = "Automatically listen when I open the voice overlay",
                    subtitle = if (autoListen) "ON · intentional overlay launch starts listening" else "OFF · press TALK",
                    checked = autoListen,
                    onCheckedChange = { on ->
                        prefs.setAutoListen(on)
                        autoListen = prefs.autoListen()
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.AUTO_LISTEN)
                    }
                )
                var starFallback by remember { mutableStateOf(prefs.mainLauncherOpensOverlay()) }
                SettingSwitch(
                    title = "Use main launcher for TV remote shortcut",
                    subtitle = if (starFallback) "ON · launcher opens the voice overlay" else "OFF · normal app launcher",
                    checked = starFallback,
                    onCheckedChange = { on ->
                        prefs.setMainLauncherOpensOverlay(on)
                        starFallback = prefs.mainLauncherOpensOverlay()
                    }
                )
                SettingsChoice(
                    title = "Open overlay & additional controls",
                    selected = false,
                    onSelect = { onLegacySection("OVERLAY") }
                )
                Text("Changes save locally. Returning to the voice screen attempts HA layout sync using the existing connection when HA-managed layout is enabled. Reopen the overlay to see visual changes.", color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "voice" -> {
                var wake by remember { mutableStateOf(prefs.wakeWord() && WakeWordService.isListening()) }
                SettingSwitch(
                    title = "Wake word (Okay Nabu)",
                    subtitle = if (WakeWordService.isListening()) "LISTENING" else if (prefs.wakeWord()) "Opted in, stopped" else "OFF",
                    checked = wake,
                    onCheckedChange = { on ->
                        if (on) {
                            val creds = try { CredentialStore(context).load() } catch (e: Exception) { null }
                            if (creds == null) {
                                Toast.makeText(context, "Configure Home Assistant before enabling the wake word", Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            if (prefs.microphoneDisabled()) {
                                Toast.makeText(context, MicRoutePolicy.MANUALLY_DISABLED_MESSAGE, Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            val routeIssue = ComposeSettingsInterop.microphonePreflight(context, prefs.allowUnverifiedMic())
                            if (routeIssue != null) {
                                Toast.makeText(context, routeIssue, Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Grant 'Display over other apps' before enabling the wake word", Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, "Grant microphone permission in Android settings first", Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            prefs.setWakeWord(true)
                            try {
                                context.startForegroundService(Intent(context, WakeWordService::class.java)
                                    .setAction(WakeWordService.ACTION_START))
                                wake = true
                                Toast.makeText(context, "Wake word ON and saved", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                prefs.setWakeWord(false)
                                wake = false
                                Toast.makeText(context, "Android couldn't start the wake word listener", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            prefs.setWakeWord(false)
                            context.stopService(Intent(context, WakeWordService::class.java))
                            wake = false
                            Toast.makeText(context, "Wake word off. The microphone is not listening.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                var follow by remember { mutableStateOf(prefs.followUp()) }
                SettingSwitch(
                    title = "Follow-up listening",
                    subtitle = "Hands-free replies after Assist",
                    checked = follow,
                    onCheckedChange = { on ->
                        prefs.setFollowUp(on)
                        follow = on
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.FOLLOW_TIMEOUT)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Follow-up silence timeout", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var followSecs by remember { mutableIntStateOf(prefs.followUpSeconds()) }
                listOf(5, 8, 10, 15).forEach { seconds ->
                    SettingsChoice(
                        title = "$seconds seconds${if (seconds == 8) " (original)" else ""}",
                        selected = follow && followSecs == seconds,
                        onSelect = {
                            prefs.setFollowUp(true)
                            prefs.setFollowUpSeconds(seconds)
                            follow = true
                            followSecs = prefs.followUpSeconds()
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.FOLLOW_TIMEOUT)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Microphone sensitivity", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Changes only how silence is detected; it does not enable the microphone.",
                    color = skin.colors.textMuted, fontSize = 12.sp)
                var noiseLevel by remember { mutableStateOf(prefs.noise()) }
                listOf(
                    OverlayPreferences.NOISE_QUIET, OverlayPreferences.NOISE_NORMAL,
                    OverlayPreferences.NOISE_TV
                ).forEach { value ->
                    SettingsChoice(
                        title = OverlayPreferences.noiseLabel(value),
                        selected = noiseLevel == value,
                        onSelect = {
                            prefs.setNoise(value)
                            noiseLevel = prefs.noise()
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.NOISE)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mic disabled: ${prefs.microphoneDisabled()}", color = skin.colors.textMuted, fontSize = 13.sp)
                SettingsChoice(
                    title = "Advanced microphone routing & diagnostics",
                    selected = false,
                    onSelect = { onLegacySection("VOICE") }
                )
                Text("Advanced diagnostics use the existing tested controls until fully migrated.",
                    color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "alerts" -> {
                var events by remember { mutableStateOf(prefs.tvEvents()) }
                SettingSwitch(
                    title = "TV notifications & camera popups",
                    subtitle = if (events) "ON" else "OFF",
                    checked = events,
                    onCheckedChange = { on ->
                        if (on) {
                            // The shared authenticated setup path supports both
                            // manual credentials and v3 Keystore-backed TV grants.
                            // Only explicit user consent can start the receiver.
                            onLegacySection("TV_ALERTS_ENABLE")
                        } else {
                            prefs.setTvEvents(false)
                            context.stopService(Intent(context, TvEventsService::class.java))
                            events = false
                            Toast.makeText(context, "TV announcements and camera popups disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                var spoken by remember { mutableStateOf(prefs.spokenAnnouncements()) }
                SettingSwitch(
                    title = "Spoken announcements",
                    subtitle = "Speak alert text",
                    checked = spoken,
                    onCheckedChange = { on ->
                        prefs.setSpokenAnnouncements(on)
                        spoken = on
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("TV alert popup position", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var alertPos by remember { mutableStateOf(prefs.alertPosition()) }
                val alertPositions = listOf(
                    TvAlertLayout.TOP_LEFT, TvAlertLayout.TOP_RIGHT,
                    TvAlertLayout.BOTTOM_LEFT, TvAlertLayout.BOTTOM_RIGHT, TvAlertLayout.CENTER
                )
                alertPositions.forEach { id ->
                    SettingsChoice(
                        title = TvAlertLayout.positionLabel(id),
                        selected = alertPos == id,
                        onSelect = {
                            prefs.setAlertPosition(id)
                            alertPos = prefs.alertPosition()
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.ALERT_POSITION)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("TV alert popup size", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var alertSize by remember { mutableStateOf(prefs.alertSize()) }
                listOf(TvAlertLayout.SMALL, TvAlertLayout.MEDIUM, TvAlertLayout.LARGE).forEach { id ->
                    SettingsChoice(
                        title = TvAlertLayout.sizeLabel(id),
                        selected = alertSize == id,
                        onSelect = {
                            prefs.setAlertSize(id)
                            alertSize = prefs.alertSize()
                            ComposeSettingsInterop.queueLayoutUpdate(HaLayout.ALERT_SIZE)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Spoken announcement volume", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Only changes alert speech, not media, timers or assistant replies.",
                    color = skin.colors.textMuted, fontSize = 12.sp)
                var announcementVolume by remember { mutableIntStateOf(prefs.spokenAnnouncementVolume()) }
                listOf(20, 40, 60, 80, 100).forEach { percent ->
                    SettingsChoice(
                        title = "$percent%",
                        selected = announcementVolume == percent,
                        onSelect = {
                            prefs.setSpokenAnnouncementVolume(percent)
                            announcementVolume = prefs.spokenAnnouncementVolume()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Live camera decoder", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Safe software is recommended. Hardware decoding may interrupt TV or HDMI video.",
                    color = skin.colors.textMuted, fontSize = 12.sp)
                var cameraMode by remember { mutableStateOf(prefs.cameraMode()) }
                listOf(LiveCameraMode.SOFTWARE, LiveCameraMode.SNAPSHOT, LiveCameraMode.HARDWARE).forEach { mode ->
                    SettingsChoice(
                        title = LiveCameraMode.label(mode),
                        selected = cameraMode == mode,
                        onSelect = {
                            if (mode == LiveCameraMode.HARDWARE && cameraMode != mode) {
                                AlertDialog.Builder(context)
                                    .setTitle("Enable hardware camera decoder?")
                                    .setMessage("Only enable this on a TV box you have tested. " +
                                        "Hardware decoding may blank HDMI or interrupt video playback.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Enable hardware") { _, _ ->
                                        prefs.setCameraMode(mode)
                                        cameraMode = prefs.cameraMode()
                                    }
                                    .show()
                            } else {
                                prefs.setCameraMode(mode)
                                cameraMode = prefs.cameraMode()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsChoice(
                    title = "Preview announcement popup",
                    selected = false,
                    onSelect = {
                        if (!prefs.tvEvents() || !Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "Enable TV alerts and overlay permission first", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                context.startForegroundService(Intent(context, TvEventsService::class.java)
                                    .setAction(TvEventsService.ACTION_PREVIEW))
                            } catch (e: RuntimeException) {
                                Toast.makeText(context, "Android blocked the alert preview", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                SettingsChoice(
                    title = "Test spoken announcement",
                    selected = false,
                    onSelect = {
                        if (!prefs.tvEvents() || !prefs.spokenAnnouncements()) {
                            Toast.makeText(context, "Enable TV alerts and spoken announcements first", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                context.startForegroundService(Intent(context, TvEventsService::class.java)
                                    .setAction(TvEventsService.ACTION_TEST_SPOKEN))
                            } catch (e: RuntimeException) {
                                Toast.makeText(context, "Android blocked the announcement test", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                Text("Changes to popup position and size are saved locally. " +
                    "If HA layout management is enabled, returning to the main screen attempts to sync them. " +
                    "The TV alert service uses the same settings in this app.",
                    color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                SettingsChoice(
                    title = "TV alert receiver status & recovery",
                    selected = false,
                    onSelect = { onLegacySection("ALERTS") }
                )
            }
            "screensaver" -> {
                var ss by remember { mutableStateOf(prefs.screensaverDashboard()) }
                SettingSwitch(
                    title = "Screensaver dashboard / cameras",
                    subtitle = if (ss) "ON" else "OFF",
                    checked = ss,
                    onCheckedChange = { on ->
                        prefs.setScreensaverDashboard(on)
                        ss = on
                        ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_ENABLED)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                var savedUrl by remember { mutableStateOf(prefs.screensaverUrl()) }
                var editingUrl by remember { mutableStateOf(false) }
                var urlDraft by remember { mutableStateOf(savedUrl) }
                var urlError by remember { mutableStateOf<String?>(null) }
                Text("Dashboard URL (Frigate or Home Assistant)", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (!editingUrl) {
                    Text(
                        text = if (savedUrl.isEmpty()) "Not configured" else savedUrl,
                        color = skin.colors.textMuted,
                        fontSize = 13.sp
                    )
                    SettingsChoice(
                        title = if (savedUrl.isEmpty()) "Enter dashboard URL" else "Edit dashboard URL",
                        selected = false,
                        onSelect = {
                            urlDraft = savedUrl
                            urlError = null
                            editingUrl = true
                            onEditorStateChanged(true)
                        }
                    )
                    if (savedUrl.isNotEmpty()) {
                        SettingsChoice(
                            title = "Clear dashboard URL",
                            selected = false,
                            onSelect = {
                                prefs.setScreensaverUrl("")
                                savedUrl = ""
                                ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_URL)
                                Toast.makeText(context, "Dashboard URL cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = {
                            urlDraft = it
                            urlError = null
                        },
                        label = { Text("http://frigate.local:5000/") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = urlError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = skin.colors.text,
                            unfocusedTextColor = skin.colors.text,
                            focusedContainerColor = skin.colors.card,
                            unfocusedContainerColor = skin.colors.card,
                            focusedBorderColor = skin.colors.focus,
                            unfocusedBorderColor = skin.colors.stroke,
                            focusedLabelColor = skin.colors.accent,
                            unfocusedLabelColor = skin.colors.textMuted,
                            cursorColor = skin.colors.accent,
                            errorTextColor = skin.colors.text,
                            errorBorderColor = skin.colors.warning
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    urlError?.let {
                        Text(it, color = skin.colors.warning, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        "Full http:// or https:// address. No passwords or tokens. Leave empty to clear.",
                        color = skin.colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SettingsChoice(
                        title = "Save URL",
                        selected = false,
                        onSelect = {
                            val typed = urlDraft.trim()
                            val safe = ScreensaverDashboard.dashboardUrl(typed)
                            if (typed.isNotEmpty() && safe.isEmpty()) {
                                urlError = "Use a full http:// or https:// address without a password or token."
                                return@SettingsChoice
                            }
                            prefs.setScreensaverUrl(safe)
                            savedUrl = safe
                            editingUrl = false
                            onEditorStateChanged(false)
                            urlError = null
                            ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_URL)
                            Toast.makeText(
                                context,
                                if (safe.isEmpty()) "Dashboard URL cleared" else "Dashboard URL saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    SettingsChoice(
                        title = "Cancel",
                        selected = false,
                        onSelect = {
                            urlDraft = savedUrl
                            urlError = null
                            editingUrl = false
                            onEditorStateChanged(false)
                        }
                    )
                }

                if (savedUrl.startsWith("http://", ignoreCase = true)) {
                    Text(
                        "LOCAL HTTP: This page stays on your home network, but " +
                            "credentials are not encrypted. Frigate pages and " +
                            "unauthenticated dashboards can use the existing WebView " +
                            "with secure sign-in OFF. Home Assistant login may " +
                            "still get stuck on 'Please wait' (issue #261). " +
                            "Automatic TV token sign-in currently requires " +
                            "a locally reachable HTTPS address. No cloud is required.",
                        color = skin.colors.warning,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                var haWebLogin by remember { mutableStateOf(prefs.screensaverHaExternalAuth()) }
                SettingSwitch(
                    title = "Sign in to Home Assistant dashboard",
                    subtitle = if (haWebLogin) "ON · Dedicated secure TV login" else "OFF · Existing web mode",
                    checked = haWebLogin,
                    onCheckedChange = { enabled ->
                        prefs.setScreensaverHaExternalAuth(enabled)
                        haWebLogin = enabled
                        ComposeSettingsInterop.screensaverSettingsChanged()
                        ComposeSettingsInterop.queueLayoutUpdate(HaLayout.SCREENSAVER_HA_AUTH)
                    }
                )
                Text(
                    "Edit the Screensaver dashboard URL and Secure HA dashboard sign-in " +
                        "switch in Home Assistant under this TV's Kellie's Voice device. " +
                        "Turn on Home Assistant-managed layout on the TV to receive changes. " +
                        "You can still edit these here.\n\n" +
                        "Optional: enter an HTTPS dashboard URL on the same Home Assistant " +
                        "server used for owner-approved TV pairing. The native app gives " +
                        "the page a short-lived access token using HA external auth. " +
                        "No tokens appear in the URL. Frigate and the four-camera " +
                        "screensaver still work when this is OFF. " +
                        "Signing out of the dashboard also revokes this TV's voice/camera login.",
                    color = skin.colors.textMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                var savedCameras by remember { mutableStateOf(prefs.screensaverCameras()) }
                var editingCameras by remember { mutableStateOf(false) }
                var cameraDraft by remember { mutableStateOf(savedCameras) }
                var cameraError by remember { mutableStateOf<String?>(null) }
                // TV Back cancels any open editor and discards its unsaved draft.
                // The parent BackHandler still exits Settings outside edit mode.
                BackHandler(enabled = editingUrl || editingCameras) {
                    if (editingCameras) {
                        cameraDraft = savedCameras
                        cameraError = null
                        editingCameras = false
                    } else {
                        urlDraft = savedUrl
                        urlError = null
                        editingUrl = false
                    }
                    onEditorStateChanged(false)
                }
                Text("Home Assistant cameras", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (!editingCameras) {
                    val count = ScreensaverDashboard.cameraList(savedCameras).size
                    Text(
                        text = if (count == 0) "None selected" else savedCameras,
                        color = skin.colors.textMuted,
                        fontSize = 13.sp
                    )
                    SettingsChoice(
                        title = if (count == 0) "Enter camera entities" else "Edit camera entities",
                        selected = false,
                        onSelect = {
                            cameraDraft = savedCameras
                            cameraError = null
                            editingCameras = true
                            onEditorStateChanged(true)
                        }
                    )
                    if (count > 0) {
                        SettingsChoice(
                            title = "Clear camera list",
                            selected = false,
                            onSelect = {
                                prefs.setScreensaverCameras("")
                                savedCameras = ""
                                ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_CAMERAS)
                                Toast.makeText(context, "Camera list cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = cameraDraft,
                        onValueChange = {
                            cameraDraft = it
                            cameraError = null
                        },
                        label = { Text("camera.driveway, camera.doorbell") },
                        singleLine = true,
                        isError = cameraError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = skin.colors.text,
                            unfocusedTextColor = skin.colors.text,
                            focusedContainerColor = skin.colors.card,
                            unfocusedContainerColor = skin.colors.card,
                            focusedBorderColor = skin.colors.focus,
                            unfocusedBorderColor = skin.colors.stroke,
                            focusedLabelColor = skin.colors.accent,
                            unfocusedLabelColor = skin.colors.textMuted,
                            cursorColor = skin.colors.accent,
                            errorTextColor = skin.colors.text,
                            errorBorderColor = skin.colors.warning
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    cameraError?.let {
                        Text(it, color = skin.colors.warning, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        "Up to four camera.* entity IDs, comma-separated. Invalid IDs are rejected, not discarded.",
                        color = skin.colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SettingsChoice(
                        title = "Save cameras",
                        selected = false,
                        onSelect = {
                            val stored = ScreensaverCameraInput.validatedOrNull(cameraDraft)
                            if (stored == null) {
                                cameraError = "Enter one to four valid camera.* entity IDs. Find them in Home Assistant → Developer Tools → States."
                                return@SettingsChoice
                            }
                            prefs.setScreensaverCameras(stored)
                            savedCameras = stored
                            editingCameras = false
                            onEditorStateChanged(false)
                            cameraError = null
                            ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_CAMERAS)
                            Toast.makeText(
                                context,
                                if (stored.isEmpty()) "Camera list cleared" else "Cameras saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    SettingsChoice(
                        title = "Cancel",
                        selected = false,
                        onSelect = {
                            cameraDraft = savedCameras
                            cameraError = null
                            editingCameras = false
                            onEditorStateChanged(false)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Dashboard size", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var dashboardSize by remember { mutableStateOf(prefs.screensaverSize()) }
                listOf("small", "medium", "large").forEach { value ->
                    SettingsChoice(
                        title = ScreensaverDashboard.sizeLabel(value),
                        selected = dashboardSize == value,
                        onSelect = {
                            prefs.setScreensaverSize(value)
                            dashboardSize = prefs.screensaverSize()
                            ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_SIZE)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Frigate page layout", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var desktopPage by remember { mutableStateOf(prefs.screensaverDesktopPage()) }
                SettingsChoice(
                    title = "Desktop TV",
                    selected = desktopPage,
                    onSelect = {
                        prefs.setScreensaverDesktopPage(true)
                        desktopPage = true
                        ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_PAGE_LAYOUT)
                    }
                )
                SettingsChoice(
                    title = "Automatic (original mobile layout)",
                    selected = !desktopPage,
                    onSelect = {
                        prefs.setScreensaverDesktopPage(false)
                        desktopPage = false
                        ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_PAGE_LAYOUT)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Page fit", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var dashboardFit by remember { mutableStateOf(prefs.screensaverFit()) }
                listOf("Fit more cameras", "Balanced", "Larger text").forEach { value ->
                    SettingsChoice(
                        title = value,
                        selected = dashboardFit == value,
                        onSelect = {
                            prefs.setScreensaverFit(value)
                            dashboardFit = prefs.screensaverFit()
                            ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_FIT)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Camera grid", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Snapshots are most reliable. Multiple live streams can overload some TVs.",
                    color = skin.colors.textMuted, fontSize = 12.sp)
                var liveMode by remember { mutableStateOf(prefs.screensaverLiveMode()) }
                listOf(
                    ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.ONE,
                    ScreensaverLiveMode.TWO, ScreensaverLiveMode.FOUR
                ).forEach { mode ->
                    SettingsChoice(
                        title = mode,
                        selected = liveMode == mode,
                        onSelect = {
                            val applyMode = {
                                prefs.setScreensaverLiveMode(mode)
                                liveMode = prefs.screensaverLiveMode()
                                ComposeSettingsInterop.applyScreensaverSetting(context, HaLayout.SCREENSAVER_LIVE)
                            }
                            if (mode == ScreensaverLiveMode.FOUR && liveMode != mode) {
                                AlertDialog.Builder(context)
                                    .setTitle("Use four simultaneous live streams?")
                                    .setMessage("This mode is advanced and may overload a TV. " +
                                        "Streams may fall back to snapshots when decoding fails.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Enable four streams") { _, _ -> applyMode() }
                                    .show()
                            } else {
                                applyMode()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsChoice(
                    title = "Preview screensaver",
                    selected = false,
                    onSelect = {
                        try {
                            context.startActivity(Intent(context, ScreensaverPreviewActivity::class.java))
                        } catch (e: RuntimeException) {
                            Toast.makeText(context, "Could not open screensaver preview", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                Text("URL and camera changes are validated, saved locally, and refresh the active screensaver. " +
                    "Returning to the main screen attempts HA sync. No microphone is started.",
                    color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                SettingsChoice(
                    title = "Screensaver status & recovery",
                    selected = false,
                    onSelect = { onLegacySection("SCREEN") }
                )
            }
            "home" -> {
                // Remember only presentation-safe text, never decrypted
                // CredentialStore.Connection (which also contains the HA token).
                val loginLabel = remember {
                    try {
                        val saved = CredentialStore(context).load()
                        val address = ComposeHomeStatus.addressSummary(saved?.url)
                        val safeAddress = if (ComposeHomeStatus.summaryHidesSecrets(address, saved?.token))
                            address else ""
                        if (ComposeSettingsInterop.hasOwnerApprovedLogin(context))
                            "Owner-approved TV login activated (credentials kept on TV)"
                        else ComposeHomeStatus.configuredLabel(saved != null) +
                            if (safeAddress.isNotEmpty()) "  ·  $safeAddress" else ""
                    } catch (e: Exception) {
                        "Saved login unavailable; use Home Assistant recovery below"
                    }
                }
                Text("Home Assistant login", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = loginLabel,
                    color = skin.colors.text,
                    fontSize = 16.sp
                )
                Text(
                    "An owner-approved TV login is renewable and requires no pasted token. " +
                        "Secure pairing uses HTTPS for the ongoing Home Assistant connection.",
                    color = skin.colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                SettingsChoice(
                    title = "Connect, edit login or start a new conversation",
                    selected = false,
                    onSelect = { onLegacySection("HOME") }
                )
                if (BuildConfig.DEBUG) {
                    SettingsChoice(
                        title = "First-time setup wizard (pair TV and grant permissions)",
                        selected = false,
                        onSelect = {
                            context.startActivity(Intent(context, FirstRunSetupActivity::class.java))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("TV pairing ID", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                val pairingId = remember { prefs.deviceId() }
                Text(
                    text = pairingId,
                    color = skin.colors.text,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    "For manual pairing: Home Assistant → Settings → Devices & services → " +
                        "Add integration → Kellie's Voice. Enter this full TV ID. On the NEXT screen " +
                        "type the last four characters of the ID shown above to confirm. " +
                        "If Home Assistant says this TV is already configured, use its existing " +
                        "device instead of adding a duplicate. This ID is not your HA token.",
                    color = skin.colors.textMuted,
                    fontSize = 12.sp
                )
                SettingsChoice(
                    title = "Copy pairing ID",
                    selected = false,
                    onSelect = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard == null) {
                            Toast.makeText(context, "Clipboard is not available on this TV", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Kellie's Voice pairing ID", pairingId))
                                Toast.makeText(context, "Pairing ID copied", Toast.LENGTH_SHORT).show()
                            } catch (e: RuntimeException) {
                                Toast.makeText(context, "This TV blocked clipboard access", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Home Assistant layout", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var haLayout by remember { mutableStateOf(prefs.haManage()) }
                SettingSwitch(
                    title = "Manage layout from Home Assistant",
                    subtitle = if (haLayout) "ON" else "OFF",
                    checked = haLayout,
                    onCheckedChange = { on ->
                        prefs.setHaManage(on)
                        haLayout = prefs.haManage()
                        HaDiscovery.setWanted(context, haLayout)
                        if (haLayout || prefs.screensaverDashboard()) {
                            ScreensaverWatchService.startIfEnabled(context)
                        } else {
                            try {
                                context.stopService(Intent(context, ScreensaverWatchService::class.java))
                            } catch (e: RuntimeException) {
                                Toast.makeText(context, "Could not stop the layout watcher", Toast.LENGTH_LONG).show()
                            }
                        }
                        Toast.makeText(
                            context,
                            if (haLayout) "Layout ON. Pair with this TV's ID, or use Discovered in HA."
                            else "Home Assistant layout control off.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                Text(
                    "Return to Kellie's Voice's main screen and leave it open while checking " +
                        "Home Assistant's Discovered devices. Automatic discovery is advertised " +
                        "only while the main app is active. Each installed package uses its own TV pairing " +
                        "ID and saved login, separate from other installed copies. " +
                        "The layout watcher does not start the microphone.",
                    color = skin.colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Connection recovery reuses the authenticated client. " +
                        "Automatic HA-first onboarding is tracked separately.",
                    color = skin.colors.textMuted,
                    fontSize = 12.sp
                )
            }
            "updates" -> {
                Text("Installed version: ${BuildConfig.VERSION_NAME}", color = skin.colors.text)
                Spacer(modifier = Modifier.height(12.dp))
                Text("App update channel", color = skin.colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                var updateChannel by remember { mutableStateOf(prefs.updateChannel()) }
                SettingsChoice(
                    title = "Stable public releases",
                    selected = updateChannel == UpdateChannel.STABLE,
                    onSelect = {
                        if (updateChannel != UpdateChannel.STABLE) {
                            prefs.setUpdateChannel(UpdateChannel.STABLE)
                            updateChannel = prefs.updateChannel()
                            ComposeSettingsInterop.notifyUpdateChannelChanged()
                        }
                    }
                )
                SettingsChoice(
                    title = "Development prereleases (manual check only)",
                    selected = updateChannel == UpdateChannel.DEVELOPMENT,
                    onSelect = {
                        if (updateChannel != UpdateChannel.DEVELOPMENT) {
                            AlertDialog.Builder(context)
                                .setTitle("Use development prereleases?")
                                .setMessage("Development builds are for testing. Choosing this channel " +
                                    "does not check for updates or download anything. " +
                                    "Only published and verified releases can be installed.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Use development") { _, _ ->
                                    prefs.setUpdateChannel(UpdateChannel.DEVELOPMENT)
                                    updateChannel = prefs.updateChannel()
                                    ComposeSettingsInterop.notifyUpdateChannelChanged()
                                }
                                .show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Updates require signature verification and your approval. " +
                    "An older stable version cannot replace a newer build.",
                    color = skin.colors.textMuted, fontSize = 12.sp)
                SettingsChoice(
                    title = "Check, download or install updates",
                    selected = false,
                    onSelect = { onLegacySection("SYSTEM") }
                )
                SettingsChoice(
                    title = "Forget saved Home Assistant login (confirm first)",
                    selected = false,
                    onSelect = { onLegacySection("SYSTEM") }
                )
                Text("These safety-critical controls temporarily use the existing verified menus " +
                    "within the new Settings flow; nothing is silently installed or deleted.",
                    color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                Text("Category content", color = skin.colors.text)
            }
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val skin = LocalKellieSkin.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) skin.colors.cardFocused else skin.colors.card, skin.cardShape)
            .border(
                if (focused) skin.focusBorderWidth else 1.dp,
                if (focused) skin.colors.focus else skin.colors.stroke,
                skin.cardShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onCheckedChange(!checked) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = skin.colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = skin.colors.textMuted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A single focus target for TV remote OK and touch. A focus border and
 * separate selected marker make it clear what is highlighted versus stored.
 */
@Composable
private fun SettingsChoice(title: String, selected: Boolean, onSelect: () -> Unit) {
    val skin = LocalKellieSkin.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (focused) skin.colors.cardFocused else skin.colors.card, skin.cardShape)
            .border(
                if (focused) skin.focusBorderWidth else 1.dp,
                if (focused) skin.colors.focus else skin.colors.stroke,
                skin.cardShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "●  $title" else "○  $title",
            color = if (selected) skin.colors.accent else skin.colors.text,
            fontSize = 15.sp
        )
    }
}
