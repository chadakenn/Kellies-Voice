package com.example.tvvoiceprobe.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tvvoiceprobe.CredentialStore
import com.example.tvvoiceprobe.MicRoutePolicy
import com.example.tvvoiceprobe.ComposeSettingsInterop
import com.example.tvvoiceprobe.OverlayPreferences
import com.example.tvvoiceprobe.ScreensaverWatchService
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val skin = remember { SkinStore.getSelected(this) }
            KellieTheme(skin = skin) {
                SettingsScreen(
                    onSkinChange = { newSkin ->
                        SkinStore.setSelected(this, newSkin)
                        recreate()
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
    onSkinChange: (com.example.tvvoiceprobe.ui.theme.KellieSkin) -> Unit,
    onBack: () -> Unit
) {
    val skin = LocalKellieSkin.current
    var selected by remember { mutableStateOf("home") }

    val categories = listOf(
        SettingsCategory("home", "Home Assistant", "Connection, pairing and conversation", "🏠"),
        SettingsCategory("voice", "Voice & microphone", "Wake word, sensitivity and input", "🎤"),
        SettingsCategory("overlay", "Overlay & appearance", "Popup, size, position and theme", "🎨"),
        SettingsCategory("alerts", "Alerts & cameras", "Notifications, speech and video", "🔔"),
        SettingsCategory("screensaver", "Screensaver", "Frigate dashboard and cameras", "🖥️"),
        SettingsCategory("updates", "Updates & account", "Versions and saved login", "👤")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(skin.colors.screen)
            .padding(32.dp)
    ) {
        Text(
            text = "Kellie's Voice  ·  Compose Settings (preview)",
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
                modifier = Modifier
                    .weight(1.05f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { cat ->
                    CategoryCard(
                        category = cat,
                        selected = selected == cat.id,
                        onSelect = { selected = cat.id }
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
                            onSkinChange = onSkinChange
                        )
                    }
                }
            }
        }

        Text(
            text = "D-pad to move  •  OK to select  •  Back returns to voice screen",
            color = skin.colors.textMuted,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { onBack() }
        )
    }
}

@Composable
fun CategoryCard(
    category: SettingsCategory,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val skin = LocalKellieSkin.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused || selected) skin.colors.cardFocused else skin.colors.card,
                skin.cardShape
            )
            .border(
                width = if (focused || selected) skin.focusBorderWidth else 1.dp,
                color = if (focused || selected) skin.colors.focus else skin.colors.stroke,
                shape = skin.cardShape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
    onSkinChange: (com.example.tvvoiceprobe.ui.theme.KellieSkin) -> Unit
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
                Text("Select theme (saved to OverlayPreferences, shared with overlay and legacy Settings):", color = skin.colors.textMuted, fontSize = 13.sp)
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
                            .focusable()
                            .clickable { onSkinChange(s) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (s.id == skin.id) "●  ${s.displayName}" else "○  ${s.displayName}",
                            color = if (s.id == skin.id) skin.colors.accent else skin.colors.text,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            "voice" -> {
                var wake by remember { mutableStateOf(prefs.wakeWord() && WakeWordService.isListening()) }
                SettingSwitch(
                    title = "Wake word (Okay Nabu)",
                    subtitle = if (WakeWordService.isListening()) "LISTENING" else if (prefs.wakeWord()) "Opted in, stopped" else "OFF",
                    checked = wake,
                    onCheckedChange = { on ->
                        if (on) {
                            // Match VoiceActivity.enableWakeWord() preflight
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
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sensitivity: ${OverlayPreferences.noiseLabel(prefs.noise())}", color = skin.colors.text)
                Text("Mic disabled: ${prefs.microphoneDisabled()}", color = skin.colors.textMuted, fontSize = 13.sp)
                Text("Use legacy SETTINGS for sensitivity, mic checks, and full wake-word diagnostics.", color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "alerts" -> {
                var events by remember { mutableStateOf(prefs.tvEvents()) }
                SettingSwitch(
                    title = "TV notifications & camera popups",
                    subtitle = if (events) "ON" else "OFF",
                    checked = events,
                    onCheckedChange = { on ->
                        if (on) {
                            val creds = try { CredentialStore(context).load() } catch (e: Exception) { null }
                            if (creds == null) {
                                Toast.makeText(context, "Configure Home Assistant before enabling TV notifications", Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Grant overlay permission first", Toast.LENGTH_LONG).show()
                                return@SettingSwitch
                            }
                            prefs.setTvEvents(true)
                            try {
                                context.startForegroundService(Intent(context, TvEventsService::class.java)
                                    .setAction(TvEventsService.ACTION_START))
                                events = true
                                Toast.makeText(context, "TV alerts enabled", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                prefs.setTvEvents(false)
                                events = false
                                Toast.makeText(context, "Android couldn't start the TV alert receiver", Toast.LENGTH_SHORT).show()
                            }
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
                Text("Announcement volume: ${prefs.spokenAnnouncementVolume()}%", color = skin.colors.text)
                Text("Camera decoder: ${prefs.cameraMode()}", color = skin.colors.textMuted, fontSize = 13.sp)
                Text("Use legacy SETTINGS for volume, camera mode, and alert status.", color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "screensaver" -> {
                var ss by remember { mutableStateOf(prefs.screensaverDashboard()) }
                SettingSwitch(
                    title = "Frigate screensaver",
                    subtitle = if (ss) "ON" else "OFF",
                    checked = ss,
                    onCheckedChange = { on ->
                        prefs.setScreensaverDashboard(on)
                        ss = on
                        if (on) {
                            ScreensaverWatchService.startIfEnabled(context)
                        } else {
                            try {
                                context.startForegroundService(Intent(context, ScreensaverWatchService::class.java)
                                    .setAction(ScreensaverWatchService.ACTION_STOP))
                            } catch (e: Exception) { }
                            ComposeSettingsInterop.screensaverSettingsChanged()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("URL: ${prefs.screensaverUrl().ifEmpty { "(none)" }}", color = skin.colors.textMuted, fontSize = 13.sp)
                Text("Live mode: ${prefs.screensaverLiveMode()}", color = skin.colors.textMuted, fontSize = 13.sp)
                Text("Use legacy SETTINGS for URL, cameras, size, fit, and preview.", color = skin.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "home" -> {
                Text("HA layout management: ${if (prefs.haManage()) "ON" else "OFF"}", color = skin.colors.text)
                Text("Use the legacy SETTINGS button for full HA address/token entry and reconnect. Those flows include URL validation and immediate Assist updates.", color = skin.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "updates" -> {
                Text("Update channel: ${prefs.updateChannel()}", color = skin.colors.text)
                Text("Use the legacy SETTINGS button for Check / Install / Forget. Those paths include release verification and safe deferred install.", color = skin.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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
            .focusable()
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
