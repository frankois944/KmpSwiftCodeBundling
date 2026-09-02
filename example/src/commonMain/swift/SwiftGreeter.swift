import Foundation

/// Swift code bundled into the framework produced by this module.
///
/// Declarations must be explicitly `public`: unlike Kotlin, Swift defaults to `internal`, so
/// anything without a visibility modifier would be invisible to the consumers of the framework.
public enum SwiftGreeter {
    /// Wraps the Kotlin API in something that reads naturally from Swift.
    public static func greet(_ name: String) -> String {
        Greeting().greet(name: name)
    }

    public static func shout(_ name: String) -> String {
        greet(name).uppercased() + "!"
    }
}
