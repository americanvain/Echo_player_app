package com.echoplayer.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ServerException(message: String, val code: Int = 0) : IOException(message)

/**
 * 与服务器的全部交互都走这里。地址来自设置页；为空时所有调用抛 [ServerException]，
 * 上层据此显示"未配置服务器"的提示而不是崩溃。
 *
 * 已实现的端点是 speecheval 服务现有的 `/health` `/assess` `/articles`；
 * 其余是 Echo_player 流水线的契约（docs/SERVER_API.md），客户端先按契约写好。
 */
class EchoServerApi(private val baseUrlProvider: () -> String) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = baseUrlProvider().isNotBlank()

    private fun url(path: String): String {
        val base = baseUrlProvider().trim().trimEnd('/')
        if (base.isEmpty()) throw ServerException("还没有配置服务器地址，请到「设置」里填写")
        val withScheme = if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
        return withScheme + path
    }

    // ---- speecheval 现有端点 -------------------------------------------------

    suspend fun health(): HealthDto = get("/health")

    suspend fun articles(): List<ArticleDto> = get("/articles")

    suspend fun assess(wav: File, text: String): AssessResult = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", "recording.wav", wav.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("text", text)
            .build()
        val req = Request.Builder().url(url("/assess")).post(body).build()
        execute(req)
    }

    // ---- Echo_player 流水线（契约） ------------------------------------------

    suspend fun importMaterial(file: File, fileName: String, mime: String, title: String, language: String = "en"): ImportResponse =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, file.asRequestBody(mime.toMediaType()))
                .addFormDataPart("title", title)
                .addFormDataPart("language", language)
                .build()
            execute(Request.Builder().url(url("/materials/import")).post(body).build())
        }

    suspend fun material(remoteId: String): RemoteMaterial = get("/materials/$remoteId")

    suspend fun downloadAudio(remoteId: String, unitId: String, dest: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("/materials/$remoteId/audio/$unitId")).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ServerException("下载语音失败 (${resp.code})", resp.code)
            dest.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                ?: throw ServerException("语音内容为空")
        }
    }

    suspend fun translate(text: String): TranslateResponse = post("/translate", TranslateRequest(text))

    suspend fun explain(request: ExplainRequest): ExplainResponse = post("/issues/explain", request)

    // ---- helpers -------------------------------------------------------------

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path)).get().build())
    }

    private suspend inline fun <reified Req, reified T> post(path: String, body: Req): T = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(kotlinx.serialization.serializer<Req>(), body)
        val req = Request.Builder().url(url(path)).post(payload.toRequestBody("application/json".toMediaType())).build()
        execute(req)
    }

    private inline fun <reified T> execute(req: Request): T {
        val resp = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw ServerException("连不上服务器：${e.message ?: e.javaClass.simpleName}")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val detail = runCatching {
                    json.parseToJsonElement(text).let { el ->
                        (el as? kotlinx.serialization.json.JsonObject)?.get("detail")?.toString()?.trim('"')
                    }
                }.getOrNull()
                throw ServerException(detail ?: "服务器返回 ${it.code}", it.code)
            }
            return try {
                json.decodeFromString(kotlinx.serialization.serializer<T>(), text)
            } catch (e: Exception) {
                throw ServerException("解析服务器响应失败：${e.message}")
            }
        }
    }
}
