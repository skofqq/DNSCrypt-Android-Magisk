package io.github.skofqq.dnscryptmanager.ui.status

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.skofqq.dnscryptmanager.data.DnsVerdict
import io.github.skofqq.dnscryptmanager.data.LeakReport
import io.github.skofqq.dnscryptmanager.data.LeakTest
import io.github.skofqq.dnscryptmanager.data.LiveServers
import io.github.skofqq.dnscryptmanager.data.ModuleRepository
import io.github.skofqq.dnscryptmanager.data.ModuleStatus
import io.github.skofqq.dnscryptmanager.data.ResolverEntry
import io.github.skofqq.dnscryptmanager.data.ResolverParser
import io.github.skofqq.dnscryptmanager.dns.DnsProbe
import io.github.skofqq.dnscryptmanager.dns.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the network itself hands out, before any interception. */
data class NetworkDns(
    val servers: List<String>,
    val privateDnsActive: Boolean,
    /** Set in strict mode (a hostname); null in automatic mode or when off. */
    val privateDnsHost: String?,
)

data class DnsCheckState(
    val open: Boolean = false,
    val running: Boolean = false,
    /** The proxy is up, its rules are in place and box is not holding port 53. */
    val proxyActive: Boolean = false,
    /** box_for_magisk captures DNS instead (json box_dns). */
    val boxDns: Boolean = false,
    /** private_dns_mode as the module read it: off, opportunistic, hostname, or empty/null when unset. */
    val privateDnsMode: String? = null,
    val probe: ProbeResult? = null,
    val live: LiveServers? = null,
    /** Catalogue entries of the servers shown, by name, for protocol flags and descriptions. */
    val catalog: Map<String, ResolverEntry> = emptyMap(),
    val network: NetworkDns? = null,
    val leakRunning: Boolean = false,
    val leak: LeakReport? = null,
    val leakError: String? = null,
) {
    val verdict: DnsVerdict
        get() = LeakTest.verdict(leak, proxyActive, network?.servers.orEmpty())
}

/**
 * The "which DNS am I using" check behind the Check button: the local probe, the
 * servers the proxy balances across, the network's own DNS and, on request, the
 * bash.ws leak test that shows which resolvers the internet actually saw.
 */
class DnsCheckViewModel(
    private val repository: ModuleRepository,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DnsCheckState())
    val state: StateFlow<DnsCheckState> = _state.asStateFlow()

    /** The 300 KB catalogue is parsed once per screen life, only to describe a few servers. */
    private var catalogCache: Map<String, ResolverEntry>? = null

    fun open(status: ModuleStatus) {
        if (_state.value.running) return
        val proxyActive = status.running && status.rulesV4 && status.boxDns != true
        _state.value = DnsCheckState(
            open = true,
            running = true,
            proxyActive = proxyActive,
            boxDns = status.boxDns == true,
            privateDnsMode = status.privateDns?.takeUnless { it == "null" },
        )
        viewModelScope.launch {
            val network = async(Dispatchers.IO) { readNetworkDns() }
            val probe = if (status.running) async { DnsProbe.probe(status.port) } else null
            // The log keeps the last run's list after a stop; only a running proxy is using it.
            val live = if (status.supportsServers && status.running) loadLive() else null
            val catalog = live?.let { describe(it) }.orEmpty()
            _state.update {
                it.copy(
                    running = false,
                    probe = probe?.await(),
                    live = live,
                    catalog = catalog,
                    network = network.await(),
                )
            }
        }
    }

    fun close() = _state.update { it.copy(open = false) }

    fun runLeakTest() {
        if (_state.value.leakRunning) return
        viewModelScope.launch {
            _state.update { it.copy(leakRunning = true, leakError = null) }
            LeakTest.run().fold(
                onSuccess = { report -> _state.update { it.copy(leakRunning = false, leak = report) } },
                onFailure = { error ->
                    _state.update { it.copy(leakRunning = false, leakError = error.message ?: error.javaClass.simpleName) }
                },
            )
        }
    }

    /** `servers live` from module 2.4.1 on; the proxy log and the config before that. */
    private suspend fun loadLive(): LiveServers? {
        repository.serversLive().getOrNull()?.takeIf { it.isSuccess }?.let { out ->
            LiveServers.parseJson(out.out)?.let { return it }
        }
        val log = repository.proxyLog(LOG_LINES).getOrNull()?.takeIf { it.isSuccess } ?: return null
        val config = repository.readConfig().getOrNull()
        return LiveServers.parse(log.stdout, config).takeIf { it.servers.isNotEmpty() }
    }

    private suspend fun describe(live: LiveServers): Map<String, ResolverEntry> {
        val catalog = catalogCache ?: run {
            val raw = repository.serversRaw().getOrNull()?.takeIf { it.isSuccess } ?: return emptyMap()
            withContext(Dispatchers.Default) { ResolverParser.parse(raw.out).associateBy { it.name } }
                .also { catalogCache = it }
        }
        return live.servers.take(SHOWN_SERVERS).mapNotNull { server -> catalog[server.name]?.let { server.name to it } }.toMap()
    }

    private fun readNetworkDns(): NetworkDns? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val properties = manager.getLinkProperties(manager.activeNetwork) ?: return null
        return NetworkDns(
            servers = properties.dnsServers.mapNotNull { it.hostAddress },
            privateDnsActive = properties.isPrivateDnsActive,
            privateDnsHost = properties.privateDnsServerName,
        )
    }

    companion object {
        /** Enough to cover a startup probe of ~800 servers, each logged up to twice. */
        private const val LOG_LINES = 2_000
        const val SHOWN_SERVERS = 12
    }
}
