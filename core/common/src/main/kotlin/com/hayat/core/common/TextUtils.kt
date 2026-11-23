package com.hayat.core.common

/** Escapes LIKE wildcards so user input is matched literally (used with `ESCAPE '\'`). */
fun String.escapeLike(): String = buildString {
    for (c in this@escapeLike) {
        if (c == '\\' || c == '%' || c == '_') append('\\')
        append(c)
    }
}

/** Normalises search input: trims and collapses whitespace. Keeps Bengali combining marks intact. */
fun String.normalizeQuery(): String = trim().replace(Regex("\\s+"), " ")
