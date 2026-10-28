package com.hangout.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.launch

@Composable
fun DirectCallControls(chatId: String, peerUid: String, peerUsername: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = remember { FirebaseFirestore.getInstance() }
    val callRef = remember(chatId) { db.collection("privateChats").document(chatId).collection("calls").document("active") }
    var status by remember(chatId) { mutableStateOf("") }
    var callerUid by remember(chatId) { mutableStateOf("") }
    var joining by remember(chatId) { mutableStateOf(false) }
    var muted by remember(chatId) { mutableStateOf(false) }
    var info by remember(chatId) { mutableStateOf("") }
    var pendingAction by remember(chatId) { mutableStateOf("") }
    var room by remember(chatId) { mutableStateOf<Room?>(null) }

    fun startCall() {
        joining = true
        callRef.set(mapOf(
            "callerUid" to uid, "calleeUid" to peerUid, "status" to "ringing",
            "roomName" to "direct_$chatId", "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            status = "ringing"
            callerUid = uid
            info = ""
        }.addOnFailureListener {
            info = "Não foi possível iniciar a ligação. Confira as regras do Firestore."
        }.addOnCompleteListener { joining = false }
    }

    fun answerCall() {
        callRef.update(mapOf("status" to "active", "updatedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener { info = "Não foi possível atender. Confira as regras do Firestore." }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            info = "O Hangout precisa da permissão do microfone para ligar."
        } else {
            when (pendingAction) {
                "call" -> startCall()
                "answer" -> answerCall()
            }
        }
        pendingAction = ""
    }

    DisposableEffect(callRef) {
        val registration = callRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                info = "Não foi possível verificar a ligação. Confira as regras do Firestore."
            } else if (snapshot == null || !snapshot.exists()) {
                status = ""
                callerUid = ""
            } else {
                val next = snapshot.getString("status") ?: ""
                status = next
                callerUid = snapshot.getString("callerUid") ?: ""
                if (next == "active" && room == null && !joining) {
                    joining = true
                    FirebaseFunctions.getInstance().getHttpsCallable("createVoiceToken")
                        .call(mapOf("roomName" to "direct_$chatId"))
                        .addOnSuccessListener { result ->
                            val payload = result.getData() as? Map<*, *>
                            val url = payload?.get("url") as? String
                            val token = payload?.get("token") as? String
                            if (url.isNullOrBlank() || token.isNullOrBlank()) {
                                joining = false
                                info = "O serviço de voz retornou dados inválidos."
                            } else {
                                scope.launch {
                                    try {
                                        val connected = LiveKit.create(context)
                                        connected.connect(url, token)
                                        connected.localParticipant.setMicrophoneEnabled(true)
                                        room = connected
                                        muted = false
                                        info = ""
                                    } catch (_: Exception) {
                                        info = "Falha ao conectar. Confira se o LiveKit está configurado."
                                    } finally {
                                        joining = false
                                    }
                                }
                            }
                        }
                        .addOnFailureListener {
                            joining = false
                            info = "Publique a função createVoiceToken e configure o LiveKit."
                        }
                }
                if (next == "ended" || next == "rejected") {
                    val oldRoom = room
                    if (oldRoom != null) {
                        scope.launch {
                            try {
                                oldRoom.localParticipant.setMicrophoneEnabled(false)
                                oldRoom.disconnect()
                            } catch (_: Exception) { }
                            room = null
                            muted = false
                        }
                    }
                    if (next == "rejected") info = "A ligação foi recusada."
                    if (next == "ended") info = "A ligação foi encerrada."
                }
            }
        }
        onDispose {
            registration.remove()
            val active = room
            if (active != null) {
                callRef.update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp()))
                scope.launch {
                    try {
                        active.localParticipant.setMicrophoneEnabled(false)
                        active.disconnect()
                    } catch (_: Exception) { }
                }
            }
        }
    }

    IconButton(
        enabled = !joining && (status.isBlank() || status == "ended" || status == "rejected"),
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startCall()
            } else {
                pendingAction = "call"
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    ) {
        Icon(Icons.Default.Call, contentDescription = "Ligar para @$peerUsername", modifier = Modifier.size(22.dp))
    }

    if (status == "ringing" && callerUid != uid) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ligação recebida") },
            text = { Text("@$peerUsername está chamando. Deseja atender?") },
            confirmButton = {
                TextButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        answerCall()
                    } else {
                        pendingAction = "answer"
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) { Icon(Icons.Default.Call, contentDescription = null); Text(" Atender") }
            },
            dismissButton = {
                TextButton(onClick = {
                    callRef.update(mapOf("status" to "rejected", "updatedAt" to FieldValue.serverTimestamp()))
                }) { Text("Recusar") }
            }
        )
    }

    if (status == "ringing" && callerUid == uid) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Chamando…") },
            text = { Text("Aguardando @$peerUsername atender.") },
            confirmButton = {
                TextButton(onClick = {
                    callRef.update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp()))
                }) { Text("Cancelar") }
            }
        )
    }

    if (status == "active") {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ligação com @$peerUsername") },
            text = { Text(if (joining) "Conectando áudio…" else if (room != null) "Conectado ao áudio." else "Preparando áudio…") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { room?.localParticipant?.setMicrophoneEnabled(muted) } catch (_: Exception) { }
                        muted = !muted
                    }
                }) { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null); Text(if (muted) " Ativar microfone" else " Silenciar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    callRef.update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp()))
                }) { Icon(Icons.Default.CallEnd, contentDescription = null); Text(" Encerrar") }
            }
        )
    }

    if (info.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { info = "" },
            title = { Text("Hangout") },
            text = { Text(info) },
            confirmButton = { TextButton(onClick = { info = "" }) { Text("OK") } }
        )
    }
}
