import Foundation
import UIKit

/// Swift bundled only into the iOS variants of the framework.
///
/// It lives in `src/iosMain/swift`, so it travels with the `iosMain` source set and can import
/// UIKit - which would not compile for a macOS or watchOS target. The rule is the one Kotlin
/// already follows: a source set's Swift reaches exactly the frameworks its Kotlin reaches.
public enum IosOnlyGreeter {
    public static func deviceGreeting() -> String {
        SwiftGreeter.greet(UIDevice.current.name)
    }
}
