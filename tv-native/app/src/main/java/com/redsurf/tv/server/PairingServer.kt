package com.redsurf.tv.server

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PairingServer(
    port: Int,
    // name added (user request, 2026-09-12): with two-or-more playlists now able to coexist,
    // every one showing up as "Xtream Playlist" made them indistinguishable in the UI.
    private val onCredentialsReceived: (method: String, name: String, server: String, user: String, pass: String, m3u: String, contentType: String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri

        if (Method.GET == method) {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>RedSurf TV Pairing</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                        body { font-family: -apple-system, sans-serif; background: #09090b; color: white; padding: 20px; max-width: 500px; margin: 0 auto; }
                        h1 { color: #e11d48; text-align: center; }
                        .card { background: #18181b; padding: 20px; border-radius: 12px; margin-bottom: 20px; }
                        input, select, button { width: 100%; box-sizing: border-box; padding: 12px; margin-bottom: 12px; border-radius: 8px; border: 1px solid #27272a; background: #09090b; color: white; font-size: 16px; }
                        button { background: #e11d48; border: none; font-weight: bold; margin-top: 10px; cursor: pointer; }
                        label { display: block; margin-bottom: 5px; font-size: 12px; color: #a1a1aa; text-transform: uppercase; }
                    </style>
                </head>
                <body>
                    <h1>RedSurf</h1>
                    <p style="text-align: center; color: #a1a1aa;">Send credentials to your TV directly over your local network.</p>
                    
                    <div class="card">
                        <h3>Xtream Codes / Stalker</h3>
                        <form action="/submit" method="POST">
                            <input type="hidden" name="type" value="xtream">
                            <label>Playlist Name</label>
                            <input type="text" name="name" placeholder="e.g. My Provider" maxlength="60">
                            <label>Server URL</label>
                            <input type="text" name="server" placeholder="http://..." required>
                            <label>Username</label>
                            <input type="text" name="user" placeholder="Username (optional for Stalker)">
                            <label>Password</label>
                            <input type="password" name="pass" placeholder="Password (optional for Stalker)">
                            <label>Content Type</label>
                            <select name="contentType">
                                <option value="both">Both (Live & VOD)</option>
                                <option value="live">Live TV Only</option>
                                <option value="vod">VOD Only</option>
                            </select>
                            <button type="submit">Send to TV</button>
                        </form>
                    </div>

                    <div class="card">
                        <h3>M3U Playlist</h3>
                        <form action="/submit" method="POST">
                            <input type="hidden" name="type" value="m3u">
                            <label>Playlist Name</label>
                            <input type="text" name="name" placeholder="e.g. My Provider" maxlength="60">
                            <label>M3U URL</label>
                            <input type="text" name="m3u" placeholder="http://..." required>
                            <label>Content Type</label>
                            <select name="contentType">
                                <option value="both">Both (Live & VOD)</option>
                                <option value="live">Live TV Only</option>
                                <option value="vod">VOD Only</option>
                            </select>
                            <button type="submit">Send to TV</button>
                        </form>
                    </div>
                </body>
                </html>
            """.trimIndent()
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        } else if (Method.POST == method && uri == "/submit") {
            try {
                session.parseBody(HashMap())
                val params = session.parameters
                val type = params["type"]?.firstOrNull() ?: ""
                val name = params["name"]?.firstOrNull()?.trim() ?: ""
                val server = params["server"]?.firstOrNull() ?: ""
                val user = params["user"]?.firstOrNull() ?: ""
                val pass = params["pass"]?.firstOrNull() ?: ""
                val m3u = params["m3u"]?.firstOrNull() ?: ""
                val contentType = params["contentType"]?.firstOrNull() ?: "both"

                CoroutineScope(Dispatchers.Main).launch {
                    onCredentialsReceived(type, name, server, user, pass, m3u, contentType)
                }

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Success</title><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                    <body style="font-family: sans-serif; background: #09090b; color: white; text-align: center; padding: 50px;">
                        <h1 style="color: #10b981;">Success!</h1>
                        <p>Credentials sent. Look at your TV screen.</p>
                        <a href="/" style="color: #e11d48;">Send another</a>
                    </body>
                    </html>
                """.trimIndent()
                return newFixedLengthResponse(Response.Status.OK, "text/html", html)
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error parsing request")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}
