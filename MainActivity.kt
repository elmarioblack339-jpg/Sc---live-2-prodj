package com.sclive2.dj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { SCLiveConsole() } }
    }
}

@Composable
private fun SCLiveConsole() {
    var locked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var use24h by remember { mutableStateOf(false) }
    var browserOpen by remember { mutableStateOf(false) }
    var network by remember { mutableIntStateOf(0) } // UI placeholder: real Android connectivity check still to implement
    var deck1 by remember { mutableStateOf("Deck 1 · Vacío") }
    var deck2 by remember { mutableStateOf("Deck 2 · Vacío") }
    var playing1 by remember { mutableStateOf(false) }
    var playing2 by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(use24h) {
        while (true) {
            val now = Date()
            val locale = Locale("es", "CO")
            clock = SimpleDateFormat(if (use24h) "HH:mm:ss" else "hh:mm:ss a", locale).format(now)
            date = SimpleDateFormat("EEEE, d 'de' MMMM yyyy", locale).format(now)
            delay(1000)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Back) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF101722), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SC", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(" LIVE 2", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(clock, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(date.replaceFirstChar { it.uppercase() }, color = Color.LightGray, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (network == 0) Icons.Default.Language else Icons.Default.Wifi, null,
                        tint = when(network) { 1 -> Green; 2 -> Amber; else -> Color.Gray })
                    Text(when(network) { 1 -> " EN LÍNEA"; 2 -> " SEÑAL REGULAR"; else -> " SIN CONEXIÓN" },
                        color = when(network) { 1 -> Green; 2 -> Amber; else -> Color.Gray }, fontSize = 10.sp)
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { locked = !locked }) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen, "Bloquear interfaz",
                        tint = if (locked) Amber else Color.White)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, "Configuración", tint = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeckPanel("DECK 1", deck1, playing1, Modifier.weight(1f),
                    onPlay = { if (deck1.contains("Vacío")) deck1 = "Deck 1 · Canción de prueba"; playing1 = !playing1 },
                    onLoad = { loadIntoSafeDeck(1, playing1, playing2, deck1, deck2) { target ->
                        if (target == 1) deck1 = "Deck 1 · Pista cargada (demo)" else deck2 = "Deck 2 · Pista cargada (demo)"
                    } })
                DeckPanel("DECK 2", deck2, playing2, Modifier.weight(1f),
                    onPlay = { if (deck2.contains("Vacío")) deck2 = "Deck 2 · Canción de prueba"; playing2 = !playing2 },
                    onLoad = { loadIntoSafeDeck(2, playing1, playing2, deck1, deck2) { target ->
                        if (target == 1) deck1 = "Deck 1 · Pista cargada (demo)" else deck2 = "Deck 2 · Pista cargada (demo)"
                    } })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BROWSER", color = Cyan, fontWeight = FontWeight.Bold)
                Text("Doble pulsación: modo en línea", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Button(onClick = { browserOpen = !browserOpen }, colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                    Text(if (browserOpen) "CERRAR BROWSER" else "ABRIR BROWSER")
                }
            }
            if (browserOpen) {
                Text("BROWSER ONLINE · Buscador y proveedor de música autorizada pendientes de integrar.",
                    color = Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
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
                    Text("Estado de red de demostración", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { network = 0 }) { Text("Sin red") }
                        TextButton(onClick = { network = 2 }) { Text("Regular") }
                        TextButton(onClick = { network = 1 }) { Text("Buena") }
                    }
                    Text("El indicador real se conectará al estado de red de Android en la siguiente fase.", fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Listo") } }
        )
    }
}

private fun loadIntoSafeDeck(
    preferred: Int, p1: Boolean, p2: Boolean, d1: String, d2: String, apply: (Int) -> Unit
) {
    // Safety policy: never overwrite a playing deck. Prefer an empty deck, then a stopped/paused deck.
    val empty1 = d1.contains("Vacío")
    val empty2 = d2.contains("Vacío")
    val target = when {
        preferred == 1 && empty1 -> 1
        preferred == 2 && empty2 -> 2
        empty1 && !p1 -> 1
        empty2 && !p2 -> 2
        !p1 && p2 -> 1
        !p2 && p1 -> 2
        !p1 -> 1
        !p2 -> 2
        else -> 0
    }
    if (target != 0) apply(target)
}

@Composable
private fun DeckPanel(
    name: String, track: String, playing: Boolean, modifier: Modifier,
    onPlay: () -> Unit, onLoad: () -> Unit
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
            Text("◎", color = Cyan, fontSize = 76.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(track, color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLoad, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263445))) { Text("CARGAR") }
            Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = if (playing) Red else Green)) {
                Text(if (playing) "PAUSA" else "PLAY")
            }
        }
    }
}
