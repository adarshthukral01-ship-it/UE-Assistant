package com.ue.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("ue_settings", MODE_PRIVATE) }
    private var listening by mutableStateOf(false)
    private var transcript by mutableStateOf("")
    private var replyText by mutableStateOf("नमस्ते! मैं UE हूँ। माइक दबाकर बात करें या नीचे कमांड लिखें।")
    private var status by mutableStateOf("UE READY")
    private var masterOn by mutableStateOf(true)
    private var wakeWordOn by mutableStateOf(false)
    private var voiceOn by mutableStateOf(true)
    private var internetOn by mutableStateOf(true)
    private var language by mutableStateOf("Hindi + Hinglish")
    private var backendUrl by mutableStateOf("")
    private var commandInput by mutableStateOf("")
    private var noteInput by mutableStateOf("")
    private var notes by mutableStateOf(listOf<String>())
    private var showSettings by mutableStateOf(false)
    private var showNotes by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && masterOn) startListening() else if (!granted) status = "Microphone permission required"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        masterOn = prefs.getBoolean("master_on", true)
        wakeWordOn = prefs.getBoolean("wake_on", false)
        voiceOn = prefs.getBoolean("voice_on", true)
        internetOn = prefs.getBoolean("internet_on", true)
        language = prefs.getString("language", "Hindi + Hinglish") ?: "Hindi + Hinglish"
        backendUrl = prefs.getString("backend_url", "") ?: ""
        notes = prefs.getStringSet("notes", emptySet())?.toList()?.sorted() ?: emptyList()
        tts = TextToSpeech(this, this)
        setContent {
            UEApp(
                masterOn, wakeWordOn, voiceOn, internetOn, listening, transcript, replyText, status,
                language, commandInput, noteInput, notes, backendUrl, showSettings, showNotes,
                onMaster = { enabled ->
                    masterOn = enabled; saveBool("master_on", enabled)
                    if (!enabled) stopAssistant() else status = "UE READY"
                },
                onWake = { enabled ->
                    wakeWordOn = enabled; saveBool("wake_on", enabled)
                    status = if (enabled) "Wake-word support is not active yet; tap TALK to speak" else "UE READY"
                },
                onVoice = { enabled -> voiceOn = enabled; saveBool("voice_on", enabled); if (!enabled) tts?.stop() },
                onInternet = { enabled -> internetOn = enabled; saveBool("internet_on", enabled) },
                onLanguage = { selected -> language = selected; prefs.edit().putString("language", selected).apply(); setTtsLanguage() },
                onMic = { if (masterOn) startListening() },
                onCommand = { value -> commandInput = value },
                onSubmit = { val cmd = commandInput.trim(); commandInput = ""; if (cmd.isNotBlank()) handleCommand(cmd) },
                onOpenSettings = { showSettings = true },
                onCloseSettings = { showSettings = false },
                onBackendUrl = { value -> backendUrl = value.trim(); prefs.edit().putString("backend_url", backendUrl).apply(); showSettings = false; status = "Backend URL saved" },
                onOpenNotes = { showNotes = true },
                onCloseNotes = { showNotes = false },
                onNoteInput = { noteInput = it },
                onAddNote = { if (noteInput.isNotBlank()) { notes = (notes + noteInput.trim()).takeLast(100); prefs.edit().putStringSet("notes", notes.toSet()).apply(); noteInput = ""; reply("नोट सेव कर दिया।") } },
                onDeleteNote = { item -> notes = notes.filterNot { it == item }; prefs.edit().putStringSet("notes", notes.toSet()).apply() }
            )
        }
    }

    private fun saveBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            chooseVoice(); tts?.setSpeechRate(0.94f); tts?.setPitch(1.06f); setTtsLanguage()
        } else status = "Phone Text-to-Speech unavailable"
    }

    private fun setTtsLanguage() {
        tts?.language = when (language) { "English" -> Locale("en", "IN"); else -> Locale("hi", "IN") }
    }

    private fun chooseVoice() {
        val engine = tts ?: return
        val voices: Set<Voice> = engine.voices ?: emptySet()
        val female = voices.firstOrNull {
            val label = (it.name + " " + it.locale.displayName).lowercase()
            (label.contains("female") || label.contains("woman") || label.contains("zira") || label.contains("jenny") || label.contains("aria")) &&
                (it.locale.language == "hi" || it.locale.language == "en")
        }
        if (female != null) engine.voice = female
    }

    private fun startListening() {
        if (!masterOn) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status = "Speech recognition unavailable on this phone"; return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { listening = true; status = "LISTENING..." }
                override fun onResults(r: Bundle?) {
                    listening = false
                    transcript = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (transcript.isBlank()) { status = "Didn't catch that — try again"; return }
                    status = "THINKING..."; handleCommand(transcript)
                }
                override fun onError(e: Int) { listening = false; status = when (e) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network error"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again"
                    else -> "UE READY"
                } }
                override fun onEndOfSpeech() { listening = false }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val locale = when (language) { "English" -> "en-IN"; "Hindi" -> "hi-IN"; else -> "hi-IN" }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        if (!masterOn) return
        val text = raw.trim(); val lower = text.lowercase(Locale.ROOT)
        when {
            lower == "help" || lower.contains("what can you do") || lower.contains("मदद") || lower.contains("कमांड") -> reply("मैं समय, तारीख, बैटरी बता सकती हूँ; YouTube, Google Search, Maps, WhatsApp और फोन सेटिंग खोल सकती हूँ; नोट सेव कर सकती हूँ। AI जवाब के लिए Settings में अपना secure backend URL जोड़ें।")
            lower.contains("time") || lower.contains("समय") || lower.contains("टाइम") -> reply("अभी समय है " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
            lower.contains("date") || lower.contains("तारीख") || lower.contains("आज कौन सा दिन") -> reply("आज " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("hi", "IN")).format(Date()))
            lower.contains("battery") || lower.contains("बैटरी") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                reply("आपके फोन की बैटरी ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} प्रतिशत है।")
            }
            lower.startsWith("note ") || lower.startsWith("नोट ") || lower.startsWith("याद रखना ") -> {
                val note = text.substringAfter(' ').trim()
                if (note.isNotBlank()) { notes = (notes + note).takeLast(100); prefs.edit().putStringSet("notes", notes.toSet()).apply(); reply("नोट सेव कर दिया: $note") }
                else reply("कृपया नोट बोलें।")
            }
            lower.contains("show notes") || lower.contains("मेरे नोट") || lower.contains("नोट दिखा") -> { reply(if (notes.isEmpty()) "अभी कोई नोट सेव नहीं है।" else "आपके नोट: " + notes.takeLast(5).joinToString("। ")); }
            lower.contains("open youtube") || lower.contains("यूट्यूब") -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")), "YouTube खोल रही हूँ।")
            lower.contains("google search") || lower.startsWith("search ") || lower.startsWith("खोजो ") || lower.startsWith("सर्च ") -> {
                val query = when { lower.startsWith("search ") -> text.drop(7); lower.startsWith("google search ") -> text.drop(14); lower.startsWith("खोजो ") -> text.drop(5); lower.startsWith("सर्च ") -> text.drop(5); else -> text }
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))), "Google पर खोज रही हूँ।")
            }
            lower.contains("weather") || lower.contains("मौसम") -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("weather near me"))), "मौसम की जानकारी खोल रही हूँ।")
            lower.contains("open maps") || lower.contains("maps खोल") || lower.contains("मैप") || lower.contains("रास्ता") -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(text.replace("open maps", "").replace("मैप", "")))), "Maps खोल रही हूँ।")
            lower.contains("whatsapp") || lower.contains("व्हाट्सएप") -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")), "WhatsApp खोलने की कोशिश कर रही हूँ।")
            lower.contains("alarm") || lower.contains("अलार्म") -> launch(Intent(AlarmClock.ACTION_SET_ALARM), "Clock ऐप में अलार्म सेट करें।")
            lower.contains("settings") || lower.contains("सेटिंग") -> launch(Intent(Settings.ACTION_SETTINGS), "फोन सेटिंग खोल रही हूँ।")
            lower.contains("call ") || lower.contains("कॉल ") -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), "डायलर खुल गया है। नंबर चुनकर कॉल करें।")
            lower.contains("open browser") || lower.contains("browser") || lower.contains("ब्राउज़र") -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "ब्राउज़र खोल रही हूँ।")
            internetOn && backendUrl.isNotBlank() -> askBackend(text)
            else -> reply("मैंने सुना: $text. अधिक स्मार्ट AI जवाब के लिए Settings में HTTPS backend URL जोड़ें। आप Help बोलकर कमांड देख सकते हैं।")
        }
    }

    private fun launch(intent: Intent, message: String) {
        try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); reply(message) }
        catch (_: Exception) { reply("यह ऐप आपके फोन में उपलब्ध नहीं है या खुल नहीं पाया।") }
    }

    private fun askBackend(message: String) {
        status = "AI THINKING..."
        Thread {
            var answer: String? = null
            try {
                val url = URL(backendUrl)
                require(url.protocol == "https") { "Use an HTTPS backend URL for safety." }
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 12000; readTimeout = 20000; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(JSONObject().put("message", message).put("language", language).put("assistant", "UE").toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (conn.responseCode in 200..299) answer = JSONObject(body).optString("reply").takeIf { it.isNotBlank() }
                conn.disconnect()
            } catch (e: Exception) { answer = "AI कनेक्शन नहीं हो पाया। Backend URL और इंटरनेट जाँचें।" }
            runOnUiThread { status = "UE READY"; reply(answer ?: "AI ने खाली जवाब दिया।") }
        }.start()
    }

    private fun reply(message: String) {
        replyText = message; status = "UE READY"
        if (voiceOn && masterOn) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "UE_REPLY")
    }

    private fun stopAssistant() { recognizer?.cancel(); listening = false; tts?.stop(); status = "UE OFF" }

    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}

@Composable
fun UEApp(
    masterOn: Boolean, wakeOn: Boolean, voiceOn: Boolean, internetOn: Boolean, listening: Boolean,
    transcript: String, replyText: String, status: String, language: String, commandInput: String,
    noteInput: String, notes: List<String>, backendUrl: String, showSettings: Boolean, showNotes: Boolean,
    onMaster: (Boolean) -> Unit, onWake: (Boolean) -> Unit, onVoice: (Boolean) -> Unit,
    onInternet: (Boolean) -> Unit, onLanguage: (String) -> Unit, onMic: () -> Unit,
    onCommand: (String) -> Unit, onSubmit: () -> Unit, onOpenSettings: () -> Unit, onCloseSettings: () -> Unit,
    onBackendUrl: (String) -> Unit, onOpenNotes: () -> Unit, onCloseNotes: () -> Unit,
    onNoteInput: (String) -> Unit, onAddNote: () -> Unit, onDeleteNote: (String) -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF05070D), Color(0xFF101B2E))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp)); Text("UE", fontSize = 42.sp, color = Color.Cyan)
                Text("PERSONAL VOICE ASSISTANT", fontSize = 12.sp, color = Color.LightGray)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { onMaster(!masterOn) }, modifier = Modifier.size(150.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (masterOn) Color(0xFF064E5B) else Color(0xFF5B1010))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (masterOn) "UE ON" else "UE OFF", fontSize = 25.sp); Text(if (listening) "Listening..." else status, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onMic, enabled = masterOn, modifier = Modifier.fillMaxWidth()) { Text("🎙  TALK TO UE") }
                Text("आपने कहा: ${transcript.ifBlank { "अभी कुछ नहीं" }}", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF18253A))) {
                    Text(replyText, Modifier.padding(14.dp), color = Color.White, fontSize = 14.sp)
                }
                OutlinedTextField(value = commandInput, onValueChange = onCommand, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Type a command / सवाल लिखें") }, singleLine = true)
                Button(onClick = onSubmit, enabled = masterOn, modifier = Modifier.fillMaxWidth()) { Text("SEND COMMAND") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenNotes, modifier = Modifier.weight(1f)) { Text("📝 Notes") }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("⚙ Settings") }
                }
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Language", Modifier.weight(1f), color = Color.LightGray)
                    Box { OutlinedButton(onClick = { expanded = true }) { Text(language) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Hindi", "Hindi + Hinglish", "English").forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onLanguage(item); expanded = false }) }
                    } }
                }
                ToggleRow("Hey UE wake word (not active yet)", wakeOn, onWake)
                ToggleRow("Female voice (if phone supports it)", voiceOn, onVoice)
                ToggleRow("Internet / AI enabled", internetOn, onInternet)
                Text("Quick commands: time • date • battery • open YouTube • search cats • weather • maps • notes • alarm • settings • help", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                Text("v0.3 • AI requires your own HTTPS backend", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            }
            var backendDraft by remember(showSettings) { mutableStateOf(backendUrl) }
            if (showSettings) AlertDialog(onDismissRequest = onCloseSettings, title = { Text("UE Settings") },
                text = { Column { Text("AI backend HTTPS URL (not an OpenAI API key)"); OutlinedTextField(value = backendDraft, onValueChange = { backendDraft = it }, label = { Text("https://your-server/chat") }) } },
                confirmButton = { TextButton(onClick = { onBackendUrl(backendDraft) }) { Text("Save") } },
                dismissButton = { TextButton(onClick = onCloseSettings) { Text("Close") } })
            if (showNotes) AlertDialog(onDismissRequest = onCloseNotes, title = { Text("UE Notes") },
                text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = noteInput, onValueChange = onNoteInput, label = { Text("New note") })
                    Button(onClick = onAddNote, modifier = Modifier.fillMaxWidth()) { Text("Save note") }
                    if (notes.isEmpty()) Text("No notes saved yet")
                    notes.takeLast(30).reversed().forEach { note -> Row(verticalAlignment = Alignment.CenterVertically) { Text(note, Modifier.weight(1f), fontSize = 13.sp); TextButton(onClick = { onDeleteNote(note) }) { Text("Delete") } } }
                } }, confirmButton = { TextButton(onClick = onCloseNotes) { Text("Done") } })
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = Color.White); Switch(checked = value, onCheckedChange = onChange)
    }
}
