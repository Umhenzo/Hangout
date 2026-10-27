package com.hangout.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

    MaterialTheme(colorScheme = darkColorScheme(primary = FbAccent, secondary = FbMint, background = FbBg, surface = FbPanel, onSurface = FbText)) {
        Surface(Modifier.fillMaxSize(), color = FbBg) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("hangout", color = FbText, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (isRegister) "Crie sua conta" else "Entre na sua conta", color = FbMuted)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Nick (3 a 20 caracteres)") },
                    singleLine = true,
                    prefix = { Text("@") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Senha (mínimo 6 caracteres)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val safeUsername = username.trim().removePrefix("@").lowercase()
                        if (safeUsername.length !in 3..20 || password.length < 6) {
                            feedback = "Use um nick de 3 a 20 caracteres e uma senha de pelo menos 6 caracteres."
                        } else {
                            busy = true
                            feedback = ""
                            // Alias interno: a pessoa entra só com nick; não precisa ter e-mail real.
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
                                                "createdAt" to FieldValue.serverTimestamp()
                                            )
                                        ).addOnFailureListener {
                                            feedback = "Conta criada, mas não foi possível salvar o perfil."
                                        }
                                    }
                                } else {
                                    feedback = result.exception?.localizedMessage ?: "Não foi possível entrar. Tente novamente."
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Aguarde..." else if (isRegister) "Criar conta" else "Entrar")
                }
                TextButton(onClick = { isRegister = !isRegister; feedback = "" }) {
                    Text(if (isRegister) "Já tenho uma conta · Entrar" else "Não tenho conta · Cadastrar")
                }
                if (feedback.isNotBlank()) {
                    Text(feedback, color = Color(0xFFFF9B9B), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("Acesso por nick e senha. Não precisa de e-mail real; não há recuperação de senha por e-mail ainda.", color = FbMuted, fontSize = 12.sp)
            }
        }
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

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Pra todo mundo", color = FbText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Mensagens sincronizadas em tempo real", color = FbMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { item ->
                val mine = item.uid == user?.uid
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (mine) Color(0xFF39335E) else FbPanel).padding(12.dp)
                ) {
                    Text(if (mine) "Você" else item.email.substringBefore("@"), color = FbMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(item.text, color = FbText)
                }
            }
        }
        if (feedback.isNotBlank()) Text(feedback, color = Color(0xFFFFB3B3), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { if (it.length <= 2000) draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Fale com todo mundo…") },
                singleLine = true
            )
            IconButton(onClick = {
                val text = draft.trim()
                val currentUser = auth.currentUser
                if (text.isNotEmpty() && currentUser != null) {
                    db.collection("globalMessages").add(
                        mapOf(
                            "text" to text,
                            "uid" to currentUser.uid,
                            "email" to (currentUser.email ?: "usuário"),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnFailureListener {
                        feedback = "Falha ao enviar. Confira se o Firestore e as regras estão configurados."
                    }
                    draft = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Enviar", tint = FbAccent)
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
