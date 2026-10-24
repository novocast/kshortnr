package com.example.shortener

import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val result = runCli(args, ShortLinkStore.fromEnvironment(), System.out, shortUrlBase())
    if (result != 0) exitProcess(result)
}

internal fun runCli(
    args: Array<String>,
    store: ShortLinkStore,
    output: PrintStream,
    baseUrl: String,
): Int {
    if (args.isEmpty() || args[0] in setOf("help", "--help", "-h")) {
        printUsage(output)
        return 0
    }

    return when (args[0]) {
        "create" -> {
            if (args.size != 2) {
                output.println("Error: create requires exactly one URL.")
                printUsage(output)
                return 2
            }

            val targetUrl = normalizeHttpUrl(args[1])
            if (targetUrl == null) {
                output.println("Error: Enter a valid http or https URL, up to 2048 characters.")
                return 2
            }

            val link = store.create(targetUrl)
            output.println("Created: ${baseUrl.trimEnd('/')}/${link.code}")
            output.println("Destination: ${link.targetUrl}")
            0
        }

        "list" -> {
            if (args.size != 1) {
                output.println("Error: list does not accept arguments.")
                printUsage(output)
                return 2
            }

            val links = store.list()
            if (links.isEmpty()) {
                output.println("No short links found.")
            } else {
                output.println("SHORT URL\tCLICKS\tDESTINATION")
                links.forEach { link ->
                    output.println("${baseUrl.trimEnd('/')}/${link.code}\t${link.clicks}\t${link.targetUrl}")
                }
            }
            0
        }

        else -> {
            output.println("Error: Unknown command '${args[0]}'.")
            printUsage(output)
            2
        }
    }
}

private fun shortUrlBase(): String {
    System.getenv("SHORTENER_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    return "http://localhost:$port"
}

private fun printUsage(output: PrintStream) {
    output.println("Usage: shortener <command> [arguments]")
    output.println("  create <url>  Create a short link")
    output.println("  list          List links and click counts")
}