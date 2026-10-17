package io.github.prismtv.data.catalog

import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Category
import io.github.prismtv.data.Channel
import io.github.prismtv.data.Country
import io.github.prismtv.data.Language
import io.github.prismtv.data.Region
import io.github.prismtv.data.Source
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Compact binary snapshot of the catalog. Reading ~18k channels this way takes a fraction of
 * the time JSON or SQLite would, which keeps cold start fast.
 */
object CatalogStore {
    private const val MAGIC = 0x50524953 // "PRIS"
    private const val VERSION = 2

    fun write(file: File, c: Catalog) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 128 * 1024)).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(VERSION)
            o.writeLong(c.updatedAt)

            o.writeInt(c.countries.size)
            c.countries.values.forEach { o.s(it.code); o.s(it.name); o.s(it.flag) }
            o.writeInt(c.languages.size)
            c.languages.values.forEach { o.s(it.code); o.s(it.name) }
            o.writeInt(c.categories.size)
            c.categories.values.forEach { o.s(it.id); o.s(it.name) }
            o.writeInt(c.regions.size)
            c.regions.forEach { r -> o.s(r.code); o.s(r.name); o.list(r.countries.toList()) }

            o.writeInt(c.channels.size)
            for (ch in c.channels) {
                o.s(ch.key); o.s(ch.name); o.list(ch.altNames); o.s(ch.network); o.s(ch.logo)
                o.s(ch.country); o.list(ch.categories); o.list(ch.languages)
                o.writeBoolean(ch.nsfw); o.s(ch.website)
                o.writeShort(ch.sources.size)
                for (s in ch.sources) {
                    o.s(s.url); o.s(s.label); o.s(s.quality); o.writeShort(s.height)
                    o.s(s.userAgent); o.s(s.referrer); o.writeByte(s.flags)
                }
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not save catalog")
        }
    }

    fun read(file: File): Catalog? {
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(FileInputStream(file), 128 * 1024)).use { i ->
            if (i.readInt() != MAGIC || i.readInt() != VERSION) return null
            val updatedAt = i.readLong()

            val countries = LinkedHashMap<String, Country>()
            repeat(i.readInt()) { val c = Country(i.s(), i.s(), i.s()); countries[c.code] = c }
            val languages = HashMap<String, Language>()
            repeat(i.readInt()) { val l = Language(i.s(), i.s()); languages[l.code] = l }
            val categories = LinkedHashMap<String, Category>()
            repeat(i.readInt()) { val c = Category(i.s(), i.s()); categories[c.id] = c }
            val regions = ArrayList<Region>()
            repeat(i.readInt()) { regions.add(Region(i.s(), i.s(), i.list().toSet())) }

            val n = i.readInt()
            val channels = ArrayList<Channel>(n)
            repeat(n) {
                val key = i.s()
                val name = i.s()
                val alt = i.list()
                val network = i.n()
                val logo = i.n()
                val country = i.s()
                val cats = i.list()
                val langs = i.list()
                val nsfw = i.readBoolean()
                val website = i.n()
                val sc = i.readShort().toInt()
                val sources = ArrayList<Source>(sc)
                repeat(sc) {
                    sources.add(
                        Source(
                            url = i.s(), label = i.s(), quality = i.n(), height = i.readShort().toInt(),
                            userAgent = i.n(), referrer = i.n(), flags = i.readByte().toInt(),
                        )
                    )
                }
                channels.add(Channel(key, name, alt, network, logo, country, cats, langs, nsfw, website, sources))
            }
            return Catalog(channels, countries, languages, categories, regions, updatedAt)
        }
    }

    private fun DataOutputStream.s(v: String?) = writeUTF(v ?: "")
    private fun DataOutputStream.list(v: List<String>) { writeShort(v.size); v.forEach { writeUTF(it) } }
    private fun DataInputStream.s(): String = readUTF()
    private fun DataInputStream.n(): String? = readUTF().ifEmpty { null }
    private fun DataInputStream.list(): List<String> {
        val n = readShort().toInt()
        if (n == 0) return emptyList()
        return List(n) { readUTF() }
    }
}
