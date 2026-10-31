/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.database.xiaomi

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

/**
 * Imports the IR database of the stock Xiaomi firmware from its `MiTvSetupCustomizer.apk`
 * (`assets/ir_*.xml`), or from a folder holding those XML files.
 */
class XiaomiIrDatabaseImporter(private val context: Context, private val db: XiaomiIrDatabase) {

    data class Result(val brands: Int, val codeSets: Int)

    /**
     * Places searched for a source, in order: the app's own folder (writable with `adb push`),
     * Download and, with all files access, the top of every USB drive or SD card.
     */
    fun searchLocations(): List<File> = buildList {
        context.getExternalFilesDir(null)?.let(::add)
        add(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS))
        if (Environment.isExternalStorageManager()) {
            context.getSystemService(StorageManager::class.java).storageVolumes
                .filter { it.isRemovable }
                .mapNotNull { it.directory }
                .forEach {
                    add(it)
                    add(File(it, Environment.DIRECTORY_DOWNLOADS))
                }
        }
    }

    /** Finds an APK or a folder of `ir_*.xml` files in [searchLocations]. */
    fun findSource(): File? = searchLocations().firstNotNullOfOrNull { dir ->
        when {
            !dir.isDirectory -> null
            File(dir, REMOTES_XML).canRead() -> dir
            else -> dir.listFiles { f -> f.name.endsWith(".apk") && f.canRead() }?.firstOrNull { apk ->
                runCatching { ZipFile(apk).use { it.getEntry("assets/$REMOTES_XML") != null } }.getOrDefault(false)
            }
        }
    }

    fun import(source: File): Result {
        val zip = if (source.isDirectory) null else ZipFile(source)
        val open: (String) -> InputStream = { name ->
            zip?.let { it.getInputStream(it.getEntry("assets/$name") ?: error("$name missing from ${source.name}")) }
                ?: File(source, name).inputStream()
        }
        try {
            var brands = 0
            var codeSets = 0
            db.replaceAll { w ->
                val usable = HashSet<Int>()
                parse(open(REMOTES_XML), "remote_controller") { rc ->
                    val id = rc.int("id") ?: return@parse
                    val descriptor = rc.children.firstOrNull { it.tag == "exts" }?.children.orEmpty()
                        .firstOrNull { it.text("tag") == DESCRIPTOR_TAG }?.text("value")?.trim()
                    if (descriptor.isNullOrEmpty()) return@parse
                    val keys = rc.all("key").associate { it.text("id").trim() to it.text("pulse").trim().ifEmpty { null } }
                    val volUp = keys[KEY_VOL_UP]
                    val volDown = keys[KEY_VOL_DOWN]
                    if (volUp == null || volDown == null) return@parse
                    w.codeSet(id, descriptor, keys[KEY_POWER], volUp, volDown)
                    usable += id
                }
                codeSets = usable.size
                val withCodes = HashSet<Int>()
                parse(open("ir_brand_remote_rel.xml"), "item") { item ->
                    val codeSet = item.int("remote_id")?.takeIf { it in usable } ?: return@parse
                    val brand = item.int("brand_id") ?: return@parse
                    val type = item.int("device_type_id") ?: return@parse
                    w.brandCodeSet(type, brand, codeSet, item.int("rank") ?: Int.MAX_VALUE)
                    withCodes += brand
                }
                parse(open("ir_brand.xml"), "brand") { b ->
                    val id = b.int("id")?.takeIf { it in withCodes } ?: return@parse
                    val name = b.text("en_name").ifBlank { b.text("name") }.trim()
                    if (name.isEmpty()) return@parse
                    // The stock "initial" is the pinyin of the Chinese name, so derive it from the shown name.
                    val first = name.first().uppercaseChar()
                    w.brand(id, name, if (first in 'A'..'Z') first.toString() else "#")
                    brands++
                }
                parse(open("ir_device_type_brand_rank.xml"), "item") { item ->
                    w.brandRank(
                        item.int("device_type_id") ?: return@parse,
                        item.int("brand_id") ?: return@parse,
                        item.int("rank") ?: return@parse,
                    )
                }
            }
            return Result(brands, codeSets)
        } finally {
            zip?.close()
        }
    }

    /** A parsed XML element: child element texts, plus nested elements. */
    private class Node(val tag: String) {
        val texts = HashMap<String, String>()
        val children = ArrayList<Node>()
        fun text(name: String) = texts[name].orEmpty()
        fun int(name: String) = texts[name]?.trim()?.toIntOrNull()
        fun all(tag: String): List<Node> = children.filter { it.tag == tag } + children.flatMap { it.all(tag) }
    }

    /**
     * Streams [record] elements out of a stock IR XML file. Those files are not always well formed
     * (`ir_device_type_brand_rank.xml` closes a `DOCUMENT` it never opens), so the `DOCUMENT`
     * wrapper is dropped and the records are wrapped in a synthetic root instead.
     */
    private fun parse(input: InputStream, record: String, onRecord: (Node) -> Unit) {
        val body = input.use { it.readBytes().decodeToString() }
            .let { if (it.startsWith("<?xml")) it.substring(it.indexOf("?>") + 2) else it }
            .replace("<DOCUMENT>", "")
            .replace("</DOCUMENT>", "")
        val parser = Xml.newPullParser()
        parser.setInput(StringReader("<ROOT>$body</ROOT>"))
        val stack = ArrayDeque<Node>()
        val text = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == record || stack.isNotEmpty()) stack.addLast(Node(parser.name))
                    text.clear()
                }

                XmlPullParser.TEXT -> text.append(parser.text)

                XmlPullParser.END_TAG -> {
                    val node = stack.removeLastOrNull() ?: continue
                    val parent = stack.lastOrNull()
                    when {
                        parent == null -> onRecord(node)
                        node.texts.isEmpty() && node.children.isEmpty() -> parent.texts[node.tag] = text.toString()
                        else -> parent.children += node
                    }
                    text.clear()
                }

                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private companion object {
        const val REMOTES_XML = "ir_remote_controller.xml"

        // From IrRemoteData in the stock wizard.
        const val DESCRIPTOR_TAG = "99999"
        const val KEY_POWER = "1"
        const val KEY_VOL_UP = "50"
        const val KEY_VOL_DOWN = "51"
    }
}
