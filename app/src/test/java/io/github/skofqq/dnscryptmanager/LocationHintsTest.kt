package io.github.skofqq.dnscryptmanager

import io.github.skofqq.dnscryptmanager.data.LocationHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationHintsTest {

    @Test
    fun citiesInNamesAndDescriptions() {
        assertEquals("UA", LocationHints.country("dnscry.pt-kyiv02-ipv4", "dnscry.pt Kyiv 02 resolver."))
        assertEquals("FI", LocationHints.country("dnscry.pt-helsinki-ipv4", "dnscry.pt Helsinki resolver."))
        assertEquals("BY", LocationHints.country("some-resolver", "Сервер в Минске, Беларусь"))
        assertEquals("US", LocationHints.country("x", "Located in New York"))
    }

    @Test
    fun countryNames() {
        assertEquals("DE", LocationHints.country("x", "Non-logging resolver hosted in Germany."))
        assertEquals("CZ", LocationHints.country("x", "Server in Czechia"))
    }

    @Test
    fun nothingOrConflictingLeavesItToGeoIp() {
        assertNull(LocationHints.country("cloudflare", "Cloudflare DNS (anycast) - aka 1.1.1.1"))
        assertNull(LocationHints.country("x", "Servers in Germany and France"))
        // A US state with a country's name must not count as that country.
        assertNull(LocationHints.country("x", "Hosted in Georgia"))
    }
}
