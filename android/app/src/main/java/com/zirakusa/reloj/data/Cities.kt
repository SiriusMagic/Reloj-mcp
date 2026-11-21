package com.zirakusa.reloj.data

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

/** One city of «Reloj mundial»: its name (Spanish), country (ISO code, shown in the phone's language) and time zone. */
data class City(val name: String, val country: String, val zone: String) {
    val id get() = "$name|$zone"
    fun offset(at: Instant): Int = ZoneId.of(zone).rules.getOffset(at).totalSeconds
}

/** The cities the user can add, A to Z, and the search over them. */
object Cities {
    private const val DATA = """Abiyán|CI|Africa/Abidjan
Abu Dabi|AE|Asia/Dubai
Acra|GH|Africa/Accra
Adelaida|AU|Australia/Adelaide
Adís Abeba|ET|Africa/Addis_Ababa
Ámsterdam|NL|Europe/Amsterdam
Anchorage|US|America/Anchorage
Argel|DZ|Africa/Algiers
Asunción|PY|America/Asuncion
Atenas|GR|Europe/Athens
Auckland|NZ|Pacific/Auckland
Bagdad|IQ|Asia/Baghdad
Bangkok|TH|Asia/Bangkok
Barcelona|ES|Europe/Madrid
Bogotá|CO|America/Bogota
Berlín|DE|Europe/Berlin
Bruselas|BE|Europe/Brussels
Bucarest|RO|Europe/Bucharest
Budapest|HU|Europe/Budapest
Buenos Aires|AR|America/Argentina/Buenos_Aires
Cancún|MX|America/Cancun
Caracas|VE|America/Caracas
Casablanca|MA|Africa/Casablanca
Chicago|US|America/Chicago
Ciudad de Guatemala|GT|America/Guatemala
Ciudad de México|MX|America/Mexico_City
Ciudad de Panamá|PA|America/Panama
Copenhague|DK|Europe/Copenhagen
Cuenca|EC|America/Guayaquil
Dakar|SN|Africa/Dakar
Delhi|IN|Asia/Kolkata
Denver|US|America/Denver
Doha|QA|Asia/Qatar
Dubái|AE|Asia/Dubai
Dublín|IE|Europe/Dublin
Estambul|TR|Europe/Istanbul
Estocolmo|SE|Europe/Stockholm
Galápagos|EC|Pacific/Galapagos
Ginebra|CH|Europe/Zurich
Guayaquil|EC|America/Guayaquil
Hanói|VN|Asia/Ho_Chi_Minh
Helsinki|FI|Europe/Helsinki
Hong Kong|HK|Asia/Hong_Kong
Honolulu|US|Pacific/Honolulu
Houston|US|America/Chicago
Islamabad|PK|Asia/Karachi
Yakarta|ID|Asia/Jakarta
Jerusalén|IL|Asia/Jerusalem
Johannesburgo|ZA|Africa/Johannesburg
Kabul|AF|Asia/Kabul
Katmandú|NP|Asia/Kathmandu
Kiev|UA|Europe/Kiev
Kuala Lumpur|MY|Asia/Kuala_Lumpur
La Habana|CU|America/Havana
La Paz|BO|America/La_Paz
Lagos|NG|Africa/Lagos
Las Vegas|US|America/Los_Angeles
Lima|PE|America/Lima
Lisboa|PT|Europe/Lisbon
Londres|GB|Europe/London
Los Ángeles|US|America/Los_Angeles
Madrid|ES|Europe/Madrid
Managua|NI|America/Managua
Manila|PH|Asia/Manila
Medellín|CO|America/Bogota
Melbourne|AU|Australia/Melbourne
Miami|US|America/New_York
Milán|IT|Europe/Rome
Montevideo|UY|America/Montevideo
Montreal|CA|America/Toronto
Moscú|RU|Europe/Moscow
Bombay|IN|Asia/Kolkata
Múnich|DE|Europe/Berlin
Nairobi|KE|Africa/Nairobi
Nueva York|US|America/New_York
Oslo|NO|Europe/Oslo
Ottawa|CA|America/Toronto
París|FR|Europe/Paris
Pekín|CN|Asia/Shanghai
Perth|AU|Australia/Perth
Praga|CZ|Europe/Prague
Quito|EC|America/Guayaquil
Reikiavik|IS|Atlantic/Reykjavik
Río de Janeiro|BR|America/Sao_Paulo
Riad|SA|Asia/Riyadh
Roma|IT|Europe/Rome
San Francisco|US|America/Los_Angeles
San José|CR|America/Costa_Rica
San Juan|PR|America/Puerto_Rico
San Salvador|SV|America/El_Salvador
Santiago de Chile|CL|America/Santiago
Santo Domingo|DO|America/Santo_Domingo
São Paulo|BR|America/Sao_Paulo
Seúl|KR|Asia/Seoul
Shanghái|CN|Asia/Shanghai
Singapur|SG|Asia/Singapore
Sídney|AU|Australia/Sydney
Taipéi|TW|Asia/Taipei
Teherán|IR|Asia/Tehran
Tegucigalpa|HN|America/Tegucigalpa
Tokio|JP|Asia/Tokyo
Toronto|CA|America/Toronto
Valencia|ES|Europe/Madrid
Vancouver|CA|America/Vancouver
Varsovia|PL|Europe/Warsaw
Viena|AT|Europe/Vienna
Washington|US|America/New_York
Zúrich|CH|Europe/Zurich"""

    val all: List<City> by lazy {
        DATA.lines().map { it.split('|') }.map { (n, c, z) -> City(n, c, z) }.sortedBy { fold(it.name) }
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Without accents or case: «bogota» finds «Bogotá». */
    fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    /** The letter a city is listed under (Á under A). */
    fun letter(city: City) = fold(city.name).firstOrNull()?.uppercaseChar() ?: '#'

    /** Cities whose name or [countryName] contains the words of [query], name matches first. */
    fun search(query: String, countryName: (String) -> String = { it }): List<City> {
        val q = fold(query)
        if (q.isEmpty()) return all
        return all.filter { fold(it.name).contains(q) || fold(countryName(it.country)).contains(q) || fold(it.zone).contains(q) }
            .sortedBy { if (fold(it.name).startsWith(q)) 0 else 1 }
    }

    /** A city by name (accents and case ignored) or an IANA zone id like «Europe/Madrid». */
    fun find(text: String): City? {
        val q = fold(text)
        if (q.isEmpty()) return null
        all.firstOrNull { fold(it.name) == q }?.let { return it }
        val zone = runCatching { ZoneId.of(text.trim()) }.getOrNull() ?: return all.firstOrNull { fold(it.name).startsWith(q) }
        return all.firstOrNull { it.zone == zone.id } ?: City(zone.id.substringAfterLast('/').replace('_', ' '), "", zone.id)
    }
}
