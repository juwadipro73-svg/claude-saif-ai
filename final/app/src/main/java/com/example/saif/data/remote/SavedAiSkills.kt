/* Copyright (c) SAIF AI. All rights reserved. */
package com.example.saif.data.remote

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Per-account instruction documents. An import succeeds only after the actual file is fetched. */
data class SkillItemModel(val name: String, val description: String = "", val instructions: String = "",
    val githubUrl: String = "") { val sourceUrl get() = githubUrl }

object SavedAiSkills {
    private val state = MutableStateFlow<List<SkillItemModel>>(emptyList())
    val skills = state.asStateFlow()
    private var app: Context? = null
    private var owner: String? = null
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun storageKey(uid: String) = "skills_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun prefs(context: Context) = context.getSharedPreferences("native_skill_documents", Context.MODE_PRIVATE)

    @Synchronized fun bind(context: Context, uid: String?) {
        app = context.applicationContext
        owner = uid
        state.value = if (uid == null) emptyList() else decode(prefs(context).getString(storageKey(uid), "[]").orEmpty())
    }
    @Synchronized fun migrateAccount(context: Context, prev: String?, current: String?) {
        if (prev == null || current == null || prev == current) return
        val prefs = prefs(context)
        val old = prefs.getString(storageKey(prev), null) ?: return
        if (!prefs.contains(storageKey(current))) {
            if (prefs.edit().putString(storageKey(current), old).commit()) prefs.edit().remove(storageKey(prev)).apply()
        }
    }
    fun visibleSkills(): List<SkillItemModel> = state.value
    fun find(name: String): SkillItemModel? = state.value.find { it.name.equals(name.removePrefix("/"), true) }
    @Synchronized fun delete(skillName: String) {
        persist(state.value.filterNot { it.name == skillName })
    }
    private fun persist(items: List<SkillItemModel>) {
        val context = checkNotNull(app) { "Skill storage is not initialized" }
        val uid = checkNotNull(owner) { "Sign in to save skills" }
        val json = JSONArray()
        items.forEach { json.put(JSONObject().put("name", it.name).put("description", it.description)
            .put("instructions", it.instructions).put("url", it.githubUrl)) }
        check(prefs(context).edit().putString(storageKey(uid), json.toString()).commit()) { "Could not save skill" }
        state.value = items
    }
    private fun decode(raw: String): List<SkillItemModel> = runCatching {
        val list = JSONArray(raw)
        (0 until list.length()).map { i -> list.getJSONObject(i).let {
            SkillItemModel(it.getString("name"), it.optString("description"), it.getString("instructions"), it.optString("url"))
        } }
    }.getOrDefault(emptyList())

    suspend fun importFromGitHub(url: String, name: String): SkillItemModel = withContext(Dispatchers.IO) {
        val uid = synchronized(this@SavedAiSkills) { checkNotNull(owner) { "Sign in to import skills" } }
        val uri = url.trim().toHttpUrl()
        require(uri.scheme == "https" && uri.host == "github.com") { "Use an HTTPS github.com repository or file URL" }
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        require(segments.size >= 2) { "Provide a repository URL" }
        val repoOwner = segments[0]
        val repo = segments[1].removeSuffix(".git")
        val isFile = segments.getOrNull(2) == "blob"
        require(segments.size == 2 || (isFile && segments.size >= 5)) { "Use a repository URL or a /blob/branch/file URL" }
        val endpoint = "https://api.github.com/repos/$repoOwner/$repo/contents".toHttpUrl().newBuilder()
        if (isFile) {
            segments.drop(4).forEach { endpoint.addPathSegment(it) }
            endpoint.addQueryParameter("ref", segments[3])
        } else endpoint.addPathSegment("SKILL.md")
        val request = Request.Builder().url(endpoint.build()).header("Accept", "application/vnd.github+json").build()
        val document = http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub import failed (HTTP ${response.code}). Use a public SKILL.md file URL." }
            val body = checkNotNull(response.body)
            require(body.contentLength() <= 2_000_000) { "Skill file is too large" }
            val bytes = body.byteStream().use { input ->
                val data = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(data.size() + n <= 2_000_000) { "Skill file is too large" }
                    data.write(buffer, 0, n)
                }
                data.toByteArray()
            }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            require(json.optString("encoding") == "base64") { "GitHub did not return a text document" }
            String(Base64.decode(json.getString("content"), Base64.DEFAULT), Charsets.UTF_8)
        }
        require(document.isNotBlank() && document.length <= 1_000_000) { "Skill must be a non-empty text document under 1 MB" }
        val slug = name.trim().removePrefix("/").ifBlank { repo.lowercase() }
        require(slug.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Use 1–64 letters, digits, hyphens or underscores for the name" }
        val skill = SkillItemModel(slug, document.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160), document, url)
        synchronized(this@SavedAiSkills) {
            check(owner == uid) { "Account changed during import; retry" }
            persist(state.value.filterNot { it.name.equals(slug, true) } + skill)
        }
        skill
    }
}
