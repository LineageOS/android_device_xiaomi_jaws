/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lineageos.remotebuttons.ir.CodeSet
import org.lineageos.remotebuttons.ir.DeviceType
import org.lineageos.remotebuttons.ir.IrCode
import org.lineageos.remotebuttons.ir.IrKey
import org.lineageos.remotebuttons.ir.IrProtocol

/**
 * The database bundled with the app, generated from Flipper-IRDB (CC0-1.0) by
 * `tools/build_ir_database.py`.
 */
class OpenIrDatabase(private val context: Context) : IrDatabase {

    private class Entry(
        val brand: Brand,
        val codeSets: Map<DeviceType, List<CodeSet>>,
        val models: Map<DeviceType, Int>,
    )

    private var entries: List<Entry>? = null

    private suspend fun entries(): List<Entry> = entries ?: withContext(Dispatchers.IO) {
        val json = context.assets.open(ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val brands = json.getJSONArray("brands")
        List(brands.length()) { i ->
            val brand = brands.getJSONObject(i)
            val name = brand.getString("name")
            val types = brand.getJSONObject("types")
            val models = brand.optJSONObject("models")
            Entry(
                Brand(name, name),
                DeviceType.entries.filter { types.has(it.id) }.associateWith { type ->
                    val sets = types.getJSONArray(type.id)
                    List(sets.length()) { n -> parseCodeSet("$name/${type.id}/$n", sets.getJSONObject(n)) }
                },
                DeviceType.entries.associateWith { models?.optInt(it.id) ?: 0 },
            )
        }.also { entries = it }
    }

    private fun parseCodeSet(id: String, json: JSONObject) = CodeSet(
        id,
        json.keys().asSequence().mapNotNull { name ->
            val key = IrKey.fromId(name) ?: return@mapNotNull null
            val code = json.getJSONObject(name)
            val protocol = IrProtocol.fromId(code.getString("p")) ?: return@mapNotNull null
            key to IrCode.Protocol(protocol, code.getInt("a"), code.getInt("c"))
        }.toMap(),
    )

    override suspend fun brands(type: DeviceType) =
        entries().filter { type in it.codeSets }.map { it.brand }

    override suspend fun popularBrands(type: DeviceType) = entries()
        .filter { type in it.codeSets && (it.models[type] ?: 0) >= POPULAR_MIN_MODELS }
        .sortedByDescending { it.models[type] }
        .take(POPULAR_BRANDS)
        .map { it.brand }

    override suspend fun codeSets(type: DeviceType, brand: Brand) =
        entries().firstOrNull { it.brand == brand }?.codeSets?.get(type).orEmpty()

    private companion object {
        const val ASSET = "ir_codes.json"
        const val POPULAR_BRANDS = 10

        /** Brands with fewer remote models than this are not worth listing first. */
        const val POPULAR_MIN_MODELS = 3
    }
}
