package app.tplay.work

private val ERROR_NAME = Regex("\"error\"\\s*:\\s*\"([A-Za-z0-9_]+)\"")

/** Plain-words explanation of a Real-Debrid error. The site's own code is kept at the end for debugging. */
fun friendlyRdError(httpCode: Int, body: String?): String {
    val name = body?.let { ERROR_NAME.find(it)?.groupValues?.get(1) }
    val reason = when (name) {
        "infringing_file" ->
            "Real-Debrid blocked this file as copyrighted content, so it cannot be streamed. Try another release."
        "unknown_ressource" -> "Real-Debrid no longer has this torrent. It was removed or never added. Try another release."
        "bad_token", "bad_login" -> "Real-Debrid refused your token. Check it in Settings."
        "permission_denied", "account_locked" ->
            "Your Real-Debrid account is not allowed to do this. Check the account on real-debrid.com."
        "traffic_exhausted", "hoster_limit_reached" -> "Real-Debrid limit reached for now. Try again later."
        "hoster_unavailable" -> "The source host is unavailable at Real-Debrid. Try another release."
        "torrent_too_big" -> "This torrent is too big for Real-Debrid."
        "active_limit" -> "Too many active torrents at Real-Debrid. Remove some on real-debrid.com, then try again."
        "not_premium", "no_premium" -> "Real-Debrid says the account is not premium."
        else -> null
    }
    val tail = name ?: body?.take(80)?.replace(Regex("\\s+"), " ").orEmpty()
    return if (reason != null) "$reason ($httpCode $tail)" else "Real-Debrid error $httpCode: $tail"
}

/** Readable text for any Real-Debrid failure. */
fun rdFailureText(error: app.tplay.data.rd.RdError): String = when (error) {
    is app.tplay.data.rd.RdError.Http -> friendlyRdError(error.code, error.message)
    is app.tplay.data.rd.RdError.Network -> "Network problem: ${error.cause.message}"
    is app.tplay.data.rd.RdError.Api -> error.message
}
