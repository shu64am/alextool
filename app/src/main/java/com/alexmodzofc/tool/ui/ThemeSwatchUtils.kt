package com.alexmodzofc.tool.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.alexmodzofc.tool.R

internal object ThemeSwatchUtils {

    data class SwatchColors(val bg: Int, val surface: Int, val accent: Int)

    fun resolveDefaultSwatchColors(context: Context, theme: String): SwatchColors = when (theme) {
        "light" -> SwatchColors(
            ContextCompat.getColor(context, R.color.light_theme_background),
            ContextCompat.getColor(context, R.color.light_theme_surface),
            ContextCompat.getColor(context, R.color.light_theme_accent)
        )
        "dark" -> SwatchColors(
            ContextCompat.getColor(context, R.color.dark_theme_background),
            ContextCompat.getColor(context, R.color.dark_theme_surface),
            ContextCompat.getColor(context, R.color.dark_theme_accent)
        )
        else -> SwatchColors(
            ContextCompat.getColor(context, R.color.surface_dark),
            ContextCompat.getColor(context, R.color.toolbar_color),
            ContextCompat.getColor(context, R.color.purple_200)
        )
    }

    fun resolveMaterialYouSwatchColors(context: Context, theme: String): SwatchColors {
        val fallbackBg = when (theme) {
            "light" -> 0xFFFAFAFA.toInt()
            "dark" -> 0xFF121212.toInt()
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        }
        val fallbackSurface = when (theme) {
            "light" -> 0xFFFFFFFF.toInt()
            "dark" -> 0xFF1E1E1E.toInt()
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        }
        if (DynamicColors.isDynamicColorAvailable()) {
            val dynCtx = DynamicColors.wrapContextIfAvailable(context)
            return SwatchColors(
                MaterialColors.getColor(dynCtx, android.R.attr.colorBackground, fallbackBg),
                MaterialColors.getColor(dynCtx, com.google.android.material.R.attr.colorSurfaceContainerHigh, fallbackSurface),
                MaterialColors.getColor(dynCtx, androidx.appcompat.R.attr.colorPrimary, 0xFFBB86FC.toInt())
            )
        }
        return SwatchColors(fallbackBg, fallbackSurface, 0xFFBB86FC.toInt())
    }

    fun resolvePurpleSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.purple_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.purple_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.purple_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.purple_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.purple_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.purple_accent_primary)
        }
    )

    fun resolveBlueSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.blue_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.blue_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.blue_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.blue_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.blue_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.blue_accent_primary)
        }
    )

    fun resolveYellowSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.yellow_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.yellow_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.yellow_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.yellow_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.yellow_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.yellow_accent_primary)
        }
    )

    fun resolveRedSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.red_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.red_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.red_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.red_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.red_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.red_accent_primary)
        }
    )

    fun resolveGreenSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.green_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.green_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.green_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.green_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.green_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.green_accent_primary)
        }
    )

    fun resolveOrangeSwatchColors(context: Context, theme: String): SwatchColors = SwatchColors(
        bg = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.orange_accent_light_bg)
            "dark" -> ContextCompat.getColor(context, R.color.orange_accent_dark_bg)
            else -> ContextCompat.getColor(context, R.color.surface_dark)
        },
        surface = when (theme) {
            "light" -> ContextCompat.getColor(context, R.color.orange_accent_light_surface)
            "dark" -> ContextCompat.getColor(context, R.color.orange_accent_dark_surface)
            else -> ContextCompat.getColor(context, R.color.toolbar_color)
        },
        accent = if (theme == "light") {
            ContextCompat.getColor(context, R.color.orange_accent_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.orange_accent_primary)
        }
    )

    fun resolveSoftTintSwatchBgSurface(context: Context, theme: String, accent: String): Pair<Int, Int> {
        val isLight = theme == "light"
        return when {
            accent == "purple" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.purple_accent_light_bg_soft else R.color.purple_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.purple_accent_light_surface_soft else R.color.purple_accent_dark_surface_soft)
            )
            accent == "blue" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.blue_accent_light_bg_soft else R.color.blue_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.blue_accent_light_surface_soft else R.color.blue_accent_dark_surface_soft)
            )
            accent == "yellow" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.yellow_accent_light_bg_soft else R.color.yellow_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.yellow_accent_light_surface_soft else R.color.yellow_accent_dark_surface_soft)
            )
            accent == "red" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.red_accent_light_bg_soft else R.color.red_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.red_accent_light_surface_soft else R.color.red_accent_dark_surface_soft)
            )
            accent == "green" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.green_accent_light_bg_soft else R.color.green_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.green_accent_light_surface_soft else R.color.green_accent_dark_surface_soft)
            )
            accent == "orange" -> Pair(
                ContextCompat.getColor(context, if (isLight) R.color.orange_accent_light_bg_soft else R.color.orange_accent_dark_bg_soft),
                ContextCompat.getColor(context, if (isLight) R.color.orange_accent_light_surface_soft else R.color.orange_accent_dark_surface_soft)
            )
            accent == "material_you" && DynamicColors.isDynamicColorAvailable() -> {
                val dynCtx = DynamicColors.wrapContextIfAvailable(context)
                Pair(
                    MaterialColors.getColor(dynCtx, android.R.attr.colorBackground, if (isLight) 0xFFFAFAFA.toInt() else 0xFF121212.toInt()),
                    MaterialColors.getColor(dynCtx, com.google.android.material.R.attr.colorSurfaceContainer, if (isLight) 0xFFFFFFFF.toInt() else 0xFF1E1E1E.toInt())
                )
            }
            isLight -> Pair(
                ContextCompat.getColor(context, R.color.light_theme_background),
                ContextCompat.getColor(context, R.color.light_theme_surface)
            )
            else -> Pair(
                ContextCompat.getColor(context, R.color.dark_theme_background),
                ContextCompat.getColor(context, R.color.dark_theme_surface)
            )
        }
    }

    fun isSurfaceIntensityEnabled(theme: String, accent: String): Boolean =
        theme != "default" && (accent == "material_you" || accent == "purple" || accent == "blue" || accent == "yellow" || accent == "red" || accent == "green" || accent == "orange" || accent == "default")
}
