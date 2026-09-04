import Foundation

/// Swift concurrency over a Kotlin `suspend` function.
///
/// `Greeting.greetLater` crosses into Objective-C as a completion-handler method, which Swift
/// imports as `async throws`. Wrapping it here means the app gets the `async` API it wants without
/// every consumer writing the same bridge.
@available(iOS 13.0, macOS 10.15, watchOS 6.0, tvOS 13.0, *)
public enum AsyncGreeter {
    public static func greet(_ name: String) async throws -> String {
        try await Greeting().greetLater(name: name)
    }
}
