package app.cozytv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CozyColors {
    val Ink = Color(0xFF0D1125)
    val Paper = Color(0xFFF8F9FF)
    val Muted = Color(0xFFB5BBD2)
    val Panel = Color(0xAA11162C)
    val PanelStrong = Color(0xEE11162C)
    val Line = Color(0x32FFFFFF)
    val Violet = Color(0xFF8497FF)
    val Aqua = Color(0xFF82E8D2)
    val Peach = Color(0xFFFFB18E)
}

object CozyType {
    private val family = FontFamily.SansSerif

    val Clock = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Light,
        fontSize = 66.sp,
        letterSpacing = (-2).sp,
        color = CozyColors.Paper,
    )
    val Hero = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        letterSpacing = (-0.4).sp,
        color = CozyColors.Paper,
    )
    val Title = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = CozyColors.Paper,
    )
    val Body = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = CozyColors.Paper,
    )
    val Label = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = CozyColors.Paper,
    )
    val Caption = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = CozyColors.Muted,
    )
}

