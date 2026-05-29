package com.nabla.chatovoice.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StripMarkdownTest {

    @Test
    fun `stripMarkdown removes h1 header`() {
        assertEquals("Hello", stripMarkdown("# Hello"))
    }

    @Test
    fun `stripMarkdown removes h2 header`() {
        assertEquals("Hello", stripMarkdown("## Hello"))
    }

    @Test
    fun `stripMarkdown removes h3 header`() {
        assertEquals("Hello", stripMarkdown("### Hello"))
    }

    @Test
    fun `stripMarkdown removes bold`() {
        assertEquals("Hello world", stripMarkdown("**Hello** world"))
    }

    @Test
    fun `stripMarkdown removes italic asterisk`() {
        assertEquals("Hello world", stripMarkdown("*Hello* world"))
    }

    @Test
    fun `stripMarkdown removes bold underscore`() {
        assertEquals("Hello world", stripMarkdown("__Hello__ world"))
    }

    @Test
    fun `stripMarkdown removes italic underscore`() {
        assertEquals("Hello world", stripMarkdown("_Hello_ world"))
    }

    @Test
    fun `stripMarkdown removes strikethrough`() {
        assertEquals("Hello world", stripMarkdown("~~Hello~~ world"))
    }

    @Test
    fun `stripMarkdown removes inline code`() {
        assertEquals("Hello world", stripMarkdown("`Hello` world"))
    }

    @Test
    fun `stripMarkdown removes bullet list markers`() {
        val input = "- item one\n- item two"
        val expected = "item one\nitem two"
        assertEquals(expected, stripMarkdown(input))
    }

    @Test
    fun `stripMarkdown removes numbered list markers`() {
        val input = "1. first\n2. second"
        val expected = "first\nsecond"
        assertEquals(expected, stripMarkdown(input))
    }

    @Test
    fun `stripMarkdown removes links but keeps label`() {
        assertEquals("click here", stripMarkdown("[click here](https://example.com)"))
    }

    @Test
    fun `stripMarkdown preserves plain text`() {
        val plain = "Hello, this is a test"
        assertEquals(plain, stripMarkdown(plain))
    }

    @Test
    fun `stripMarkdown handles empty string`() {
        assertEquals("", stripMarkdown(""))
    }

    @Test
    fun `stripMarkdown handles multiline mixed markdown`() {
        val input = "## Meeting Notes\n\nThis is **important** and _should_ be plain.\n\n- Task one\n- Task two"
        val result = stripMarkdown(input)
        assert(!result.contains("##")) { "Headers should be removed" }
        assert(!result.contains("**")) { "Bold markers should be removed" }
        assert(!result.contains("_")) { "Italic markers should be removed" }
        assert(!result.startsWith("-")) { "Bullet markers should be removed" }
    }
}
