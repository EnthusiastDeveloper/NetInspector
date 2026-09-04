package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.RegisteredDnsServersRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

/** `runQuery` builds [dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer]
 * from which of the two query paths ran (design §9.4) - this pins down the wiring between "was
 * the server field resolvable" and what ends up in [DnsUiState.queriedServer], since
 * `queriedDnsServerOf` itself (the decision logic) is already covered by
 * `DnsServerMatchingTest` on the JVM in `:data:diagnostics`. */
@OptIn(ExperimentalCoroutinesApi::class)
class DnsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val registeredNetwork =
        RegisteredDnsNetwork(
            transport = NetworkTransport.WIFI,
            ipv4Servers = listOf(ip("192.168.1.1")),
            ipv6Servers = emptyList(),
            isPrivateDnsActive = false,
            privateDnsServerName = null,
        )
    private val dnsRepository = mockk<DnsRepository>()
    private val registeredDnsServersDataSource =
        mockk<RegisteredDnsServersRepository> {
            every { snapshot() } returns listOf(registeredNetwork)
            every { activeTransport() } returns NetworkTransport.WIFI
        }
    private val diagnosticRunRepository = mockk<DiagnosticRunRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): DnsViewModel =
        DnsViewModel(
            dnsRepository = dnsRepository,
            registeredDnsServersDataSource = registeredDnsServersDataSource,
            diagnosticRunRepository = diagnosticRunRepository,
        )

    @Test
    fun `blank server field queries the system resolver and reports SystemResolver`() =
        runTest {
            coEvery { dnsRepository.querySystemResolver(any(), any()) } returns
                DnsQueryOutcome.Success(emptyList(), queryTimeMs = 1.0)
            val viewModel = viewModel()

            viewModel.updateName("example.com")
            viewModel.runQuery()
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value.queriedServer).isEqualTo(QueriedDnsServer.SystemResolver)
            assertThat(viewModel.uiState.value.activeTransportAtQuery).isEqualTo(NetworkTransport.WIFI)
        }

    @Test
    fun `a resolvable custom server queries it directly and flags a registered mismatch`() =
        runTest {
            coEvery { dnsRepository.queryServer(any(), any(), any(), any()) } returns
                DnsQueryOutcome.Success(emptyList(), queryTimeMs = 1.0)
            val viewModel = viewModel()

            viewModel.updateName("example.com")
            viewModel.updateCustomServer("8.8.8.8")
            viewModel.runQuery()
            dispatcher.scheduler.advanceUntilIdle()

            val queried = viewModel.uiState.value.queriedServer
            assertThat(queried).isInstanceOf(QueriedDnsServer.Explicit::class.java)
            queried as QueriedDnsServer.Explicit
            assertThat(queried.address).isEqualTo(ip("8.8.8.8"))
            assertThat(queried.matchesRegistered).isFalse()
        }

    // The "server field couldn't be resolved at all" branch (-> Error outcome, queriedServer
    // stays null) isn't covered here: exercising it means calling the real
    // `InetAddress.getByName` resolution path on a genuinely non-existent hostname, which is
    // network-dependent and not something any other test in this codebase does on the JVM
    // (every other `InetAddress` test in the repo uses numeric literals, which never hit the
    // network). That one-line mapping is covered by code inspection instead.
}

private fun ip(text: String): Inet4Address = InetAddress.getByName(text) as Inet4Address
