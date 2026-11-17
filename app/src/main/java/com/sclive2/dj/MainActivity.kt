package com.sclive2.dj

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Back = Color(0xFF080B10)
private val Panel = Color(0xFF151C26)
private val Cyan = Color(0xFF38BDF8)
private val Green = Color(0xFF22C55E)
private val Amber = Color(0xFFEAB308)
private val Red = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { SCLiveConsole() } }
    }
}

@Composable
private fun SCLiveConsole() {
    val context = LocalContext.current
    val player1 = remember { ExoPlayer.Builder(context).build() }
    val player2 = remember { ExoPlayer.Builder(context).build() }
    var playing1 by remember { mutableStateOf(false) }
    var playing2 by remember { mutableStateOf(false) }
    var progress1 by remember { mutableFloatStateOf(0f) }
    var progress2 by remember { mutableFloatStateOf(0f) }
    var track1 by remember { mutableStateOf("") }
    var track2 by remember { mutableStateOf("") }
    var uri1 by remember { mutableStateOf<Uri?>(null) }
    var uri2 by remember { mutableStateOf<Uri?>(null) }
    var pendingTarget by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(true) }
    var crossfader by remember { mutableFloatStateOf(0.5f) }
    var gain1 by remember { mutableFloatStateOf(1f) }
    var gain2 by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var use24h by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("Selecciona CARGAR para abrir música del teléfono.") }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2300)
        showSplash = false
    }

    DisposableEffect(player1, player2) {
        val listener1 = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing1 = isPlaying }
        }
        val listener2 = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing2 = isPlaying }
        }
        player1.addListener(listener1)
        player2.addListener(listener2)
        onDispose {
            player1.removeListener(listener1)
            player2.removeListener(listener2)
            player1.release()
            player2.release()
        }
    }

    LaunchedEffect(player1, player2) {
        while (true) {
            progress1 = if (player1.duration > 0) (player1.currentPosition.toFloat() / player1.duration).coerceIn(0f, 1f) else 0f
            progress2 = if (player2.duration > 0) (player2.currentPosition.toFloat() / player2.duration).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    LaunchedEffect(gain1, gain2, crossfader) {
        player1.volume = gain1 * sqrt((1f - crossfader).coerceIn(0f, 1f))
        player2.volume = gain2 * sqrt(crossfader.coerceIn(0f, 1f))
    }

    LaunchedEffect(use24h) {
        while (true) {
            val now = Date()
            val locale = Locale("es", "CO")
            clock = SimpleDateFormat(if (use24h) "HH:mm:ss" else "hh:mm:ss a", locale).format(now)
            date = SimpleDateFormat("EEEE, d 'de' MMMM yyyy", locale).format(now)
            delay(1000)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = pendingTarget
            val isTargetPlaying = if (target == 1) player1.isPlaying else player2.isPlaying
            if (target == 0 || isTargetPlaying) {
                notice = "No se cargó: el deck seleccionado está reproduciendo."
            } else {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                val title = displayName(context, uri)
                val player = if (target == 1) player1 else player2
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                if (target == 1) { uri1 = uri; track1 = title }
                else { uri2 = uri; track2 = title }
                notice = "Cargada en Deck $target: $title"
            }
        }
    }

    fun requestLoad(preferred: Int) {
        val target = chooseSafeDeck(preferred, playing1, playing2, uri1 == null, uri2 == null)
        if (target == 0) {
            notice = "Ambos decks están reproduciendo. Pausa uno antes de cargar otra pista."
        } else {
            pendingTarget = target
            picker.launch(arrayOf("audio/*"))
        }
    }

    if (showSplash) {
        MysticSplash()
    } else Surface(Modifier.fillMaxSize(), color = Back) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF101722), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SC", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(" LIVE 2", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(clock, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(date.replaceFirstChar { it.uppercase() }, color = Color.LightGray, fontSize = 10.sp)
                }
                Text("AUDIO LOCAL · OFFLINE", color = Green, fontSize = 10.sp)
                IconButton(onClick = {
                    locked = !locked
                    (context as? MainActivity)?.requestedOrientation = if (locked) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen, "Bloquear interfaz",
                        tint = if (locked) Amber else Color.White)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, "Configuración", tint = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeckPanel("DECK 1", track1, playing1, uri1 != null, Modifier.weight(1f),
                    onLoad = { requestLoad(1) },
                    onPlay = { if (uri1 != null) { if (player1.isPlaying) player1.pause() else player1.play() } else notice = "Primero carga una canción en Deck 1." },
                    onStop = { player1.pause(); player1.seekTo(0) },
                    onCue = { player1.seekTo(0) }, onSync = { player1.setPlaybackSpeed(1f) },
                    progress = progress1, onSeek = { player1.seekTo((player1.duration.coerceAtLeast(0L) * it).toLong()) })
                DeckPanel("DECK 2", track2, playing2, uri2 != null, Modifier.weight(1f),
                    onLoad = { requestLoad(2) },
                    onPlay = { if (uri2 != null) { if (player2.isPlaying) player2.pause() else player2.play() } else notice = "Primero carga una canción en Deck 2." },
                    onStop = { player2.pause(); player2.seekTo(0) },
                    onCue = { player2.seekTo(0) }, onSync = { player2.setPlaybackSpeed(1f) },
                    progress = progress2, onSeek = { player2.seekTo((player2.duration.coerceAtLeast(0L) * it).toLong()) })
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xFF101722), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MIXER", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("DECK 1", color = Color.LightGray, fontSize = 10.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("CROSSFADE", color = Color.White, fontSize = 10.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("DECK 2", color = Color.LightGray, fontSize = 10.sp)
                }
                Slider(value = crossfader, onValueChange = { crossfader = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GANANCIA 1", color = Color.LightGray, fontSize = 9.sp)
                    Slider(value = gain1, onValueChange = { gain1 = it }, modifier = Modifier.weight(1f))
                    Text("${(gain1 * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("GANANCIA 2", color = Color.LightGray, fontSize = 9.sp)
                    Slider(value = gain2, onValueChange = { gain2 = it }, modifier = Modifier.weight(1f))
                    Text("${(gain2 * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(notice, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF101722), RoundedCornerShape(8.dp)).padding(9.dp))
            if (locked) Text("INTERFAZ BLOQUEADA · La reproducción sigue habilitada", color = Green, fontSize = 10.sp)
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configuración SC LIVE 2") },
            text = {
                Column {
                    Text("Formato de reloj")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !use24h, onClick = { use24h = false }); Text("12 horas")
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = use24h, onClick = { use24h = true }); Text("24 horas")
                    }
                    Text("Las canciones se abren desde el almacenamiento del teléfono y quedan disponibles sin internet.", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Listo") } }
        )
    }
}

private fun chooseSafeDeck(preferred: Int, p1: Boolean, p2: Boolean, empty1: Boolean, empty2: Boolean): Int {
    return when {
        empty1 && !p1 && preferred == 1 -> 1
        empty2 && !p2 && preferred == 2 -> 2
        empty1 && !p1 -> 1
        empty2 && !p2 -> 2
        preferred == 1 && !p1 -> 1
        preferred == 2 && !p2 -> 2
        !p1 -> 1
        !p2 -> 2
        else -> 0
    }
}

private fun formatPlayerTime(progress: Float): String {
    return String.format(Locale.US, "%02d:%02d", (progress * 3600).toInt() / 60, (progress * 3600).toInt() % 60)
}

private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "Pista de audio"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    }
    return name
}

@Composable
private fun DeckPanel(
    name: String, track: String, playing: Boolean, loaded: Boolean, modifier: Modifier,
    onLoad: () -> Unit, onPlay: () -> Unit, onStop: () -> Unit,
    onCue: () -> Unit, onSync: () -> Unit,
    progress: Float, onSeek: (Float) -> Unit
) {
    Column(modifier.fillMaxHeight().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(9.dp).background(if (playing) Green else Color.Gray, CircleShape))
            Text(if (playing) " PLAY" else " STOP", color = Color.LightGray, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0B1119), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            val spin = rememberInfiniteTransition(label = "vinyl-$name")
            val angle by spin.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1800), repeatMode = androidx.compose.animation.core.RepeatMode.Restart),
                label = "platter-rotation"
            )
            Box(Modifier.size(190.dp).graphicsLayer { rotationZ = if (playing) angle else 0f }
                .background(Color(0xFF05070B), CircleShape)) {
                Box(Modifier.fillMaxSize().padding(5.dp).background(Color(0xFF171D27), CircleShape)) {
                    Box(Modifier.fillMaxSize().padding(4.dp).background(Color(0xFF090D14), CircleShape)) {
                        Box(Modifier.align(Alignment.Center).size(62.dp).background(Color(0xFF123C59), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text("SC", color = Color(0xFF8BE9FF), fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Box(Modifier.align(Alignment.Center).size(7.dp).background(Color.LightGray, CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (loaded) track else "Deck vacío · carga una pista", color = Color.White, fontSize = 12.sp, maxLines = 1)
        Slider(value = progress, onValueChange = onSeek, enabled = loaded, modifier = Modifier.fillMaxWidth())
        Text(formatPlayerTime(progress), color = Color.LightGray, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLoad, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263445))) { Text("CARGAR") }
            Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = if (playing) Red else Green)) {
                Text(if (playing) "PAUSA" else "PLAY")
            }
            OutlinedButton(onClick = onStop, enabled = loaded) { Text("STOP") }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = onCue, enabled = loaded, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 3.dp)) { Text("CUE") }
            OutlinedButton(onClick = onSync, enabled = loaded, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 3.dp)) { Text("SYNC · 1.0×") }
        }
    }
}

@Composable
private fun MysticSplash() {
    val transition = rememberInfiniteTransition(label = "mystic-glow")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(950, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glow"
    )
    val reveal by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "reveal"
    )
    Box(Modifier.fillMaxSize().background(Color(0xFF03050B)), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(230.dp).graphicsLayer {
                scaleX = 0.82f + glow * 0.22f
                scaleY = 0.82f + glow * 0.22f
                alpha = glow * 0.24f
            }.background(Color(0xFF2563EB), CircleShape)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SC", color = Color(0xFF8BE9FF), fontSize = 94.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal
                    scaleX = 0.72f + reveal * 0.28f
                    scaleY = 0.72f + reveal * 0.28f
                    shadowElevation = 26f * glow
                }
            )
            Text(
                "LIVE 2", color = Color.White, fontSize = 23.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 8.sp,
                modifier = Modifier.graphicsLayer { alpha = reveal }
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "PRO DJ SYSTEM", color = Color(0xFF9CA3AF), fontSize = 10.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.graphicsLayer { alpha = reveal }
            )
        }
    }
}
