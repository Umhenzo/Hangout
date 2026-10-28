package com.hangout.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.functions.FirebaseFunctions
import java.security.MessageDigest
import java.security.SecureRandom
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private val FbBg = Color(0xFF171717)
private val FbPanel = Color(0xFF242424)
private val FbAccent = Color(0xFFC6C6C6)
private val FbMint = Color(0xFFB8B8B8)
private val FbText = Color(0xFFF5F5F5)
private val FbMuted = Color(0xFFA5A5A5)

@Composable
fun FirebaseGate(content: @Composable () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    if (user == null) FirebaseAuthScreen() else content()
}

@Composable
private fun FirebaseAuthScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryNick by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var recoveryBusy by remember { mutableStateOf(false) }
    var recoveryFeedback by remember { mutableStateOf("") }
    var recoveryCodeResult by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = FbAccent, secondary = FbMint, background = FbBg, surface = FbPanel, onSurface = FbText)) {
        Surface(Modifier.fillMaxSize(), color = FbBg) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = FbPanel,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF3A3A3A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("H", color = FbText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("hangout", color = FbText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isRegister) "Crie seu espaço" else "Entre na sua conta", color = FbMuted, fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nick (3 a 20 caracteres)") },
                            singleLine = true,
                            prefix = { Text("@") },
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Senha (mínimo 6 caracteres)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                val safeUsername = username.trim().removePrefix("@").lowercase()
                                if (safeUsername.length !in 3..20 || password.length < 6) {
                                    feedback = "Use um nick de 3 a 20 caracteres e uma senha de pelo menos 6 caracteres."
                                } else {
                                    busy = true
                                    feedback = ""
                                    val internalEmail = "$safeUsername@users.hangout.invalid"
                                    val task = if (isRegister) auth.createUserWithEmailAndPassword(internalEmail, password)
                                               else auth.signInWithEmailAndPassword(internalEmail, password)
                                    task.addOnCompleteListener { result ->
                                        busy = false
                                        if (result.isSuccessful) {
                                            val createdUser = result.result?.user
                                            if (isRegister && createdUser != null) {
                                                db.collection("profiles").document(createdUser.uid).set(
                                                    mapOf(
                                                        "uid" to createdUser.uid,
                                                        "username" to safeUsername,
                                                        "createdAt" to FieldValue.serverTimestamp(),
                                                        "status" to "Disponível",
                                                        "isOnline" to true,
                                                        "lastSeen" to FieldValue.serverTimestamp()
                                                    )
                                                ).addOnFailureListener {
                                                    feedback = "Conta criada, mas o perfil não foi salvo. Verifique as regras do Firestore."
                                                }
                                            }
                                        } else {
                                            feedback = when (result.exception?.message) {
                                                else -> result.exception?.localizedMessage ?: "Não foi possível entrar. Verifique o nick e a senha."
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0D0D0), contentColor = Color(0xFF171717))
                        ) {
                            Text(if (busy) "Aguarde…" else if (isRegister) "Criar conta" else "Entrar", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { isRegister = !isRegister; feedback = "" }) {
                            Text(if (isRegister) "Já tenho uma conta · Entrar" else "Criar uma conta nova")
                        }
                        if (!isRegister) {
                            TextButton(onClick = { showRecovery = true; recoveryFeedback = "" }) {
                                Text("Esqueci a senha")
                            }
                        }
                        if (feedback.isNotBlank()) {
                            Text(feedback, color = Color(0xFFFFB0B0), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Entre usando apenas seu nick. Guarde seu código de recuperação em um local seguro.",
                            color = FbMuted, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showRecovery) {
        AlertDialog(
            onDismissRequest = { if (!recoveryBusy) showRecovery = false },
            title = { Text("Recuperar conta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use o nick e o código de recuperação que você salvou.", color = FbMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = recoveryNick,
                        onValueChange = { recoveryNick = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20) },
                        label = { Text("Nick") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = recoveryCode,
                        onValueChange = { recoveryCode = it.uppercase().take(24) },
                        label = { Text("Código de recuperação") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = recoveryPassword,
                        onValueChange = { recoveryPassword = it },
                        label = { Text("Nova senha (mín. 8 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (recoveryFeedback.isNotBlank()) {
                        Text(recoveryFeedback, color = Color(0xFFFFB0B0), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !recoveryBusy,
                    onClick = {
                        val nick = recoveryNick.trim().removePrefix("@").lowercase()
                        if (nick.length !in 3..20 || recoveryCode.isBlank() || recoveryPassword.length < 8) {
                            recoveryFeedback = "Preencha o nick, o código e uma senha com pelo menos 8 caracteres."
                        } else {
                            recoveryBusy = true
                            recoveryFeedback = ""
                            FirebaseFunctions.getInstance().getHttpsCallable("recoverAccount")
                                .call(mapOf(
                                    "username" to nick,
                                    "recoveryCode" to recoveryCode,
                                    "newPassword" to recoveryPassword
                                ))
                                .addOnSuccessListener { result ->
                                    val data = result.data as? Map<*, *>
                                    recoveryCodeResult = data?.get("newRecoveryCode") as? String
                                    recoveryBusy = false
                                    if (recoveryCodeResult != null) {
                                        showRecovery = false
                                        recoveryCode = ""
                                        recoveryPassword = ""
                                    } else {
                                        recoveryFeedback = "A recuperação retornou uma resposta inválida."
                                    }
                                }
                                .addOnFailureListener {
                                    recoveryBusy = false
                                    recoveryFeedback = "Não foi possível recuperar. Confira o nick, o código e se a função foi publicada."
                                }
                        }
                    }
                ) { Text(if (recoveryBusy) "Recuperando…" else "Redefinir senha") }
            },
            dismissButton = { TextButton(enabled = !recoveryBusy, onClick = { showRecovery = false }) { Text("Cancelar") } }
        )
    }

    recoveryCodeResult?.let { freshCode ->
        AlertDialog(
            onDismissRequest = { recoveryCodeResult = null },
            title = { Text("Senha alterada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Guarde este NOVO código de recuperação. O código anterior foi invalidado.")
                    Text(freshCode, color = FbText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { recoveryCodeResult = null }) { Text("Já salvei") }
            }
        )
    }
}
@Composable
fun FirebaseGlobalChat(modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val user = auth.currentUser
    val messages = remember { mutableStateListOf<GlobalChatMessage>() }
    var draft by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(user?.uid) {
        if (user == null) {
            feedback = "Entre na sua conta para ver as mensagens."
            onDispose { }
        } else {
            val registration = db.collection("globalMessages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        feedback = "Não foi possível carregar mensagens. Verifique as regras do Firestore."
                    } else if (snapshot != null) {
                        messages.clear()
                        snapshot.documents.reversed().forEach { doc ->
                            val text = doc.getString("text") ?: return@forEach
                            val uid = doc.getString("uid") ?: ""
                            val email = doc.getString("email") ?: "usuário"
                            messages.add(GlobalChatMessage(doc.id, uid, email, text))
                        }
                        feedback = ""
                    }
                }
            onDispose { registration.remove() }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = FbPanel,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("G", color = FbText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Chat global", color = FbText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Mensagens para toda a comunidade", color = FbMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { item ->
                val mine = item.uid == user?.uid
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier.widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (mine) Color(0xFF3C3C3C) else FbPanel)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            if (mine) "Você" else "@" + item.email.substringBefore("@"),
                            color = FbMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(item.text, color = FbText, fontSize = 15.sp)
                    }
                }
            }
        }
        if (feedback.isNotBlank()) {
            Text(feedback, color = Color(0xFFFFB3B3), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 2000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escreva uma mensagem…") },
                singleLine = true,
                enabled = !sending,
                shape = RoundedCornerShape(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                enabled = !sending && draft.isNotBlank(),
                onClick = {
                    val text = draft.trim()
                    val currentUser = auth.currentUser
                    if (text.isNotEmpty() && currentUser != null && !sending) {
                        sending = true
                        db.collection("globalMessages").add(
                            mapOf(
                                "text" to text,
                                "uid" to currentUser.uid,
                                "email" to (currentUser.email ?: "usuário"),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            draft = ""
                            feedback = ""
                        }.addOnFailureListener {
                            feedback = "Falha ao enviar. Confira as regras do Firestore e sua conexão."
                        }.addOnCompleteListener {
                            sending = false
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFD0D0D0),
                    contentColor = Color(0xFF171717)
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Enviar")
            }
        }
    }
}
@Composable
fun FirebaseSignOutButton() {
    TextButton(onClick = { FirebaseAuth.getInstance().signOut() }) {
        Text("Sair da conta", color = Color(0xFFFF9B9B))
    }
}

private data class GlobalChatMessage(
    val id: String,
    val uid: String,
    val email: String,
    val text: String
)
