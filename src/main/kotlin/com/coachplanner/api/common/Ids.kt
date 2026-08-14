package com.coachplanner.api.common

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

private val random = SecureRandom()

/**
 * UUIDv7 (RFC 9562): a 48-bit millisecond Unix timestamp followed by random
 * bits, with the version/variant nibbles set per spec. Time-ordered so
 * primary-key B-tree inserts stay sequential rather than scattering across
 * pages (AD-105). java.util.UUID has no v7 factory method, so this is
 * hand-rolled from the RFC's byte layout rather than adding a dependency
 * for one function.
 *
 * Byte layout (16 bytes):
 *   0-5   48-bit unix_ts_ms, big-endian
 *   6     top nibble = version (0111 = 7); bottom nibble = top 4 bits of rand_a
 *   7     bottom 8 bits of rand_a (rand_a is 12 random bits total)
 *   8     top 2 bits = variant (10); bottom 6 bits = top 6 bits of rand_b
 *   9-15  remaining 56 bits of rand_b (62 random bits total)
 */
fun newId(): UUID {
    val bytes = ByteArray(16)
    val timestamp = System.currentTimeMillis()

    bytes[0] = (timestamp shr 40).toByte()
    bytes[1] = (timestamp shr 32).toByte()
    bytes[2] = (timestamp shr 24).toByte()
    bytes[3] = (timestamp shr 16).toByte()
    bytes[4] = (timestamp shr 8).toByte()
    bytes[5] = timestamp.toByte()

    val randomBytes = ByteArray(10)
    random.nextBytes(randomBytes)
    System.arraycopy(randomBytes, 0, bytes, 6, 10)

    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // version 7
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variant 10

    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}
