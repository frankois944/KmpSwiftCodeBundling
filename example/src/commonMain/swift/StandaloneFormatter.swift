import Foundation

/// Deliberately depends on nothing else in this module: used to check that a change to another
/// bundled Swift file does not force this one to be recompiled.
public enum StandaloneFormatter {
    public static func shout(_ text: String) -> String {
        text.uppercased()
    }
}
