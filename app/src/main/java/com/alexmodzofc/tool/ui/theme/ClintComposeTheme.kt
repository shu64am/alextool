package com.alexmodzofc.tool.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors
import com.alexmodzofc.tool.R

/**
 * The small set of design tokens the setup screens actually use, resolved from the
 * XML theme that AlexToolActivity already applied via setTheme()/recreate(). This is a
 * read-only bridge, not a parallel design system: change the XML theme/attrs and
 * these follow automatically.
 */
data class AlexToolColors(
    val background: Color,
    val onSurface: Color,
    val secondaryText: Color,
    val cardBackground: Color,
    val buttonBackground: Color,
    val surfaceVariant: Color,
    val popupBackground: Color,
    val primary: Color,
    val iconTint: Color,
    val divider: Color,
    val popupText: Color,
    val surface: Color,
    val buttonIconTint: Color,
    val popupStroke: Color,
    val popupCheck: Color,
    val onPrimary: Color,
    val colorError: Color,
    val colorErrorContainer: Color,
    val colorOnErrorContainer: Color,
    val buttonTextColor: Color,
    val addressBarColor: Color,
    val isLight: Boolean
)

val LocalAlexToolColors = compositionLocalOf<AlexToolColors> {
    error("AlexToolComposeTheme not applied")
}

private fun resolveAlexToolColors(context: android.content.Context, isLight: Boolean): AlexToolColors {
    fun attr(attrId: Int, fallback: Int): Color {
        val resolved = MaterialColors.getColor(context, attrId, fallback)
        val c = Color(resolved)
        // Android 15+ dynamic Material You can resolve some on-color attrs as fully transparent;
        // a transparent on-color makes text invisible (e.g. the address bar field). Clamp to opaque.
        return if (c.alpha >= 255) c else Color(resolved or 0xFF000000.toInt())
    }
    return AlexToolColors(
        background = attr(android.R.attr.colorBackground, 0xFF121212.toInt()),
        onSurface = attr(com.google.android.material.R.attr.colorOnSurface, if (isLight) 0xFF000000.toInt() else 0xFFE6E6E6.toInt()),
        secondaryText = attr(R.attr.alextoolSecondaryTextColor, 0xFFAAAAAA.toInt()),
        cardBackground = attr(R.attr.alextoolCardBackground, 0xFF1E1E1E.toInt()),
        buttonBackground = attr(R.attr.alextoolButtonBackground, 0xFFBB86FC.toInt()),
        surfaceVariant = attr(R.attr.alextoolSurfaceVariant, 0xFF2A2A2A.toInt()),
        popupBackground = attr(R.attr.alextoolPopupBackground, 0xFF1E1E1E.toInt()),
        primary = attr(androidx.appcompat.R.attr.colorPrimary, 0xFFBB86FC.toInt()),
        iconTint = attr(R.attr.alextoolIconTint, 0xFFAAAAAA.toInt()),
        divider = attr(R.attr.alextoolDividerColor, 0x1FFFFFFF),
        popupText = attr(R.attr.alextoolPopupTextColor, 0xFFFFFFFF.toInt()),
        surface = attr(com.google.android.material.R.attr.colorSurface, 0xFF1E1E1E.toInt()),
        buttonIconTint = attr(R.attr.alextoolButtonIconTint, 0xFF000000.toInt()),
        popupStroke = attr(R.attr.alextoolPopupStrokeColor, 0x33FFFFFF),
        popupCheck = attr(R.attr.alextoolPopupCheckColor, 0xFFBB86FC.toInt()),
        onPrimary = attr(com.google.android.material.R.attr.colorOnPrimary, 0xFF000000.toInt()),
        colorError = attr(android.R.attr.colorError, 0xFFCF6679.toInt()),
        colorErrorContainer = attr(com.google.android.material.R.attr.colorErrorContainer, 0xFF4E0002.toInt()),
        colorOnErrorContainer = attr(com.google.android.material.R.attr.colorOnErrorContainer, 0xFFFFDAD6.toInt()),
        buttonTextColor = attr(R.attr.alextoolButtonTextColor, 0xFF000000.toInt()),
        addressBarColor = attr(R.attr.alextoolAddressBarColor, 0xFF2A2A2A.toInt()),
        isLight = isLight
    )
}

@Composable
fun AlexToolComposeTheme(theme: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isLight = when (theme) {
        "light" -> true
        "dark" -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
    }
    // Re-resolve whenever the underlying (XML) theme instance changes.
    val alextoolColors = remember(context.theme, theme) { resolveAlexToolColors(context, isLight) }

    val base = if (isLight) lightColorScheme() else darkColorScheme()
    val colorScheme = base.copy(
        primary = alextoolColors.primary,
        onPrimary = if (isLight) Color.White else Color.Black,
        background = alextoolColors.background,
        onBackground = alextoolColors.onSurface,
        surface = alextoolColors.cardBackground,
        onSurface = alextoolColors.onSurface,
        surfaceVariant = alextoolColors.surfaceVariant,
        onSurfaceVariant = alextoolColors.secondaryText
    )

    CompositionLocalProvider(LocalAlexToolColors provides alextoolColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
