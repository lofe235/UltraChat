package com.ultrasys.ultrachat.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AdminServerInfo(
    val version: String = "",
    val uptime: Long = 0L,
    val apk: Boolean = false,
    val serverTime: Long = 0L,
)

data class AdminStats(
    val users: Int = 0,
    val messages: Int = 0,
    val online: Int = 0,
    val lobbyMessages: Int = 0,
)

data class AdminUser(
    val id: String,
    val nickname: String,
    val displayName: String,
    val deviceId: String,
    val lastIp: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val isCreator: Boolean,
    val model: String,
    val online: Boolean,
    val deviceBanned: Boolean,
    val ipBanned: Boolean,
)

data class AdminBan(
    val value: String,
    val reason: String,
    val createdAt: Long,
)

data class AdminBans(
    val devices: List<AdminBan> = emptyList(),
    val ips: List<AdminBan> = emptyList(),
) {
    val total: Int get() = devices.size + ips.size
}

data class AdminNotice(
    val id: String,
    val title: String,
    val text: String,
    val version: String,
    val url: String,
    val createdAt: Long,
)

data class AdminState(
    val server: AdminServerInfo = AdminServerInfo(),
    val stats: AdminStats = AdminStats(),
    val users: List<AdminUser> = emptyList(),
    val bans: AdminBans = AdminBans(),
    val notice: AdminNotice? = null,
)

data class AdminSession(
    val token: String,
    val expiresAt: Long,
)

/**
 * HTTP-клиент скрытой админ-панели. Токен живёт только в памяти ViewModel
 * и никогда не сохраняется на диск.
 */
class AdminApi(private val baseUrlProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun login(password: String): AdminSession = withContext(Dispatchers.IO) {
        val data = request(
            path = "/api/admin/login",
            method = "POST",
            token = null,
            body = JSONObject().put("password", password),
        )
        val token = data.optString("token")
        if (token.isBlank()) throw IOException("Сервер не выдал токен администратора")
        AdminSession(
            token = token,
            expiresAt = data.optLong("expiresAt", 0L),
        )
    }

    suspend fun state(token: String): AdminState = withContext(Dispatchers.IO) {
        parseState(
            request(
                path = "/api/admin/state",
                method = "GET",
                token = token,
                body = null,
            ),
        )
    }

    suspend fun clearLobby(token: String): Int = withContext(Dispatchers.IO) {
        request(
            path = "/api/admin/lobby/clear",
            method = "POST",
            token = token,
            body = JSONObject(),
        ).optInt("removed", 0)
    }

    suspend fun ban(
        token: String,
        kind: String,
        value: String,
        reason: String,
    ): Unit = withContext(Dispatchers.IO) {
        request(
            path = "/api/admin/ban",
            method = "POST",
            token = token,
            body = JSONObject()
                .put("kind", kind)
                .put("value", value)
                .put("reason", reason),
        )
        Unit
    }

    suspend fun unban(token: String, kind: String, value: String): Unit =
        withContext(Dispatchers.IO) {
            request(
                path = "/api/admin/unban",
                method = "POST",
                token = token,
                body = JSONObject().put("kind", kind).put("value", value),
            )
            Unit
        }

    suspend fun kick(token: String, userId: String): Int = withContext(Dispatchers.IO) {
        request(
            path = "/api/admin/kick",
            method = "POST",
            token = token,
            body = JSONObject().put("userId", userId),
        ).optInt("kicked", 0)
    }

    suspend fun deleteUser(token: String, userId: String): Int = withContext(Dispatchers.IO) {
        request(
            path = "/api/admin/delete-user",
            method = "POST",
            token = token,
            body = JSONObject().put("userId", userId),
        ).optInt("removedMessages", 0)
    }

    suspend fun sendNotice(
        token: String,
        title: String,
        text: String,
        version: String,
        url: String,
    ): AdminNotice = withContext(Dispatchers.IO) {
        val data = request(
            path = "/api/admin/notice",
            method = "POST",
            token = token,
            body = JSONObject()
                .put("title", title)
                .put("text", text)
                .put("version", version)
                .put("url", url),
        )
        parseNotice(data.optJSONObject("notice"))
            ?: throw IOException("Сервер не вернул уведомление")
    }

    suspend fun clearNotice(token: String): Unit = withContext(Dispatchers.IO) {
        request(
            path = "/api/admin/notice/clear",
            method = "POST",
            token = token,
            body = JSONObject(),
        )
        Unit
    }

    private fun request(
        path: String,
        method: String,
        token: String?,
        body: JSONObject?,
    ): JSONObject {
        val base = baseUrlProvider().trim().trimEnd('/')
        if (base.isBlank()) throw IOException("Не указан адрес сервера")
        val url = "$base$path"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (method == "POST") {
            builder.post((body ?: JSONObject()).toString().toRequestBody(JSON))
        } else {
            builder.get()
        }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (err: Exception) {
            throw IOException(err.message ?: "Нет связи с сервером")
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw IOException("Сервер вернул непонятный ответ (${response.code})")
            }
            if (!it.isSuccessful || !json.optBoolean("ok", false)) {
                throw IOException(adminError(it.code, json))
            }
            return json
        }
    }

    private fun adminError(code: Int, json: JSONObject): String {
        val serverMessage = json.optString("message").trim()
        return when (val error = json.optString("error").trim()) {
            "bad_password" -> "Неверный пароль"
            "admin_disabled" -> "Пароль администратора не настроен на сервере"
            "unauthorized" -> "Сессия истекла. Войдите снова."
            "rate_limited" -> "Слишком много попыток. Подождите минуту."
            "value_required" -> "Укажите значение для бана"
            "bad_ip" -> "Некорректный IP-адрес"
            "bad_device" -> "Слишком короткий ID устройства"
            "user_required" -> "Не указан пользователь"
            "unknown_user" -> "Пользователь не найден"
            "forbidden" -> "Действие запрещено сервером"
            else -> serverMessage.ifBlank {
                if (error.isNotBlank()) error else "Сервер ответил $code"
            }
        }
    }

    private fun parseState(json: JSONObject): AdminState {
        val server = json.optJSONObject("server")
        val stats = json.optJSONObject("stats")
        val users = json.optJSONArray("users")
        val bans = json.optJSONObject("bans")
        return AdminState(
            server = AdminServerInfo(
                version = server?.optString("version").orEmpty(),
                uptime = server?.optLong("uptime", 0L) ?: 0L,
                apk = server?.optBoolean("apk", false) ?: false,
                serverTime = server?.optLong("serverTime", 0L) ?: 0L,
            ),
            stats = AdminStats(
                users = stats?.optInt("users", 0) ?: 0,
                messages = stats?.optInt("messages", 0) ?: 0,
                online = stats?.optInt("online", 0) ?: 0,
                lobbyMessages = stats?.optInt("lobbyMessages", 0) ?: 0,
            ),
            users = parseUsers(users),
            bans = AdminBans(
                devices = parseBans(bans?.optJSONArray("devices")),
                ips = parseBans(bans?.optJSONArray("ips")),
            ),
            notice = parseNotice(json.optJSONObject("notice")),
        )
    }

    private fun parseUsers(array: JSONArray?): List<AdminUser> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), 500)) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    AdminUser(
                        id = id,
                        nickname = item.optString("nickname"),
                        displayName = item.optString("displayName")
                            .ifBlank { item.optString("nickname") },
                        deviceId = item.optString("deviceId"),
                        lastIp = item.optString("lastIp"),
                        createdAt = item.optLong("createdAt", 0L),
                        lastSeenAt = item.optLong("lastSeenAt", 0L),
                        isCreator = item.optBoolean("creator", false),
                        model = item.optString("model"),
                        online = item.optBoolean("online", false),
                        deviceBanned = item.optBoolean("deviceBanned", false),
                        ipBanned = item.optBoolean("ipBanned", false),
                    ),
                )
            }
        }
    }

    private fun parseBans(array: JSONArray?): List<AdminBan> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), 500)) {
                val item = array.optJSONObject(index) ?: continue
                val value = item.optString("value")
                if (value.isBlank()) continue
                add(
                    AdminBan(
                        value = value,
                        reason = item.optString("reason"),
                        createdAt = item.optLong("createdAt", 0L),
                    ),
                )
            }
        }
    }

    private fun parseNotice(json: JSONObject?): AdminNotice? {
        if (json == null) return null
        val id = json.optString("id")
        val title = json.optString("title")
        val text = json.optString("text")
        if (id.isBlank() || title.isBlank() && text.isBlank()) return null
        return AdminNotice(
            id = id,
            title = title.ifBlank { "Обновление UltraChat" },
            text = text,
            version = json.optString("version"),
            url = json.optString("url"),
            createdAt = json.optLong("createdAt", 0L),
        )
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
