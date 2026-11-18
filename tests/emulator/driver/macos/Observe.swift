import AppKit
import ApplicationServices
import Foundation

// Root-test-only observer. It never activates an application or requests AX/TCC permission.
struct Failure: Error {
    let reason: String
    let dispatched: Bool
    init(_ reason: String, _ dispatched: Bool = false) { self.reason = reason; self.dispatched = dispatched }
}

func object(_ data: Data, _ maximum: Int) throws -> [String: Any] {
    guard data.count <= maximum, let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure("protocol") }
    return value
}
func string(_ value: [String: Any], _ key: String) throws -> String {
    guard let result = value[key] as? String, !result.contains("\0"), result.utf8.count <= 4096 else { throw Failure("protocol") }
    return result
}
func integer(_ value: [String: Any], _ key: String) throws -> Int32 {
    guard let number = value[key] as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
          number.doubleValue.isFinite, number.doubleValue.rounded() == number.doubleValue,
          number.int64Value > 0, number.int64Value <= Int64(Int32.max) else { throw Failure("protocol") }
    return number.int32Value
}
func uuid(_ value: String) -> Bool { UUID(uuidString: value)?.uuidString.lowercased() == value }
func regular(_ url: URL, maximum: Int) throws -> Data {
    guard url.path == url.resolvingSymlinksInPath().path else { throw Failure("owner") }
    let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
    guard attributes[.type] as? FileAttributeType == .typeRegular,
          (attributes[.ownerAccountID] as? NSNumber)?.uint32Value == getuid(),
          (attributes[.posixPermissions] as? NSNumber)?.intValue == 0o600,
          (attributes[.size] as? NSNumber)?.intValue ?? Int.max <= maximum else { throw Failure("owner") }
    return try Data(contentsOf: url)
}

final class Observer {
    let ownerURL: URL
    let ownerBytes: Data
    let owner: String
    let coordinator: pid_t
    let appURL: URL
    let pid: pid_t
    let started: String
    let application: AXUIElement
    var window: AXUIElement?
    var windowToken: String?
    var observation: String?
    var nodes: [String: (AXUIElement, [Int], NSDictionary)] = [:]
    var usedActions: Set<String> = []
    var deadline = ProcessInfo.processInfo.systemUptime

    init(_ ownerURL: URL) throws {
        self.ownerURL = ownerURL
        ownerBytes = try regular(ownerURL, maximum: 4096)
        let config = try object(ownerBytes, 4096)
        coordinator = try integer(config, "coordinatorPid")
        guard Set(config.keys) == Set(["schemaVersion", "ownerId", "coordinatorPid", "appPath", "pid", "startedMillis", "windowTitle"]),
              try integer(config, "schemaVersion") == 1, coordinator == getppid(),
              try string(config, "windowTitle") == "Voboost Installer" else { throw Failure("owner") }
        owner = try string(config, "ownerId")
        guard uuid(owner) else { throw Failure("owner") }
        appURL = URL(fileURLWithPath: try string(config, "appPath"))
        pid = try integer(config, "pid")
        started = try string(config, "startedMillis")
        guard started.range(of: "^[1-9][0-9]{0,18}$", options: .regularExpression) != nil else { throw Failure("owner") }
        let parent = try FileManager.default.attributesOfItem(atPath: ownerURL.deletingLastPathComponent().path)
        guard (parent[.posixPermissions] as? NSNumber)?.intValue == 0o700,
              (parent[.ownerAccountID] as? NSNumber)?.uint32Value == getuid() else { throw Failure("owner") }
        application = AXUIElementCreateApplication(pid)
        AXUIElementSetMessagingTimeout(application, 1)
    }
    func budget() throws { if ProcessInfo.processInfo.systemUptime >= deadline { throw Failure("timeout") } }
    func attribute(_ element: AXUIElement, _ name: CFString) throws -> CFTypeRef? {
        try budget()
        var value: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(element, name, &value)
        if result == .noValue || result == .attributeUnsupported { return nil }
        guard result == .success else { throw Failure("tree") }
        return value
    }
    func text(_ element: AXUIElement, _ name: CFString) throws -> String {
        guard let value = try attribute(element, name) else { return "" }
        let result: String
        if let string = value as? String { result = string }
        else if let number = value as? NSNumber { result = number.stringValue }
        else { return "" }
        guard result.utf8.count <= 4096, !result.contains("\0") else { throw Failure("tree") }
        return result
    }
    func elements(_ element: AXUIElement, _ name: CFString) throws -> [AXUIElement] {
        guard let value = try attribute(element, name) else { return [] }
        guard let array = value as? [AXUIElement], array.count <= 5000 else { throw Failure("tree") }
        return array
    }
    func children(_ element: AXUIElement) throws -> [AXUIElement] {
        return try elements(element, kAXChildrenAttribute as CFString)
    }
    func frame(_ element: AXUIElement) throws -> [Double]? {
        guard let position = try attribute(element, kAXPositionAttribute as CFString),
              let size = try attribute(element, kAXSizeAttribute as CFString),
              CFGetTypeID(position) == AXValueGetTypeID(), CFGetTypeID(size) == AXValueGetTypeID() else { return nil }
        var point = CGPoint.zero, dimensions = CGSize.zero
        guard AXValueGetValue(unsafeBitCast(position, to: AXValue.self), .cgPoint, &point),
              AXValueGetValue(unsafeBitCast(size, to: AXValue.self), .cgSize, &dimensions) else { throw Failure("tree") }
        let result = [point.x, point.y, dimensions.width, dimensions.height].map(Double.init)
        guard result.allSatisfy({ $0.isFinite && abs($0) <= 1_000_000 }), result[2] >= 0, result[3] >= 0 else { throw Failure("tree") }
        return result
    }
    func identity(_ element: AXUIElement) throws -> NSDictionary {
        var actual: pid_t = 0
        guard AXUIElementGetPid(element, &actual) == .success, actual == pid else { throw Failure("identity") }
        let role = try text(element, kAXRoleAttribute as CFString)
        let subrole = try text(element, kAXSubroleAttribute as CFString)
        let secure = subrole == "AXSecureTextField"
        return ["role": role, "subrole": subrole,
                "title": try text(element, kAXTitleAttribute as CFString),
                "description": try text(element, kAXDescriptionAttribute as CFString),
                "identifier": try text(element, kAXIdentifierAttribute as CFString),
                "value": secure ? "" : try text(element, kAXValueAttribute as CFString),
                "enabled": (try attribute(element, kAXEnabledAttribute as CFString) as? Bool) ?? false,
                "frame": try frame(element) as Any? ?? NSNull(), "redacted": secure] as NSDictionary
    }
    func scope(_ requestedWindow: String?) throws -> AXUIElement {
        guard AXIsProcessTrusted() else { throw Failure("permission") }
        guard try regular(ownerURL, maximum: 4096) == ownerBytes, getppid() == coordinator else { throw Failure("owner") }
        guard appURL.lastPathComponent == "Voboost Installer.app", appURL.path == appURL.resolvingSymlinksInPath().path,
              try appURL.resourceValues(forKeys: [.volumeIsReadOnlyKey]).volumeIsReadOnly == true,
              Bundle(url: appURL)?.bundleIdentifier == "ru.voboost.installer",
              let app = NSRunningApplication(processIdentifier: pid), !app.isTerminated,
              app.bundleIdentifier == "ru.voboost.installer", app.bundleURL?.resolvingSymlinksInPath().path == appURL.path,
              app.executableURL?.resolvingSymlinksInPath().path == appURL.appendingPathComponent("Contents/MacOS/voboost-installer").path,
              let launch = app.launchDate, String(Int64(launch.timeIntervalSince1970 * 1000)) == started else { throw Failure("scope") }
        var matches: [AXUIElement] = []
        for candidate in try elements(application, kAXWindowsAttribute as CFString) {
            if try text(candidate, kAXTitleAttribute as CFString) == "Voboost Installer",
               try text(candidate, kAXRoleAttribute as CFString) == "AXWindow" { matches.append(candidate) }
        }
        guard matches.count == 1 else { throw Failure("window") }
        if let prior = window {
            guard requestedWindow == windowToken, CFEqual(prior, matches[0]) else { throw Failure("window") }
        } else {
            guard requestedWindow == nil else { throw Failure("window") }
            window = matches[0]; windowToken = UUID().uuidString.lowercased()
        }
        return matches[0]
    }
    func inspect(_ root: AXUIElement) throws -> [[String: Any]] {
        nodes.removeAll(); observation = UUID().uuidString.lowercased()
        var result: [[String: Any]] = []
        func visit(_ element: AXUIElement, _ path: [Int], _ parent: String?) throws {
            try budget()
            guard path.count <= 48, result.count < 3000 else { throw Failure("tree") }
            let token = UUID().uuidString.lowercased(), value = try identity(element)
            nodes[token] = (element, path, value)
            var row = value as! [String: Any]; row["nodeToken"] = token; row["parent"] = parent as Any? ?? NSNull()
            result.append(row)
            for (index, child) in try children(element).enumerated() { try visit(child, path + [index], token) }
        }
        try visit(root, [], nil)
        return result
    }
    func execute(_ request: [String: Any]) throws -> [String: Any] {
        deadline = ProcessInfo.processInfo.systemUptime + 20
        let op = try string(request, "op"), requestID = try string(request, "requestId")
        guard uuid(requestID), try string(request, "ownerId") == owner, try integer(request, "schemaVersion") == 1 else { throw Failure("protocol") }
        var fields: Set<String> = ["schemaVersion", "op", "requestId", "ownerId", "windowToken"]
        if op != "inspect" { fields.formUnion(["observationId", "nodeToken", "actionId"]) }
        if op == "set-value" { fields.insert("value") }
        if op == "resize" { fields.insert("preset") }
        guard ["inspect", "press", "set-value", "resize"].contains(op), Set(request.keys) == fields else { throw Failure("protocol") }
        let suppliedWindow = request["windowToken"] is NSNull ? nil : try string(request, "windowToken")
        let root = try scope(suppliedWindow)
        var result: [String: Any] = ["schemaVersion": 1, "ownerId": owner, "requestId": requestID,
            "pid": pid, "startedMillis": started, "windowToken": windowToken!, "status": "ok"]
        if op == "inspect" {
            result["nodes"] = try inspect(root); result["observationId"] = observation!
            return result
        }
        let action = try string(request, "actionId"), node = try string(request, "nodeToken")
        guard uuid(action), !usedActions.contains(action), try string(request, "observationId") == observation,
              let prior = nodes[node] else { throw Failure("identity") }
        let actionURL = ownerURL.deletingLastPathComponent().appendingPathComponent("actions/\(action).json")
        let record = try object(regular(actionURL, maximum: 4096), 4096)
        guard Set(record.keys) == Set(["schemaVersion", "ownerId", "actionId", "operation", "state"]),
              try integer(record, "schemaVersion") == 1, try string(record, "ownerId") == owner,
              try string(record, "actionId") == action, try string(record, "operation") == op,
              try string(record, "state") == "dispatched" else { throw Failure("owner") }
        var current = root
        for index in prior.1 {
            let values = try children(current); guard values.indices.contains(index) else { throw Failure("identity") }; current = values[index]
        }
        guard CFEqual(current, prior.0), try identity(current).isEqual(to: prior.2 as! [AnyHashable: Any]) else { throw Failure("identity") }
        let role = prior.2["role"] as? String ?? ""
        var outcome: AXError
        if op == "press" {
            guard prior.2["enabled"] as? Bool == true,
                  ["AXButton", "AXCheckBox", "AXRadioButton", "AXPopUpButton", "AXMenuItem", "AXDisclosureTriangle"].contains(role) else { throw Failure("unsupported") }
            var actions: CFArray?
            guard AXUIElementCopyActionNames(current, &actions) == .success, (actions as? [String])?.contains(kAXPressAction) == true else { throw Failure("unsupported") }
            usedActions.insert(action); outcome = AXUIElementPerformAction(current, kAXPressAction as CFString)
        } else {
            let name: CFString, value: CFTypeRef
            if op == "set-value" {
                guard ["AXTextField", "AXTextArea", "AXComboBox"].contains(role), prior.2["redacted"] as? Bool == false else { throw Failure("unsupported") }
                name = kAXValueAttribute as CFString; value = try string(request, "value") as CFString
            } else {
                guard prior.1.isEmpty, role == "AXWindow" else { throw Failure("unsupported") }
                let preset = try string(request, "preset"); guard ["default", "minimum"].contains(preset) else { throw Failure("protocol") }
                var size = preset == "default" ? CGSize(width: 1060, height: 740) : CGSize(width: 900, height: 640)
                name = kAXSizeAttribute as CFString; value = AXValueCreate(.cgSize, &size)!
            }
            var settable = DarwinBoolean(false)
            guard AXUIElementIsAttributeSettable(current, name, &settable) == .success, settable.boolValue else { throw Failure("unsupported") }
            usedActions.insert(action); outcome = AXUIElementSetAttributeValue(current, name, value)
        }
        guard outcome == .success else { throw Failure("unknown_action", true) }
        result["actionId"] = action; result["actionDispatched"] = true
        return result
    }
}

func emit(_ value: [String: Any]) throws {
    let bytes = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
    guard bytes.count <= 2 * 1024 * 1024 else { throw Failure("output") }
    FileHandle.standardOutput.write(bytes); FileHandle.standardOutput.write(Data([10]))
}

do {
    guard CommandLine.arguments.count == 3, CommandLine.arguments[1] == "--owner" else { throw Failure("protocol") }
    let observer = try Observer(URL(fileURLWithPath: CommandLine.arguments[2]))
    var pending = Data()
    while let chunk = try FileHandle.standardInput.read(upToCount: 4096), !chunk.isEmpty {
        pending.append(chunk)
        while let index = pending.firstIndex(of: 10) {
            let line = Data(pending[..<index]); pending.removeSubrange(...index)
            var request: [String: Any] = [:]
            do { request = try object(line, 65536); try emit(observer.execute(request)) }
            catch let error as Failure {
                try emit(["schemaVersion": 1, "ownerId": observer.owner, "requestId": request["requestId"] as? String ?? "",
                    "status": error.dispatched ? "unknown" : "blocked", "reason": error.reason, "actionDispatched": error.dispatched])
            }
            catch { try emit(["schemaVersion": 1, "ownerId": observer.owner, "requestId": request["requestId"] as? String ?? "", "status": "blocked", "reason": "protocol", "actionDispatched": false]) }
        }
        guard pending.count <= 65536 else { throw Failure("protocol") }
    }
    guard pending.isEmpty else { throw Failure("protocol") }
} catch { try? emit(["schemaVersion": 1, "status": "blocked", "reason": "owner-or-protocol", "actionDispatched": false]); exit(2) }
