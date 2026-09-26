package com.won3000.glowplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.won3000.glowplayer.R

/** Colours sampled from the reference design. */
object GlowColors {
    // Dark backdrop behind the card
    val Backdrop = Color(0xFF241B1E)
    val BackdropTop = Color(0xFF2B2124)
    val BackdropBottom = Color(0xFF1D1618)

    // Soft light around the card
    val Halo = Color(0xB3735D60)
    val GlowTight = Color(0xE6FFF1F4)

    // The card itself
    val CardTop = Color(0xFFEAD5DA)
    val CardBottom = Color(0xFFEEE2E5)

    // Text & controls on the card
    val Ink = Color(0xFF120C10)
    val InkSoft = Color(0xFF231B1F)
    val Muted = Color(0xFF8F7F89)
    val Track = Color(0xFFBEABAD)
    val PlayButton = Color(0xFF1C1A1F)
    val OnPlayButton = Color(0xFFF2EDF0)
    val ArtFrame = Color(0xFFF8F4F5)

    // Text on the dark backdrop
    val OnBackdrop = Color(0xFFF1E6E9)
    val OnBackdropMuted = Color(0xFFA8979D)
    val Accent = Color(0xFFF4C6D2)
}

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

@Composable
fun GlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GlowColors.CardBottom,
            onPrimary = GlowColors.Ink,
            background = GlowColors.Backdrop,
            onBackground = GlowColors.OnBackdrop,
            surface = GlowColors.BackdropTop,
            onSurface = GlowColors.OnBackdrop,
            surfaceContainerHigh = GlowColors.BackdropTop,
        ),
        content = content,
    )
}
