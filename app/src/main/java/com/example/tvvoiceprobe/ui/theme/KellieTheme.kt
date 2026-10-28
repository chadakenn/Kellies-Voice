package com.example.tvvoiceprobe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import com.example.tvvoiceprobe.OverlayPreferences

/**
 * Skin definition that can change more than colors.
 * Designed so future downloadable skins can be loaded as data (not code).
 * IDs map to existing OverlayPreferences skin keys so overlays and legacy UI stay in sync.
 */
data class KellieSkin(
    val id: String,
    val displayName: String,
    val colors: KellieColors,
    val typography: Typography,
    val panelShape: RoundedCornerShape,
    val cardShape: RoundedCornerShape,
    val focusBorderWidth: Dp,
    val spacing: Dp,
    val animationScale: Float
)

data class KellieColors(
    val screen: Color,
    val panel: Color,
    val card: Color,
    val cardFocused: Color,
    val accent: Color,
    val accentSecondary: Color,
    val text: Color,
    val textMuted: Color,
    val stroke: Color,
    val focus: Color,
    val success: Color,
    val warning: Color
)

object KellieSkins {
    // Maps to OverlayPreferences.SKIN_GLASS (default copper)
    val COPPER = KellieSkin(
        id = OverlayPreferences.SKIN_GLASS,
        displayName = "Copper Glass",
        colors = KellieColors(
            screen = Color(0xFF1B1412),
            panel = Color(0xFF2B1B16),
            card = Color(0xFF493128),
            cardFocused = Color(0xFF62453B),
            accent = Color(0xFFE9B390),
            accentSecondary = Color(0xFFBA785B),
            text = Color(0xFFF7E8DB),
            textMuted = Color(0xFFC59A80),
            stroke = Color(0xFFB98465),
            focus = Color(0xFFE9B390),
            success = Color(0xFF4CAF50),
            warning = Color(0xFFFF9800)
        ),
        typography = Typography(),
        panelShape = RoundedCornerShape(16.dp),
        cardShape = RoundedCornerShape(12.dp),
        focusBorderWidth = 3.dp,
        spacing = 12.dp,
        animationScale = 1.0f
    )

    // Maps to OverlayPreferences.SKIN_CLASSIC
    val MINIMAL = KellieSkin(
        id = OverlayPreferences.SKIN_CLASSIC,
        displayName = "Compact Charcoal",
        colors = KellieColors(
            screen = Color(0xFF181512),
            panel = Color(0xFF261C17),
            card = Color(0xFF49382F),
            cardFocused = Color(0xFF5A4538),
            accent = Color(0xFFE6AB82),
            accentSecondary = Color(0xFFA66A4B),
            text = Color(0xFFF1DDC9),
            textMuted = Color(0xFFC89A7B),
            stroke = Color(0xFFB88664),
            focus = Color(0xFFE6AB82),
            success = Color(0xFF66BB6A),
            warning = Color(0xFFFFB74D)
        ),
        typography = Typography(),
        panelShape = RoundedCornerShape(8.dp),
        cardShape = RoundedCornerShape(6.dp),
        focusBorderWidth = 2.dp,
        spacing = 10.dp,
        animationScale = 0.8f
    )

    // Maps to OverlayPreferences.SKIN_AURORA
    val AURORA = KellieSkin(
        id = OverlayPreferences.SKIN_AURORA,
        displayName = "Aurora Glass",
        colors = KellieColors(
            screen = Color(0xFF0A1225),
            panel = Color(0xFF162947),
            card = Color(0xFF213758),
            cardFocused = Color(0xFF365AA0),
            accent = Color(0xFFBED6FF),
            accentSecondary = Color(0xFF3F6BB6),
            text = Color(0xFFE4ECFF),
            textMuted = Color(0xFF9CB9E9),
            stroke = Color(0xFF709FF8),
            focus = Color(0xFFBED6FF),
            success = Color(0xFF3FB950),
            warning = Color(0xFFD29922)
        ),
        typography = Typography(),
        panelShape = RoundedCornerShape(20.dp),
        cardShape = RoundedCornerShape(14.dp),
        focusBorderWidth = 3.dp,
        spacing = 14.dp,
        animationScale = 1.2f
    )

    // Maps to OverlayPreferences.SKIN_SIGNAL
    val NIGHT = KellieSkin(
        id = OverlayPreferences.SKIN_SIGNAL,
        displayName = "Night Signal",
        colors = KellieColors(
            screen = Color(0xFF171411),
            panel = Color(0xFF26201A),
            card = Color(0xFF39302B),
            cardFocused = Color(0xFF6D523E),
            accent = Color(0xFFE7C39B),
            accentSecondary = Color(0xFF865E41),
            text = Color(0xFFF5E8D5),
            textMuted = Color(0xFFAF9881),
            stroke = Color(0xFFB38A58),
            focus = Color(0xFFE7C39B),
            success = Color(0xFF00E676),
            warning = Color(0xFFFFAB00)
        ),
        typography = Typography(),
        panelShape = RoundedCornerShape(4.dp),
        cardShape = RoundedCornerShape(2.dp),
        focusBorderWidth = 2.dp,
        spacing = 8.dp,
        animationScale = 0.6f
    )

    // Maps to OverlayPreferences.SKIN_OBSIDIAN — Neon Obsidian glass-and-glow
    val OBSIDIAN = KellieSkin(
        id = OverlayPreferences.SKIN_OBSIDIAN,
        displayName = "Neon Obsidian",
        colors = KellieColors(
            screen = Color(0xFF06070C),
            panel = Color(0xFF0E1018),
            card = Color(0xB8101320),
            cardFocused = Color(0xFF1A1D2E),
            accent = Color(0xFF00E5FF),
            accentSecondary = Color(0xFF8B5CFF),
            text = Color(0xFFEAF0FF),
            textMuted = Color(0xFF8A93B2),
            stroke = Color(0xFF292A3C),
            focus = Color(0xFF00E5FF),
            success = Color(0xFFB6FF3B),
            warning = Color(0xFFFF2E88)
        ),
        typography = Typography(),
        panelShape = RoundedCornerShape(18.dp),
        cardShape = RoundedCornerShape(16.dp),
        focusBorderWidth = 3.dp,
        spacing = 14.dp,
        animationScale = 1.1f
    )

    val ALL = listOf(COPPER, MINIMAL, AURORA, NIGHT, OBSIDIAN)

    fun byId(id: String): KellieSkin = ALL.find { it.id == id } ?: COPPER
}

/**
 * Single source of truth: OverlayPreferences.skin().
 * No separate preference file. Existing user choices are preserved.
 */
object SkinStore {
    fun getSelected(context: Context): KellieSkin {
        val prefs = OverlayPreferences(context)
        return KellieSkins.byId(prefs.skin())
    }

    fun setSelected(context: Context, skin: KellieSkin) {
        OverlayPreferences(context).setSkin(skin.id)
    }
}

val LocalKellieSkin = staticCompositionLocalOf { KellieSkins.COPPER }

@Composable
fun KellieTheme(
    skin: KellieSkin = KellieSkins.COPPER,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalKellieSkin provides skin,
        content = content
    )
}
