package dev.enthusiastdev.netinspector.core.common.vendor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VendorLookupTest {
    @Test
    fun `vendorFor resolves a known OUI regardless of colon formatting or case`() {
        assertThat(VendorLookup.vendorFor("00:0A:EB:11:22:33")).isEqualTo("Tp-Link Technologies Co.,Ltd.")
        assertThat(VendorLookup.vendorFor("000aeb112233")).isEqualTo("Tp-Link Technologies Co.,Ltd.")
    }

    @Test
    fun `vendorFor returns null for an OUI not in the table`() {
        assertThat(VendorLookup.vendorFor("FF:FF:FF:11:22:33")).isNull()
    }

    @Test
    fun `vendorFor returns null for a locally-administered MAC even if the OUI bytes collide`() {
        // 02:0A:EB:.. has the locally-administered bit set on its first octet - IEEE never
        // assigns real OUIs there, so this must not match despite sharing 0A:EB with a real entry.
        assertThat(VendorLookup.vendorFor("02:0A:EB:11:22:33")).isNull()
    }

    @Test
    fun `vendorFor returns null for a malformed address`() {
        assertThat(VendorLookup.vendorFor("not-a-mac")).isNull()
    }
}
