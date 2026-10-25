package com.alexmodzofc.tool.ui

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

@Composable
fun AlexToolCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAlexToolColors.current
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = CheckboxDefaults.colors(
            checkedColor = colors.primary,
            uncheckedColor = colors.secondaryText,
            checkmarkColor = colors.background,
            disabledCheckedColor = colors.primary.copy(alpha = 0.38f),
            disabledUncheckedColor = colors.secondaryText.copy(alpha = 0.38f),
            disabledIndeterminateColor = colors.primary.copy(alpha = 0.38f)
        )
    )
}
