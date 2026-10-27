package com.hangout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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

private val Bg = Color(0xFF10121D)
private val Panel = Color(0xFF1A1D2B)
private val Accent = Color(0xFF8B7CFF)
private val Mint = Color(0xFF62E6BD)
private val MainText = Color(0xFFF4F3FF)
private val Muted = Color(0xFF969BB5)

private data class Person(val name: String, val user: String, val emoji: String, val country: String, val status: String, val online: Boolean, val message: String)
private val people = listOf(
    Person("Luna", "@luna.exe", "🌙", "🇧🇷 Brasil", "Jogando Roblox", true, "bora jogar? 🎮"),
    Person("Kai", "@kaizinn", "🦊", "🇵🇹 Portugal", "No TikTok", true, "mandei as figurinhas kkk"),
    Person("Mika", "@mika.wav", "🦋", "🇯🇵 Japão", "Ausente", false, "áudio de 0:12"),
    Person("Noah", "@noah.mp4", "🐼", "🇧🇷 Brasil", "Offline", false, "até mais!"),
    Person("Ari", "@ari.zip", "🐸", "🇨🇦 Canadá", "No Instagram", true, "olha isso 😂")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HangoutApp() }
    }
}

@Composable
private fun HangoutApp() {
    var tab by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<Person?>(null) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(listOf<String>()) }
    var addDialog by remember { mutableStateOf(false) }
    var newUser by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Mint, background = Bg, surface = Panel, onSurface = MainText)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize()) {
                if (opened != null) {
                    val p = opened!!
                    Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { opened = null }) { Icon(Icons.Default.ArrowBack, null, tint = MainText) }
                        Avatar(p, 42)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(p.name, color = MainText, fontWeight = FontWeight.Bold)
                            Text(if (p.online) p.status else "Offline", color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { notice = "Chamadas precisam de um servidor WebRTC" }) { Icon(Icons.Default.Call, null, tint = Mint) }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("🔒 Conversa privada demonstrativa", color = Muted, fontSize = 12.sp) }
                        item { Bubble("Oi! Bem-vindo ao Hangout ✨", false) }
                        items(sent) { Bubble(it, true) }
                    }
                    Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { notice = "Figurinhas serão integradas depois" }) { Icon(Icons.Default.EmojiEmotions, null, tint = Accent) }
                        OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.weight(1f), placeholder = { Text("Mensagem…") }, singleLine = true)
                        IconButton(onClick = { if (message.isNotBlank()) { sent = sent + message; message = "" } }) { Icon(Icons.Default.Send, null, tint = Accent) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("hangout", color = MainText, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                            Text(listOf("Suas conversas", "Pra todo mundo", "Encontre sua galera", "Seu espaço")[tab], color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { notice = "Você está em dia ✨" }) { Icon(Icons.Default.NotificationsNone, null, tint = MainText) }
                        IconButton(onClick = { addDialog = true }, modifier = Modifier.clip(CircleShape).background(Accent)) { Icon(Icons.Default.PersonAdd, null, tint = Color.White) }
                    }
                    when (tab) {
                        0 -> {
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Buscar conversa ou username…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                            Text("  ATIVOS AGORA", Modifier.padding(16.dp), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                people.filter { it.online }.forEach { p ->
                                    Column(Modifier.clickable { opened = p }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Avatar(p, 48)
                                        Text(p.name, color = MainText, fontSize = 11.sp)
                                    }
                                }
                            }
                            Text("  MENSAGENS", Modifier.padding(16.dp), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                items(people.filter { (it.name + it.user + it.message).contains(query, true) }) { p ->
                                    Row(Modifier.fillMaxWidth().clickable { opened = p }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(p, 52)
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(p.name + "  " + p.user, color = MainText, fontWeight = FontWeight.SemiBold)
                                            Text(if (p.online) p.status else p.message, color = Muted, fontSize = 12.sp)
                                            Text(p.message, color = Muted, fontSize = 12.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                                    }
                                    HorizontalDivider(color = Panel)
                                }
                            }
                        }
                        1 -> GlobalTab { notice = it }
                        2 -> RoomsTab { notice = it }
                        else -> ProfileTab { notice = it }
                    }
                    NavigationBar(containerColor = Panel) {
                        listOf("Chats", "Global", "Salas", "Perfil").forEachIndexed { i, label ->
                            val icon = when (i) { 0 -> Icons.Default.ChatBubble; 1 -> Icons.Default.Public; 2 -> Icons.Default.GraphicEq; else -> Icons.Default.AccountCircle }
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
                        }
                    }
                }
            }
            if (addDialog) {
                AlertDialog(onDismissRequest = { addDialog = false }, containerColor = Panel,
                    title = { Text("Adicionar pessoa", color = MainText) },
                    text = { OutlinedTextField(newUser, { newUser = it }, placeholder = { Text("@username") }, singleLine = true) },
                    confirmButton = { TextButton(onClick = { notice = if (newUser.isBlank()) "Digite um username" else "Pedido demonstrativo enviado para $newUser"; newUser = ""; addDialog = false }) { Text("Adicionar") } },
                    dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancelar") } })
            }
            if (notice.isNotBlank()) {
                LaunchedEffect(notice) { kotlinx.coroutines.delay(2300); notice = "" }
                Snackbar(Modifier.padding(12.dp), action = { TextButton(onClick = { notice = "" }) { Text("OK") } }) { Text(notice) }
            }
        }
    }
}

@Composable
private fun Avatar(person: Person, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF39335E)), contentAlignment = Alignment.Center) {
        Text(person.emoji, fontSize = (size * .48).sp)
        Box(Modifier.size(11.dp).align(Alignment.BottomEnd).clip(CircleShape).background(if (person.online) Mint else Muted))
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(text, Modifier.clip(RoundedCornerShape(16.dp)).background(if (mine) Color(0xFF39335E) else Panel).padding(12.dp), color = MainText)
    }
}

@Composable
private fun GlobalTab(notify: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf("Luna: alguém online pra jogar? 🎮", "Kai: bom diaaa ✨", "Mika: essa figurinha KKK 😂") }
    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
        Text("Pra todo mundo", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Converse com a comunidade Hangout", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), color = MainText) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Fale com todo mundo…") }, singleLine = true)
            IconButton(onClick = { if (draft.isNotBlank()) { messages.add("Você: $draft"); draft = "" } }) { Icon(Icons.Default.Send, null, tint = Accent) }
        }
    }
}

@Composable
private fun RoomsTab(notify: (String) -> Unit) {
    var joined by remember { mutableStateOf("") }
    Column(Modifier.weight(1f).padding(16.dp)) {
        Text("Salas de voz", color = MainText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Entre pra conversar ao vivo com sua galera.", color = Muted)
        Spacer(Modifier.height(16.dp))
        listOf("🎮  Resenha & Roblox", "🎧  Só música", "🌙  Papo de madrugada", "💬  Conhecendo gente").forEach { room ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(room, Modifier.weight(1f), color = MainText)
                Button(onClick = { joined = if (joined == room) "" else room; notify(if (joined.isEmpty()) "Você saiu da sala" else "Sala demonstrativa: $room") }) { Text(if (joined == room) "Sair" else "Entrar") }
            }
        }
        if (joined.isNotEmpty()) Text("Você está em $joined. Voz real precisa de servidor de chamadas.", color = Mint)
    }
}

@Composable
private fun ProfileTab(notify: (String) -> Unit) {
    var status by remember { mutableStateOf("Jogando Roblox") }
    var country by remember { mutableStateOf("🇧🇷 Brasil") }
    Column(Modifier.weight(1f).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF39335E)), contentAlignment = Alignment.Center) { Text("🌌", fontSize = 48.sp) }
        Text("Seu perfil", Modifier.padding(top = 12.dp), color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("@seu.username", color = Accent)
        Text("● Online agora", color = Mint, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp)) {
                Text("STATUS DE ATIVIDADE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { status = when (status) { "Jogando Roblox" -> "No Instagram"; "No Instagram" -> "Assistindo TikTok"; else -> "Jogando Roblox" } }) { Text("🎮  $status  ✎") }
                HorizontalDivider(color = Bg)
                TextButton(onClick = { country = if (country == "🇧🇷 Brasil") "🇵🇹 Portugal" else "🇧🇷 Brasil" }) { Text("🌎  País: $country") }
                TextButton(onClick = { notify("Última atividade demonstrativa") }) { Text("◷  Última vez online: agora mesmo") }
                TextButton(onClick = { notify("Foto de perfil será integrada depois") }) { Text("📷  Personalizar avatar") }
                TextButton(onClick = { notify("Privacidade demonstrativa") }) { Text("🔒  Privacidade") }
            }
        }
    }
}
