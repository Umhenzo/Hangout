package com.hangout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay

private val Bg = Color(0xFF171717)
private val Panel = Color(0xFF242424)
private val Accent = Color(0xFFC6C6C6)
private val MainText = Color(0xFFF5F5F5)
private val Muted = Color(0xFFA5A5A5)

class MainActivity : ComponentActivity() {
    private val presenceObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { updatePresence(true) }
        override fun onStop(owner: LifecycleOwner) { updatePresence(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(presenceObserver)
        setContent { FirebaseGate { HangoutApp() } }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(presenceObserver)
        super.onDestroy()
    }

    private fun updatePresence(online: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return
        val ref = FirebaseFirestore.getInstance().collection("profiles").document(user.uid)
        ref.get().addOnSuccessListener { snapshot ->
            if (!online && !snapshot.exists()) return@addOnSuccessListener
            val fields = hashMapOf<String, Any>(
                "uid" to user.uid,
                "username" to username,
                "isOnline" to online,
                "lastSeen" to FieldValue.serverTimestamp()
            )
            if (!snapshot.exists()) fields["createdAt"] = FieldValue.serverTimestamp()
            ref.set(fields, SetOptions.merge())
        }
    }
}

@Composable
private fun HangoutApp() {
    var tab by remember { mutableIntStateOf(0) }
    var openedChat by remember { mutableStateOf<ChatSummary?>(null) }
    var notice by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Accent,
            background = Bg,
            surface = Panel,
            onSurface = MainText,
            onPrimary = Color(0xFF171717),
            onBackground = MainText
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (openedChat != null) {
                PrivateChatScreen(chat = openedChat!!, onBack = { openedChat = null })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("hangout", color = MainText, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                            Text(listOf("Suas conversas", "Chat global", "Salas", "Seu perfil")[tab], color = Muted, fontSize = 12.sp)
                        }
                        if (tab == 3) {
                            IconButton(onClick = { FirebaseAuth.getInstance().signOut() }) {
                                Icon(Icons.Default.Logout, contentDescription = "Sair da conta", tint = MainText)
                            }
                        }
                    }
                    when (tab) {
                        0 -> ChatsTab(
                            modifier = Modifier.weight(1f),
                            onOpenChat = { openedChat = it },
                            onNotice = { notice = it }
                        )
                        1 -> FirebaseGlobalChat(Modifier.weight(1f))
                        2 -> RoomsTab(Modifier.weight(1f))
                        else -> ProfileTab(Modifier.weight(1f))
                    }
                    NavigationBar(containerColor = Panel, contentColor = MainText) {
                        listOf("Chats", "Global", "Salas", "Perfil").forEachIndexed { index, label ->
                            val icon = when (index) {
                                0 -> Icons.Default.ChatBubble
                                1 -> Icons.Default.Public
                                2 -> Icons.Default.GraphicEq
                                else -> Icons.Default.AccountCircle
                            }
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MainText,
                                    selectedTextColor = MainText,
                                    indicatorColor = Color(0xFF414141),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
                if (notice.isNotBlank()) {
                    LaunchedEffect(notice) {
                        delay(2600)
                        notice = ""
                    }
                    Snackbar(
                        Modifier.padding(12.dp),
                        action = { TextButton(onClick = { notice = "" }) { Text("OK") } }
                    ) { Text(notice) }
                }
            }
        }
    }
}

private data class HangoutProfile(val uid: String, val username: String, val photoUrl: String? = null, val isOnline: Boolean = false)

@Composable
private fun ChatsTab(
    modifier: Modifier,
    onOpenChat: (ChatSummary) -> Unit,
    onNotice: (String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<HangoutProfile>() }
    val chats = remember { mutableStateListOf<ChatSummary>() }

    LaunchedEffect(search, uid) {
        val term = search.trim().removePrefix("@").lowercase()
        results.clear()
        searchError = ""
        if (term.length < 3 || uid == null) {
            searching = false
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        db.collection("profiles")
            .whereEqualTo("username", term)
            .limit(10)
            .get()
            .addOnSuccessListener { snapshot ->
                if (search.trim().removePrefix("@").lowercase() == term) {
                    results.clear()
                    snapshot.documents.forEach { doc ->
                        val profileUid = doc.getString("uid") ?: doc.id
                        val username = doc.getString("username") ?: return@forEach
                        if (profileUid != uid) results.add(HangoutProfile(profileUid, username, doc.getString("photoUrl"), doc.getBoolean("isOnline") == true))
                    }
                    searching = false
                }
            }
            .addOnFailureListener {
                if (search.trim().removePrefix("@").lowercase() == term) {
                    searching = false
                    searchError = "Não foi possível pesquisar. Confira as regras do Firestore."
                }
            }
    }

    DisposableEffect(uid) {
        if (uid == null) {
            onDispose { }
        } else {
            val registration = db.collection("privateChats")
                .whereArrayContains("members", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onNotice("Não foi possível carregar as conversas. Verifique as regras do Firestore.")
                    } else if (snapshot != null) {
                        val parsed = snapshot.documents.mapNotNull { doc ->
                            val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                            val peerIndex = members.indexOfFirst { it != uid }
                            if (peerIndex < 0) return@mapNotNull null
                            ChatSummary(
                                id = doc.id,
                                peerUid = members[peerIndex],
                                peerUsername = "Carregando…",
                                members = members,
                                lastMessage = doc.getString("lastMessage") ?: "",
                                updatedAt = doc.getTimestamp("updatedAt")
                            )
                        }
                        chats.clear()
                        chats.addAll(parsed.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L })
                        parsed.forEach { summary ->
                            db.collection("profiles").document(summary.peerUid).get()
                                .addOnSuccessListener { profile ->
                                    val name = profile.getString("username") ?: "usuário"
                                    val index = chats.indexOfFirst { it.id == summary.id }
                                    if (index >= 0) chats[index] = chats[index].copy(peerUsername = name, peerPhotoUrl = profile.getString("photoUrl"), peerOnline = profile.getBoolean("isOnline") == true)
                                }
                        }
                    }
                }
            onDispose { registration.remove() }
        }
    }

    fun beginChat(profile: HangoutProfile) {
        val current = auth.currentUser
        if (current == null) {
            onNotice("Entre na sua conta primeiro.")
            return
        }
        val members = listOf(current.uid, profile.uid).sorted()
        val chatId = members.joinToString("_")
        val document = db.collection("privateChats").document(chatId)
        document.get()
            .addOnSuccessListener { existing ->
                if (existing.exists()) {
                    onOpenChat(
                        ChatSummary(
                            id = chatId,
                            peerUid = profile.uid,
                            peerUsername = profile.username,
                            members = members,
                            lastMessage = existing.getString("lastMessage") ?: "",
                            updatedAt = existing.getTimestamp("updatedAt")
                        )
                    )
                } else {
                    document.set(
                        mapOf(
                            "members" to members,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "lastMessage" to "",
                            "lastSenderUid" to ""
                        )
                    ).addOnSuccessListener {
                        onOpenChat(ChatSummary(chatId, profile.uid, profile.username, members, "", Timestamp.now(), profile.photoUrl, profile.isOnline))
                    }.addOnFailureListener {
                        document.get().addOnSuccessListener { concurrent ->
                            if (concurrent.exists()) {
                                onOpenChat(
                                    ChatSummary(
                                        chatId, profile.uid, profile.username, members,
                                        concurrent.getString("lastMessage") ?: "",
                                        concurrent.getTimestamp("updatedAt")
                                    )
                                )
                            } else onNotice("Não foi possível iniciar a conversa. Confira as regras do Firestore.")
                        }.addOnFailureListener {
                            onNotice("Não foi possível iniciar a conversa. Confira as regras do Firestore.")
                        }
                    }
                }
            }
            .addOnFailureListener {
                onNotice("Não foi possível abrir a conversa. Confira as regras do Firestore.")
            }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Pesquisar pelo nick…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Text("Digite pelo menos 3 caracteres para encontrar uma pessoa.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Panel)
        }
        if (searchError.isNotBlank()) {
            Text(searchError, color = Color(0xFFFFB0B0), fontSize = 12.sp)
        }
        if (search.trim().removePrefix("@").length >= 3 && !searching) {
            if (results.isEmpty() && searchError.isBlank()) {
                Text("Nenhum usuário encontrado com esse nick.", color = Muted, fontSize = 13.sp)
            } else {
                Text("RESULTADOS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                results.forEach { profile ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                            .clickable { beginChat(profile) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(profile.username, profile.photoUrl, 44)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("@${profile.username}", color = MainText, fontWeight = FontWeight.SemiBold)
                            Text("Toque para conversar", color = Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Chat, contentDescription = "Iniciar conversa", tint = Accent)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("SUAS CONVERSAS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        if (chats.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Muted, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text("Nenhuma conversa ainda", color = MainText, fontWeight = FontWeight.SemiBold)
                Text("Pesquise um nick acima para enviar a primeira mensagem.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                            .clickable { onOpenChat(chat) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(chat.peerUsername, chat.peerPhotoUrl, 48)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("@${chat.peerUsername}", color = MainText, fontWeight = FontWeight.SemiBold)
                            Text(chat.lastMessage.ifBlank { if (chat.peerOnline) "● Online agora" else "Toque para abrir a conversa" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomsTab(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Accent, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text("Salas de voz", color = MainText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            "As salas de voz ainda não estão conectadas a um serviço de áudio. Os botões anteriores eram apenas demonstrativos.",
            color = Muted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ProfileTab(modifier: Modifier) {
    val user = FirebaseAuth.getInstance().currentUser
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = user?.uid
    val defaultUsername = user?.email?.substringBefore("@") ?: "usuário"
    var username by remember { mutableStateOf(defaultUsername) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Disponível") }
    var online by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid == null) {
            onDispose { }
        } else {
            val registration = db.collection("profiles").document(uid).addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    username = snapshot.getString("username") ?: defaultUsername
                    photoUrl = snapshot.getString("photoUrl")
                    status = snapshot.getString("status") ?: "Disponível"
                    online = snapshot.getBoolean("isOnline") == true
                }
            }
            onDispose { registration.remove() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uid != null) {
            busy = true
            notice = ""
            val imageRef = FirebaseStorage.getInstance().reference.child("profilePhotos/" + uid + "/avatar.jpg")
            imageRef.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Upload falhou")
                    imageRef.downloadUrl
                }
                .addOnSuccessListener { url ->
                    db.collection("profiles").document(uid).update("photoUrl", url.toString())
                        .addOnSuccessListener { notice = "Foto de perfil atualizada." }
                        .addOnFailureListener { notice = "Foto enviada, mas o perfil não foi atualizado. Verifique as regras do Firestore." }
                        .addOnCompleteListener { busy = false }
                }
                .addOnFailureListener {
                    busy = false
                    notice = "Falha no upload. Ative o Firebase Storage e publique storage.rules."
                }
        }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Panel,
            tonalElevation = 2.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(username, photoUrl, 104)
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (online) Color(0xFFB7B7B7) else Color(0xFF666666))
                            .padding(4.dp).clip(CircleShape).background(Panel)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Seu perfil", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("@" + username, color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (online) "● Online agora" else "○ Offline", color = Accent, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "Enviando foto…" else "Alterar foto de perfil")
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Seu status") },
                    placeholder = { Text("O que você está fazendo?") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (uid != null) {
                            db.collection("profiles").document(uid)
                                .update("status", status.trim().ifBlank { "Disponível" })
                                .addOnSuccessListener { notice = "Status atualizado." }
                                .addOnFailureListener { notice = "Não foi possível salvar. Verifique as regras do Firestore." }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar status") }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Bg)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { FirebaseAuth.getInstance().signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sair da conta")
                }
                if (notice.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(notice, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(username: String, photoUrl: String?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF414141)),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(username.take(1).uppercase(), color = MainText, fontSize = (size * 0.38).sp, fontWeight = FontWeight.Bold)
        }
    }
}
