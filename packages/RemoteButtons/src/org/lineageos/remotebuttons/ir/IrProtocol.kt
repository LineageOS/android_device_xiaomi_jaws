/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.ir

/**
 * Consumer IR protocols, named as in Flipper-IRDB. Address and command values use the same
 * conventions as Flipper-IRDB (little-endian, protocol-specific widths).
 */
enum class IrProtocol(val id: String) {
    NEC("NEC"),
    NEC_EXT("NECext"),
    SAMSUNG32("Samsung32"),
    SIRC("SIRC"),
    SIRC15("SIRC15"),
    SIRC20("SIRC20"),
    RC5("RC5"),
    RC5X("RC5X"),
    RC6("RC6"),
    KASEIKYO("Kaseikyo");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}
