package com.jozy.geminitouch

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private lateinit var keyInput: EditText
    private lateinit var taskInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private var steps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 18, 22, 18); setBackgroundColor(0xFFF7F5FA.toInt()) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, 0).also { it.height = 0 }.let { lp -> LinearLayout.LayoutParams(-1, 0, 1f) })
        content.addView(label("GEMINI TOUCH", 26f, true))
        content.addView(label("Controlo contínuo do Android • protótipo 1.0", 14f, false))
        content.addView(label("1. Chave API Gemini", 16f, true))
        keyInput = field("Cola a tua chave AIza…", true)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        content.addView(keyInput)
        content.addView(label("A chave fica guardada apenas nesta sessão; não a partilhes.", 12f, false))
        content.addView(label("2. Modelo Gemini", 16f, true))
        modelInput = field("gemini-2.5-flash", false); content.addView(modelInput)
        content.addView(label("3. O que queres que o Gemini faça?", 16f, true))
        taskInput = field("Ex.: abre o Chrome e pesquisa notícias de IA. Observa o ecrã e continua até terminar.", false)
        taskInput.minLines = 3; taskInput.maxLines = 6; taskInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        content.addView(taskInput)
        val accessibility = Button(this).apply { text = "ATIVAR SERVIÇO DE ACESSIBILIDADE"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        content.addView(accessibility)
        startButton = Button(this).apply { text = "▶ INICIAR AGENTE"; setOnClickListener { if (running.get()) stopAgent() else startAgent() } }
        content.addView(startButton)
        val stop = Button(this).apply { text = "■ PARAR AGORA"; setOnClickListener { stopAgent() } }; content.addView(stop)
        status = label("Estado: pronto. Ativa o serviço de acessibilidade antes de iniciar.", 14f, false); content.addView(status)
        content.addView(label("Funções: observar ecrã, tocar, deslizar, escrever, voltar, ir ao início, verificar resultados e repetir. Ações de envio, compra, eliminação ou publicação exigem instrução explícita na tarefa.", 13f, false))
        setContentView(root)
    }

    private fun label(text: String, size: Float, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(0xFF24212B.toInt()); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(2, 9, 2, 9)
    }
    private fun field(hintText: String, single: Boolean) = EditText(this).apply {
        hint = hintText; textSize = 15f; setSingleLine(single); setPadding(14, 12, 14, 12)
        background = getDrawable(android.R.drawable.edit_text)
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
    }

    private fun startAgent() {
        val key = keyInput.text.toString().trim()
        val task = taskInput.text.toString().trim()
        val model = modelInput.text.toString().trim().ifBlank { "gemini-2.5-flash" }
        if (key.isBlank() || task.isBlank()) { setStatus("Preenche a chave API e a tarefa primeiro."); return }
        if (ScreenAgentService.active == null) { setStatus("Ativa Gemini Touch em Definições → Acessibilidade e volta aqui."); return }
        running.set(true); steps = 0; startButton.text = "⏸ PAUSAR / PARAR"
        setStatus("Agente ativo. A observar o ecrã…")
        worker.execute { loop(key, model, task) }
    }

    private fun stopAgent() { running.set(false); startButton.text = "▶ INICIAR AGENTE"; setStatus("Agente parado pelo utilizador.") }
    private fun setStatus(message: String) { main.post { if (::status.isInitialized) status.text = "Estado: $message" } }

    private fun loop(key: String, model: String, task: String) {
        while (running.get()) {
            val service = ScreenAgentService.active
            if (service == null) { setStatus("Serviço de acessibilidade desligado."); running.set(false); break }
            try {
                val bitmap = captureBlocking(service) ?: run { setStatus("Não consegui capturar o ecrã. Confirma a permissão/serviço."); Thread.sleep(700); continue }
                val originalW = bitmap.width; val originalH = bitmap.height
                val scaled = resize(bitmap, 768); if (scaled !== bitmap) bitmap.recycle()
                val imageBytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 70, out); out.toByteArray() }
                val prompt = "És um agente Android de Computer Use. Tarefa do utilizador: $task\n" +
                    "Analisa a captura atual e devolve APENAS um objeto JSON, sem markdown, neste formato: " +
                    "{\"action\":\"tap|swipe|type|back|home|wait|done\",\"x\":0,\"y\":0,\"x2\":0,\"y2\":0,\"text\":\"\",\"duration_ms\":250,\"reason\":\"curto\"}. " +
                    "Coordenadas x/y são pixels da imagem fornecida (${scaled.width}x${scaled.height}). Para swipe usa x,y,x2,y2. " +
                    "Faz apenas UMA ação por ciclo, espera por alterações visíveis e depois observa novamente. Usa wait apenas se a interface estiver a carregar. " +
                    "Não envies mensagens, publiques, compres, elimines dados ou confirmes ações irreversíveis a menos que a tarefa peça isso explicitamente. " +
                    "Se a tarefa estiver concluída, action=done. Se não for possível continuar, action=done e explica em reason."
                scaled.recycle()
                val response = callGemini(key, model, prompt, imageBytes)
                val action = parseAction(response)
                steps++
                val scaleX = originalW.toFloat() / 768f.coerceAtMost(originalW.toFloat())
                val scaleY = originalH.toFloat() / (originalH.toFloat() * minOf(1f, 768f / originalW))
                setStatus("Passo $steps • ${action.optString("action")} • ${action.optString("reason")}")
                when (action.optString("action").lowercase()) {
                    "tap", "click" -> awaitGesture { cb -> service.tap(action.optDouble("x", 0.0).toFloat() * scaleX, action.optDouble("y", 0.0).toFloat() * scaleY, action.optLong("duration_ms", 60), cb) }
                    "swipe", "scroll" -> awaitGesture { cb -> service.swipe(action.optDouble("x", 0.0).toFloat() * scaleX, action.optDouble("y", 0.0).toFloat() * scaleY, action.optDouble("x2", 0.0).toFloat() * scaleX, action.optDouble("y2", 0.0).toFloat() * scaleY, action.optLong("duration_ms", 350), cb) }
                    "type" -> { val ok = service.typeText(action.optString("text")); if (!ok) setStatus("Não encontrei um campo de texto focado.") }
                    "back" -> service.pressBack()
                    "home" -> service.pressHome()
                    "wait" -> Thread.sleep(action.optLong("duration_ms", 500).coerceIn(150, 2500))
                    "done" -> { setStatus("Concluído: ${action.optString("reason")}"); running.set(false) }
                    else -> { setStatus("Ação não reconhecida; a parar para segurança."); running.set(false) }
                }
                if (running.get()) Thread.sleep(180)
            } catch (e: Exception) {
                setStatus("Erro: ${e.message ?: e.javaClass.simpleName}")
                try { Thread.sleep(1000) } catch (_: Exception) {}
            }
        }
        main.post { startButton.text = "▶ INICIAR AGENTE" }
    }

    private fun captureBlocking(service: ScreenAgentService): Bitmap? {
        val lock = java.util.concurrent.CountDownLatch(1); var result: Bitmap? = null
        main.post { service.capture { result = it; lock.countDown() } }
        if (!lock.await(4, java.util.concurrent.TimeUnit.SECONDS)) return null
        return result
    }

    private fun resize(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val height = (source.height * (maxWidth.toFloat() / source.width)).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, height, true)
    }

    private fun callGemini(key: String, model: String, prompt: String, image: ByteArray): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 25000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
        }
        val partImage = JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP)))
        val parts = JSONArray().put(JSONObject().put("text", prompt)).put(partImage)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("responseMimeType", "application/json"))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use { it.readText() }; connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Gemini HTTP $code: ${text.take(220)}")
        val root = JSONObject(text)
        return root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun parseAction(text: String): JSONObject {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{'); val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("Resposta da IA não contém JSON válido: ${cleaned.take(150)}")
        return JSONObject(cleaned.substring(start, end + 1))
    }

    private fun awaitGesture(start: (((Boolean) -> Unit) -> Unit)) {
        val latch = java.util.concurrent.CountDownLatch(1)
        start { latch.countDown() }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    override fun onDestroy() { running.set(false); worker.shutdownNow(); super.onDestroy() }
}
