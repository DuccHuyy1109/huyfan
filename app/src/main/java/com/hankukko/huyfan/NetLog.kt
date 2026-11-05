package com.hankukko.huyfan

import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/** Ghi từng bước của mỗi request (không ghi key/nội dung) – xem bằng `adb logcat -s HuyFanNet`. */
class NetLog : EventListener() {
    private var t0 = 0L
    private var path = ""
    private fun ms() = System.currentTimeMillis() - t0
    private fun log(msg: String) {
        Log.i(TAG, "[$path] +${ms()}ms $msg")
    }

    override fun callStart(call: Call) {
        t0 = System.currentTimeMillis()
        path = call.request().url.let { it.host + it.encodedPath }
        log("start ${call.request().method}")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
        log("dns ${inetAddressList.joinToString { it.hostAddress ?: "?" }}")

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        log("connect ${inetSocketAddress.address?.hostAddress}")

    override fun secureConnectEnd(call: Call, handshake: Handshake?) = log("tls ${handshake?.tlsVersion}")

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
        log("connected $protocol")

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) =
        log("connect FAILED ${ioe.javaClass.simpleName}: ${ioe.message}")

    override fun requestBodyEnd(call: Call, byteCount: Long) = log("sent body $byteCount B")
    override fun responseHeadersEnd(call: Call, response: Response) = log("response ${response.code} ${response.protocol}")
    override fun responseBodyEnd(call: Call, byteCount: Long) = log("body $byteCount B")
    override fun callEnd(call: Call) = log("done")
    override fun callFailed(call: Call, ioe: IOException) = log("FAILED ${ioe.javaClass.simpleName}: ${ioe.message}")
    override fun canceled(call: Call) = log("canceled")

    companion object {
        const val TAG = "HuyFanNet"
    }
}
