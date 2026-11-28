package com.memora.app.export

import com.memora.app.data.repository.TopilmalarBackup
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TopilmalarDocumentWriter {
    fun html(backup: TopilmalarBackup, includeComments: Boolean): String {
        val cardsBySource = backup.cards.groupBy { it.sourceId }
        val comments = backup.comments.groupBy { it.cardId }
        val sources = backup.sources.associateBy { it.id }
        return buildString {
            append("<!doctype html><html lang=\"uz\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>Memora — Topilmalar</title><style>body{font-family:system-ui;max-width:860px;margin:40px auto;padding:0 20px;background:#f7f6f2;color:#1d2823}section{margin:34px 0}article{background:white;border-radius:16px;padding:20px;margin:12px 0;box-shadow:0 2px 14px #0000000d}.meta{color:#64736b;font-size:.9rem}.comment{border-left:3px solid #74a090;padding:7px 12px;margin:9px 0;color:#39473f}h1,h2{color:#2f5d50}</style></head><body>")
            append("<h1>Memora — Topilmalar</h1><p class=\"meta\">${backup.sources.size} manba · ${backup.cards.size} topilma</p>")
            val orderedKeys = cardsBySource.keys.sortedWith(compareBy<String?> { it == null }.thenBy { sources[it]?.title.orEmpty() })
            orderedKeys.forEach { sourceId ->
                val source = sources[sourceId]
                append("<section><h2>${escape(source?.title ?: "Manbasi aniqlanmagan")}</h2>")
                source?.author?.let { append("<p class=\"meta\">${escape(it)}</p>") }
                cardsBySource[sourceId].orEmpty().sortedByDescending { it.createdAt }.forEach { card ->
                    append("<article><div>${escape(card.text).replace("\n", "<br>")}</div>")
                    append("<p class=\"meta\">${java.time.Instant.ofEpochMilli(card.createdAt)}</p>")
                    if (includeComments) comments[card.id].orEmpty().forEach { comment ->
                        append("<div class=\"comment\">${escape(comment.text)}</div>")
                    }
                    append("</article>")
                }
                append("</section>")
            }
            append("</body></html>")
        }
    }

    fun docx(backup: TopilmalarBackup, includeComments: Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        val cardsBySource = backup.cards.groupBy { it.sourceId }
        val comments = backup.comments.groupBy { it.cardId }
        val sources = backup.sources.associateBy { it.id }
        val body = buildString {
            paragraph("Memora — Topilmalar", "Title")
            cardsBySource.keys.sortedWith(compareBy<String?> { it == null }.thenBy { sources[it]?.title.orEmpty() }).forEach { sourceId ->
                val source = sources[sourceId]
                paragraph(source?.title ?: "Manbasi aniqlanmagan", "Heading1")
                source?.author?.let { paragraph(it, "Subtitle") }
                cardsBySource[sourceId].orEmpty().sortedByDescending { it.createdAt }.forEach { card ->
                    paragraph(card.text, null)
                    if (includeComments) comments[card.id].orEmpty().forEach { paragraph("Izoh: ${it.text}", "Quote") }
                }
            }
        }
        ZipOutputStream(output).use { zip ->
            add(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""")
            add(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            add(zip, "word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>""")
            add(zip, "word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            add(zip, "word/styles.xml", styles())
        }
        return output.toByteArray()
    }

    private fun StringBuilder.paragraph(text: String, style: String?) {
        append("<w:p>")
        if (style != null) append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>")
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) append("<w:r><w:br/></w:r>")
            append("<w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r>")
        }
        append("</w:p>")
    }

    private fun add(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="24"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="40"/><w:color w:val="2F5D50"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="Heading 1"/><w:rPr><w:b/><w:sz w:val="32"/><w:color w:val="2F5D50"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:rPr><w:i/><w:color w:val="64736B"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:pPr><w:ind w:left="480"/></w:pPr><w:rPr><w:i/><w:color w:val="52665B"/></w:rPr></w:style></w:styles>"""
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun xml(value: String) = escape(value).replace("'", "&apos;")
}
