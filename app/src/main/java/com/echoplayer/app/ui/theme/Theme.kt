package com.echoplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

object EchoColors {
    val Green = Color(0xFF1F6F5B)
    val GreenSoft = Color(0xFFDDEFE7)
    val Amber = Color(0xFFB7791F)
    val AmberSoft = Color(0xFFFBEFD8)
    val Red = Color(0xFFC0392B)
    val RedSoft = Color(0xFFF9DEDB)
    val Ink = Color(0xFF1B2A24)
    val Muted = Color(0xFF6B7A74)

    // 五层各一个颜色，贯穿定位按钮、记录页统计与句子角标
    val LayerPhonetic = Color(0xFF2E7D9A)
    val LayerLexicalForm = Color(0xFF6C5CE7)
    val LayerLexicalSemantics = Color(0xFFD35400)
    val LayerSyntax = Color(0xFF16A085)
    val LayerCompositional = Color(0xFFAD1457)

    fun layer(id: Int): Color = when (id) {
        1 -> LayerPhonetic
        2 -> LayerLexicalForm
        3 -> LayerLexicalSemantics
        4 -> LayerSyntax
        else -> LayerCompositional
    }

    fun verdict(v: String): Color = when (v) {
        "good" -> Green
        "warn" -> Amber
        else -> Red
    }

    fun score(score: Int): Color = when {
        score >= 80 -> Green
        score >= 60 -> Amber
        else -> Red
    }

    fun scoreSoft(score: Int): Color = when {
        score >= 80 -> GreenSoft
        score >= 60 -> AmberSoft
        else -> RedSoft
    }
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = EchoColors.Green,
    onPrimary = Color.White,
    primaryContainer = EchoColors.GreenSoft,
    onPrimaryContainer = Color(0xFF0B3A2E),
    secondary = Color(0xFF4B6B62),
    secondaryContainer = Color(0xFFE2ECE7),
    onSecondaryContainer = Color(0xFF14332A),
    tertiary = EchoColors.Amber,
    tertiaryContainer = EchoColors.AmberSoft,
    background = Color(0xFFF6F9F7),
    onBackground = EchoColors.Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = EchoColors.Ink,
    surfaceVariant = Color(0xFFEDF3F0),
    onSurfaceVariant = Color(0xFF4A5A54),
    outline = Color(0xFFC3D0CA),
    error = EchoColors.Red,
    errorContainer = EchoColors.RedSoft,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD1B9),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF1F5145),
    onPrimaryContainer = Color(0xFFCBEDE0),
    secondary = Color(0xFFB2CCC3),
    secondaryContainer = Color(0xFF344D45),
    onSecondaryContainer = Color(0xFFD5EAE2),
    tertiary = Color(0xFFF0C27B),
    tertiaryContainer = Color(0xFF5A4213),
    background = Color(0xFF0F1614),
    onBackground = Color(0xFFE2ECE7),
    surface = Color(0xFF161F1C),
    onSurface = Color(0xFFE2ECE7),
    surfaceVariant = Color(0xFF243230),
    onSurfaceVariant = Color(0xFFB7C7C0),
    outline = Color(0xFF5A6C66),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1F1A),
)

val EchoTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun EchoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = EchoTypography,
        content = content,
    )
}
