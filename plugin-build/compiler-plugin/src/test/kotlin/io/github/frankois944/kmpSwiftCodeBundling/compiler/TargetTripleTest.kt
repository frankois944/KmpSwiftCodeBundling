package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTripleTest {
    @Test
    fun `renders a triple with an environment`() {
        assertEquals("arm64-apple-ios-simulator", TargetTriple("arm64", "apple", "ios", "simulator").toString())
    }

    @Test
    fun `renders a triple without an environment`() {
        assertEquals("arm64-apple-ios", TargetTriple("arm64", "apple", "ios", null).toString())
    }

    @Test
    fun `the minimum os version goes on the os component`() {
        // swiftc expects `-target arm64-apple-ios14.0-simulator`, not a version at the end.
        assertEquals(
            "arm64-apple-ios14.0-simulator",
            TargetTriple("arm64", "apple", "ios", "simulator").withOsVersion("14.0").toString(),
        )
    }

    @Test
    fun `recognises macOS, which alone uses a versioned bundle`() {
        assertTrue(TargetTriple("arm64", "apple", "macos", null).isMacos)
        assertFalse(TargetTriple("arm64", "apple", "ios", "simulator").isMacos)
    }
}
