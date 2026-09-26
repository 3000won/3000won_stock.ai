package com.won3000.glowplayer.ui

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.won3000.glowplayer.R
import com.won3000.glowplayer.ui.theme.GlowColors
import com.won3000.glowplayer.ui.theme.GlowTheme
import com.won3000.glowplayer.ui.theme.Montserrat
import java.util.Locale

@Immutable
data class PlayerActions(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
)

/**
 * The glowing player card from the reference design.
 *
 * [wide] = the original landscape composition (album art left, everything else on the right).
 * On a portrait phone the same pieces are stacked: art + titles on top, seek bar and controls
 * underneath, and the listener silhouette peeking over the bottom edge.
 */
@Composable
fun GlowPlayerCard(
    ui: PlayerUiState,
    position: State<Long>,
    actions: PlayerActions,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val m = remember(maxWidth, wide) { if (wide) wideMetrics(maxWidth) else compactMetrics(maxWidth) }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = m.silhouetteOverflow),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .cardGlow(m.corner),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(m.corner))
                    .background(Brush.linearGradient(listOf(GlowColors.CardTop, GlowColors.CardBottom))),
            ) {
                if (m.wide) {
                    WideCardContent(ui, position, actions, m)
                } else {
                    CompactCardContent(ui, position, actions, m)
                }
            }
        }
        ListenerSilhouette(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = m.silhouetteX)
                .width(m.silhouetteWidth),
        )
    }
}

// ---- Layout metrics ---------------------------------------------------------------------------

@Immutable
private data class CardMetrics(
    val wide: Boolean,
    val cardHeight: Dp,
    val corner: Dp,
    val padStart: Dp,
    val padTop: Dp,
    val padEnd: Dp,
    val padBottom: Dp,
    val art: Dp,
    val gap: Dp,
    val title: TextUnit,
    val artist: TextUnit,
    val album: TextUnit,
    val time: TextUnit,
    val textGap: Dp,
    val icon: Dp,
    val playButton: Dp,
    val playIcon: Dp,
    val controlSpacing: Dp,
    val silhouetteWidth: Dp,
    val silhouetteX: Dp,
    val silhouetteOverflow: Dp,
)

private const val SILHOUETTE_ASPECT = 230f / 196f

/** Proportions measured from the reference image (card ≈ 3.1 : 1). */
private fun wideMetrics(width: Dp): CardMetrics {
    val h = (width / 3.1f).coerceIn(250.dp, 340.dp)
    val s = h / 300.dp
    val art = h * 0.63f
    val padStart = h * 0.14f
    val silhouetteWidth = h * 0.42f
    return CardMetrics(
        wide = true,
        cardHeight = h,
        corner = 30.dp * s,
        padStart = padStart,
        padTop = h * 0.12f,
        padEnd = h * 0.16f,
        padBottom = 0.dp,
        art = art,
        gap = h * 0.19f,
        title = (30 * s).sp,
        artist = (18 * s).sp,
        album = (15.5f * s).sp,
        time = (13.5f * s).sp,
        textGap = 7.dp * s,
        icon = 26.dp * s,
        playButton = 62.dp * s,
        playIcon = 26.dp * s,
        controlSpacing = 34.dp * s,
        silhouetteWidth = silhouetteWidth,
        silhouetteX = padStart + art * 0.5f - silhouetteWidth * 0.36f,
        silhouetteOverflow = h * 0.10f,
    )
}

private fun compactMetrics(width: Dp): CardMetrics {
    val pad = 22.dp
    val art = (width * 0.34f).coerceIn(100.dp, 150.dp)
    val silhouetteWidth = 96.dp
    val silhouetteHeight = silhouetteWidth / SILHOUETTE_ASPECT
    val overflow = 32.dp
    return CardMetrics(
        wide = false,
        cardHeight = Dp.Unspecified,
        corner = 26.dp,
        padStart = pad,
        padTop = pad,
        padEnd = pad,
        padBottom = silhouetteHeight - overflow + 8.dp,
        art = art,
        gap = 18.dp,
        title = 23.sp,
        artist = 16.sp,
        album = 13.5.sp,
        time = 12.sp,
        textGap = 5.dp,
        icon = 24.dp,
        playButton = 60.dp,
        playIcon = 26.dp,
        controlSpacing = 0.dp,
        silhouetteWidth = silhouetteWidth,
        silhouetteX = pad + art * 0.5f - silhouetteWidth * 0.36f,
        silhouetteOverflow = overflow,
    )
}

// ---- Layouts ----------------------------------------------------------------------------------

@Composable
private fun WideCardContent(ui: PlayerUiState, position: State<Long>, actions: PlayerActions, m: CardMetrics) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(m.cardHeight)
            .padding(start = m.padStart, top = m.padTop, end = m.padEnd),
    ) {
        AlbumArt(ui, m.art)
        Spacer(Modifier.width(m.gap))
        Column(
            Modifier
                .weight(1f)
                .height(m.cardHeight * 0.74f),
        ) {
            TrackText(ui, m)
            Spacer(Modifier.weight(1f))
            SeekSection(ui, position, actions.onSeek, m)
            Spacer(Modifier.height(m.textGap))
            ControlsRow(
                ui = ui,
                actions = actions,
                m = m,
                arrangement = Arrangement.spacedBy(m.controlSpacing, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactCardContent(ui: PlayerUiState, position: State<Long>, actions: PlayerActions, m: CardMetrics) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = m.padStart, top = m.padTop, end = m.padEnd, bottom = m.padBottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(ui, m.art)
                Spacer(Modifier.width(m.gap))
                TrackText(ui, m, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            SeekSection(ui, position, actions.onSeek, m)
            Spacer(Modifier.height(6.dp))
            ControlsRow(
                ui = ui,
                actions = actions,
                m = m,
                arrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.upNext?.let { next ->
            Text(
                text = stringResource(R.string.up_next, next),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = GlowColors.Muted,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.55f)
                    .padding(end = m.padEnd, bottom = 20.dp),
            )
        }
    }
}

// ---- Pieces -----------------------------------------------------------------------------------

@Composable
private fun AlbumArt(ui: PlayerUiState, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(96, 1024)
    val artwork = rememberArtwork(ui.mediaId, ui.artworkUri, ui.artworkData, px)
    val frame = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(size)
            .shadow(elevation = 18.dp, shape = frame, ambientColor = GlowColors.Ink, spotColor = GlowColors.Ink)
            .background(GlowColors.ArtFrame, frame)
            .border(0.8.dp, GlowColors.Ink.copy(alpha = 0.35f), frame)
            .padding(3.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        Crossfade(targetState = artwork, label = "artwork") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ArtPlaceholder()
            }
        }
    }
}

@Composable
internal fun ArtPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFF6DDE4), Color(0xFFD9B3C0)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.38f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackText(ui: PlayerUiState, m: CardMetrics, modifier: Modifier = Modifier) {
    val title = ui.title.ifBlank {
        stringResource(if (ui.hasMedia) R.string.app_name else R.string.nothing_playing)
    }
    val artist = when {
        !ui.hasMedia -> stringResource(R.string.pick_a_song)
        ui.artist.isBlank() -> stringResource(R.string.unknown_artist)
        else -> ui.artist
    }
    val unknownAlbum = stringResource(R.string.unknown_album)
    val album = if (!ui.hasMedia) {
        ""
    } else {
        buildString {
            append(ui.album.ifBlank { unknownAlbum })
            ui.year?.let { append(" (").append(it).append(')') }
        }
    }

    Column(modifier) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = m.title,
                color = GlowColors.Ink,
                letterSpacing = (-0.3).sp,
            ),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        Spacer(Modifier.height(m.textGap))
        Text(
            text = artist,
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = m.artist,
                color = GlowColors.Ink,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.isNotEmpty()) {
            Spacer(Modifier.height(m.textGap))
            Text(
                text = album,
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = m.album,
                    color = GlowColors.InkSoft,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeekSection(ui: PlayerUiState, position: State<Long>, onSeek: (Long) -> Unit, m: CardMetrics) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs
    val shownPosition = dragFraction?.let { (it * duration).toLong() }
        ?: position.value.coerceIn(0L, duration.coerceAtLeast(0L))
    val fraction = if (duration > 0) shownPosition.toFloat() / duration else 0f

    Column(Modifier.fillMaxWidth()) {
        GlowSeekBar(
            fraction = fraction,
            dragging = dragFraction != null,
            enabled = ui.hasMedia && duration > 0,
            onDrag = { dragFraction = it },
            onRelease = { released ->
                dragFraction = null
                onSeek((released * duration).toLong())
            },
        )
        val timeStyle = TextStyle(
            fontFamily = Montserrat,
            fontWeight = FontWeight.Medium,
            fontSize = m.time,
            color = GlowColors.Muted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
            Text(formatTime(shownPosition), style = timeStyle)
            Spacer(Modifier.weight(1f))
            Text(formatTime(duration), style = timeStyle)
        }
    }
}

/** Thin line, thicker played part and a small dot – like the reference. */
@Composable
private fun GlowSeekBar(
    fraction: Float,
    dragging: Boolean,
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val thumbRadius by animateDpAsState(if (dragging) 8.dp else 5.5.dp, label = "thumb")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    currentOnRelease((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnDrag(last)
                    },
                    onDragEnd = { currentOnRelease(last) },
                    onDragCancel = { currentOnRelease(last) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        currentOnDrag(last)
                    },
                )
            },
    ) {
        val y = size.height / 2f
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawLine(
            color = GlowColors.Track,
            start = Offset(x, y),
            end = Offset(size.width, y),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (x > 0f) {
            drawLine(
                color = GlowColors.Ink,
                start = Offset(0f, y),
                end = Offset(x, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = GlowColors.Ink, radius = thumbRadius.toPx(), center = Offset(x, y))
    }
}

@Composable
private fun ControlsRow(
    ui: PlayerUiState,
    actions: PlayerActions,
    m: CardMetrics,
    arrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        ModeButton(
            icon = R.drawable.ic_shuffle,
            description = stringResource(R.string.action_shuffle),
            active = ui.shuffle,
            onClick = actions.onShuffle,
            size = m.icon,
        )
        ControlButton(R.drawable.ic_skip_previous, stringResource(R.string.action_previous), actions.onPrevious, m.icon)
        PlayPauseButton(ui.showPause, actions.onPlayPause, m.playButton, m.playIcon)
        ControlButton(R.drawable.ic_skip_next, stringResource(R.string.action_next), actions.onNext, m.icon)
        ModeButton(
            icon = if (ui.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            description = stringResource(R.string.action_repeat),
            active = ui.repeatMode != Player.REPEAT_MODE_OFF,
            onClick = actions.onRepeat,
            size = m.icon,
        )
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, size: Dp) {
    Box(
        Modifier
            .size(size * 1.9f)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = GlowColors.Ink,
            modifier = Modifier.size(size),
        )
    }
}

/** Shuffle / repeat: full ink with a dot when on, faded when off. */
@Composable
private fun ModeButton(@DrawableRes icon: Int, description: String, active: Boolean, onClick: () -> Unit, size: Dp) {
    val alpha by animateFloatAsState(if (active) 1f else 0.42f, label = "mode")
    Box(
        Modifier
            .size(size * 1.9f)
            .clip(CircleShape)
            .toggleable(value = active, role = Role.Switch, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = GlowColors.Ink.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.14f)
                    .size(4.dp)
                    .background(GlowColors.Ink, CircleShape),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(showPause: Boolean, onClick: () -> Unit, size: Dp, iconSize: Dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val description = stringResource(if (showPause) R.string.action_pause else R.string.action_play)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = GlowColors.Ink, spotColor = GlowColors.Ink)
            .background(GlowColors.PlayButton, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = showPause, label = "playPause") { pause ->
            Icon(
                painter = painterResource(if (pause) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = description,
                tint = GlowColors.OnPlayButton,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** The person with headphones looking up at the album – fades out below the card. */
@Composable
private fun ListenerSilhouette(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.listener_silhouette),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .aspectRatio(SILHOUETTE_ASPECT)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(0.62f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/** Wide warm halo + bright rim around the card. */
private fun Modifier.cardGlow(corner: Dp): Modifier = drawBehind {
    val radius = corner.toPx()

    val spread = 72.dp.toPx()
    val rx = size.width / 2f + spread
    val ry = size.height / 2f + spread
    val inner = (size.width / 2f) / rx
    val halo = Brush.radialGradient(
        0f to GlowColors.Halo,
        inner * 0.9f to GlowColors.Halo,
        1f to Color.Transparent,
        center = center,
        radius = rx,
    )
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(brush = halo, radius = rx, center = center)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = GlowColors.CardBottom.toArgb()
                setShadowLayer(22.dp.toPx(), 0f, 0f, GlowColors.GlowTight.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
        }
    } else {
        // Blurred shadow layers need API 28 with hardware rendering; fake it with soft rings.
        for (step in 1..5) {
            val grow = step * 4.dp.toPx()
            drawRoundRect(
                color = GlowColors.GlowTight.copy(alpha = 0.09f),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(radius + grow),
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

// ---- Previews ---------------------------------------------------------------------------------

private val previewState = PlayerUiState(
    mediaId = "preview",
    title = "Creep",
    artist = "Radiohead",
    album = "Pablo Honey",
    year = 1992,
    showPause = true,
    isPlaying = true,
    durationMs = 240_000L,
    upNext = "Nice Dream",
)

private val previewActions = PlayerActions({}, {}, {}, {}, {}, {})

@Preview(name = "Wide (reference layout)", widthDp = 1000, heightDp = 420, backgroundColor = 0xFF241B1E, showBackground = true)
@Composable
private fun WideCardPreview() {
    GlowTheme {
        Box(Modifier.padding(40.dp)) {
            GlowPlayerCard(previewState, remember { mutableLongStateOf(68_000L) }, previewActions, wide = true)
        }
    }
}

@Preview(name = "Phone", widthDp = 400, heightDp = 480, backgroundColor = 0xFF241B1E, showBackground = true)
@Composable
private fun CompactCardPreview() {
    GlowTheme {
        Box(Modifier.padding(16.dp)) {
            GlowPlayerCard(previewState, remember { mutableLongStateOf(68_000L) }, previewActions, wide = false)
        }
    }
}
