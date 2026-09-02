import Foundation
import UIKit

/// Swift code bundled only into the iOS variants of the framework.
public enum IosOnlyGreeter {
    public static func deviceGreeting() -> String {
        SwiftGreeter.greet(UIDevice.current.name)
    }
}
