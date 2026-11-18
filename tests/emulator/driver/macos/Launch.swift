import AppKit
import Foundation

// Test-only background launch. The launcher PID is never treated as the application PID.
struct Stop: Error {}
func fail() throws -> Never { throw Stop() }
func read(_ url: URL) throws -> Data {
    guard url.path == url.resolvingSymlinksInPath().path else { try fail() }
    let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
    guard attributes[.type] as? FileAttributeType == .typeRegular,
          (attributes[.ownerAccountID] as? NSNumber)?.uint32Value == getuid(),
          (attributes[.posixPermissions] as? NSNumber)?.intValue == 0o600,
          (attributes[.size] as? NSNumber)?.intValue ?? Int.max <= 4096 else { try fail() }
    return try Data(contentsOf: url)
}
func emit(_ value: [String: Any]) throws {
    let data = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
    guard data.count <= 4096 else { try fail() }
    FileHandle.standardOutput.write(data); FileHandle.standardOutput.write(Data([10]))
}
func object(_ data: Data) throws -> [String: Any] {
    guard data.count <= 4096, let result = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { try fail() }
    return result
}
func text(_ value: [String: Any], _ key: String) throws -> String {
    guard let result = value[key] as? String, result.utf8.count <= 4096, !result.contains("\0") else { try fail() }
    return result
}
func pid(_ value: [String: Any], _ key: String) throws -> pid_t {
    guard let number = value[key] as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
          number.doubleValue.rounded() == number.doubleValue, number.int64Value > 0,
          number.int64Value <= Int64(Int32.max) else { try fail() }
    return number.int32Value
}
do {
    guard CommandLine.arguments.count == 3, CommandLine.arguments[1] == "--owner" else { try fail() }
    let url = URL(fileURLWithPath: CommandLine.arguments[2]), bytes = try read(url), config = try object(bytes)
    let coordinator = try pid(config, "coordinatorPid"), owner = try text(config, "ownerId")
    let parent = try FileManager.default.attributesOfItem(atPath: url.deletingLastPathComponent().path)
    guard Set(config.keys) == Set(["schemaVersion", "coordinatorPid", "ownerId", "appPath", "device"]),
          try pid(config, "schemaVersion") == 1, coordinator == getppid(),
          UUID(uuidString: owner)?.uuidString.lowercased() == owner,
          (parent[.ownerAccountID] as? NSNumber)?.uint32Value == getuid(),
          (parent[.posixPermissions] as? NSNumber)?.intValue == 0o700 else { try fail() }
    let appURL = URL(fileURLWithPath: try text(config, "appPath")), device = try text(config, "device")
    guard device.range(of: "^emulator-[1-9][0-9]{0,4}$", options: .regularExpression) != nil,
          let port = Int(device.dropFirst(9)), port >= 1024, port <= 65534, port % 2 == 0,
          appURL.path == appURL.resolvingSymlinksInPath().path, appURL.lastPathComponent == "Voboost Installer.app",
          Bundle(url: appURL)?.bundleIdentifier == "ru.voboost.installer",
          try appURL.resourceValues(forKeys: [.volumeIsReadOnlyKey]).volumeIsReadOnly == true else { try fail() }
    let options = NSWorkspace.OpenConfiguration(); options.activates = false
    options.createsNewApplicationInstance = true; options.arguments = ["--device", device]
    let beginning = Int64(Date().timeIntervalSince1970 * 1000)
    var application: NSRunningApplication?, finished = false, launchFailed = false
    // The receipt is emitted only from the exact NSWorkspace completion, never from process enumeration.
    NSWorkspace.shared.openApplication(at: appURL, configuration: options) { app, error in
        application = app; launchFailed = error != nil; finished = true
    }
    let deadline = Date(timeIntervalSinceNow: 20)
    while !finished && Date() < deadline { RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.02)) }
    guard finished, !launchFailed, let app = application, let launch = app.launchDate else { try fail() }
    let started = String(Int64(launch.timeIntervalSince1970 * 1000)), appPid = app.processIdentifier
    func scope() throws {
        guard getppid() == coordinator, try read(url) == bytes, !app.isTerminated,
              app.bundleIdentifier == "ru.voboost.installer", app.processIdentifier == appPid,
              app.bundleURL?.resolvingSymlinksInPath().path == appURL.path,
              app.executableURL?.resolvingSymlinksInPath().path == appURL.appendingPathComponent("Contents/MacOS/voboost-installer").path,
              app.launchDate.map({ String(Int64($0.timeIntervalSince1970 * 1000)) }) == started,
              Int64(started)! >= beginning else { try fail() }
    }
    try scope()
    try emit(["schemaVersion": 1, "ownerId": owner, "status": "launched", "pid": appPid,
              "startedMillis": started, "appPath": appURL.path, "device": device])
    // EOF retains the application for reconciliation. Only one exact owned release can terminate it.
    var input = Data()
    while let chunk = try FileHandle.standardInput.read(upToCount: 4096), !chunk.isEmpty {
        input.append(chunk); guard input.count <= 4096 else { try fail() }
        if input.last == 10 { break }
    }
    if input.isEmpty { exit(0) }
    guard input.last == 10 else { try fail() }; input.removeLast()
    let release = try object(input)
    guard Set(release.keys) == Set(["schemaVersion", "ownerId", "op", "pid", "startedMillis"]),
          try pid(release, "schemaVersion") == 1, try text(release, "ownerId") == owner,
          try text(release, "op") == "release", try pid(release, "pid") == appPid,
          try text(release, "startedMillis") == started else { try fail() }
    try scope(); guard app.terminate() else { try fail() }
    let ending = Date(timeIntervalSinceNow: 5)
    while !app.isTerminated && Date() < ending { RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.02)) }
    guard app.isTerminated else { try fail() }
    try emit(["schemaVersion": 1, "ownerId": owner, "status": "released", "pid": appPid, "startedMillis": started])
} catch {
    try? emit(["schemaVersion": 1, "status": "unknown", "reason": "owned-launch-or-release-unverified"])
    exit(2)
}
