package com.won3000.glowplayer.ui

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.won3000.glowplayer.R
import com.won3000.glowplayer.data.Song
import com.won3000.glowplayer.tile.MusicTileService
import com.won3000.glowplayer.ui.theme.GlowColors
import com.won3000.glowplayer.ui.theme.Montserrat

@Composable
fun GlowPlayerRoute(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val position = viewModel.position.collectAsStateWithLifecycle()

    val permission = remember { audioPermission() }
    var granted by remember { mutableStateOf(context.hasPermission(permission)) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var askedOnLaunch by rememberSaveable { mutableStateOf(false) }
    var showTileHelp by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    // Ask once when the app first opens; afterwards the button in the list asks again.
    LaunchedEffect(Unit) {
        if (!granted && !askedOnLaunch) {
            askedOnLaunch = true
            permissionLauncher.launch(permission)
        }
    }
    // The user may grant the permission from system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasPermission(permission)
    }
    LaunchedEffect(granted) {
        viewModel.onPermissionResult(granted)
    }

    val actions = remember(viewModel) {
        PlayerActions(
            onPlayPause = viewModel::playPause,
            onPrevious = viewModel::skipPrevious,
            onNext = viewModel::skipNext,
            onSeek = viewModel::seekTo,
            onShuffle = viewModel::toggleShuffle,
            onRepeat = viewModel::cycleRepeat,
        )
    }

    GlowPlayerScreen(
        ui = ui,
        position = position,
        songs = songs,
        library = library,
        actions = actions,
        permanentlyDenied = permanentlyDenied,
        onSongClick = viewModel::playSongAt,
        onRequestPermission = {
            if (permanentlyDenied) context.openAppSettings() else permissionLauncher.launch(permission)
        },
        onAddTile = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestAddTile(context, onNotAdded = { showTileHelp = true })
            } else {
                showTileHelp = true
            }
        },
    )

    if (showTileHelp) {
        AlertDialog(
            onDismissRequest = { showTileHelp = false },
            confirmButton = {
                TextButton(onClick = { showTileHelp = false }) { Text(stringResource(R.string.ok)) }
            },
            title = { Text(stringResource(R.string.add_tile_help_title)) },
            text = { Text(stringResource(R.string.add_tile_help_body)) },
        )
    }
}

@Composable
fun GlowPlayerScreen(
    ui: PlayerUiState,
    position: State<Long>,
    songs: List<Song>,
    library: LibraryState,
    actions: PlayerActions,
    permanentlyDenied: Boolean,
    onSongClick: (Int) -> Unit,
    onRequestPermission: () -> Unit,
    onAddTile: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlowColors.BackdropTop, GlowColors.Backdrop, GlowColors.BackdropBottom),
                ),
            ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val wide = maxWidth >= 600.dp
            val contentWidth = if (wide) minOf(maxWidth - 64.dp, 1040.dp) else maxWidth - 32.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "top") {
                    TopBar(
                        onAddTile = onAddTile,
                        modifier = Modifier
                            .zIndex(1f)
                            .width(contentWidth)
                            .statusBarsPadding(),
                    )
                }
                item(key = "player") {
                    GlowPlayerCard(
                        ui = ui,
                        position = position,
                        actions = actions,
                        wide = wide,
                        modifier = Modifier
                            .width(contentWidth)
                            .padding(top = if (wide) 28.dp else 18.dp),
                    )
                }
                item(key = "library") {
                    LibraryHeader(
                        count = songs.size,
                        showCount = library == LibraryState.Ready,
                        modifier = Modifier
                            .width(contentWidth)
                            .padding(top = 16.dp, bottom = 6.dp),
                    )
                }
                when (library) {
                    LibraryState.NeedsPermission -> item(key = "permission") {
                        PermissionNotice(
                            permanentlyDenied = permanentlyDenied,
                            onRequest = onRequestPermission,
                            modifier = Modifier.width(contentWidth),
                        )
                    }
                    LibraryState.Loading -> item(key = "loading") {
                        Notice(stringResource(R.string.library_loading), Modifier.width(contentWidth))
                    }
                    LibraryState.Empty -> item(key = "empty") {
                        Notice(stringResource(R.string.library_empty), Modifier.width(contentWidth))
                    }
                    LibraryState.Ready -> itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id.toString() == ui.mediaId,
                            isPlaying = ui.isPlaying,
                            onClick = { onSongClick(index) },
                            modifier = Modifier.width(contentWidth),
                        )
                    }
                }
                item(key = "bottom") {
                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onAddTile: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GlowColors.OnBackdrop,
            ),
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.09f))
                .clickable(onClick = onAddTile)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = GlowColors.OnBackdrop,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.add_tile),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = GlowColors.OnBackdrop,
                ),
            )
        }
    }
}

@Composable
private fun LibraryHeader(count: Int, showCount: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = stringResource(R.string.library_title),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = GlowColors.OnBackdrop,
            ),
        )
        if (showCount) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.song_count, count),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GlowColors.OnBackdropMuted,
                ),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun PermissionNotice(permanentlyDenied: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = GlowColors.OnBackdrop,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = TextStyle(fontSize = 13.5.sp, color = GlowColors.OnBackdropMuted),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(if (permanentlyDenied) R.string.permission_settings else R.string.permission_button),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = GlowColors.Ink,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GlowColors.CardBottom)
                .clickable(onClick = onRequest)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TextStyle(fontSize = 14.sp, color = GlowColors.OnBackdropMuted),
        modifier = modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongThumb(song, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isCurrent) GlowColors.Accent else GlowColors.OnBackdrop,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(song.artist ?: unknownArtist, song.album).joinToString(" · "),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontSize = 12.5.sp,
                    color = GlowColors.OnBackdropMuted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isCurrent) {
            if (isPlaying) {
                PlayingBars(GlowColors.Accent, Modifier.size(16.dp))
            } else {
                StaticBars(GlowColors.Accent, Modifier.size(16.dp))
            }
        } else {
            Text(
                text = formatTime(song.durationMs),
                style = TextStyle(
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = GlowColors.OnBackdropMuted,
                ),
            )
        }
    }
}

@Composable
private fun SongThumb(song: Song, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val artwork = rememberArtwork(song.id.toString(), song.artworkUri, null, px)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(9.dp)),
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArtPlaceholder()
        }
    }
}

@Composable
private fun PlayingBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val levels = listOf(0, 160, 320).map { offset ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offset),
            ),
            label = "bar",
        )
    }
    Canvas(modifier) {
        levels.forEachIndexed { i, level -> drawBar(i, level.value, color) }
    }
}

@Composable
private fun StaticBars(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        listOf(0.45f, 0.8f, 0.6f).forEachIndexed { i, level -> drawBar(i, level, color) }
    }
}

private fun DrawScope.drawBar(index: Int, level: Float, color: Color) {
    val barWidth = size.width / 5f
    val height = size.height * level
    drawRoundRect(
        color = color,
        topLeft = Offset(barWidth * index * 2, size.height - height),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(barWidth / 2f),
    )
}

// ---- Helpers ----------------------------------------------------------------------------------

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Shows the system "Add tile to quick settings?" dialog (Android 13+). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, onNotAdded: () -> Unit) {
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (manager == null) {
        onNotAdded()
        return
    }
    manager.requestAddTileService(
        ComponentName(context, MusicTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_music_note),
        context.mainExecutor,
    ) { result ->
        when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                Toast.makeText(context, R.string.add_tile_added, Toast.LENGTH_SHORT).show()
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                Toast.makeText(context, R.string.add_tile_already, Toast.LENGTH_SHORT).show()
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit // user said no
            else -> onNotAdded() // not supported here – explain how to add it by hand
        }
    }
}
