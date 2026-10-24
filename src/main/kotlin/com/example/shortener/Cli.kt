package com.example.shortener

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty() || args.first() in HELP_OPTIONS) {
        printUsage(System.out)
        return
    }

    val result = runCli(
        args,
        { ShortLinkStore.fromEnvironment() },
        System.out,
        shortUrlBase(),
        BufferedReader(InputStreamReader(System.`in`)),
        System.err,
    )
    if (result != 0) exitProcess(result)
}

internal fun runCli(
    args: Array<String>,
    store: ShortLinkStore,
    output: PrintStream,
    baseUrl: String,
    input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    prompt: PrintStream = System.err,
): Int = runCli(args, { store }, output, baseUrl, input, prompt)

internal fun runCli(
    args: Array<String>,
    storeProvider: () -> ShortLinkStore,
    output: PrintStream,
    baseUrl: String,
    input: BufferedReader,
    prompt: PrintStream,
): Int {
    val jsonOutput = args.contains("--json")
    val commandArgs = args.filterNot { it == "--json" }
    if (commandArgs.isEmpty() || commandArgs[0] in HELP_OPTIONS) {
        printUsage(output)
        return 0
    }

    return when (commandArgs[0]) {
        "create" -> {
            if (commandArgs.size != 2) {
                return fail(output, jsonOutput, "invalid_arguments", "create requires exactly one URL.", true)
            }

            val targetUrl = normalizeHttpUrl(commandArgs[1])
            if (targetUrl == null) {
                return fail(
                    output,
                    jsonOutput,
                    "invalid_url",
                    "Enter a valid http or https URL, up to 2048 characters.",
                )
            }

            val link = storeProvider().create(targetUrl)
            if (jsonOutput) {
                output.println(CLI_JSON.encodeToString(link.toCliLink(baseUrl)))
            } else {
                output.println("Created: ${baseUrl.trimEnd('/')}/${link.code}")
                output.println("Destination: ${link.targetUrl}")
            }
            0
        }

        "list" -> {
            if (commandArgs.size != 1) {
                return fail(output, jsonOutput, "invalid_arguments", "list does not accept arguments.", true)
            }

            val links = storeProvider().list()
            if (jsonOutput) {
                output.println(CLI_JSON.encodeToString(links.map { it.toCliLink(baseUrl) }))
            } else if (links.isEmpty()) {
                output.println("No short links found.")
            } else {
                output.println("SHORT URL\tCLICKS\tDESTINATION")
                links.forEach { link ->
                    output.println("${baseUrl.trimEnd('/')}/${link.code}\t${link.clicks}\t${link.targetUrl}")
                }
            }
            0
        }

        "show" -> {
            if (commandArgs.size != 2) {
                return fail(output, jsonOutput, "invalid_arguments", "show requires exactly one short code.", true)
            }

            val link = storeProvider().find(commandArgs[1])
            if (link == null) {
                return fail(output, jsonOutput, "not_found", "No short link found for code '${commandArgs[1]}'.", status = 1)
            }

            if (jsonOutput) {
                output.println(CLI_JSON.encodeToString(link.toCliLink(baseUrl)))
            } else {
                output.println("Short URL: ${baseUrl.trimEnd('/')}/${link.code}")
                output.println("Destination: ${link.targetUrl}")
                output.println("Created: ${link.createdAt}")
                output.println("Clicks: ${link.clicks}")
            }
            0
        }

        "delete" -> {
            val deleteArgs = commandArgs.drop(1)
            val confirmedByFlag = deleteArgs.contains("--yes")
            val codes = deleteArgs.filterNot { it == "--yes" }
            if (codes.size != 1 || deleteArgs.any { it.startsWith("--") && it != "--yes" }) {
                return fail(output, jsonOutput, "invalid_arguments", "delete requires one short code and optionally --yes.", true)
            }

            val code = codes.single()
            val store = storeProvider()
            val link = store.find(code)
            if (link == null) {
                return reportDelete(output, jsonOutput, CliDeleteResult(false, code, "not_found"), status = 1)
            }

            if (!confirmedByFlag) {
                prompt.print("Delete ${link.code} -> ${link.targetUrl}? [y/N] ")
                prompt.flush()
                if (!input.readLine().equals("y", ignoreCase = true)) {
                    return reportDelete(output, jsonOutput, CliDeleteResult(false, code, "cancelled"), status = 1)
                }
            }

            val deleted = store.delete(code)
            val result = CliDeleteResult(deleted, code, if (deleted) null else "not_found")
            reportDelete(output, jsonOutput, result, status = if (deleted) 0 else 1)
        }

        "stats" -> {
            if (commandArgs.size != 1) {
                return fail(output, jsonOutput, "invalid_arguments", "stats does not accept arguments.", true)
            }

            val links = storeProvider().list()
            val stats = CliStats(
                linkCount = links.size,
                totalClicks = links.sumOf { it.clicks },
                topLinks = links.asSequence()
                    .filter { it.clicks > 0 }
                    .sortedWith(compareByDescending<ShortLink> { it.clicks }.thenByDescending { it.createdAt })
                    .take(5)
                    .map { it.toCliLink(baseUrl) }
                    .toList(),
            )
            if (jsonOutput) {
                output.println(CLI_JSON.encodeToString(stats))
            } else {
                output.println("Links: ${stats.linkCount}")
                output.println("Total clicks: ${stats.totalClicks}")
                output.println("Most-clicked links:")
                if (stats.topLinks.isEmpty()) {
                    output.println("No clicks yet.")
                } else {
                    stats.topLinks.forEach { link ->
                        output.println("${link.shortUrl}\t${link.clicks}\t${link.targetUrl}")
                    }
                }
            }
            0
        }

        "easter-egg" -> {
            if (commandArgs.size != 1) {
                return fail(output, jsonOutput, "invalid_arguments", "easter-egg does not accept arguments.", true)
            }
            output.println("Long URLs have been shortened. Their stories remain.")
            0
        }

        "forecast" -> {
            if (commandArgs.size != 1) {
                return fail(output, jsonOutput, "invalid_arguments", "forecast does not accept arguments.", true)
            }
            output.println(RICKROLL_URL)
            0
        }

        else -> {
            fail(output, jsonOutput, "unknown_command", "Unknown command '${commandArgs[0]}'.", true)
        }
    }
}

private fun fail(
    output: PrintStream,
    jsonOutput: Boolean,
    code: String,
    message: String,
    showUsage: Boolean = false,
    status: Int = 2,
): Int {
    if (jsonOutput) {
        output.println(CLI_JSON.encodeToString(CliError(code, message)))
    } else {
        output.println("Error: $message")
        if (showUsage) printUsage(output)
    }
    return status
}

private fun reportDelete(output: PrintStream, jsonOutput: Boolean, result: CliDeleteResult, status: Int): Int {
    if (jsonOutput) {
        output.println(CLI_JSON.encodeToString(result))
    } else if (result.deleted) {
        output.println("Deleted ${result.code}.")
    } else {
        output.println(if (result.reason == "cancelled") "Deletion cancelled." else "No short link found for code '${result.code}'.")
    }
    return status
}

private fun ShortLink.toCliLink(baseUrl: String) = CliLink(
    code = code,
    shortUrl = "${baseUrl.trimEnd('/')}/$code",
    targetUrl = targetUrl,
    createdAt = createdAt,
    clicks = clicks,
)

private fun shortUrlBase(): String {
    System.getenv("SHORTENER_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    return "http://localhost:$port"
}

private fun printUsage(output: PrintStream) {
    output.println("Usage: shortener <command> [arguments]")
    output.println("  create <url>          Create a short link")
    output.println("  list                  List links and click counts")
    output.println("  show <code>           Show link details")
    output.println("  delete <code> [--yes] Delete a link (prompts unless --yes is given)")
    output.println("  stats                 Show totals and most-clicked links")
    output.println("  easter-egg            Print a short-link proverb")
    output.println("  forecast              Check the weather")
    output.println("Add --json to create, list, show, delete, or stats for JSON output")
}

@Serializable
internal data class CliLink(
    val code: String,
    val shortUrl: String,
    val targetUrl: String,
    val createdAt: String,
    val clicks: Int,
)

@Serializable
internal data class CliStats(
    val linkCount: Int,
    val totalClicks: Int,
    val topLinks: List<CliLink>,
)

@Serializable
internal data class CliDeleteResult(val deleted: Boolean, val code: String, val reason: String? = null)

@Serializable
private data class CliError(val error: String, val message: String)

private const val RICKROLL_URL = "https://youtu.be/dQw4w9WgXcQ"
private val CLI_JSON = Json { prettyPrint = true }
private val HELP_OPTIONS = setOf("help", "--help", "-h")