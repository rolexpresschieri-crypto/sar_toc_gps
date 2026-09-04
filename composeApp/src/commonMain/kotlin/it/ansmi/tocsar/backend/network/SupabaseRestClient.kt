package it.ansmi.tocsar.backend.network

import it.ansmi.tocsar.backend.TocSarConfig
import it.ansmi.tocsar.backend.TocSarException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.content.ByteArrayContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal class SupabaseRestClient(
    private val config: TocSarConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val http = createPlatformHttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun <T> getMaybeSingle(
        table: String,
        select: String,
        filters: List<Pair<String, String>>,
        deserializer: (String) -> T,
    ): T? {
        val response = http.get("${config.restBaseUrl}$table") {
            authHeaders()
            parameter("select", select)
            filters.forEach { (column, value) ->
                parameter(column, "eq.$value")
            }
            parameter("limit", "1")
            header("Accept", "application/vnd.pgrst.object+json")
        }

        if (response.status == HttpStatusCode.NotAcceptable) {
            return null
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        if (body.isBlank()) {
            return null
        }
        return deserializer(body)
    }

    suspend fun patch(
        table: String,
        filters: List<Pair<String, String>>,
        body: Any,
        isNullColumns: List<String> = emptyList(),
    ) {
        val response = http.patch("${config.restBaseUrl}$table") {
            authHeaders()
            preferMinimal()
            filters.forEach { (column, value) ->
                parameter(column, "eq.$value")
            }
            isNullColumns.forEach { column ->
                parameter(column, "is.null")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response)
    }

    suspend fun <T> insertReturning(
        table: String,
        body: Any,
        deserializer: (String) -> T,
    ): T {
        val response = http.post("${config.restBaseUrl}$table") {
            authHeaders()
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response)
        val payload = response.bodyAsText()
        val arrayStart = payload.indexOf('[')
        val arrayEnd = payload.lastIndexOf(']')
        val rowJson =
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                payload.substring(arrayStart + 1, arrayEnd).trim().trimEnd(',')
            } else {
                payload
            }
        if (rowJson.isBlank()) {
            throw TocSarException("Risposta insert vuota da $table")
        }
        return deserializer(rowJson)
    }

    suspend fun insert(
        table: String,
        body: Any,
    ) {
        val response = http.post("${config.restBaseUrl}$table") {
            authHeaders()
            preferMinimal()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response)
    }

    suspend fun uploadStorageObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        val encoded = objectPath.trim('/').split('/').joinToString("/") { segment ->
            segment.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
        val response = http.post("${config.storageObjectUrl}$bucket/$encoded") {
            authHeaders()
            header("x-upsert", "true")
            setBody(ByteArrayContent(bytes, ContentType.parse(contentType)))
        }
        ensureSuccess(response)
    }

    suspend fun downloadStorageObject(
        bucket: String,
        objectPath: String,
    ): String {
        val encoded = objectPath.trim('/').split('/').joinToString("/") { segment ->
            segment.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
        val response = http.get("${config.storageObjectUrl}$bucket/$encoded") {
            authHeaders()
        }
        ensureSuccess(response)
        return response.bodyAsText()
    }

    suspend fun <T> getList(
        table: String,
        select: String,
        eqFilters: List<Pair<String, String>> = emptyList(),
        order: String? = null,
        limit: Int? = null,
        deserializer: (String) -> List<T>,
    ): List<T> {
        val response = http.get("${config.restBaseUrl}$table") {
            authHeaders()
            parameter("select", select)
            eqFilters.forEach { (column, value) ->
                parameter(column, "eq.$value")
            }
            order?.let { parameter("order", it) }
            limit?.let { parameter("limit", it.toString()) }
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        if (body.isBlank() || body == "[]") {
            return emptyList()
        }
        return deserializer(body)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders() {
        header("apikey", config.supabaseAnonKey)
        header("Authorization", "Bearer ${config.supabaseAnonKey}")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.preferMinimal() {
        header("Prefer", "return=minimal")
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val detail = runCatching { response.bodyAsText() }.getOrDefault("")
            throw TocSarException(
                "Supabase HTTP ${response.status.value}${if (detail.isNotBlank()) ": $detail" else ""}",
            )
        }
    }
}
