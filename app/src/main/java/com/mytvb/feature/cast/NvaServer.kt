package com.mytvb.feature.cast

import com.mytvb.core.common.log.AppLog
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * NVA 服务端：9958 端口同时承载 UPnP 描述（HTTP）与哔哩必连长连接（NVA/1.0）。
 */
internal class NvaServer(
    private val controller: NvaCastController,
) {
    private val tag = "NvaServer"
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val sessions = CopyOnWriteArrayList<NvaSession>()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptThread =
            thread(name = "nva-accept", isDaemon = true) {
                var retryDelay = RETRY_MIN_MS
                while (running.get()) {
                    var bound: ServerSocket? = null
                    try {
                        val listening =
                            ServerSocket().apply {
                                reuseAddress = true
                                bind(InetSocketAddress(NvaProtocol.PORT))
                            }
                        bound = listening
                        serverSocket = listening
                        retryDelay = RETRY_MIN_MS
                        AppLog.i(tag, "NVA 服务已监听 :${NvaProtocol.PORT}")

                        while (running.get()) {
                            val client = listening.accept()
                            // 半开连接（手机休眠/切网/进程被杀）不会发 FIN，靠系统级 keepalive
                            // 在长时间后回收，否则读线程会永远卡在 read 上形成僵尸会话。
                            runCatching { client.keepAlive = true }
                            thread(name = "nva-conn-${client.port}", isDaemon = true) {
                                handleConnection(client)
                            }
                        }
                    } catch (t: Throwable) {
                        if (!running.get()) return@thread
                        // 端口被别的实例/进程短暂占用时不能就此放弃：一旦 bind 失败就
                        // 永久失效，表现为"手机再也投不上来，只能重启 TV 端"。
                        AppLog.w(tag, "NVA 端口 ${NvaProtocol.PORT} 异常，${retryDelay}ms 后重试", t)
                    } finally {
                        runCatching { bound?.close() }
                        serverSocket = null
                    }
                    runCatching { Thread.sleep(retryDelay) }
                    retryDelay = (retryDelay * 2).coerceAtMost(RETRY_MAX_MS)
                }
            }
    }

    fun stop() {
        running.set(false)
        sessions.toList().forEach { it.close() }
        sessions.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleConnection(socket: Socket) {
        runCatching {
            socket.tcpNoDelay = true
            socket.soTimeout = HTTP_TIMEOUT_MS

            val input = socket.getInputStream()
            val head = readHttpHead(input)
            if (head == null) {
                socket.close()
                return
            }

            val requestLine = head.lineSequence().firstOrNull().orEmpty()
            val parts = requestLine.split(' ')
            if (parts.size < 3) {
                socket.close()
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val path = parts[1]
            val protocol = parts[2]

            if (protocol.contains("NVA", ignoreCase = true) ||
                method == "SETUP" ||
                method == "RESTORE" ||
                method == "STARTRESTORE"
            ) {
                handleNvaUpgrade(socket, head, method)
                return
            }

            when (method) {
                "GET", "HEAD" -> handleHttpGet(socket, path, head, method == "HEAD")
                "POST" -> handleSoapPost(socket)
                else -> writeHttp(socket, 405, "Method Not Allowed", "text/plain", "")
            }
        }.onFailure { t ->
            AppLog.w(tag, "连接处理失败 ${socket.remoteSocketAddress}", t)
        }.also {
            runCatching { socket.close() }
        }
    }

    private fun handleNvaUpgrade(socket: Socket, head: String, method: String) {
        val clientUuid = headerValue(head, "UUID") ?: ""
        val sessionId = headerValue(head, "Session")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val response =
            buildString(256) {
                append("NVA/1.0 200 OK\r\n")
                append("NvaVersion: 1\r\n")
                append("Session: ").append(sessionId).append("\r\n")
                append("Connection: Keep-Alive\r\n")
                append("UUID: ").append(NvaDevice.usn(controller.context)).append("\r\n")
                append("Date: ").append(httpDate()).append("\r\n")
                append("Content-Length: 0\r\n")
                append("Server: Linux/3.0.0, UPnP/1.0, Platinum/1.0.5.13\r\n")
                append("\r\n")
            }
        val out = DataOutputStream(socket.getOutputStream())
        out.write(response.toByteArray(Charsets.UTF_8))
        out.flush()

        AppLog.i(tag, "NVA 握手成功 method=$method client=$clientUuid session=$sessionId remote=${socket.remoteSocketAddress}")

        // 同一设备重连时先踢掉旧会话，避免状态广播发送到僵尸连接。
        sessions.filter { it.clientUuid == clientUuid && clientUuid.isNotBlank() }.forEach { old ->
            AppLog.i(tag, "替换同一设备的旧会话 ${old.sessionId}")
            old.close()
            sessions.remove(old)
        }

        // 半开会话兜底：手机端异常退出（WiFi 断、进程被杀）不会发 FIN，
        // 读线程会一直挂在 read 上。超过上限就踢最老的，防止线程/队列无限堆积。
        while (sessions.size >= MAX_SESSIONS) {
            val oldest = sessions.firstOrNull() ?: break
            AppLog.w(tag, "会话数达到上限 $MAX_SESSIONS，踢掉最老会话 ${oldest.sessionId}")
            oldest.close()
            sessions.remove(oldest)
        }

        socket.soTimeout = 0
        val session = NvaSession(socket, out, clientUuid, sessionId, this, controller)
        sessions.add(session)
        controller.onSessionOpened(session)
        session.run()
    }

    private fun handleHttpGet(socket: Socket, path: String, head: String, headOnly: Boolean) {
        // /dlna/*.xml 是服务描述；其余路径（含 /、/description.xml、/desc/<ver>/description.xml）
        // 一律给设备描述，不同版本客户端请求的入口路径不一致，放宽匹配比精确匹配更稳。
        val isServiceDescription = path.contains("/dlna/") && path.endsWith(".xml")
        val body =
            if (isServiceDescription) {
                NvaDevice.scpdXml(path)
            } else {
                NvaDevice.deviceDescriptionXml(controller.context, "http://${localAddress()}:${NvaProtocol.PORT}/")
            }
        writeHttp(socket, 200, "OK", "text/xml; charset=\"utf-8\"", if (headOnly) "" else body)
        // 把实际返回的设备名打进日志：手机端显示旧名时，一眼就能判断是服务端没改过来
        // 还是客户端用了缓存（服务端名字对 = 客户端缓存问题）。
        // UA 也记下来：多台设备命名异常时，靠它区分是哪个客户端/版本在拉描述。
        if (isServiceDescription) {
            AppLog.i(tag, "HTTP GET $path -> ${body.length} bytes")
        } else {
            AppLog.i(
                tag,
                "HTTP GET $path from=${socket.remoteSocketAddress} ua=${headerValue(head, "User-Agent").orEmpty()} " +
                    "-> ${body.length} bytes " +
                    "name=${NvaDevice.friendlyName(controller.context)} " +
                    "model=${NvaDevice.modelName(controller.context)} " +
                    "brand=${NvaDevice.brandName(controller.context)} " +
                    "serial=${NvaDevice.serialNumber(controller.context)} " +
                    "bootId=${NvaDevice.bootId(controller.context)}",
            )
        }
    }

    private fun handleSoapPost(socket: Socket) {
        // 现代客户端不会真的走 SOAP，这里返回合法空响应即可，避免探测失败。
        val body =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
                """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
                "<s:Body></s:Body></s:Envelope>"
        writeHttp(socket, 200, "OK", "text/xml; charset=\"utf-8\"", body)
    }

    private fun writeHttp(socket: Socket, code: Int, reason: String, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header =
            buildString(256) {
                append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Connection: close\r\n")
                append("Server: Linux/3.0.0, UPnP/1.0, Platinum/1.0.5.13\r\n")
                append("\r\n")
            }
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) out.write(bytes)
        out.flush()
    }

    private fun localAddress(): String = NvaDevice.localIpv4() ?: "127.0.0.1"

    private fun headerValue(head: String, header: String): String? =
        head.lineSequence()
            .firstOrNull { it.startsWith("$header:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()

    private fun httpDate(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(Date())

    private fun readHttpHead(input: InputStream): String? {
        // 必须逐字节读，不能用 BufferedReader：它会预读，把后续二进制帧吃掉。
        val buffer = ByteArrayOutputStream(1024)
        var state = 0
        while (buffer.size() < MAX_HTTP_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else String(buffer.toByteArray(), Charsets.UTF_8)
            buffer.write(b)
            state =
                when {
                    b == '\r'.code && (state == 0 || state == 2) -> state + 1
                    b == '\n'.code && (state == 1 || state == 3) -> state + 1
                    else -> 0
                }
            if (state == 4) return String(buffer.toByteArray(), Charsets.UTF_8)
        }
        return null
    }

    private companion object {
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val MAX_HTTP_HEAD_BYTES = 16 * 1024

        /** 监听失败后的重试退避，1s 起翻倍，封顶 30s。 */
        private const val RETRY_MIN_MS = 1_000L
        private const val RETRY_MAX_MS = 30_000L

        /**
         * 同时存在的会话上限。手机端异常退出时可能留下半开会话，
         * 超过这个数就踢掉最老的，避免线程与写队列无限堆积。
         */
        private const val MAX_SESSIONS = 8
    }
}

/**
 * 单个手机端连接。读写都在独立线程，写操作串行化（心跳与命令回执可能并发）。
 */
internal class NvaSession(
    private val socket: Socket,
    private val output: DataOutputStream,
    val clientUuid: String,
    val sessionId: String,
    private val server: NvaServer,
    private val controller: NvaCastController,
) {
    private val tag = "NvaSession"
    private val counter = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()
    private val writeQueue = LinkedBlockingQueue<ByteArray>(WRITE_QUEUE_CAPACITY)

    /**
     * 最近一次"真的把字节写出去"的时间。
     *
     * 手机端半开连接（休眠、切网、进程被杀）不会发 FIN，读线程永远等不到 EOF，
     * 会话就变成僵尸：每秒还在发心跳、往一个死连接入队。TCP 写进了内核缓冲区
     * 就算成功，所以只能靠"写操作长时间没有任何进展"来反推对端已经不读了。
     */
    @Volatile
    private var lastWriteAt = System.currentTimeMillis()

    @Volatile
    private var writer: Thread? = null

    @Volatile
    var lastPlayState: Int = -1

    fun run() {
        // 写线程在这里提前拉起，保证后续任何线程（含主线程）的发送都只是入队。
        ensureWriter()
        startWatchdog()

        val heartbeat =
            thread(name = "nva-ping-${socket.port}", isDaemon = true) {
                while (!closed.get()) {
                    runCatching { sendPing() }.onFailure { return@thread }
                    runCatching { Thread.sleep(NvaProtocol.PING_INTERVAL_MS) }
                }
            }

        try {
            val input = socket.getInputStream()
            while (!closed.get()) {
                val frame = NvaProtocol.readFrame(input) ?: break
                when (frame) {
                    is NvaProtocol.Frame.Command -> {
                        AppLog.i(tag, "<< ${frame.method} ${frame.payload.orEmpty()}")
                        handleCommand(frame.counter, frame.method, frame.payload)
                    }

                    is NvaProtocol.Frame.Reply -> {
                        AppLog.d(tag, "<< reply#${frame.counter} ${frame.payload.orEmpty()}")
                    }

                    is NvaProtocol.Frame.Ping -> Unit
                }
            }
        } catch (t: Throwable) {
            if (!closed.get()) AppLog.w(tag, "会话结束（异常）", t)
        } finally {
            closed.set(true)
            runCatching { heartbeat.interrupt() }
            close()
            server.sessions.remove(this)
            controller.onSessionClosed(this)
            AppLog.i(tag, "会话关闭 session=$sessionId")
        }
    }

    private fun handleCommand(counter: Int, method: String, payload: String?) {
        val params = payload?.let { runCatching { JSONObject(it) }.getOrNull() }

        // 有返回值的命令先算再回，其余先回执后执行，避免手机端等待超时。
        val immediateResult =
            when (method) {
                "GetVolume" -> JSONObject().put("volume", controller.currentVolume())
                // 手机 B 站 App 实发 "GetTvInfo"（v 小写），与参考补丁的 "GetTVInfo" 并存兼容；
                // 拼写漏匹配会让手机端拿不到 capability，投屏控制条"分辨率"永远停在自动。
                "GetTVInfo", "GetTvInfo" -> controller.tvInfo()
                "GetPlayInfo" -> controller.playInfo()
                else -> null
            }

        sendReply(counter, immediateResult)
        if (immediateResult != null) return

        runCatching { controller.handleCommand(this, method, params) }
            .onFailure { AppLog.e(tag, "命令处理失败 method=$method", it) }
    }

    fun sendReply(counter: Int, payload: JSONObject?) {
        enqueue(NvaProtocol.encodeReply(counter, payload?.toString()))
    }

    fun sendCommand(method: String, payload: JSONObject?) {
        val next = counter.incrementAndGet()
        enqueue(NvaProtocol.encodeCommand(next, method, payload?.toString()))
    }

    private fun sendPing() {
        enqueue(NvaProtocol.encodePing(counter.incrementAndGet()))
    }

    /**
     * 唯一的发送入口：只入队，绝不在调用线程上做 socket 写。
     *
     * 调用方分布在主线程（生命周期回调、每秒状态轮询）和网络线程（命令回执）两侧。
     * 直接在调用线程 write 会触发 NetworkOnMainThreadException 把 App 打崩；
     * 改为单写线程 + 队列后，帧顺序与 counter 递增顺序保持一致，调用方永不阻塞。
     */
    private fun enqueue(bytes: ByteArray) {
        if (closed.get()) return
        ensureWriter()
        if (!writeQueue.offer(bytes)) {
            AppLog.w(tag, "发送队列已满，丢弃一帧 session=$sessionId")
        }
    }

    private fun ensureWriter() {
        if (writer != null) return
        synchronized(this) {
            if (writer != null) return
            writer =
                thread(name = "nva-write-${socket.port}", isDaemon = true) {
                    try {
                        while (!closed.get()) {
                            // 带超时的 poll：即使 close 时来不及打断，线程也会在 1 秒内自行退出。
                            val bytes = writeQueue.poll(1, TimeUnit.SECONDS) ?: continue
                            if (closed.get()) return@thread
                            synchronized(writeLock) {
                                output.write(bytes)
                                output.flush()
                            }
                            lastWriteAt = System.currentTimeMillis()
                        }
                    } catch (t: Throwable) {
                        if (!closed.get()) {
                            AppLog.w(tag, "写线程异常，主动断开 session=$sessionId", t)
                        }
                        closed.set(true)
                        runCatching { socket.close() }
                    }
                }
        }
    }

    /**
     * 写进展看门狗。
     *
     * 心跳每秒入队一帧，正常情况下 [lastWriteAt] 每秒都会被刷新。一旦手机端进入
     * 半开状态（休眠、切网、进程被杀），TCP 缓冲被填满后 `write` 会永久阻塞，
     * 时间戳停止前进，这里就能判定连接已死并主动回收，不再需要等手机端重连
     * 或用户重启 TV 端 App。
     */
    private fun startWatchdog() {
        thread(name = "nva-watch-${socket.port}", isDaemon = true) {
            while (!closed.get()) {
                runCatching { Thread.sleep(WRITE_STALL_CHECK_MS) }
                if (closed.get()) return@thread
                val idleMs = System.currentTimeMillis() - lastWriteAt
                if (idleMs > WRITE_STALL_TIMEOUT_MS) {
                    AppLog.w(
                        tag,
                        "写线程 ${idleMs}ms 无进展，判定手机端已断开，主动回收 session=$sessionId",
                    )
                    close()
                    return@thread
                }
            }
        }
    }

    fun close() {
        // Socket.close() 幂等，直接关闭即可；不能用 CAS 提前 return，否则读循环无法被唤醒。
        closed.set(true)
        writer?.interrupt()
        runCatching { socket.close() }
    }

    private companion object {
        private const val WRITE_QUEUE_CAPACITY = 128

        /** 看门狗检查周期。 */
        private const val WRITE_STALL_CHECK_MS = 5_000L

        /**
         * 写操作允许停滞的上限。手机端正常时每秒都有心跳写出，
         * 连续 30 秒写不动说明对端已经不读了，可以安全回收。
         */
        private const val WRITE_STALL_TIMEOUT_MS = 30_000L
    }
}
