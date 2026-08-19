package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurityTypeTest {
    @Test
    fun `securityTypeOf maps known platform values`() {
        assertThat(securityTypeOf(0)).isEqualTo(SecurityType.OPEN)
        assertThat(securityTypeOf(1)).isEqualTo(SecurityType.WEP)
        assertThat(securityTypeOf(2)).isEqualTo(SecurityType.WPA2) // SECURITY_TYPE_PSK
        assertThat(securityTypeOf(3)).isEqualTo(SecurityType.EAP)
        assertThat(securityTypeOf(4)).isEqualTo(SecurityType.WPA3) // SECURITY_TYPE_SAE
        assertThat(securityTypeOf(5)).isEqualTo(SecurityType.EAP) // EAP_WPA3_ENTERPRISE_192_BIT
        assertThat(securityTypeOf(6)).isEqualTo(SecurityType.OWE)
        assertThat(securityTypeOf(9)).isEqualTo(SecurityType.EAP) // EAP_WPA3_ENTERPRISE
        assertThat(securityTypeOf(11)).isEqualTo(SecurityType.EAP) // PASSPOINT_R1_R2
        assertThat(securityTypeOf(12)).isEqualTo(SecurityType.EAP) // PASSPOINT_R3
    }

    @Test
    fun `securityTypeOf maps unknown and out-of-scope values to UNKNOWN`() {
        assertThat(securityTypeOf(-1)).isEqualTo(SecurityType.UNKNOWN)
        assertThat(securityTypeOf(7)).isEqualTo(SecurityType.UNKNOWN) // WAPI_PSK
        assertThat(securityTypeOf(8)).isEqualTo(SecurityType.UNKNOWN) // WAPI_CERT
        assertThat(securityTypeOf(10)).isEqualTo(SecurityType.UNKNOWN) // OSEN
        assertThat(securityTypeOf(13)).isEqualTo(SecurityType.UNKNOWN) // DPP
    }

    @Test
    fun `securityTypesOf preserves both entries of a WPA2WPA3 transition network`() {
        val types = securityTypesOf(intArrayOf(2, 4))
        assertThat(types).containsExactly(SecurityType.WPA2, SecurityType.WPA3)
    }

    @Test
    fun `securityTypesOf deduplicates repeated platform values`() {
        val types = securityTypesOf(intArrayOf(2, 2))
        assertThat(types).containsExactly(SecurityType.WPA2)
    }

    @Test
    fun `securityTypesOf on an empty array yields an empty set`() {
        assertThat(securityTypesOf(intArrayOf())).isEmpty()
    }
}
