package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun `lowerCamelCaseName joins the parts`() {
        assertEquals("processSwiftSourcesIosArm64", lowerCamelCaseName("processSwiftSources", "iosArm64"))
    }

    @Test
    fun `lowerCamelCaseName skips null and empty parts`() {
        assertEquals("processSwiftSourcesIosArm64", lowerCamelCaseName("processSwiftSources", null, "", "iosArm64"))
    }

    @Test
    fun `lowerCamelCaseName lowercases only the first part`() {
        assertEquals("unpackSwiftSourcesDebugFramework", lowerCamelCaseName("UnpackSwiftSources", "DebugFramework"))
    }

    @Test
    fun `collisionFreeIdentifier returns the name when it is free`() {
        assertEquals("example", "example".collisionFreeIdentifier(setOf("other")))
    }

    @Test
    fun `collisionFreeIdentifier appends underscores until it is free`() {
        assertEquals("example__", "example".collisionFreeIdentifier(setOf("example", "example_")))
    }
}
