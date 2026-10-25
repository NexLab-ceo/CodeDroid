package com.nexlab.codedroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF111318)
private val Surface = Color(0xFF191B22)
private val SurfaceRaised = Color(0xFF222530)
private val BorderColor = Color(0xFF303440)
private val Accent = Color(0xFF887BFF)
private val TextMain = Color(0xFFE8EAF2)
private val TextMuted = Color(0xFF9299AA)
private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("codedroid", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Background, surface = Surface,
                onBackground = TextMain, onSurface = TextMain
            )) {
                CodeDroidApp(
                    initialCode = prefs.getString("code", starterCode) ?: starterCode,
                    saveCode = { prefs.edit().putString("code", it).apply() },
                    savedMode = prefs.getString("mode", "Local") ?: "Local",
                    saveMode = { prefs.edit().putString("mode", it).apply() },
                    savedLocalUrl = prefs.getString("localUrl", "http://10.0.2.2:11434/v1/chat/completions") ?: "",
                    saveLocalUrl = { prefs.edit().putString("localUrl", it).apply() },
                    savedCloudUrl = prefs.getString("cloudUrl", "") ?: "",
                    saveCloudUrl = { prefs.edit().putString("cloudUrl", it).apply() },
                    savedModel = prefs.getString("model", "qwen2.5-coder:7b") ?: "",
                    saveModel = { prefs.edit().putString("model", it).apply() }
                )
            }
        }
    }
}

private const val starterCode = """fun greet(name: String): String {
    return "Hello, " + name + "!";
}

fun main() {
    println(greet("CodeDroid"))
}
"""

@Composable
private fun CodeDroidApp(
    initialCode: String,
    saveCode: (String) -> Unit,
    savedMode: String,
    saveMode: (String) -> Unit,
    savedLocalUrl: String,
    saveLocalUrl: (String) -> Unit,
    savedCloudUrl: String,
    saveCloudUrl: (String) -> Unit,
    savedModel: String,
    saveModel: (String) -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }
    var mode by remember { mutableStateOf(savedMode) }
    var localUrl by remember { mutableStateOf(savedLocalUrl) }
    var cloudUrl by remember { mutableStateOf(savedCloudUrl) }
    var model by remember { mutableStateOf(savedModel) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Привет! Я CodePilot AI. Напиши вопрос о коде или попроси объяснить функцию.") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Готово. Код можно редактировать; выполнение программ пока не подключено.") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("CodeDroid", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Native Android IDE · Kotlin", color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, "Настройки", tint = TextMuted)
            }
        }

        if (showSettings) {
            Column(
                Modifier.fillMaxWidth().background(SurfaceRaised).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("НАСТРОЙКИ AI", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Провайдер", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Local", "Cloud", "Auto").forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option; saveMode(option) },
                            label = { Text(option) }
                        )
                    }
                }
                LabeledField("Local endpoint (OpenAI-compatible)", localUrl) {
                    localUrl = it; saveLocalUrl(it)
                }
                LabeledField("Cloud backend URL (/api/chat)", cloudUrl) {
                    cloudUrl = it; saveCloudUrl(it)
                }
                LabeledField("Local model", model) { model = it; saveModel(it) }
                Text("На реальном планшете укажи IP компьютера/сервера в локальной сети. 10.0.2.2 работает только в Android Emulator.", color = TextMuted, fontSize = 11.sp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tablet = maxWidth >= 760.dp
            if (tablet) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorPanel(code, { code = it; saveCode(it) }, output, { output = it }, Modifier.weight(1.15f).fillMaxHeight())
                    AssistantPanel(answer, prompt, { prompt = it }, busy, { sendPrompt(prompt, code, mode, model, localUrl, cloudUrl, { busy = it }, { answer = it }, scope) }, Modifier.weight(0.85f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorPanel(code, { code = it; saveCode(it) }, output, { output = it }, Modifier.weight(1f).fillMaxWidth())
                    AssistantPanel(answer, prompt, { prompt = it }, busy, { sendPrompt(prompt, code, mode, model, localUrl, cloudUrl, { busy = it }, { answer = it }, scope) }, Modifier.weight(0.85f).fillMaxWidth())
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Accent).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text("main", color = Color.White, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("Kotlin · UTF-8", color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EditorPanel(
    code: String,
    onCodeChange: (String) -> Unit,
    output: String,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = Accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("main.kt", color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCodeChange(starterCode) }) { Text("Reset", fontSize = 11.sp) }
            IconButton(onClick = onRun, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.PlayArrow, "Запуск", tint = Accent)
            }
        }
        HorizontalDivider(color = BorderColor)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.background(Color(0xFF15171D)).padding(horizontal = 8.dp, vertical = 12.dp)) {
                (1..maxOf(1, code.lines().size)).forEach {
                    Text(it.toString().padStart(2, ' '), color = Color(0xFF666D7E), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
                }
            }
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFD7DDF0), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                ),
                decorationBox = { inner ->
                    Box {
                        if (code.isEmpty()) Text("Введите Kotlin-код…", color = TextMuted, fontSize = 12.sp)
                        inner()
                    }
                }
            )
        }
        HorizontalDivider(color = BorderColor)
        Column(Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 92.dp).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("OUTPUT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(output, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AssistantPanel(
    answer: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, tint = Accent)
            Spacer(Modifier.width(8.dp))
            Text("AI ASSISTANT", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (busy) "THINKING…" else "READY", color = Accent, fontSize = 9.sp)
        }
        HorizontalDivider(color = BorderColor)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("CODEPILOT", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(answer, color = TextMain, fontSize = 13.sp, lineHeight = 20.sp)
        }
        HorizontalDivider(color = BorderColor)
        OutlinedTextField(
            value = prompt, onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            placeholder = { Text("Спроси о коде…", fontSize = 12.sp) },
            minLines = 2, maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = BorderColor,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain
            )
        )
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AI может отправлять код выбранному провайдеру", color = TextMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Button(onClick = onSend, enabled = !busy && prompt.isNotBlank()) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Send", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        OutlinedTextField(
            value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = BorderColor,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain
            )
        )
    }
}

private fun sendPrompt(
    prompt: String,
    code: String,
    mode: String,
    model: String,
    localUrl: String,
    cloudUrl: String,
    setBusy: (Boolean) -> Unit,
    setAnswer: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val question = prompt.trim()
    if (question.isEmpty()) return
    setBusy(true)
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            try {
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "Ты CodePilot AI, помощник программиста. Отвечай по-русски. Проанализируй текущий Kotlin-код:\n\n$code"))
                    .put(JSONObject().put("role", "user").put("content", question))
                val body = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                    .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val target = when (mode) {
                    "Cloud" -> cloudUrl
                    else -> localUrl
                }
                if (target.isBlank()) throw IllegalStateException("Укажи URL сервера в настройках.")
                val request = Request.Builder().url(target).post(body).build()
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $payload")
                    val json = JSONObject(payload)
                    json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                        ?: json.optString("content", "Сервер вернул пустой ответ.")
                }
            } catch (e: Exception) {
                if (mode == "Auto" && cloudUrl.isNotBlank()) {
                    try {
                        val messages = JSONArray()
                            .put(JSONObject().put("role", "system").put("content", "Ты CodePilot AI. Отвечай по-русски. Код:\n$code"))
                            .put(JSONObject().put("role", "user").put("content", question))
                        val body = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        client.newCall(Request.Builder().url(cloudUrl).post(body).build()).execute().use { response ->
                            val payload = response.body?.string().orEmpty()
                            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $payload")
                            JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                                ?: JSONObject(payload).optString("content", "Пустой ответ.")
                        }
                    } catch (cloudError: Exception) {
                        "Ошибка AI: ${cloudError.message ?: "неизвестная ошибка"}"
                    }
                } else "Ошибка AI: ${e.message ?: "проверь URL и подключение"}"
            }
        }
        setAnswer(result)
        setBusy(false)
    }
}
}name!"
}

fun main() {
    println(greet("CodeDroid"))
}
"""

@Composable
private fun CodeDroidApp(
    initialCode: String,
    saveCode: (String) -> Unit,
    savedMode: String,
    saveMode: (String) -> Unit,
    savedLocalUrl: String,
    saveLocalUrl: (String) -> Unit,
    savedCloudUrl: String,
    saveCloudUrl: (String) -> Unit,
    savedModel: String,
    saveModel: (String) -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }
    var mode by remember { mutableStateOf(savedMode) }
    var localUrl by remember { mutableStateOf(savedLocalUrl) }
    var cloudUrl by remember { mutableStateOf(savedCloudUrl) }
    var model by remember { mutableStateOf(savedModel) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Привет! Я CodePilot AI. Напиши вопрос о коде или попроси объяснить функцию.") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Готово. Код можно редактировать; выполнение программ пока не подключено.") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("CodeDroid", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Native Android IDE · Kotlin", color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, "Настройки", tint = TextMuted)
            }
        }

        if (showSettings) {
            Column(
                Modifier.fillMaxWidth().background(SurfaceRaised).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("НАСТРОЙКИ AI", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Провайдер", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Local", "Cloud", "Auto").forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option; saveMode(option) },
                            label = { Text(option) }
                        )
                    }
                }
                LabeledField("Local endpoint (OpenAI-compatible)", localUrl) {
                    localUrl = it; saveLocalUrl(it)
                }
                LabeledField("Cloud backend URL (/api/chat)", cloudUrl) {
                    cloudUrl = it; saveCloudUrl(it)
                }
                LabeledField("Local model", model) { model = it; saveModel(it) }
                Text("На реальном планшете укажи IP компьютера/сервера в локальной сети. 10.0.2.2 работает только в Android Emulator.", color = TextMuted, fontSize = 11.sp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tablet = maxWidth >= 760.dp
            if (tablet) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorPanel(code, { code = it; saveCode(it) }, output, { output = it }, Modifier.weight(1.15f).fillMaxHeight())
                    AssistantPanel(answer, prompt, { prompt = it }, busy, { sendPrompt(prompt, code, mode, model, localUrl, cloudUrl, { busy = it }, { answer = it }, scope) }, Modifier.weight(0.85f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorPanel(code, { code = it; saveCode(it) }, output, { output = it }, Modifier.weight(1f).fillMaxWidth())
                    AssistantPanel(answer, prompt, { prompt = it }, busy, { sendPrompt(prompt, code, mode, model, localUrl, cloudUrl, { busy = it }, { answer = it }, scope) }, Modifier.weight(0.85f).fillMaxWidth())
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Accent).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text("main", color = Color.White, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("Kotlin · UTF-8", color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EditorPanel(
    code: String,
    onCodeChange: (String) -> Unit,
    output: String,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = Accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("main.kt", color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onCodeChange(starterCode) }) { Text("Reset", fontSize = 11.sp) }
            IconButton(onClick = onRun, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.PlayArrow, "Запуск", tint = Accent)
            }
        }
        HorizontalDivider(color = BorderColor)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.background(Color(0xFF15171D)).padding(horizontal = 8.dp, vertical = 12.dp)) {
                (1..maxOf(1, code.lines().size)).forEach {
                    Text(it.toString().padStart(2, ' '), color = Color(0xFF666D7E), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
                }
            }
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFD7DDF0), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                ),
                decorationBox = { inner ->
                    Box {
                        if (code.isEmpty()) Text("Введите Kotlin-код…", color = TextMuted, fontSize = 12.sp)
                        inner()
                    }
                }
            )
        }
        HorizontalDivider(color = BorderColor)
        Column(Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 92.dp).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("OUTPUT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(output, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AssistantPanel(
    answer: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Surface, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, tint = Accent)
            Spacer(Modifier.width(8.dp))
            Text("AI ASSISTANT", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (busy) "THINKING…" else "READY", color = Accent, fontSize = 9.sp)
        }
        HorizontalDivider(color = BorderColor)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("CODEPILOT", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(answer, color = TextMain, fontSize = 13.sp, lineHeight = 20.sp)
        }
        HorizontalDivider(color = BorderColor)
        OutlinedTextField(
            value = prompt, onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            placeholder = { Text("Спроси о коде…", fontSize = 12.sp) },
            minLines = 2, maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = BorderColor,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain
            )
        )
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AI может отправлять код выбранному провайдеру", color = TextMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Button(onClick = onSend, enabled = !busy && prompt.isNotBlank()) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Send", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        OutlinedTextField(
            value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = BorderColor,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain
            )
        )
    }
}

private fun sendPrompt(
    prompt: String,
    code: String,
    mode: String,
    model: String,
    localUrl: String,
    cloudUrl: String,
    setBusy: (Boolean) -> Unit,
    setAnswer: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val question = prompt.trim()
    if (question.isEmpty()) return
    setBusy(true)
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            try {
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "Ты CodePilot AI, помощник программиста. Отвечай по-русски. Проанализируй текущий Kotlin-код:\n\n$code"))
                    .put(JSONObject().put("role", "user").put("content", question))
                val body = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                    .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val target = when (mode) {
                    "Cloud" -> cloudUrl
                    else -> localUrl
                }
                if (target.isBlank()) throw IllegalStateException("Укажи URL сервера в настройках.")
                val request = Request.Builder().url(target).post(body).build()
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $payload")
                    val json = JSONObject(payload)
                    json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                        ?: json.optString("content", "Сервер вернул пустой ответ.")
                }
            } catch (e: Exception) {
                if (mode == "Auto" && cloudUrl.isNotBlank()) {
                    try {
                        val messages = JSONArray()
                            .put(JSONObject().put("role", "system").put("content", "Ты CodePilot AI. Отвечай по-русски. Код:\n$code"))
                            .put(JSONObject().put("role", "user").put("content", question))
                        val body = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        client.newCall(Request.Builder().url(cloudUrl).post(body).build()).execute().use { response ->
                            val payload = response.body?.string().orEmpty()
                            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $payload")
                            JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                                ?: JSONObject(payload).optString("content", "Пустой ответ.")
                        }
                    } catch (cloudError: Exception) {
                        "Ошибка AI: ${cloudError.message ?: "неизвестная ошибка"}"
                    }
                } else "Ошибка AI: ${e.message ?: "проверь URL и подключение"}"
            }
        }
        setAnswer(result)
        setBusy(false)
    }
}
