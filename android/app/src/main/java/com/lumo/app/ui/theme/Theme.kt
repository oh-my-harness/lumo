package com.lumo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 方案1：柔和极简磨砂风 ──
// 日间：米白底 + 浅青主色 + 浅紫辅助
private val LightColors = lightColorScheme(
    primary = Color(0xFF58B7B1),            // 浅青
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6F0EE),
    onPrimaryContainer = Color(0xFF1A3A37),
    secondary = Color(0xFF9688E8),           // 浅紫
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E2F8),
    onSecondaryContainer = Color(0xFF2A2250),
    tertiary = Color(0xFFF5A623),            // 暖橙点缀
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDEDD0),
    onTertiaryContainer = Color(0xFF3D2A00),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE4E5),
    onErrorContainer = Color(0xFF410005),
    background = Color(0xFFF8F9FA),          // 米白底
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),             // 纯白卡片
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0F1F3),      // 浅灰分区
    onSurfaceVariant = Color(0xFF6B6B70),
    surfaceTint = Color(0xFF58B7B1),
    outline = Color(0xFFE0E0E5),
    outlineVariant = Color(0xFFEDEDF0),
    scrim = Color(0x99000000),
)

// 夜间：深灰底 + 低亮度荧光强调
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3CC),             // 低亮度荧光青
    onPrimary = Color(0xFF00322E),
    primaryContainer = Color(0xFF1A4A45),
    onPrimaryContainer = Color(0xFFB0EBE6),
    secondary = Color(0xFFB5A8F0),           // 低亮度荧光紫
    onSecondary = Color(0xFF251E50),
    secondaryContainer = Color(0xFF352D60),
    onSecondaryContainer = Color(0xFFE0D9F8),
    tertiary = Color(0xFFF5B947),
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFF5A3F00),
    onTertiaryContainer = Color(0xFFFDEDD0),
    error = Color(0xFFFF8A8E),
    onError = Color(0xFF5C0003),
    errorContainer = Color(0xFF8C1A1E),
    onErrorContainer = Color(0xFFFCE4E5),
    background = Color(0xFF1E2129),          // 深灰底（非纯黑）
    onBackground = Color(0xFFE4E4E8),
    surface = Color(0xFF282B33),             // 半透磨砂卡片感
    onSurface = Color(0xFFE4E4E8),
    surfaceVariant = Color(0xFF33363F),
    onSurfaceVariant = Color(0xFFA0A0A8),
    surfaceTint = Color(0xFF6FD3CC),
    outline = Color(0xFF3D404A),
    outlineVariant = Color(0xFF33363F),
    scrim = Color(0xCC000000),
)

// ── Typography ──
// 系统默认 sans-serif，CJK 自动回退 Noto Sans CJK
// 行高 1.5~1.7 增强阅读舒适感，letterSpacing 微调
private val LumoTypography = Typography(
    // 大标题 — 页面标题
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp,
    ),
    // 标题 — 区块标题
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    // 正文
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    // 标签 / 按钮
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun LumoTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LumoTypography,
        content = content
    )
}
