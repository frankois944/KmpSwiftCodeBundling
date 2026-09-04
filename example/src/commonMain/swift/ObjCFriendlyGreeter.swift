import Foundation

/// The Swift that Objective-C consumers can see.
///
/// Swift reaches Objective-C only through `@objc` on an `NSObject` subclass, so this class is the
/// one thing here that lands in `ExampleKit-Swift.h`. Nothing else in this module is missing from
/// that header by accident - Swift-only consumers `import ExampleKit` and get the overlay module,
/// enums and extensions included.
@objc
public class ObjCFriendlyGreeter: NSObject {
    @objc
    public static func greet(_ name: String) -> String {
        SwiftGreeter.greet(name)
    }
}
