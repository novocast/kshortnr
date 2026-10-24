package com.example.shortener

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

fun Application.module(store: ShortLinkStore = ShortLinkStore.fromEnvironment()) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    val indexHtml = checkNotNull(environment.classLoader.getResource("index.html")) {
        "The index.html resource is missing"
    }.readText()

    routing {
        get("/") {
            call.respondText(indexHtml, ContentType.Text.Html)
        }

        get("/api/links") {
            call.respond(store.list())
        }

        post("/api/links") {
            val request = try {
                call.receive<CreateLinkRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Send a JSON object with a url field."))
                return@post
            }

            val targetUrl = normalizeHttpUrl(request.url)
            if (targetUrl == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Enter a valid http or https URL, up to 2048 characters."),
                )
                return@post
            }

            val link = store.create(targetUrl)
            val requestScheme = call.request.headers["X-Forwarded-Proto"]
                ?.substringBefore(',')
                ?.takeIf { it == "http" || it == "https" }
                ?: "http"
            val requestHost = call.request.headers["Host"] ?: call.request.host()
            val shortUrl = "$requestScheme://$requestHost/${link.code}"
            call.respond(
                HttpStatusCode.Created,
                CreateLinkResponse(link.code, shortUrl, link.targetUrl, link.createdAt),
            )
        }

        get("/{code}") {
            val code = call.parameters["code"]
            if (code == null || !code.matches(CODE_PATTERN)) {
                call.respond(HttpStatusCode.NotFound, "Short link not found")
                return@get
            }

            val targetUrl = store.incrementAndGetTarget(code)
            if (targetUrl == null) {
                call.respond(HttpStatusCode.NotFound, "Short link not found")
            } else {
                call.respondRedirect(targetUrl, permanent = false)
            }
        }
    }
}

private fun normalizeHttpUrl(input: String): String? {
    val value = input.trim()
    if (value.isEmpty() || value.length > 2048) return null

    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return null

    return uri.toASCIIString()
}

private val CODE_PATTERN = Regex("[A-Za-z0-9]{7}")

@Serializable
data class CreateLinkRequest(val url: String)

@Serializable
data class CreateLinkResponse(
    val code: String,
    val shortUrl: String,
    val targetUrl: String,
    val createdAt: String,
)

@Serializable
data class ErrorResponse(val error: String)