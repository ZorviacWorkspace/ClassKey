package com.classkey.modernattendance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.classkey.modernattendance.data.AttStatus

/** ClassKey design tokens — matches the Figma reference (light-first). */
object CK {
    val Bg = Color(0xFFF0F4F8)
    val Card = Color.White
    val Text = Color(0xFF0D1B3E)
    val Muted = Color(0xFF6B7A99)
    val Primary = Color(0xFF1B6FE4)
    val PrimaryDark = Color(0xFF1558B8)
    val Teal = Color(0xFF0DADA5)
    val Success = Color(0xFF18A957)
    val Warning = Color(0xFFF5A524)
    val Error = Color(0xFFE5484D)
    val Purple = Color(0xFF7C5CE0)
    val Border = Color(0x1A0D1B3E)
    val SoftBlue = Color(0xFFEAF1FF)
    val SoftGreen = Color(0xFFE7F7EE)
    val SoftAmber = Color(0xFFFDF3E0)
    val SoftRed = Color(0xFFFDEBEC)
    val SoftTeal = Color(0xFFE3F6F5)
    val SoftPurple = Color(0xFFF0EBFC)
}

fun statusColor(status: AttStatus): Color = when (status) {
    AttStatus.PRESENT -> CK.Success
    AttStatus.LATE -> CK.Warning
    AttStatus.ABSENT -> CK.Error
    AttStatus.OD -> CK.Primary
    AttStatus.HALF_DAY -> CK.Purple
    AttStatus.LEAVE -> CK.Purple
    AttStatus.PERMISSION -> CK.Teal
    AttStatus.NOT_MARKED -> CK.Muted
}

fun statusSoft(status: AttStatus): Color = when (status) {
    AttStatus.PRESENT -> CK.SoftGreen
    AttStatus.LATE -> CK.SoftAmber
    AttStatus.ABSENT -> CK.SoftRed
    AttStatus.OD -> CK.SoftBlue
    AttStatus.HALF_DAY -> CK.SoftPurple
    AttStatus.LEAVE -> CK.SoftPurple
    AttStatus.PERMISSION -> CK.SoftTeal
    AttStatus.NOT_MARKED -> Color(0xFFEDF1F7)
}

@Composable
fun ClassKeyTheme(content: @Composable () -> Unit) {
    // Light-first identity per the design reference.
    val scheme = lightColorScheme(
        primary = CK.Primary,
        onPrimary = Color.White,
        secondary = CK.Teal,
        onSecondary = Color.White,
        tertiary = CK.Warning,
        background = CK.Bg,
        onBackground = CK.Text,
        surface = CK.Card,
        onSurface = CK.Text,
        surfaceVariant = CK.SoftBlue,
        onSurfaceVariant = CK.Muted,
        error = CK.Error,
        onError = Color.White,
        outline = CK.Border
    )
    // Keep light scheme even in system dark mode: dark variant is a future pass.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
