package com.hankukko.huyfan.util

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DNS chống mạng chập chờn (Wi-Fi quán, router lỗi):
 * hỏi DNS hệ thống tối đa 2,5 s → không được thì hỏi DNS bảo mật của Google (DoH qua 8.8.8.8)
 * → vẫn không được thì dùng địa chỉ đã nhớ lần trước.
 */
class SmartDns : Dns {

    private data class Entry(val addrs: List<InetAddress>, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pool = Executors.newCachedThreadPool()

    private val doh: Dns by lazy {
        DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build())
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
            .includeIPv6(false)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { if (now - it.at < TTL_MS) return it.addrs }

        val system = pool.submit<List<InetAddress>> { Dns.SYSTEM.lookup(hostname) }
        runCatching { system.get(2500, TimeUnit.MILLISECONDS) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            return remember(hostname, it)
        }
        runCatching { doh.lookup(hostname) }
            .onFailure { Log.w(TAG, "DoH failed for $hostname: ${it.message}") }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                Log.i(TAG, "resolved $hostname via DoH")
                return remember(hostname, it)
            }
        runCatching { system.get(5000, TimeUnit.MILLISECONDS) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            return remember(hostname, it)
        }
        cache[hostname]?.let { return it.addrs }
        throw UnknownHostException("Không phân giải được $hostname (mạng/DNS lỗi)")
    }

    private fun remember(host: String, addrs: List<InetAddress>): List<InetAddress> {
        cache[host] = Entry(addrs, System.currentTimeMillis())
        return addrs
    }

    /** Tra trước để câu hỏi đầu tiên không phải chờ DNS. */
    fun warmUp(vararg hosts: String) {
        hosts.forEach { h -> pool.execute { runCatching { lookup(h) } } }
    }

    companion object {
        private const val TAG = "HuyFanNet"
        private const val TTL_MS = 30 * 60 * 1000L
    }
}
