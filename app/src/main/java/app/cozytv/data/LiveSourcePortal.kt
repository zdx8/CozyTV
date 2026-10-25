package app.cozytv.data

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A tiny LAN-only HTTP endpoint shown behind a QR code on the live source screen.
 * It lets a phone paste one or more playlist URLs and saves them straight into the launcher.
 */
class LiveSourcePortal(
    private val urlsProvider: () -> List<String>,
    private val onSave: (List<String>) -> Boolean,
) {
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null
    private var boundPort = DEFAULT_PORT

    fun start(): String? {
        if (running.get()) return address()
        val socket = bind() ?: return null
        serverSocket = socket
        boundPort = socket.localPort
        running.set(true)
        worker = Thread({
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { handle(client) }
                runCatching { client.close() }
            }
        }, "cozytv-live-portal").apply {
            isDaemon = true
            start()
        }
        return address()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.interrupt()
        worker = null
    }

    private fun bind(): ServerSocket? {
        for (port in DEFAULT_PORT until DEFAULT_PORT + 10) {
            runCatching {
                return ServerSocket(port).apply {
                    reuseAddress = true
                }
            }
        }
        return null
    }

    private fun address(): String? {
        val host = localIpv4() ?: return null
        return "http://$host:$boundPort/"
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore('?')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
        }
        val body = if (method == "POST") {
            readBody(input, headers["content-length"]?.toIntOrNull() ?: 0)
        } else {
            ""
        }
        when {
            method == "GET" && path == "/" -> respond(client, 200, formPage(urlsProvider()))
            method == "POST" && path == "/save" -> {
                val raw = parseForm(body)["urls"].orEmpty()
                val urls = raw.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList()
                if (saveOnMain(urls)) respond(client, 200, successPage(urls.size))
                else respond(client, 400, errorPage())
            }
            else -> respond(client, 404, notFoundPage())
        }
    }

    private fun saveOnMain(urls: List<String>): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return onSave(urls)
        val latch = CountDownLatch(1)
        var result = false
        val posted = mainHandler.post {
            result = runCatching { onSave(urls) }.getOrDefault(false)
            latch.countDown()
        }
        if (!posted) return false
        latch.await(4, TimeUnit.SECONDS)
        return result
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(byte.toChar())
        }
    }

    private fun respond(client: Socket, status: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            else -> "Not Found"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        val output: OutputStream = client.getOutputStream()
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun parseForm(body: String): Map<String, String> = body
        .split('&')
        .mapNotNull { pair ->
            val split = pair.indexOf('=')
            if (split < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, split), "UTF-8")
            val value = URLDecoder.decode(pair.substring(split + 1), "UTF-8")
            key to value
        }
        .toMap()

    private fun formPage(urls: List<String>): String = page(
        title = "CozyTV 直播源",
        content = """
            <p class="hint">每行一个 M3U / M3U8 地址。按顺序尝试，当前源不可用时会自动切换到下一个。</p>
            <form method="post" action="/save">
            <textarea id="urls" name="urls" rows="9" spellcheck="false" autofocus inputmode="url" placeholder="https://example.com/list1.m3u8&#10;https://example.com/list2.m3u8">${escape(urls.joinToString("\n"))}</textarea>
              <button type="submit">保存到电视</button>
            </form>
            <script>
              const field = document.getElementById('urls');
              if (field) {
                field.focus();
                field.setSelectionRange(field.value.length, field.value.length);
              }
            </script>
            <p class="hint">留空并保存可恢复内置演示源。只添加你有权播放的内容。</p>
        """.trimIndent(),
    )

    private fun successPage(count: Int): String = page(
        title = "已保存",
        content = """
            <h2>已保存 $count 个直播源</h2>
            <p>电视会自动尝试这些地址，当前源不可用时切换到下一个。</p>
            <p><a href="/">继续编辑</a></p>
        """.trimIndent(),
    )

    private fun errorPage(): String = page(
        title = "保存失败",
        content = """
            <h2>地址格式不正确</h2>
            <p>每个直播源都必须以 <code>http://</code> 或 <code>https://</code> 开头。</p>
            <p><a href="/">返回修改</a></p>
        """.trimIndent(),
    )

    private fun notFoundPage(): String =
        page("未找到", "<p>页面不存在。</p><p><a href=\"/\">返回</a></p>")

    private fun page(title: String, content: String): String = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>$title</title>
          <style>
            :root { color-scheme: dark; }
            * { box-sizing: border-box; }
            body { margin: 0; min-height: 100vh; background: #0c1120; color: #f4f6fb;
                   font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
            main { max-width: 640px; margin: 0 auto; padding: 28px 18px 48px; }
            h1 { font-size: 26px; margin: 0 0 6px; }
            h2 { font-size: 20px; }
            .hint { color: #aab2c5; font-size: 14px; line-height: 1.6; }
            textarea { width: 100%; min-height: 240px; margin: 12px 0; padding: 14px;
                       border-radius: 14px; border: 1px solid #3a4560; background: #151c30;
                       color: #fff; font: 14px/1.6 ui-monospace, Menlo, monospace; }
            button { width: 100%; padding: 15px; border: 0; border-radius: 14px;
                     background: #fff; color: #0c1120; font-size: 16px; font-weight: 700; }
            a { color: #9ec1ff; }
            code { background: #202944; padding: 2px 6px; border-radius: 6px; }
          </style>
        </head>
        <body><main><h1>$title</h1>$content</main></body>
        </html>
    """.trimIndent()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val DEFAULT_PORT = 8765

        fun localIpv4(): String? = runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            Collections.list(interfaces)
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy {
                    if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1
                }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
