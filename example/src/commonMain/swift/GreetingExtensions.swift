import Foundation

/// Swift extensions on the Kotlin types of this very module.
///
/// This is what the overlay module buys: the bundled Swift is compiled against the framework's own
/// Objective-C module, so it can extend the classes Kotlin exported as if they were its own.
/// Consumers see a single API and cannot tell which language a member was written in.
public extension Greeting {
    /// Swift naming for a Kotlin method - `greeting(for:)` rather than `greet(name:)`.
    func greeting(for name: String) -> String {
        greet(name: name)
    }

    /// Something Kotlin never exported at all: a Swift-only affordance on a Kotlin class.
    func greeting(for names: [String]) -> String {
        names
            .map { greeting(for: $0) }
            .joined(separator: " ")
    }
}
