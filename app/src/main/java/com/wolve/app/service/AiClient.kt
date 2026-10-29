package com.wolve.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal REST client for Google Gemini and OpenAI. Used only when the user sends a message to the AI Coach. */
object AiClient {
    class Attach(val mime: String, val b64: String)
    class Result(val ok: Boolean, val text: String)

    const val GEMINI = "gemini"
    const val OPENAI = "openai"
    val GEMINI_PRESETS = listOf("gemini-2.5-flash", "gemini-2.5-pro")
    val OPENAI_PRESETS = listOf("gpt-4o", "gpt-4o-mini")

    private class Reply(val code: Int, val body: String)

    private fun call(url: String, method: String, headers: Map<String, String>, body: String?): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 90_000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return Reply(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun errorText(r: Reply): String {
        val msg = try { JSONObject(r.body).getJSONObject("error").optString("message") } catch (e: Exception) { "" }
        return "Request failed (${r.code}). ${if (msg.isNotEmpty()) msg else "Check your API key and model name."}"
    }

    suspend fun ask(provider: String, model: String, key: String, system: String, prompt: String, attach: Attach? = null): Result =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext Result(false, "Add your API key first (Settings on this screen).")
            if (model.isBlank()) return@withContext Result(false, "Choose or type a model name first.")
            try {
                if (provider == OPENAI) askOpenAi(model.trim(), key, system, prompt, attach) else askGemini(model.trim().removePrefix("models/"), key, system, prompt, attach)
            } catch (e: Exception) {
                Result(false, "Network error: ${e.message ?: "no connection"}")
            }
        }

    private fun askGemini(model: String, key: String, system: String, prompt: String, attach: Attach?): Result {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (attach != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", attach.mime).put("data", attach.b64)))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        val r = call(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            "POST", mapOf("x-goog-api-key" to key), body.toString(),
        )
        if (r.code !in 200..299) return Result(false, errorText(r))
        val out = StringBuilder()
        val ps = JSONObject(r.body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        for (i in 0 until ps.length()) out.append(ps.getJSONObject(i).optString("text"))
        return Result(true, out.toString().trim())
    }

    private fun askOpenAi(model: String, key: String, system: String, prompt: String, attach: Attach?): Result {
        val userContent: Any = if (attach == null) {
            prompt
        } else {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${attach.mime};base64,${attach.b64}")))
        }
        val body = JSONObject().put("model", model).put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", userContent)),
        )
        val r = call("https://api.openai.com/v1/chat/completions", "POST", mapOf("Authorization" to "Bearer $key"), body.toString())
        if (r.code !in 200..299) return Result(false, errorText(r))
        val text = JSONObject(r.body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        return Result(true, text.trim())
    }

    /** Models the key can use, so any current model can be picked. */
    suspend fun listModels(provider: String, key: String): List<String> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext emptyList<String>()
        try {
            if (provider == OPENAI) {
                val r = call("https://api.openai.com/v1/models", "GET", mapOf("Authorization" to "Bearer $key"), null)
                if (r.code !in 200..299) return@withContext emptyList<String>()
                val a = JSONObject(r.body).getJSONArray("data")
                (0 until a.length()).map { a.getJSONObject(it).getString("id") }
                    .filter { it.startsWith("gpt") || it.startsWith("o") || it.startsWith("chatgpt") }.sorted()
            } else {
                val r = call("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100", "GET", mapOf("x-goog-api-key" to key), null)
                if (r.code !in 200..299) return@withContext emptyList<String>()
                val a = JSONObject(r.body).getJSONArray("models")
                (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { m ->
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    }
                    .map { it.getString("name").removePrefix("models/") }.sorted()
            }
        } catch (e: Exception) {
            emptyList<String>()
        }
    }
}
