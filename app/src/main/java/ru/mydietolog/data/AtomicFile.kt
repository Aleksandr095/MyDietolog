package ru.mydietolog.data

import java.io.File

/** Пишет файл через временный и переименование, чтобы не оставить обрезанный JSON. */
internal fun File.writeAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        writeText(text)
        tmp.delete()
    }
}
