package com.alexmodzofc.tool.ui

import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

/**
 * The app's themed replacement for Material3's bare [RadioButton]. Every call site only
 * overrode `selectedColor`, leaving `unselectedColor` (and the disabled variants) on
 * Material3's stock default color scheme, which is why an unselected radio button showed a
 * purple-tinted ring instead of matching the app's theme.
 */
@Composable
fun AlexToolRadioButton(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAlexToolColors.current
    RadioButton(
        selected = selected,
        onClick = null,
        modifier = modifier,
        colors = RadioButtonDefaults.colors(
            selectedColor = colors.primary,
            unselectedColor = colors.secondaryText,
            disabledSelectedColor = colors.primary.copy(alpha = 0.38f),
            disabledUnselectedColor = colors.secondaryText.copy(alpha = 0.38f)
        )
    )
}
