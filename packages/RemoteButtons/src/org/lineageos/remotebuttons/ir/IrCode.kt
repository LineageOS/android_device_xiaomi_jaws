/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.ir

/** The code one remote button sends. */
sealed interface IrCode {
    /** A code described by its protocol; any [org.lineageos.remotebuttons.remote.RemoteProgrammer] can encode it. */
    data class Protocol(val protocol: IrProtocol, val address: Int, val command: Int) : IrCode

    /**
     * A code already encoded for one remote family, e.g. imported from a vendor database.
     * Only programmers that know [format] accept it.
     */
    data class Raw(val format: String, val data: String) : IrCode
}
