package com.alexmodzofc.tool.browser.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.ui.AlexToolDialog
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

private const val TELEGRAM_CHANNEL_URL = "https://t.me/alexmodzofc"

@Composable
internal fun TelegramJoinDialog(hideStatusBar: Boolean, onJoined: () -> Unit) {
    val colors = LocalAlexToolColors.current
    val context = LocalContext.current

    fun openChannel() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHANNEL_URL))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // A browser is normally available even when the Telegram app is not installed.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHANNEL_URL)))
        }
    }

    AlexToolDialog(
        title = stringResource(R.string.telegram_join_title),
        hideStatusBar = hideStatusBar,
        onDismiss = {},
        cancelable = false,
        footer = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onJoined,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.telegram_join_done), maxLines = 1)
                }
                Button(
                    onClick = ::openChannel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text(
                        stringResource(R.string.telegram_join_action),
                        color = colors.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_alextool_logo_circle),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(CircleShape)
            )
            Text(
                stringResource(R.string.telegram_join_subtitle),
                color = colors.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            Surface(
                color = colors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.telegram_join_channel_label),
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "@alexmodzofc",
                        color = colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.telegram_join_message),
                color = colors.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }
    }
}
