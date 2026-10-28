package com.hangout.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.functions.FirebaseFunctions
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.launch

private data class VoiceRoomOption(val id: String, val title: String, val subtitle: String)

private val voiceRoomOptions = listOf(
    VoiceRoomOption("roblox", "Resenha & Roblox", "Fale sobre jogos e combine partidas"),
    VoiceRoomOption("music", "Só música", "Converse sobre seus sons favoritos"),
    VoiceRoomOption("late-night", "Papo tranquilo", "Um espaço para conversar"),
    VoiceRoomOption("meet-people", "Conhecendo gente", "Conheça a comunidade Hangout")
)

@Composable
fun VoiceRoomsTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceRoom by remember { mutableStateOf<Room?>(null) }
    var activeRoom by remember { mutableStateOf<VoiceRoomOption?>(null) }
    var pendingRoom by remember { mutableStateOf<VoiceRoomOption?>(null) }
    var muted by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var showSetupHelp by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val selected = pendingRoom
        if (!granted) {
            working = false
            feedback = "O Hangout precisa de permissão para usar o microfone nas salas de voz."
        } else if (selected != null) {
            working = true
            feedback = ""
            FirebaseFunctions.getInstance()
                .getHttpsCallable("createVoiceToken")
                .call(mapOf("roomName" to selected.id))
                .addOnSuccessListener { result ->
                    val data = result.getData() as? Map<*, *>
                    val serverUrl = data?.get("url") as? String
                    val token = data?.get("token") as? String
                    if (serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
                        working = false
                        feedback = "O serviço de voz retornou uma configuração inválida."
                    } else {
                        scope.launch {
                            try {
                                val createdRoom = LiveKit.create(context)
                                createdRoom.connect(serverUrl, token)
                                createdRoom.localParticipant.setMicrophoneEnabled(true)
                                voiceRoom = createdRoom
                                activeRoom = selected
                                muted = false
                                feedback = ""
                            } catch (error: Exception) {
                                feedback = "Não foi possível conectar à sala. Verifique o servidor de voz."
                                try { voiceRoom?.disconnect() } catch (_: Exception) { }
                                voiceRoom = null
                                activeRoom = null
                            } finally {
                                working = false
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    working = false
                    feedback = "O serviço de voz ainda não está configurado. Veja a documentação do repositório."
                    showSetupHelp = true
                }
        } else {
            working = false
        }
    }

    val latestRoom by rememberUpdatedState(voiceRoom)
    DisposableEffect(Unit) {
        onDispose {
            val room = latestRoom
            if (room != null) {
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                    try {
                        room.localParticipant.setMicrophoneEnabled(false)
                        room.disconnect()
                    } catch (_: Exception) { }
                }
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        if (activeRoom == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF363636)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFFE0E0E0), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Salas de voz", color = Color(0xFFF5F5F5), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Converse ao vivo com a comunidade", color = Color(0xFFA5A5A5), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            voiceRoomOptions.forEach { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF242424)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF3C3C3C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFE0E0E0))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                            Text(option.title, color = Color(0xFFF5F5F5), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(option.subtitle, color = Color(0xFFA5A5A5), fontSize = 11.sp)
                        }
                        Button(
                            enabled = !working,
                            onClick = {
                                pendingRoom = option
                                feedback = ""
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    working = true
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0D0D0),
                                contentColor = Color(0xFF171717)
                            )
                        ) {
                            Text(if (working) "Aguarde" else "Entrar")
                        }
                    }
                }
            }
            if (feedback.isNotBlank()) {
                Text(feedback, color = Color(0xFFFFB0B0), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            TextButton(onClick = { showSetupHelp = true }) {
                Text("Sobre a configuração das salas", color = Color(0xFFCECECE))
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color(0xFF242424),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF3D3D3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFFF5F5F5), modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(activeRoom?.title ?: "Sala de voz", color = Color(0xFFF5F5F5), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Conectado à sala", color = Color(0xFFBDBDBD), fontSize = 13.sp)
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val room = voiceRoom
                                if (room != null) {
                                    scope.launch {
                                        try {
                                            room.localParticipant.setMicrophoneEnabled(muted)
                                            muted = !muted
                                        } catch (_: Exception) {
                                            feedback = "Não foi possível alterar o microfone."
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (muted) "Ativar microfone" else "Silenciar")
                        }
                        Button(
                            onClick = {
                                val room = voiceRoom
                                scope.launch {
                                    try {
                                        room?.localParticipant?.setMicrophoneEnabled(false)
                                        room?.disconnect()
                                    } catch (_: Exception) { }
                                    voiceRoom = null
                                    activeRoom = null
                                    muted = false
                                    feedback = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF474747), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.PhoneDisabled, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sair")
                        }
                    }
                    if (feedback.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(feedback, color = Color(0xFFFFB0B0), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showSetupHelp) {
        AlertDialog(
            onDismissRequest = { showSetupHelp = false },
            title = { Text("Configuração das salas de voz") },
            text = {
                Text(
                    "As chamadas usam LiveKit. Para ativá-las, configure LIVEKIT_URL, LIVEKIT_API_KEY e LIVEKIT_API_SECRET no backend Firebase Functions e publique a função createVoiceToken. Nunca coloque a chave secreta dentro do APK."
                )
            },
            confirmButton = { TextButton(onClick = { showSetupHelp = false }) { Text("Entendi") } }
        )
    }
}
