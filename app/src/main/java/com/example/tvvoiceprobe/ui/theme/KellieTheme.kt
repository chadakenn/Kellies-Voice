package com.example.tvvoiceprobe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.SharedPreferences

/**
 * Skin definition that can change more than colors.
 * Designed so future downloadable skins can be loaded as data (not code).
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
    val COPPER = KellieSkin(
        id = "copper",
        displayName = "Copper",
        colors = KellieColors(
            screen = Color(0xFF121212),
            panel = Color(0xFF1E1E1E),
            card = Color(0xFF252525),
            cardFocused = Color(0xFF333333),
            accent = Color(0xFFD4A574),
            accentSecondary = Color(0xFFB8865A),
            text = Color(0xFFF5F5F5),
            textMuted = Color(0xFFAAAAAA),
            stroke = Color(0xFF3A3A3A),
            focus = Color(0xFFD4A574),
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

    val MINIMAL = KellieSkin(
        id = "minimal",
        displayName = "Minimal",
        colors = KellieColors(
            screen = Color(0xFF0F0F0F),
            panel = Color(0xFF181818),
            card = Color(0xFF202020),
            cardFocused = Color(0xFF282828),
            accent = Color(0xFF9E9E9E),
            accentSecondary = Color(0xFF757575),
            text = Color(0xFFE0E0E0),
            textMuted = Color(0xFF9E9E9E),
            stroke = Color(0xFF2A2A2A),
            focus = Color(0xFFBDBDBD),
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

    val AURORA = KellieSkin(
        id = "aurora",
        displayName = "Aurora",
        colors = KellieColors(
            screen = Color(0xFF0D1117),
            panel = Color(0xFF161B22),
            card = Color(0xFF21262D),
            cardFocused = Color(0xFF30363D),
            accent = Color(0xFF58A6FF),
            accentSecondary = Color(0xFF79C0FF),
            text = Color(0xFFF0F6FC),
            textMuted = Color(0xFF8B949E),
            stroke = Color(0xFF30363D),
            focus = Color(0xFF58A6FF),
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

    val NIGHT = KellieSkin(
        id = "night",
        displayName = "Night Signal",
        colors = KellieColors(
            screen = Color(0xFF000000),
            panel = Color(0xFF111111),
            card = Color(0xFF1A1A1A),
            cardFocused = Color(0xFF222222),
            accent = Color(0xFF00E5FF),
            accentSecondary = Color(0xFF00B8D4),
            text = Color(0xFFFFFFFF),
            textMuted = Color(0xFF888888),
            stroke = Color(0xFF333333),
            focus = Color(0xFF00E5FF),
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

    val ALL = listOf(COPPER, MINIMAL, AURORA, NIGHT)

    fun byId(id: String): KellieSkin = ALL.find { it.id == id } ?: COPPER
}

object SkinStore {
    private const val PREFS = "kellie_skin_prefs"
    private const val KEY_SKIN = "selected_skin"

    fun getSelected(context: Context): KellieSkin {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_SKIN, KellieSkins.COPPER.id) ?: KellieSkins.COPPER.id
        return KellieSkins.byId(id)
    }

    fun setSelected(context: Context, skin: KellieSkin) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKIN, skin.id)
            .apply()
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
