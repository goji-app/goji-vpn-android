package xyz.gojihub.vpn.vpn.geo

import java.io.File
import java.net.InetAddress

/**
 * Ручной protobuf-декодер под конкретную, стабильную wire-схему v2ray/xray geoip.dat/
 * geosite.dat (GeoIPList/GeoSiteList из app/router/config.proto — тот же формат, что
 * использует официальный geosite.dat, Loyalsoldier/v2ray-rules-dat и runetfreedom/
 * russia-v2ray-rules-dat). Полноценная protobuf-библиотека сюда не подключается ради
 * веса APK — схема достаточно простая и стабильная, чтобы разобрать её вручную.
 *
 * Используется вместо встроенного в Xray-core загрузчика geoip.dat/geosite.dat: тот
 * ищет файлы по переменной окружения xray.location.asset, которая в этой сборке libXray
 * до Go-рантайма не доходит (см. GeoAssets.kt) — поэтому геоданные читает и подставляет
 * в конфиг в виде обычных доменов/подсетей сам GodjiVpnService.resolveGeoDataRules().
 *
 * Не материализует доменные/IP-списки категорий, которые не были запрошены — некоторые
 * категории (например geosite:ru-blocked-all) содержат сотни тысяч записей.
 */
object GeoDataParser {

    fun resolveDomains(file: File, tags: Set<String>): Map<String, List<String>> {
        if (tags.isEmpty() || !file.exists()) return emptyMap()
        val wanted = tags.map { it.uppercase() }.toSet()
        val result = mutableMapOf<String, MutableList<String>>()
        val buf = file.readBytes()
        val reader = ProtoReader(buf, 0, buf.size)
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            if (field == 1 && wireType == 2) {
                val (start, end) = reader.readLengthDelimitedRange()
                val countryCode = peekCountryCode(buf, start, end) ?: continue
                if (countryCode.uppercase() in wanted) {
                    result.getOrPut(countryCode.uppercase()) { mutableListOf() }
                        .addAll(decodeDomains(buf, start, end))
                }
            } else {
                reader.skip(wireType)
            }
        }
        return result
    }

    fun resolveCidrs(file: File, tags: Set<String>): Map<String, List<String>> {
        if (tags.isEmpty() || !file.exists()) return emptyMap()
        val wanted = tags.map { it.uppercase() }.toSet()
        val result = mutableMapOf<String, MutableList<String>>()
        val buf = file.readBytes()
        val reader = ProtoReader(buf, 0, buf.size)
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            if (field == 1 && wireType == 2) {
                val (start, end) = reader.readLengthDelimitedRange()
                val countryCode = peekCountryCode(buf, start, end) ?: continue
                if (countryCode.uppercase() in wanted) {
                    result.getOrPut(countryCode.uppercase()) { mutableListOf() }
                        .addAll(decodeCidrs(buf, start, end))
                }
            } else {
                reader.skip(wireType)
            }
        }
        return result
    }

    /** Ищет только строковое поле №1 (country_code) в подсообщении GeoSite/GeoIP, не
     *  трогая остальные (потенциально огромные) поля — без этого пришлось бы декодировать
     *  доменные/IP-списки вообще всех категорий файла, а не только запрошенных. */
    private fun peekCountryCode(buf: ByteArray, start: Int, end: Int): String? {
        val reader = ProtoReader(buf, start, end)
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            if (field == 1 && wireType == 2) {
                val (s, e) = reader.readLengthDelimitedRange()
                return String(buf, s, e - s, Charsets.UTF_8)
            } else {
                reader.skip(wireType)
            }
        }
        return null
    }

    private fun decodeDomains(buf: ByteArray, start: Int, end: Int): List<String> {
        val out = mutableListOf<String>()
        val reader = ProtoReader(buf, start, end)
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            if (field == 2 && wireType == 2) {
                val (s, e) = reader.readLengthDelimitedRange()
                decodeDomain(buf, s, e)?.let { out.add(it) }
            } else {
                reader.skip(wireType)
            }
        }
        return out
    }

    /** Domain{ type: enum(1), value: string(2), attribute: repeated(3, игнорируем — только
     *  флаги вроде "только для China Ads", в наших правилах не нужны) }. Префиксы значений —
     *  ровно тот формат, что сам Xray принимает в routing.rules[].domain (см. Xray-core conf:
     *  без префикса — подстрока, "domain:" — поддомен, "full:" — точное совпадение,
     *  "regexp:" — регулярное выражение). */
    private fun decodeDomain(buf: ByteArray, start: Int, end: Int): String? {
        val reader = ProtoReader(buf, start, end)
        var type = 0L
        var value: String? = null
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            when {
                field == 1 && wireType == 0 -> type = reader.readVarint()
                field == 2 && wireType == 2 -> {
                    val (s, e) = reader.readLengthDelimitedRange()
                    value = String(buf, s, e - s, Charsets.UTF_8)
                }
                else -> reader.skip(wireType)
            }
        }
        val v = value ?: return null
        return when (type.toInt()) {
            1 -> "regexp:$v"
            2 -> "domain:$v"
            3 -> "full:$v"
            else -> v
        }
    }

    private fun decodeCidrs(buf: ByteArray, start: Int, end: Int): List<String> {
        val out = mutableListOf<String>()
        val reader = ProtoReader(buf, start, end)
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            if (field == 2 && wireType == 2) {
                val (s, e) = reader.readLengthDelimitedRange()
                decodeCidr(buf, s, e)?.let { out.add(it) }
            } else {
                reader.skip(wireType)
            }
        }
        return out
    }

    /** CIDR{ ip: bytes(1) — 4 байта (IPv4) или 16 байт (IPv6), prefix: uint32(2) }. */
    private fun decodeCidr(buf: ByteArray, start: Int, end: Int): String? {
        val reader = ProtoReader(buf, start, end)
        var ip: ByteArray? = null
        var prefix = 0L
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag()
            when {
                field == 1 && wireType == 2 -> {
                    val (s, e) = reader.readLengthDelimitedRange()
                    ip = buf.copyOfRange(s, e)
                }
                field == 2 && wireType == 0 -> prefix = reader.readVarint()
                else -> reader.skip(wireType)
            }
        }
        val addr = ip ?: return null
        return "${InetAddress.getByAddress(addr).hostAddress}/$prefix"
    }

    /** Курсор поверх среза общего буфера файла, без копирования каждого подсообщения —
     *  geosite.dat весит десятки МБ (одна категория "-all" — под 700 тыс. доменов), лишние
     *  копии по каждой непрошенной категории были бы заметны по памяти/времени на телефоне. */
    private class ProtoReader(private val buf: ByteArray, start: Int, private val end: Int) {
        var pos = start

        fun hasMore() = pos < end

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toLong() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0L) break
                shift += 7
            }
            return result
        }

        fun readTag(): Pair<Int, Int> {
            val tag = readVarint()
            return Pair((tag shr 3).toInt(), (tag and 0x7).toInt())
        }

        fun readLengthDelimitedRange(): Pair<Int, Int> {
            val len = readVarint().toInt()
            val s = pos
            pos += len
            return Pair(s, pos)
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                2 -> { val len = readVarint().toInt(); pos += len }
                1 -> pos += 8
                5 -> pos += 4
                else -> error("unsupported protobuf wire type $wireType")
            }
        }
    }
}
