package com.example.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.data.model.ExtractedApk
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class LocalHttpServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var listeningPort = 8080
    private var sharedFiles: List<ExtractedApk> = emptyList()
    private var downloadCounter = 0

    fun getPort(): Int = listeningPort

    fun getDownloadCount(): Int = downloadCounter

    suspend fun startServer(files: List<ExtractedApk>): String = withContext(Dispatchers.IO) {
        stopServer()
        sharedFiles = files
        downloadCounter = 0

        var port = 8080
        var attempts = 0
        while (attempts < 10) {
            try {
                serverSocket = ServerSocket(port)
                listeningPort = port
                break
            } catch (e: Exception) {
                port = (8081..8099).random()
                attempts++
            }
        }

        val socket = serverSocket ?: throw IllegalStateException("Não foi possível iniciar o servidor local na porta $port")
        isRunning.set(true)

        Thread {
            while (isRunning.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    Thread {
                        handleClient(client)
                    }.start()
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }.start()

        val ip = getLocalIpAddress()
        "http://$ip:$listeningPort"
    }

    fun stopServer() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val path = parts[1]

                val decodedPath = URLDecoder.decode(path, "UTF-8")

                if (decodedPath == "/" || decodedPath.startsWith("/?")) {
                    sendHtmlResponse(output)
                } else if (decodedPath.startsWith("/download")) {
                    val fileName = decodedPath.substringAfter("file=", "").substringBefore("&")
                    val matchedFile = sharedFiles.find { it.fileName == fileName }
                        ?: sharedFiles.firstOrNull()

                    if (matchedFile != null && File(matchedFile.filePath).exists()) {
                        downloadCounter++
                        sendApkFile(output, File(matchedFile.filePath), matchedFile.fileName)
                    } else {
                        sendNotFound(output)
                    }
                } else {
                    sendNotFound(output)
                }
            }
        } catch (_: Exception) {
            // Client disconnected or aborted
        }
    }

    private fun sendHtmlResponse(output: OutputStream) {
        val html = buildHtmlPage()
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendApkFile(output: OutputStream, file: File, fileName: String) {
        val safeFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/vnd.android.package-archive\r\n" +
                "Content-Disposition: attachment; filename=\"$safeFileName\"; filename*=UTF-8''$safeFileName\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: close\r\n\r\n"

        output.write(headers.toByteArray(StandardCharsets.UTF_8))

        val buffer = ByteArray(64 * 1024)
        BufferedInputStream(FileInputStream(file)).use { bis ->
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
        output.flush()
    }

    private fun sendNotFound(output: OutputStream) {
        val body = "<h1>404 Não Encontrado</h1>"
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun buildHtmlPage(): String {
        val filesHtml = sharedFiles.joinToString("") { apk ->
            val sizeFormatted = formatFileSize(apk.fileSize)
            val downloadUrl = "/download?file=${URLEncoder.encode(apk.fileName, "UTF-8")}"
            """
            <div class="card">
                <div class="card-icon">📦</div>
                <div class="card-info">
                    <div class="app-title">${apk.appName}</div>
                    <div class="app-meta">v${apk.versionName} • ${apk.packageName}</div>
                    <div class="app-size">$sizeFormatted</div>
                </div>
                <a href="$downloadUrl" class="btn-download">
                    <span>Baixar APK</span>
                </a>
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Extractus Apk - Download Direto</title>
            <style>
                :root {
                    --primary: #38BDF8;
                    --primary-hover: #0284C7;
                    --bg: #090D16;
                    --card: #111827;
                    --text: #F9FAFB;
                    --subtext: #9CA3AF;
                    --border: #2E384D;
                    --badge: #0C4A6E;
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #10131A;
                        --card: #181C24;
                        --text: #E2E2E6;
                        --subtext: #8E94A4;
                        --border: #262E3D;
                        --badge: #1A2840;
                    }
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background-color: var(--bg); color: var(--text); padding: 24px 16px; display: flex; justify-content: center; }
                .container { width: 100%; max-width: 600px; }
                header { text-align: center; margin-bottom: 28px; }
                .logo { font-size: 42px; margin-bottom: 8px; }
                h1 { font-size: 24px; font-weight: 700; }
                p.subtitle { color: var(--subtext); font-size: 14px; margin-top: 4px; }
                .card {
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    padding: 20px;
                    margin-bottom: 16px;
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.04);
                }
                .card-icon {
                    font-size: 36px;
                    background: var(--badge);
                    width: 60px;
                    height: 60px;
                    border-radius: 14px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    flex-shrink: 0;
                }
                .card-info { flex: 1; min-width: 0; }
                .app-title { font-weight: 600; font-size: 17px; word-break: break-word; }
                .app-meta { font-size: 13px; color: var(--subtext); margin-top: 3px; word-break: break-all; }
                .app-size { font-size: 12px; font-weight: 600; color: var(--primary); margin-top: 4px; }
                .btn-download {
                    background: var(--primary);
                    color: #fff;
                    text-decoration: none;
                    padding: 12px 18px;
                    border-radius: 12px;
                    font-weight: 600;
                    font-size: 14px;
                    white-space: nowrap;
                    display: inline-block;
                    transition: background 0.2s;
                }
                .btn-download:hover { background: var(--primary-hover); }
                .instructions {
                    margin-top: 24px;
                    padding: 16px;
                    background: var(--card);
                    border: 1px solid var(--border);
                    border-radius: 12px;
                    font-size: 13px;
                    color: var(--subtext);
                    line-height: 1.5;
                }
                .instructions strong { color: var(--text); }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div class="logo">⚡</div>
                    <h1>Transferência Direta de APK</h1>
                    <p class="subtitle">Compartilhado diretamente do Android via Wi-Fi Local</p>
                </header>

                <div class="file-list">
                    $filesHtml
                </div>

                <div class="instructions">
                    <strong>Como instalar no Android:</strong><br>
                    1. Toque em "Baixar APK" para salvar o instalador.<br>
                    2. Abra o arquivo baixado e permita "Instalar de fontes desconhecidas" se solicitado.<br>
                    3. Conclua a instalação e aproveite!
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress
                            // Prefer standard local IP ranges (192.168.x.x, 10.x.x.x, 172.x.x.x)
                            if (hostAddress != null && (hostAddress.startsWith("192.168.") ||
                                        hostAddress.startsWith("10.") ||
                                        hostAddress.startsWith("172."))) {
                                return hostAddress
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }

        fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap? {
            return try {
                val hints = mapOf(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to "UTF-8"
                )
                val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)

                for (y in 0 until height) {
                    val offset = y * width
                    for (x in 0 until width) {
                        pixels[offset + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                    }
                }

                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                null
            }
        }
    }
}
