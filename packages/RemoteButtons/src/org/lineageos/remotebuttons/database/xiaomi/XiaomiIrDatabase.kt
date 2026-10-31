/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.remotebuttons.database.xiaomi

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lineageos.remotebuttons.database.Brand
import org.lineageos.remotebuttons.database.IrDatabase
import org.lineageos.remotebuttons.ir.CodeSet
import org.lineageos.remotebuttons.ir.DeviceType
import org.lineageos.remotebuttons.ir.IrCode
import org.lineageos.remotebuttons.ir.IrKey
import org.lineageos.remotebuttons.remote.xiaomi.XiaomiRemoteProgrammer

/**
 * The IR database of the stock Xiaomi firmware, imported by the user with
 * [XiaomiIrDatabaseImporter]. It is not redistributable, so it is never bundled.
 * Its codes are already in the Xiaomi remote format.
 */
class XiaomiIrDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "xiaomi_ir.sqlite", null, 1), IrDatabase {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE brand (id INTEGER PRIMARY KEY, name TEXT NOT NULL, initial TEXT NOT NULL)")
        db.execSQL("CREATE TABLE type_brand_rank (type INTEGER, brand INTEGER, rank INTEGER)")
        db.execSQL("CREATE TABLE brand_code_set (type INTEGER, brand INTEGER, code_set INTEGER, rank INTEGER)")
        db.execSQL(
            "CREATE TABLE code_set (id INTEGER PRIMARY KEY, descriptor TEXT NOT NULL, " +
                "power TEXT, vol_up TEXT, vol_down TEXT)",
        )
        db.execSQL("CREATE INDEX brand_code_set_idx ON brand_code_set (type, brand, rank)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun isPopulated(): Boolean = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT 1 FROM code_set LIMIT 1", null).use { it.moveToFirst() }
    }

    override suspend fun brands(type: DeviceType) = queryBrands(
        "SELECT DISTINCT b.id, b.name FROM brand b JOIN brand_code_set r ON r.brand = b.id " +
            "WHERE r.type = ? ORDER BY b.initial, b.name COLLATE NOCASE",
        type.dbId.toString(),
    )

    override suspend fun popularBrands(type: DeviceType) = queryBrands(
        "SELECT b.id, b.name FROM type_brand_rank p JOIN brand b ON b.id = p.brand " +
            "WHERE p.type = ? AND EXISTS (SELECT 1 FROM brand_code_set r WHERE r.type = p.type AND r.brand = p.brand) " +
            "ORDER BY p.rank LIMIT $POPULAR_BRANDS",
        type.dbId.toString(),
    )

    override suspend fun codeSets(type: DeviceType, brand: Brand) = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT s.id, s.descriptor, s.power, s.vol_up, s.vol_down FROM brand_code_set r " +
                "JOIN code_set s ON s.id = r.code_set WHERE r.type = ? AND r.brand = ? ORDER BY r.rank",
            arrayOf(type.dbId.toString(), brand.id),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val keys = listOf(IrKey.POWER to 2, IrKey.VOLUME_UP to 3, IrKey.VOLUME_DOWN to 4)
                        .mapNotNull { (key, column) ->
                            c.getString(column)?.let { key to IrCode.Raw(XiaomiRemoteProgrammer.RAW_FORMAT, it) }
                        }
                        .toMap()
                    add(CodeSet("xiaomi/${c.getInt(0)}", keys, rawParameters = c.getString(1)))
                }
            }
        }
    }

    private suspend fun queryBrands(sql: String, vararg args: String) = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(Brand(c.getInt(0).toString(), c.getString(1))) }
        }
    }

    /** Replaces the whole database contents inside one transaction. */
    fun replaceAll(fill: (Writer) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("brand", "type_brand_rank", "brand_code_set", "code_set").forEach { db.delete(it, null, null) }
            fill(Writer(db))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    class Writer(private val db: SQLiteDatabase) {
        fun brand(id: Int, name: String, initial: String) = insert("brand") {
            put("id", id); put("name", name); put("initial", initial)
        }

        fun brandRank(type: Int, brand: Int, rank: Int) = insert("type_brand_rank") {
            put("type", type); put("brand", brand); put("rank", rank)
        }

        fun brandCodeSet(type: Int, brand: Int, codeSet: Int, rank: Int) = insert("brand_code_set") {
            put("type", type); put("brand", brand); put("code_set", codeSet); put("rank", rank)
        }

        fun codeSet(id: Int, descriptor: String, power: String?, volUp: String?, volDown: String?) =
            insert("code_set") {
                put("id", id); put("descriptor", descriptor)
                put("power", power); put("vol_up", volUp); put("vol_down", volDown)
            }

        private inline fun insert(table: String, values: ContentValues.() -> Unit) {
            db.insertWithOnConflict(table, null, ContentValues().apply(values), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    companion object {
        private const val POPULAR_BRANDS = 10

        @Volatile
        private var instance: XiaomiIrDatabase? = null

        /** Device type ids in the stock database; soundbars and receivers share "PA" codes. */
        private val DeviceType.dbId
            get() = when (this) {
                DeviceType.TV -> 2
                DeviceType.SOUNDBAR, DeviceType.RECEIVER -> 7
            }

        /** One helper per process, so the wizard, the TV Settings page and the import share it. */
        fun get(context: Context): XiaomiIrDatabase = instance ?: synchronized(this) {
            instance ?: XiaomiIrDatabase(context.applicationContext).also { instance = it }
        }
    }
}
