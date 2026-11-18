# Cluster PW indicator

**English** | [Русский](cluster-pw-visual-observer.ru.md)

The QNX `vehicle-drive-pedestrian-warning` agent hides the original pedestrian-sound OFF indicator in `light`. It receives activation through the QNX daemon's normal native-agent lifecycle. It does not read CAN, determine actual sound state, or wait for periodic Android permissions.

## Durable setting

The head unit sends the setting through the existing authenticated Cluster channel. The [QNX daemon](../../voboost-inject/cluster/native/src/engine.rs) validates the request and atomically saves Desired in its own persistent store. It reloads the saved configuration after restart. The [controller](../../voboost-inject/cluster/native/src/controller.rs) passes it to native runtime: an enabled module receives `start`; a disabled module receives `stop`.

No separate PW state file is needed. A disconnected head unit does not revoke the saved choice or require a heartbeat. A newer accepted setting replaces the previous one. Replaying an old request must not re-enable a disabled feature. Pairing, signed-request verification and atomic writes remain part of the ordinary setting protocol.

## Apply and restore

The [module](../../voboost-agents/src/main/cpp/agents/vehicle/drive/pedestrianwarning/src/agent.cpp) makes OEM calls only on the `light` UI thread. Its backend verifies the current Screen, attached `SoundEnable` node, `Node.Visible` type and property-source ownership. Compatibility uses symbol, type and behavior probes, without firmware/APK SHA lists.

The mask adds its own false source to `Node.Visible` on the verified indicator container. The original visibility sources of the glyph nodes remain in place. It neither overwrites the OEM value nor sends vehicle commands. `stop` removes only its own source and cookie. The latest original value then takes effect, including OEM updates received while hidden. A new UI root requires removal of the old mask and a fresh owner probe. Failed removal reports `reload-required`, never successful disablement.

The active path does not depend on a live lease, CAN observation age or transient proof. The dedicated periodic PW courier is no longer scheduled. Retained source files for the previous channel still require separate reference-checked retirement; they do not authorize the mask.

## Automated checks

From the product `voboost` repository:

```sh
CXX=/absolute/host/clang++ ./gradlew testPwNative
```

The compiler needs a configured host SDK. The product-owned runner compiles production `agent.cpp` and `mask.cpp` with colocated tests. Checks cover startup without a CAN/live-lease resolver, continued operation without a heartbeat, stop/start, mask ownership and restoration failure. Shared QNX Rust tests separately cover setting admission, persistence, restart, retained OFF, conflicts and damaged storage.

After a release build, runtime acceptance must verify the saved setting and actual `applied` state of the current `light` module; visible → hidden → restored pixels; QNX restart and operation without a head-unit connection. A visible original baseline may use the existing virtual-MCU input, explicitly recorded as a fixture stimulus. It does not prove the complete physical CAN→MCU path.

The focused emulator tests run on a prepared machine. They exercise VehicleSetting and the left drawer while the agent is active and record the independent virtual-MCU stimulus. They compare all pixels in the 46×46 region at x841/y102 with reviewed lossless WebP references for the Classic theme on A9. Before capturing the hidden state they require the current QNX agent to confirm application. After disabling the agent they require its acknowledgement and compare the restored stock glyph against the visible reference.

See [native runtime](native.md), [agent development](agent-development.md) and [testing](testing.md). Agent-owned documentation is [beside its code](../../voboost-agents/src/main/cpp/agents/vehicle/drive/pedestrianwarning/README.md).
