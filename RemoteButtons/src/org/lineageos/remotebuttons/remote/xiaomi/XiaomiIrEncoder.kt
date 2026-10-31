/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.remote.xiaomi

import org.lineageos.remotebuttons.ir.IrCode
import org.lineageos.remotebuttons.ir.IrProtocol
import kotlin.math.roundToInt

/**
 * Encodes IR codes in the format of the Xiaomi BLE remote (Xiaomi TV Box S 2nd Gen and similar).
 *
 * A code set is one *descriptor* (carrier, bit timings, framing; shared by all keys) plus the
 * data bytes of each key. Descriptors are built from the published protocol timings, all
 * durations being expressed in carrier cycles:
 *
 * Pulse distance / width (kind 0):
 * `00 | data bytes | freq/10 u16 | repeat mode | 00 | frame period u16 |
 *  zero mark,space u16 | one mark,space u16 |
 *  leader: 01, 00 (mark+space) or 01 (mark only), length, u16... |
 *  bit split: length, (byte index, bit count)... | 00 |
 *  repeat frame: 00, or 01, length, u16...`
 *
 * Symbol list (kind 1), used for bi-phase protocols:
 * `01 | symbols per frame | freq/10 u16 | 02 | 00 | frame period u16 |
 *  table length | (length, u16 durations with 0x8000 marking a mark)... | 00`
 * Key data then lists the symbols of the frame: one bit each with two symbols (RC5), one hex
 * digit each with more (RC6).
 *
 * Key data is a `00` prefix followed by the frame; toggling protocols store both frames as
 * `A&B`. These layouts match the stock database: see `tools/validate_xiaomi_encoder.py`.
 */
object XiaomiIrEncoder {
    data class Encoded(val descriptor: String, val keyData: String)

    fun encode(code: IrCode.Protocol): Encoded? {
        val a = code.address
        val c = code.command
        return when (code.protocol) {
            IrProtocol.NEC -> Encoded(nec(), data(a, a.inv(), c, c.inv()))
            IrProtocol.NEC_EXT -> Encoded(nec(), data(a, a shr 8, c, c shr 8))
            IrProtocol.SAMSUNG32 -> Encoded(samsung(), data(a, a, c, c.inv()))
            IrProtocol.SIRC -> Encoded(sirc(12), data(c and 0x7F, a and 0x1F))
            IrProtocol.SIRC15 -> Encoded(sirc(15), data(c and 0x7F, a and 0xFF))
            IrProtocol.SIRC20 -> Encoded(sirc(20), data(c and 0x7F, a shr 5, a and 0x1F))
            IrProtocol.RC5, IrProtocol.RC5X -> Encoded(rc5(), rc5Frame(a, c, 0) + "&" + rc5Frame(a, c, 1))
            IrProtocol.RC6 -> Encoded(rc6(), rc6Frame(a, c, 0) + "&" + rc6Frame(a, c, 1))
            IrProtocol.KASEIKYO -> Encoded(kaseikyo(), kaseikyoData(a, c))
        }
    }

    private fun nec() = pulseDistance(
        dataBytes = 4, periodUs = 108_000, zero = 562 to 562, one = 562 to 1687,
        leader = listOf(9000, 4500), repeat = listOf(9000, 2250, 562), repeatMode = REPEAT_CODE,
    )

    private fun samsung() = pulseDistance(
        dataBytes = 4, periodUs = 108_000, zero = 562 to 562, one = 562 to 1687, leader = listOf(4500, 4500),
    )

    /** Sony: a 2.4 ms leader mark, then per bit a 600 us space and a 600 / 1200 us mark. */
    private fun sirc(bits: Int) = pulseDistance(
        dataBytes = if (bits == 20) 3 else 2,
        periodUs = 45_000, zero = 600 to 600, one = 600 to 1200, leader = listOf(2400),
        split = when (bits) {
            12 -> listOf(0 to 7, 1 to 5)
            15 -> listOf(0 to 7)
            else -> listOf(0 to 7, 2 to 5)
        },
        freq = 40_000,
    )

    private fun rc5(freq: Int = 36_000) = symbols(
        freq, symbolCount = 14, periodUs = 113_778,
        listOf(listOf(MARK to 889, SPACE to 889), listOf(SPACE to 889, MARK to 889)),
    )

    /** RC6 mode 0: leader, start bit, 3 mode bits, double-length toggle, 8 address and 8 command bits. */
    private fun rc6(freq: Int = 36_000) = symbols(
        freq, symbolCount = 22, periodUs = 106_667,
        listOf(
            listOf(SPACE to RC6_T, MARK to RC6_T), // 0
            listOf(MARK to RC6_T, SPACE to RC6_T), // 1
            listOf(SPACE to 2 * RC6_T, MARK to 2 * RC6_T), // toggle 0
            listOf(MARK to 2 * RC6_T, SPACE to 2 * RC6_T), // toggle 1
            listOf(MARK to 6 * RC6_T, SPACE to 2 * RC6_T), // leader
        ),
    )

    private fun rc6Frame(address: Int, command: Int, toggle: Int) = "00" + RC6_LEADER + "1" + "000" +
        (if (toggle == 0) RC6_TOGGLE_0 else RC6_TOGGLE_1) +
        (address and 0xFF).toString(2).padStart(8, '0') + (command and 0xFF).toString(2).padStart(8, '0')

    /** Panasonic (Kaseikyo): 3456 / 1728 us leader, 432 us unit, 48 bits. */
    private fun kaseikyo() = pulseDistance(
        dataBytes = 6, periodUs = 74_736, zero = 432 to 432, one = 432 to 1296, leader = listOf(3456, 1728),
        flags = 0x01, freq = 37_000,
    )

    /** Same packing as Flipper-IRDB: vendor id, vendor parity, genres, command, id, checksum. */
    private fun kaseikyoData(address: Int, command: Int): String {
        val vendor = address shr 8 and 0xFFFF
        val bytes = IntArray(6)
        bytes[0] = vendor and 0xFF
        bytes[1] = vendor shr 8
        val parity = bytes[0] xor bytes[1]
        bytes[2] = ((parity and 0xF) xor (parity shr 4)) or ((address shr 4 and 0xF) shl 4)
        bytes[3] = (address and 0xF) or ((command and 0xF) shl 4)
        bytes[4] = ((address shr 24 and 0x3) shl 6) or (command shr 4 and 0x3F)
        bytes[5] = bytes[2] xor bytes[3] xor bytes[4]
        return data(*bytes)
    }

    private fun symbols(freq: Int, symbolCount: Int, periodUs: Int, table: List<List<Pair<Boolean, Int>>>): String {
        val body = table.flatMap { symbol ->
            val durations = symbol.flatMap { (mark, us) -> u16(cycles(us, freq) or if (mark) 0x8000 else 0) }
            listOf(durations.size) + durations
        }
        return hex(
            listOf(0x01, symbolCount) + u16(freq / 10) + listOf(REPEAT_FRAME, 0x00) + u16(cycles(periodUs, freq)) +
                listOf(body.size) + body + 0x00,
        )
    }

    private fun rc5Frame(address: Int, command: Int, toggle: Int): String {
        val field = if (command >= 64) 0 else 1
        val bits = listOf(1, field, toggle) +
            (4 downTo 0).map { address shr it and 1 } +
            (5 downTo 0).map { command shr it and 1 }
        val bytes = bits.chunked(8).map { chunk -> chunk.fold(0) { acc, b -> acc shl 1 or b } shl (8 - chunk.size) }
        return "00" + hex(bytes)
    }

    private fun pulseDistance(
        dataBytes: Int,
        periodUs: Int,
        zero: Pair<Int, Int>,
        one: Pair<Int, Int>,
        leader: List<Int>,
        split: List<Pair<Int, Int>> = emptyList(),
        repeat: List<Int>? = null,
        repeatMode: Int = REPEAT_FRAME,
        flags: Int = 0x00,
        freq: Int = 38_000,
    ): String {
        val c = { us: Int -> u16(cycles(us, freq)) }
        return hex(
            buildList {
                addAll(listOf(0x00, dataBytes) + u16(freq / 10) + listOf(repeatMode, flags) + c(periodUs))
                addAll(c(zero.first) + c(zero.second) + c(one.first) + c(one.second))
                addAll(listOf(0x01, if (leader.size == 2) 0x00 else 0x01, 2 * leader.size) + leader.flatMap(c))
                addAll(listOf(2 * split.size) + split.flatMap { listOf(it.first, it.second) } + 0x00)
                addAll(if (repeat == null) listOf(0x00) else listOf(0x01, 2 * repeat.size) + repeat.flatMap(c))
            },
        )
    }

    private const val MARK = true
    private const val SPACE = false
    private const val RC6_T = 444
    private const val RC6_LEADER = "4"
    private const val RC6_TOGGLE_0 = "2"
    private const val RC6_TOGGLE_1 = "3"
    private const val REPEAT_CODE = 0x01
    private const val REPEAT_FRAME = 0x02

    private fun cycles(us: Int, freq: Int) = maxOf(1, (us.toLong() * freq / 1_000_000.0).roundToInt())

    private fun u16(v: Int) = listOf(v shr 8 and 0xFF, v and 0xFF)

    private fun data(vararg bytes: Int) = "00" + hex(bytes.map { it and 0xFF })

    private fun hex(bytes: List<Int>) = bytes.joinToString("") { "%02X".format(it and 0xFF) }
}
