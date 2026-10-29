package com.wolve.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolve.app.analytics.Nutrition
import com.wolve.app.data.StrainTracker
import com.wolve.app.service.AiClient
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val LAB_PROMPT =
    "The attached file is my lab report. Read it carefully. List every marker you can read with its value, unit, reference range and a status " +
        "(in range, borderline, low or high). Then explain in plain language what the notable results mean for training, recovery, energy and nutrition, " +
        "and what to discuss with my doctor. Do not diagnose. If the file is unreadable, say so."

private fun prepareImage(ctx: Context, uri: Uri): ByteArray? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val k = 1600f / max(bmp.width, bmp.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return bos.toByteArray()
    } catch (e: Throwable) {
        return null
    }
}

@Composable
fun AiCoachScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val models by vm.aiModels.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val strain by StrainTracker.strain.collectAsStateWithLifecycle()

    var provider by remember { mutableStateOf(vm.aiProvider()) }
    var model by remember { mutableStateOf(vm.aiModel()) }
    var key by remember { mutableStateOf(vm.aiKey(vm.aiProvider())) }
    var showConfig by remember { mutableStateOf(vm.aiKey(vm.aiProvider()).isBlank()) }
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val plan = Nutrition.plan(profile.age, profile.weightKg, profile.heightCm, profile.male, strain)
    val presets = if (provider == AiClient.OPENAI) AiClient.OPENAI_PRESETS else AiClient.GEMINI_PRESETS

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            if (type == "application/pdf" && provider == AiClient.OPENAI) {
                note = "ChatGPT cannot read PDFs here. Attach a photo of the report, or switch to Gemini."
            } else {
                val bytes: ByteArray? = if (type == "application/pdf") {
                    try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
                } else {
                    prepareImage(ctx, uri)
                }
                if (bytes == null || bytes.isEmpty()) {
                    note = "Could not read that file."
                } else if (bytes.size > 14_000_000) {
                    note = "That file is too large. Use a smaller PDF or a photo."
                } else {
                    note = ""
                    val mime = if (type == "application/pdf") "application/pdf" else "image/jpeg"
                    vm.askAi(LAB_PROMPT, "Lab report attached.", AiClient.Attach(mime, Base64.encodeToString(bytes, Base64.NO_WRAP)))
                }
            }
        }
    }

    Page {
        Title("Wolve AI Coach", "Daily briefing, training, diet and lab reports")

        PCard {
            Text("Nutrition targets", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "From your age ${profile.age}, weight ${profile.weightKg.toInt()} kg, height ${profile.heightCm.toInt()} cm and today's strain.",
                color = Pl.sub, fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Fact("Resting need", "${plan.bmr} kcal")
                Fact("Daily target", "${plan.tdee} kcal")
                Fact("Water", "${plan.waterMl} ml")
            }
            Spacer(Modifier.height(8.dp))
            plan.meals.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m.name, color = Pl.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${m.kcal} kcal   P ${m.proteinG}  C ${m.carbG}  F ${m.fatG} g", color = Pl.sub, fontSize = 13.sp)
                }
            }
        }

        PCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ask the coach", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (showConfig) "Hide settings" else "AI settings", color = Pl.green, fontSize = 13.sp,
                    modifier = Modifier.clickable { showConfig = !showConfig })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Daily briefing", false) {
                    vm.askAi("Give me today's briefing: how recovered I am, my target strain, and a short training plan for today (type, duration, heart-rate zones).", "Daily briefing")
                }
                Pill("Training plan", false) {
                    vm.askAi("Create a training plan for today and a simple 3-day outlook based on my recovery and recent workouts.", "Training plan")
                }
                Pill("3-meal diet", false) {
                    vm.askAi("Create a daily diet plan with exactly three meals (breakfast, lunch, dinner) matching my calorie and macro targets for my age and weight. For each meal list foods with portions, calories and macros.", "3-meal diet plan")
                }
                Pill("Lab report", false) { picker.launch(arrayOf("image/*", "application/pdf")) }
            }
            if (note.isNotEmpty()) Text(note, color = Pl.amber, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (showConfig) {
            PCard {
                Text("AI settings", color = Pl.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Gemini", provider == AiClient.GEMINI) {
                        provider = AiClient.GEMINI
                        key = vm.aiKey(provider)
                        model = ""
                    }
                    Pill("ChatGPT", provider == AiClient.OPENAI) {
                        provider = AiClient.OPENAI
                        key = vm.aiKey(provider)
                        model = ""
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    key, { key = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p -> Pill(p, model == p) { model = p } }
                }
                if (models.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Models available to your key", color = Pl.sub, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        models.take(60).forEach { p -> Pill(p, model == p) { model = p } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.loadModels(provider, key) }, modifier = Modifier.weight(1f)) { Text("Load my models") }
                    Button(
                        onClick = {
                            vm.saveAi(provider, model, key)
                            showConfig = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                    ) { Text("Save", fontWeight = FontWeight.Bold) }
                }
                Text(
                    "Your key is stored only on this phone and is never included in backups. Messages go straight to ${if (provider == AiClient.OPENAI) "OpenAI" else "Google"} over HTTPS, " +
                        "together with a short summary of your numbers. Usage is billed by the provider. You can type any model name your key allows.",
                    color = Pl.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        chat.forEach { msg ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (msg.fromUser) Pl.card2 else Pl.card).padding(14.dp),
            ) {
                Text(if (msg.fromUser) "You" else "Wolve AI", color = if (msg.fromUser) Pl.sub else Pl.green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(msg.text, color = Pl.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (busy) Text("Thinking...", color = Pl.sub, fontSize = 13.sp)

        PCard {
            OutlinedTextField(input, { input = it }, label = { Text("Ask anything about your training, sleep or food") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val q = input.trim()
                        if (q.isNotEmpty()) {
                            vm.askAi(q)
                            input = ""
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Pl.accent, contentColor = Pl.bg),
                ) { Text("Send", fontWeight = FontWeight.Bold) }
                if (chat.isNotEmpty()) OutlinedButton(onClick = { vm.clearChat() }, modifier = Modifier.weight(1f)) { Text("Clear chat") }
            }
            Text("AI answers are guidance, not medical advice.", color = Pl.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
