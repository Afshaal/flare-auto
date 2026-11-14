package app.flare.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only this repository's .zip release assets linked from the README become downloadable packs. */
class MapPackDownloadTest {
    @Test
    fun readmeLinksBecomePacks() {
        val readme = """
            - [Lahore map pack (r7)](https://github.com/Afshaal/flare-auto/releases/download/v1.2.1/FlareAuto-Lahore-pack-r7.zip)
            - [Kuwait map pack (r1)](https://github.com/Afshaal/flare-auto/releases/download/v1.2.1/FlareAuto-Kuwait-pack-r1.zip)
            [App](https://github.com/Afshaal/flare-auto/releases/download/v1.2.2/FlareAuto-v1.2.2.apk)
            [Elsewhere](https://example.com/Afshaal/flare-auto/releases/download/x/pack.zip)
            [Other repo](https://github.com/someone/else/releases/download/x/pack.zip)
            [Plain http](http://github.com/Afshaal/flare-auto/releases/download/x/pack.zip)
            [Lahore again](https://github.com/Afshaal/flare-auto/releases/download/v1.2.1/FlareAuto-Lahore-pack-r7.zip)
        """.trimIndent()
        val links = MapPackDownload.links(readme)
        assertEquals(listOf("Lahore map pack (r7)", "Kuwait map pack (r1)"), links.map { it.name })
    }

    /** Local server with HTTP Range support: [data] in full (200) or from an offset (206), with a fixed ETag. */
    private class Server(val data: ByteArray, val ranges: Boolean) : Thread() {
        val socket = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        init { isDaemon = true; start() }
        override fun run() {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: Exception) { return }
                client.use { c ->
                    val head = c.getInputStream().bufferedReader().let { r -> generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.toList() }
                    val from = head.firstNotNullOfOrNull { Regex("""(?i)^Range: bytes=(\d+)-""").find(it)?.groupValues?.get(1)?.toInt() }
                    val out = c.getOutputStream()
                    val partial = ranges && from != null
                    val start = if (partial) from!! else 0
                    val headers = (if (partial) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-${data.size - 1}/${data.size}\r\n" else "HTTP/1.1 200 OK\r\n") +
                        "ETag: \"v1\"\r\nContent-Length: ${data.size - start}\r\nConnection: close\r\n\r\n"
                    out.write(headers.toByteArray()); out.write(data, start, data.size - start); out.flush()
                }
            }
        }
    }

    private fun fetch(data: ByteArray, partial: Int, ranges: Boolean): ByteArray {
        val server = Server(data, ranges)
        try {
            val dir = java.nio.file.Files.createTempDirectory("pack").toFile()
            val url = "http://127.0.0.1:${server.socket.localPort}/pack.zip"
            val part = java.io.File(dir, "p.part"); val meta = java.io.File(dir, "p.meta")
            part.writeBytes(data.copyOf(partial))
            meta.writeText(listOf(url, "\"v1\"", data.size.toString()).joinToString("\n", postfix = "\n"))
            MapPackDownload.fetch(MapPackDownload.Link("Test", url), part, meta, dir, {}, { }, requireHttps = false)
            return part.readBytes()
        } finally { server.socket.close() }
    }

    @Test
    fun interruptedDownloadResumesFromThePartialFile() {
        val data = ByteArray(300_000) { (it * 31 % 251).toByte() }
        assertTrue(fetch(data, 120_000, ranges = true).contentEquals(data))
    }

    @Test
    fun serverWithoutRangesStartsOver() {
        val data = ByteArray(200_000) { (it * 7 % 253).toByte() }
        assertTrue(fetch(data, 50_000, ranges = false).contentEquals(data))
    }
}
