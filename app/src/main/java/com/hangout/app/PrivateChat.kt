package com.hangout.app

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp

data class ChatSummary(
    val id: String,
    val peerUid: String,
    val peerUsername: String,
    val members: List<String>,
    val lastMessage: String,
    val updatedAt: Timestamp?,
    val peerPhotoUrl: String? = null,
    val peerOnline: Boolean = false
)

private data class PrivateMessage(
    val id: String,
    val senderUid: String,
    val senderUsername: String,
    val text: String,
    val imageUrl: String? = null,
    val audioData: String? = null
)

private val PrivateBg = Color(0xFF171717)
private val PrivatePanel = Color(0xFF242424)
private val PrivateAccent = Color(0xFFC6C6C6)
private val PrivateText = Color(0xFFF5F5F5)
private val PrivateMuted = Color(0xFFA5A5A5)
private val PrivateOutgoing = Color(0xFF3B3B3B)

@Composable
fun PrivateChatScreen(chat: ChatSummary, onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current.applicationContext
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid
    val currentUsername = currentUser?.email?.substringBefore("@") ?: "usuário"
    val messages = remember(chat.id) { mutableStateListOf<PrivateMessage>() }
    var draft by remember(chat.id) { mutableStateOf("") }
    var feedback by remember(chat.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<java.io.File?>(null) }

    fun startVoiceRecording() {
        try {
            val (activeRecorder, file) = HangoutAudioData.startRecording(context)
            recorder = activeRecorder
            recordingFile = file
            recording = true
            feedback = "Gravando áudio… toque em parar para enviar."
        } catch (_: Exception) {
            recording = false
            feedback = "Não foi possível iniciar a gravação. Confira a permissão do microfone."
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else feedback = "O Hangout precisa do microfone para gravar áudio."
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentUid != null && !sending && !uploadingPhoto) {
            uploadingPhoto = true
            feedback = "Comprimindo foto…"
            Thread {
                val imageData = HangoutImageData.encode(context, uri)
                Handler(Looper.getMainLooper()).post {
                    if (imageData == null) {
                        uploadingPhoto = false
                        feedback = "Não foi possível preparar a foto. Tente outra imagem."
                    } else {
                        db.collection("privateChats").document(chat.id).collection("messages")
                            .add(
                                mapOf(
                                    "text" to "",
                                    "imageUrl" to imageData,
                                    "senderUid" to currentUid,
                                    "senderUsername" to currentUsername,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                db.collection("privateChats").document(chat.id).update(
                                    mapOf(
                                        "lastMessage" to "📷 Foto",
                                        "lastSenderUid" to currentUid,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }
                            .addOnFailureListener {
                                feedback = "Não foi possível criar a mensagem. Confira as regras do Firestore."
                            }
                            .addOnCompleteListener { uploadingPhoto = false }
                    }
                }
            }.start()
        }
    }

    DisposableEffect(chat.id, currentUid) {
        if (currentUid == null) {
            feedback = "Entre na sua conta para ver as mensagens."
            onDispose { }
        } else {
            val registration = db.collection("privateChats")
                .document(chat.id)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        feedback = "Não foi possível carregar as mensagens privadas. Confira as regras do Firestore."
                    } else if (snapshot != null) {
                        messages.clear()
                        snapshot.documents.reversed().forEach { document ->
                            val text = document.getString("text") ?: return@forEach
                            messages.add(
                                PrivateMessage(
                                    id = document.id,
                                    senderUid = document.getString("senderUid") ?: "",
                                    senderUsername = document.getString("senderUsername") ?: "usuário",
                                    text = text,
                                    imageUrl = document.getString("imageUrl"),
                                    audioData = document.getString("audioData")
                                )
                            )
                        }
                        feedback = ""
                    }
                }
            onDispose { registration.remove() }
        }
    }

    DisposableEffect(chat.id) { onDispose { try { recorder?.stop() } catch (_: Exception) { }; try { recorder?.release() } catch (_: Exception) { } } }

    Column(Modifier.fillMaxSize().background(PrivateBg)) {
        Row(
            Modifier.fillMaxWidth().background(PrivatePanel).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PrivateText)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF414141)),
                contentAlignment = Alignment.Center
            ) {
                if (!chat.peerPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = remember(chat.peerPhotoUrl) { HangoutImageData.model(chat.peerPhotoUrl) },
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(chat.peerUsername.take(1).uppercase(), color = PrivateText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("@${chat.peerUsername}", color = PrivateText, fontWeight = FontWeight.Bold)
                Text(if (chat.peerOnline) "● Online agora" else "Conversa privada", color = PrivateMuted, fontSize = 11.sp)
            }
        }

        if (messages.isEmpty() && feedback.isBlank()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Esta é uma conversa privada.", color = PrivateText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Envie uma mensagem para começar.", color = PrivateMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { item ->
                    val mine = item.senderUid == currentUid
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            Modifier.widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (mine) PrivateOutgoing else PrivatePanel)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            if (!mine) {
                                Text("@${item.senderUsername}", color = PrivateAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                            }
                            if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = remember(item.imageUrl) { HangoutImageData.model(item.imageUrl) },
                                    contentDescription = "Foto enviada na conversa",
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                            }
                            if (!item.audioData.isNullOrBlank()) {
                                OutlinedButton(onClick = { HangoutAudioData.play(context, item.audioData) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproduzir áudio")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ouvir áudio")
                                }
                            }
                            if (item.text.isNotBlank()) Text(item.text, color = PrivateText, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        if (feedback.isNotBlank()) {
            Text(feedback, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = Color(0xFFFFB0B0), fontSize = 12.sp)
        }

        Row(
            Modifier.fillMaxWidth().background(PrivatePanel).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !sending && !uploadingPhoto && !recording,
                onClick = { photoPicker.launch("image/*") }
            ) { Icon(Icons.Default.Image, contentDescription = "Enviar foto", tint = PrivateAccent) }
            IconButton(
                enabled = !sending && !uploadingPhoto,
                onClick = {
                    if (!recording) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceRecording()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        try { recorder?.stop() } catch (_: Exception) { }
                        try { recorder?.release() } catch (_: Exception) { }
                        recorder = null
                        recording = false
                        val file = recordingFile
                        recordingFile = null
                        if (file != null) {
                            sending = true
                            feedback = "Preparando áudio…"
                            Thread {
                                val data = HangoutAudioData.encode(file)
                                Handler(Looper.getMainLooper()).post {
                                    if (data == null) {
                                        feedback = "Áudio muito grande ou vazio. Grave um trecho mais curto."
                                        sending = false
                                        file.delete()
                                    } else {
                                        db.collection("privateChats").document(chat.id).collection("messages")
                                            .add(mapOf("text" to "", "audioData" to data, "senderUid" to currentUid,
                                                "senderUsername" to currentUsername, "createdAt" to FieldValue.serverTimestamp()))
                                            .addOnSuccessListener {
                                                db.collection("privateChats").document(chat.id).update(mapOf(
                                                    "lastMessage" to "🎤 Áudio", "lastSenderUid" to currentUid,
                                                    "updatedAt" to FieldValue.serverTimestamp()))
                                            }
                                            .addOnFailureListener { feedback = "Áudio não enviado. Confira as regras do Firestore." }
                                            .addOnCompleteListener { sending = false; file.delete() }
                                    }
                                }
                            }.start()
                        }
                    }
                }
            ) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (recording) "Parar gravação" else "Gravar áudio",
                    tint = if (recording) Color(0xFFFF7777) else PrivateAccent)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (recording) "Gravando áudio…" else "Mensagem…") },
                singleLine = true,
                enabled = !sending && !uploadingPhoto && !recording,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                enabled = !sending && !uploadingPhoto && draft.isNotBlank(),
                onClick = {
                    val text = draft.trim()
                    val uid = auth.currentUser?.uid
                    if (text.isNotEmpty() && uid != null && !sending) {
                        sending = true
                        feedback = ""
                        db.collection("privateChats").document(chat.id)
                            .collection("messages")
                            .add(
                                mapOf(
                                    "text" to text,
                                    "senderUid" to uid,
                                    "senderUsername" to currentUsername,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                db.collection("privateChats").document(chat.id)
                                    .update(
                                        mapOf(
                                            "lastMessage" to text.take(200),
                                            "lastSenderUid" to uid,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                    .addOnFailureListener {
                                        feedback = "Mensagem enviada, mas não foi possível atualizar a prévia da conversa."
                                    }
                                draft = ""
                                sending = false
                            }
                            .addOnFailureListener {
                                feedback = "Falha ao enviar. Verifique a conexão e as regras do Firestore."
                                sending = false
                            }
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar mensagem", tint = PrivateAccent)
            }
        }
    }
}
