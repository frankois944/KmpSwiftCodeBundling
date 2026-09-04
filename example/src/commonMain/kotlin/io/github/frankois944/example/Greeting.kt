package io.github.frankois944.example

public class Greeting {
    public fun greet(name: String): String = "Hello, $name!"

    /**
     * Suspending on purpose, to show what the bundled Swift can wrap.
     *
     * Kotlin/Native exports a suspending function as an Objective-C completion-handler method,
     * which Swift imports as `async throws` - see `AsyncGreeter`.
     */
    public suspend fun greetLater(name: String): String = greet(name)
}
