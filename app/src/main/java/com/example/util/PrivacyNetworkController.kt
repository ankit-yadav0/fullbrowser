package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

/**
 * App-level fail-closed networking. NOT a system kill switch.
 *
 * Mechanisms (all app-process scoped):
 *  1. The process is bound (ConnectivityManager.bindProcessToNetwork) to an Android VPN network.
 *     When that network disappears the binding is DELIBERATELY KEPT: per the platform contract,
 *     sockets and DNS bound this way stop working once the network disconnects, instead of silently
 *     falling back to the physical network.
 *  2. While not ready, a loopback black-hole proxy is installed for WebView (when PROXY_OVERRIDE is
 *     supported). It is removed only after the VPN bind succeeded.
 *  3. [isReady] flips to false synchronously on any VPN loss; interceptors and the downloader check it.
 *
 * Not covered: other apps, system services (e.g. Safe Browsing lookups made by Google Play services),
 * and any VPN provider that excludes this app (split tunnelling). Needs on-device verification.
 */
class PrivacyNetworkController(context: Context) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val gate = VpnGate()
    private val main = Handler(Looper.getMainLooper())
    private val directExecutor = Executor { it.run() }
    private val networks = HashMap<Long, Network>()

    @Volatile private var epoch = 0
    @Volatile private var started = false
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var listener: ((Boolean) -> Unit)? = null
    private var lastNotified: Boolean? = null

    /** Safe from any thread. */
    fun isReady(): Boolean = started && gate.isReady()

    /** Call on the main thread. [onStateChanged] is always invoked on the main thread. */
    fun start(onStateChanged: (Boolean) -> Unit) {
        if (started) return
        started = true
        listener = onStateChanged
        lastNotified = null
        installBlackhole()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(networks) { networks[network.networkHandle] = network }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                synchronized(networks) { networks[network.networkHandle] = network }
                val usable = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                gate.update(network.networkHandle, usable)
                main.post { reconcile() }
            }

            override fun onLost(network: Network) {
                synchronized(networks) { networks.remove(network.networkHandle) }
                gate.lost(network.networkHandle) // synchronous: closes the gate on this thread
                main.post { reconcile() }
            }
        }
        callback = cb
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) // VPN networks lack NOT_VPN
            .build()
        val registered = runCatching { cm.registerNetworkCallback(request, cb) }.isSuccess
        if (!registered) {
            callback = null
            gate.forceClosed()
        }
        reconcile()
    }

    /** Call on the main thread. Leaves the black-hole proxy installed and the process binding in place. */
    fun stop() {
        if (!started) return
        started = false
        epoch++
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        gate.shutdown()
        synchronized(networks) { networks.clear() }
        installBlackhole()
        listener = null
        lastNotified = null
    }

    private fun reconcile() {
        if (!started) return
        val candidate = gate.pickCandidate()
        if (candidate != null && gate.isReadyFor(candidate)) {
            notifyState(true)
            return
        }
        val net = candidate?.let { id -> synchronized(networks) { networks[id] } }
        if (candidate == null || net == null) {
            gate.forceClosed()
            installBlackhole()
            notifyState(false)
            return
        }
        val myEpoch = ++epoch
        val bindOk = runCatching { cm.bindProcessToNetwork(net) }.getOrDefault(false)
        if (!bindOk) {
            gate.forceClosed()
            installBlackhole()
            notifyState(false)
            return
        }
        gate.beginBind(candidate)
        notifyState(false)
        clearBlackhole {
            // Runs on the executor; only publish if nothing changed meanwhile.
            if (myEpoch == epoch && gate.markReady(candidate)) main.post { if (started) notifyState(gate.isReady()) }
        }
    }

    private fun notifyState(ready: Boolean) {
        if (lastNotified == ready) return
        lastNotified = ready
        listener?.invoke(ready)
    }

    private fun proxySupported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }.getOrDefault(false)

    private fun installBlackhole() {
        if (!proxySupported()) return
        runCatching {
            ProxyController.getInstance().setProxyOverride(
                ProxyConfig.Builder().addProxyRule("127.0.0.1:1").build(),
                directExecutor,
                Runnable { }
            )
        }
    }

    private fun clearBlackhole(done: () -> Unit) {
        if (!proxySupported()) {
            done()
            return
        }
        runCatching {
            ProxyController.getInstance().clearProxyOverride(directExecutor, Runnable { done() })
        } // on failure the gate simply stays closed
    }
}
