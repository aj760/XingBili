package blbl.cat3399.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Dns
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

fun ipv4OnlyDns(ipv4OnlyEnabled: () -> Boolean): Dns =
    object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val host = hostname.trim()
            if (host.isBlank()) throw UnknownHostException("hostname is blank")

            val addresses = Dns.SYSTEM.lookup(host)
            if (!ipv4OnlyEnabled()) return addresses

            val ipv4 = ArrayList<InetAddress>(addresses.size)
            for (address in addresses) {
                if (address is Inet4Address) ipv4.add(address)
            }
            if (ipv4.isNotEmpty()) return ipv4
            throw UnknownHostException("No IPv4 address for $host")
        }
    }
