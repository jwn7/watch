package com.issr.watch.stomp

/**
 * STOMP 1.2 frame representation.
 * Spec: https://stomp.github.io/stomp-specification-1.2.html
 */
data class StompFrame(
    val command: String,
    val headers: Map<String, String>,
    val body: String = ""
)

/** STOMP CONNECT frame with heartbeat declaration (10s/10s per Phase 1 STOMP config). */
fun buildConnectFrame(heartbeatMs: Int = 10_000): String = buildString {
    append("CONNECT\n")
    append("accept-version:1.2\n")
    append("heart-beat:$heartbeatMs,$heartbeatMs\n")
    append("\n\u0000")
}

/** STOMP SEND frame. body must be valid JSON string (snake_case per Phase 1 contract). */
fun buildSendFrame(destination: String, body: String): String = buildString {
    append("SEND\n")
    append("destination:$destination\n")
    append("content-type:application/json\n")
    append("content-length:${body.toByteArray(Charsets.UTF_8).size}\n")
    append("\n")
    append(body)
    append("\u0000")
}

/** STOMP SUBSCRIBE frame. id must be unique per subscription (e.g. "sub-0"). */
fun buildSubscribeFrame(destination: String, id: String): String = buildString {
    append("SUBSCRIBE\n")
    append("destination:$destination\n")
    append("id:$id\n")
    append("\n\u0000")
}

/** STOMP DISCONNECT frame. */
fun buildDisconnectFrame(): String = buildString {
    append("DISCONNECT\n")
    append("\n\u0000")
}

/**
 * Parse a raw STOMP frame string into a StompFrame.
 * Returns null if the input is a heartbeat (\n only) or malformed.
 */
fun parseStompFrame(raw: String): StompFrame? {
    if (raw.isBlank()) return null
    val nullIndex = raw.indexOf('\u0000')
    val frameText = if (nullIndex >= 0) raw.substring(0, nullIndex) else raw
    val lines = frameText.split('\n')
    if (lines.isEmpty()) return null

    val command = lines[0].trim()
    if (command.isEmpty()) return null  // heartbeat frame

    val headers = mutableMapOf<String, String>()
    var bodyStartIndex = 1
    for (i in 1 until lines.size) {
        val line = lines[i]
        if (line.isEmpty()) {
            bodyStartIndex = i + 1
            break
        }
        val colonIndex = line.indexOf(':')
        if (colonIndex > 0) {
            headers[line.substring(0, colonIndex).trim()] = line.substring(colonIndex + 1).trim()
        }
    }
    val body = if (bodyStartIndex < lines.size) lines.subList(bodyStartIndex, lines.size).joinToString("\n") else ""
    return StompFrame(command, headers, body)
}
