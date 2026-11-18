# Native dependencies

**English** | [Русский](native-dependencies.ru.md)

Run commands from `voboost-inject` with NDK r29, CMake and Ninja. Android native code targets API 28; LSPlant and implant use `c++_static`. Keep scratch in the project before running source-contract checks:

```sh
mkdir -p build/tmp
export TMPDIR="$PWD/build/tmp"
```

## Native payload outputs and the armv7 Dobby exception

`voboost-inject/prebuilt/<abi>/` receives native outputs from the repository-integrated Dobby and LSPlant builds. `libdobby.a`, `liblsplant_static.a`, and `libdex_builder_static.a` link into `libimplant.so` with one static C++ runtime. The implant calls LSPlant directly after attaching to ART; no separate LSPlant shared library is loaded.

### Source contract

The parent repository pins reachable upstream Dobby
`5dfc8546954ce3b3198132ab13fddb89ee92cdd7` and LSPlant
`a96c7978a9fe578e0e265cc8e45cb117fa8250a3`. Exact parent-owned patches add
the required NDK r29 build changes before compilation. The complete
`.gitmodules` mapping, gitlinks, patches, and prepared source state are verified
by:

```sh
make test-third-party-source-contract
```

The source build validates the live pinned checkouts, applies only the repository-owned patches to Git-identified copies under `build/third-party-source/`, and produces:

| File | `arm64-v8a` | `armeabi-v7a` | Consumer |
|---|---|---|---|
| `libdobby.a` | built from the pinned source | committed exception | statically linked into `libimplant.so` |
| `liblsplant_static.a` | built from the pinned source | built from the pinned source | statically linked into `libimplant.so` |
| `libdex_builder_static.a` | built with LSPlant | built with LSPlant | statically linked into `libimplant.so` |

Generated outputs are ignored by Git except for the documented armv7 Dobby
archive and its provenance record.

### Committed armv7 Dobby archive

The pinned Dobby source retains an ARM build breakage and does not produce the
required `armeabi-v7a` archive. The repository therefore keeps
`prebuilt/armeabi-v7a/libdobby.a` as its only native prebuilt exception.

The archive is the exact `prefab/modules/dobby/libs/android.armeabi-v7a/libdobby.a` member of Maven Central [`io.github.vvb2060.ndk:dobby:1.2`](https://repo.maven.apache.org/maven2/io/github/vvb2060/ndk/dobby/1.2/dobby-1.2.aar). The AAR SHA-256 is `251f48ae21686d7f69276c50644ca345f450e45110057437f7d76bb14cddf3a1`; its published ABI metadata records API 16, NDK 22, and STL `none`. The [published POM](https://repo.maven.apache.org/maven2/io/github/vvb2060/ndk/dobby/1.2/dobby-1.2.pom) declares Apache-2.0 and links the [upstream license](https://github.com/LSPosed/Dobby/blob/master/LICENSE). These dependency metadata do not change this repository's NDK r29 build requirement.

The source commit used to create this archive is unknown. The repository does
not claim that its bytes can be rebuilt from the pinned Dobby source or from a
known source revision. `libdobby.provenance.json` records that limitation and
pins the published origin above and the archive identity:

- SHA-256 `fbaeb9cfe18f6953ad4d24cad34ed4fdd04964fcea0f74ce09285e5dbd11e2fd`;
- Git blob `e9f6677dcbb3a859f742bbf6a4e681eb536c3e67`.

The identity gate is:

```sh
make test-armv7-dobby-prebuilt
```

Replacing the archive requires new, reviewable source provenance; changing its
name or keeping only an equivalent binary is not sufficient.

### Build and release checks

```sh
make build-native
make check-prebuilt
```

`make build-native` runs the source contract before compiling third-party
code. `make check-prebuilt` then checks the release shared objects for both
ABIs. Each must be an ET_DYN object with the expected machine, SONAME, readable
dynamic section, and exact `DT_NEEDED` set. The accepted sets exclude
`libc++_shared.so` because each implant contains one static C++ runtime. C++ exceptions and RTTI remain enabled. These checks establish the link contract; Android hook behavior requires separate runtime acceptance.

## libsepol source contract

The parent repository pins `third_party/libsepol` to Android commit
`c4882b81f186bc131f79ad0b28808bbeef6fc678` from
`https://android.googlesource.com/platform/external/libsepol`.

`0001-android-xperms-layout.patch` has SHA-256
`d12b749a57b7eee47eae4b7b52aaac986ae516145bf161101dca0ae1370d04d9`.
It adds the final Android ioctl xperms layout, keeps xperms kinds and drivers
separate during expansion, and includes the focused expander regression.

The authoritative source input is the pinned gitlink plus this parent-owned
patch. `node tools/prepare-libsepol-source.ts --apply` accepts only two tracked
states:

1. the clean pinned checkout, when the exact patch applies without conflict;
2. the byte-exact patched checkout, when the same patch reverses cleanly.

Any other tracked change or untracked source file fails closed. Untracked files
with only the `.o`, `.a`, or `.d` suffix are treated as compiler residue; they
are ignored by the source-state check and never count as source or test
evidence.

Run the complete third-party contract, including the disposable clean-checkout
patch test, with:

```sh
make test-third-party-source-contract
```

The gate validates the exact `.gitmodules` mapping and gitlink before exercising
the libsepol preparation tool. A fresh checkout can therefore reproduce the
patched source state without relying on changes inside the submodule worktree.

The preparation tools create Git-identified build inputs without changing the live upstream checkout. [Third-party terms](../../voboost-inject/THIRD_PARTY.md), original licenses, patches and provenance records stay beside their source owners. These dependency identities establish source provenance; they do not select compatible firmware. Host checks do not establish ART injection or vehicle readiness. See [native runtime](native.md) and [QNX link inputs](cluster-link-inputs.md).
