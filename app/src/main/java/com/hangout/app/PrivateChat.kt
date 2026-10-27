package com.hangout.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Timestamp

data class ChatSummary(
    val id: String,
    val peerUid: String,
    val peerUsername: String,
    val members: List<String>,
    val lastMessage: String,
    val updatedAt: Timestamp?
)

private data class PrivateMessage(
    val id: String,
    val senderUid: String,
    val senderUsername: String,
    val text: String
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
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid
    val currentUsername = currentUser?.email?.substringBefore("@") ?: "usuário"
    val messages = remember(chat.id) { mutableStateListOf<PrivateMessage>() }
    var draft by remember(chat.id) { mutableStateOf("") }
    var feedback by remember(chat.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

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
                                    text = text
                                )
                            )
                        }
                        feedback = ""
                    }
                }
            onDispose { registration.remove() }
        }
    }

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
                Text(chat.peerUsername.take(1).uppercase(), color = PrivateText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("@${chat.peerUsername}", color = PrivateText, fontWeight = FontWeight.Bold)
                Text("Conversa privada", color = PrivateMuted, fontSize = 11.sp)
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
                            Text(item.text, color = PrivateText, fontSize = 15.sp)
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
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensagem…") },
                singleLine = true,
                enabled = !sending,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                enabled = !sending && draft.isNotBlank(),
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
