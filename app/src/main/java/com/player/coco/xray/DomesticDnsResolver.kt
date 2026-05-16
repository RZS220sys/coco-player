package com.player.coco.xray

import okhttp3.Dns
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

class DomesticDnsResolver(
    private val dnsServers: List<String>,
    private val useIpv4Only: Boolean,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (dnsServers.isEmpty() || isIpLiteral(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val recordTypes = if (useIpv4Only) listOf(TYPE_A) else listOf(TYPE_A, TYPE_AAAA)
        val addresses = linkedSetOf<InetAddress>()
        var lastError: Exception? = null

        dnsServers.forEach { server ->
            recordTypes.forEach { recordType ->
                try {
                    addresses.addAll(query(server, hostname, recordType))
                } catch (error: Exception) {
                    lastError = error
                }
            }
        }

        if (addresses.isNotEmpty()) {
            return addresses.toList()
        }

        throw UnknownHostException("Could not resolve $hostname with domestic DNS servers.")
            .apply { lastError?.let { initCause(it) } }
    }

    private fun query(server: String, hostname: String, recordType: Int): List<InetAddress> {
        val (serverHost, serverPort) = parseServer(server)
        val packet = buildQuery(hostname, recordType)
        DatagramSocket().use { socket ->
            socket.soTimeout = DNS_TIMEOUT_MILLIS
            socket.send(
                DatagramPacket(
                    packet,
                    packet.size,
                    InetSocketAddress(InetAddress.getByName(serverHost), serverPort)
                )
            )

            val buffer = ByteArray(MAX_DNS_PACKET_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            return try {
                socket.receive(response)
                parseResponse(buffer.copyOf(response.length), hostname, recordType)
            } catch (error: SocketTimeoutException) {
                throw UnknownHostException("Domestic DNS server timed out: $server").apply { initCause(error) }
            }
        }
    }

    private fun buildQuery(hostname: String, recordType: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val transactionId = Random.nextInt(0, 0xffff)
        output.writeShort(transactionId)
        output.writeShort(0x0100)
        output.writeShort(1)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(0)

        hostname.trimEnd('.').split(".").forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.writeShort(recordType)
        output.writeShort(CLASS_IN)
        return output.toByteArray()
    }

    private fun parseResponse(response: ByteArray, hostname: String, recordType: Int): List<InetAddress> {
        if (response.size < DNS_HEADER_SIZE) {
            return emptyList()
        }

        var offset = DNS_HEADER_SIZE
        val questionCount = response.readUnsignedShort(4)
        val answerCount = response.readUnsignedShort(6)

        repeat(questionCount) {
            offset = skipName(response, offset)
            offset += 4
        }

        val addresses = mutableListOf<InetAddress>()
        repeat(answerCount) {
            offset = skipName(response, offset)
            if (offset + 10 > response.size) {
                return addresses
            }

            val answerType = response.readUnsignedShort(offset)
            offset += 2
            val answerClass = response.readUnsignedShort(offset)
            offset += 2
            offset += 4
            val dataLength = response.readUnsignedShort(offset)
            offset += 2

            if (offset + dataLength > response.size) {
                return addresses
            }

            val isAddressType = answerType == recordType &&
                answerClass == CLASS_IN &&
                (dataLength == IPV4_BYTES || dataLength == IPV6_BYTES)
            if (isAddressType) {
                val bytes = response.copyOfRange(offset, offset + dataLength)
                addresses.add(InetAddress.getByAddress(hostname, bytes))
            }
            offset += dataLength
        }

        return addresses
    }

    private fun skipName(response: ByteArray, startOffset: Int): Int {
        var offset = startOffset
        while (offset < response.size) {
            val length = response[offset].toInt() and 0xff
            if (length == 0) {
                return offset + 1
            }
            if ((length and 0xc0) == 0xc0) {
                return offset + 2
            }
            offset += length + 1
        }
        return response.size
    }

    private fun parseServer(value: String): Pair<String, Int> {
        val trimmed = value.trim()
        if (trimmed.startsWith("[") && "]" in trimmed) {
            val host = trimmed.substringAfter("[").substringBefore("]")
            val port = trimmed.substringAfter("]", "").trimStart(':').toIntOrNull() ?: DNS_PORT
            return host to port
        }

        val hasSingleColon = trimmed.count { it == ':' } == 1
        if (hasSingleColon) {
            return trimmed.substringBefore(":") to (trimmed.substringAfter(":").toIntOrNull() ?: DNS_PORT)
        }

        return trimmed to DNS_PORT
    }

    private fun isIpLiteral(hostname: String): Boolean {
        return IPV4_PATTERN.matches(hostname) || ":" in hostname
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int {
        return ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value shr 8) and 0xff)
        write(value and 0xff)
    }

    companion object {
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MILLIS = 3000
        private const val DNS_HEADER_SIZE = 12
        private const val MAX_DNS_PACKET_SIZE = 512
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val CLASS_IN = 1
        private const val IPV4_BYTES = 4
        private const val IPV6_BYTES = 16
        private val IPV4_PATTERN = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    }
}
