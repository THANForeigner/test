package com.example.afinal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- LIGHT THEME ---
private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,       // 0xFF2196F3
    onPrimary = White,
    secondary = TealAccent,      // 0xFF00BFA5
    onSecondary = White,
    tertiary = BlueDark,         // 0xFF0069C0

    background = Gray50,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600
)

// --- DARK THEME ---
//private val DarkColorScheme = darkColorScheme(
//    primary = BlueLight,
//    onPrimary = Color(0xFF003258),
//    secondary = TealAccent,
//    onSecondary = Color(0xFF00251A),
//
//    background = Color(0xFF121212),
//    onBackground = Color(0xFFE0E0E0),
//    surface = Color(0xFF1E1E1E),
//    onSurface = Color(0xFFE0E0E0),
//    surfaceVariant = Color(0xFF424242),
//    onSurfaceVariant = Color(0xFFBDBDBD)
//)
private val DarkColorScheme = LightColorScheme // tạm thời buộc dùng theme sáng


@Composable
fun Modifier.appCardStyle(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp
): Modifier {
    val isDark = isSystemInDarkTheme()

    return this
        .shadow(
            elevation = if (isDark) 0.dp else elevation, // Tắt shadow ở Dark Mode cho đỡ nặng
            shape = shape,
            spotColor = Color.Black.copy(alpha = 0.1f) // Shadow nhẹ hơn mặc định
        )
        // Trong Dark Mode: Thêm viền mỏng để tạo hiệu ứng 3D tách biệt
        .then(
            if (isDark) Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            else Modifier
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
}

@Composable
fun FINALTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            window.statusBarColor = Color.Transparent.toArgb()

            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}