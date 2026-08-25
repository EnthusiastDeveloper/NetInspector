package dev.enthusiastdev.netinspector.data.diagnostics.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DefaultDnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.httpinspect.DefaultHttpInspectorRepository
import dev.enthusiastdev.netinspector.data.diagnostics.httpinspect.HttpInspectorRepository
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.DefaultPingRepository
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingRepository
import dev.enthusiastdev.netinspector.data.diagnostics.portscan.DefaultPortScannerRepository
import dev.enthusiastdev.netinspector.data.diagnostics.portscan.PortScannerRepository
import dev.enthusiastdev.netinspector.data.diagnostics.throughput.DefaultLanThroughputRepository
import dev.enthusiastdev.netinspector.data.diagnostics.throughput.LanThroughputRepository
import dev.enthusiastdev.netinspector.data.diagnostics.traceroute.DefaultTracerouteRepository
import dev.enthusiastdev.netinspector.data.diagnostics.traceroute.TracerouteRepository
import dev.enthusiastdev.netinspector.data.diagnostics.whois.DefaultWhoisRepository
import dev.enthusiastdev.netinspector.data.diagnostics.whois.WhoisRepository
import dev.enthusiastdev.netinspector.data.diagnostics.wol.DefaultWakeOnLanRepository
import dev.enthusiastdev.netinspector.data.diagnostics.wol.WakeOnLanRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindPingRepository(impl: DefaultPingRepository): PingRepository

    @Binds
    @Singleton
    abstract fun bindTracerouteRepository(impl: DefaultTracerouteRepository): TracerouteRepository

    @Binds
    @Singleton
    abstract fun bindDnsRepository(impl: DefaultDnsRepository): DnsRepository

    @Binds
    @Singleton
    abstract fun bindPortScannerRepository(impl: DefaultPortScannerRepository): PortScannerRepository

    @Binds
    @Singleton
    abstract fun bindWhoisRepository(impl: DefaultWhoisRepository): WhoisRepository

    @Binds
    @Singleton
    abstract fun bindHttpInspectorRepository(impl: DefaultHttpInspectorRepository): HttpInspectorRepository

    @Binds
    @Singleton
    abstract fun bindWakeOnLanRepository(impl: DefaultWakeOnLanRepository): WakeOnLanRepository

    @Binds
    @Singleton
    abstract fun bindLanThroughputRepository(impl: DefaultLanThroughputRepository): LanThroughputRepository
}
