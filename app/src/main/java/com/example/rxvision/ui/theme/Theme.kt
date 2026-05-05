package com.example.rxvision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RxVisionDarkColorScheme = darkColorScheme(
    primary            = IndigoAccent,
    onPrimary          = TextPrimary,
    primaryContainer   = SurfaceGrey,
    onPrimaryContainer = TextPrimary,
    secondary          = PurpleAccent,
    onSecondary        = TextPrimary,
    background         = DarkBackground,
    onBackground       = TextPrimary,
    surface            = SurfaceGrey,
    onSurface          = TextPrimary,
    surfaceVariant     = ElevatedGrey,
    onSurfaceVariant   = TextSecondary,
    outline            = DividerGrey,
    error              = DangerRed,
    onError            = TextPrimary
)

@Composable
fun RxVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RxVisionDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}