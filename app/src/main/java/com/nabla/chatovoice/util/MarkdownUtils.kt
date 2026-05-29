package com.nabla.chatovoice.util

/**
 * Strips common markdown syntax from [text], returning plain readable text.
 *
 * Handles: headers, bold, italic, strikethrough, inline code, bullet lists,
 * numbered lists, and inline links.
 */
fun stripMarkdown(text: String): String =
    text
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")   // headers
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")                   // bold
        .replace(Regex("\\*(.+?)\\*"), "$1")                         // italic
        .replace(Regex("__(.+?)__"), "$1")                           // bold alt
        .replace(Regex("_(.+?)_"), "$1")                             // italic alt
        .replace(Regex("~~(.+?)~~"), "$1")                           // strikethrough
        .replace(Regex("`(.+?)`"), "$1")                             // inline code
        .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "") // bullets
        .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "") // numbered lists
        .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")               // links
        .trim()
