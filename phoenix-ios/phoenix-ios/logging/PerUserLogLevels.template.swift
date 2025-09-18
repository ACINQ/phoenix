/// Modify this value to use per-developer log levels (for debugging).
/// Then use it in code like this:
/// ```
/// #if DEBUG
/// if DEVELOPER == "robbie_hanson" {
///   fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
/// } else {
///   fileprivate let log = LoggerFactory.shared.logger(filename, .info)
/// }
/// #else
/// fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
/// #endif
/// ```
let DEVELOPER = "unknown_developer"

// Source control:
// * PerUserLogLevels.template.swift
//   - Checked into git
//
// * PerUserLogLevels.swift
//   - NOT checked into git
//   - Added to .gitignore
//   - Added to Xcode project
//   - Automatically generated from template file if missing during compilation
