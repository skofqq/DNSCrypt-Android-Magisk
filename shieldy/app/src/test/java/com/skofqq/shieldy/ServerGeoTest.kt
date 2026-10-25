package com.skofqq.shieldy

import com.skofqq.shieldy.data.GeoIp
import com.skofqq.shieldy.data.ServerCountries
import com.skofqq.shieldy.data.StampAddress
import com.skofqq.shieldy.data.StampAddresses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.Base64

class ServerGeoTest {

    private fun stamp(protocol: Int, build: ByteArrayOutputStream.() -> Unit): String {
        val out = ByteArrayOutputStream()
        out.write(protocol)
        out.build()
        return "sdns://" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun ByteArrayOutputStream.lp(text: String) {
        write(text.length)
        write(text.toByteArray())
    }

    private fun ByteArrayOutputStream.props() = write(ByteArray(8))

    @Test
    fun dnscryptStampYieldsIp() {
        val s = stamp(0x01) { props(); lp("95.216.1.2:8443"); lp("pk"); lp("2.dnscrypt-cert.example") }
        assertEquals(StampAddress("95.216.1.2", null), StampAddresses.of(s))
    }

    @Test
    fun dohStampYieldsIpAndHost() {
        val s = stamp(0x02) {
            props(); lp("[2a00:1450::1]:443")
            write(0x80 or 2); write(byteArrayOf(1, 2)); write(1); write(3)
            lp("dns.example.com:443"); lp("/dns-query")
        }
        assertEquals(StampAddress("2a00:1450::1", "dns.example.com"), StampAddresses.of(s))
    }

    @Test
    fun dohStampWithoutAddressKeepsHost() {
        val s = stamp(0x02) { props(); lp(""); write(0); lp("doh.example"); lp("/dns-query") }
        assertEquals(StampAddress(null, "doh.example"), StampAddresses.of(s))
    }

    @Test
    fun relayAndGarbage() {
        assertEquals(StampAddress("1.2.3.4", null), StampAddresses.of(stamp(0x81) { lp("1.2.3.4:443") }))
        assertNull(StampAddresses.of("sdns://!!"))
        assertNull(StampAddresses.of(stamp(0x01) { props() }))
    }

    @Test
    fun geoIpLooksUpBundledTable() {
        val geo = GeoIp.read(File("src/main/assets/geoip4.bin").readBytes())
        assertEquals("US", geo.country(InetAddress.getByName("8.8.8.8")))
        assertEquals("RU", geo.country(InetAddress.getByName("77.88.8.8")))
        assertNull(geo.country(InetAddress.getByName("10.0.0.1")))
        assertNull(geo.country(InetAddress.getByName("2001:db8::1")))
    }

    @Test
    fun flags() {
        assertEquals("🇫🇮", ServerCountries.flag("FI"))
    }
}
