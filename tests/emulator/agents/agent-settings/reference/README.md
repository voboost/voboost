# Reviewed emulator OEM font references

This directory owns real stock Settings raster goldens for automated emulator verification. No runtime goldens are present yet. `tests/unit/agents/agent-settings/ui-raster` contains labelled host-only negative/algorithm fixtures and is not admitted here.

A reference is added only after the coordinator has inspected the original stock PNG and selected exact controls/ROIs. This is ordinary test-golden review, separate from product publication approval and physical vehicle fidelity. Candidate Voboost screenshots never calibrate the oracle.

`StockSettingsBaseline.collect(port, viewport, language)` collects original primary1920×720 SDK frames in stock EN or ZH before Voboost installation. Its driver independently checks current owned cold Android, Enforcing and packaged absence plus root absence; after a real uninstall it cold-boots first. Only observed harmless locale/navigation inputs are allowed. `SYSTEM_ACTIONS` reads `btn_factory_reset` without tapping; `LANGUAGE_RADIO` reads the original Chinese option. Restore the original locale afterwards.

The collector output is a review candidate, never a registry. To pin a reviewed reference, retain compact actual status/absence authorities, both actual all-attached UI trees, the original full PNG and capture manifest below one reference directory. Paths in the registry are relative to that directory, with no symlinks or historical job dependencies.

The loader is `StockFontReference.load(productSources, referenceId, profile, existingFontInputs)`. It only reads `tests/emulator/agents/agent-settings/reference/<referenceId>/reference.json`. Font inputs are canonical existing SDK/OEM/component files selected by the owning driver; do not copy the SDK. Font-file SHA verifies integrity. Firmware/APK compatibility hashes are not used.

The registry schema is:

```json
{
  "schema": 1,
  "kind": "emulator-stock-oem-golden",
  "referenceId": "reviewed-source-id",
  "reviewedBy": "root",
  "profiles": ["profile-id"],
  "samples": [{
    "model": "dfp-28-selected-radio",
    "family": "radio",
    "captureDirectory": "captures/zh-language",
    "authorityBefore": "authority-before.json",
    "authorityAfter": "authority-after.json",
    "uiBefore": "ui-before.json",
    "uiAfter": "ui-after.json",
    "resourceId": "com.qinggan.app.setting:id/display_language_radio_chinese",
    "caption": "中文（简体）",
    "fonts": [{
      "input": "canonical-owning-font-input-id",
      "sha256": "actual-font-file-sha256",
      "faceIndex": 0,
      "postscriptName": "actual-PostScript-face"
    }]
  }]
}
```

The authority JSON contains actual `profile`, `sessionId`, `bootId`, `androidPid`, `quickbootMode`, packaged `status` and independent `rootAbsence`; retain the driver's ready/Enforcing receipt alongside it. `captureDirectory` contains unmodified `primary.png` and `manifest.json`. Each UI tree must expose actual TextView `text_layout` and getter metadata from the current framework bridge.

Font families are `button`, `radio`, `heading`, `body`, `dialog-action`, `dialog-message`. Each actual size/scale/letter spacing/style/opaque color/gravity/alignment combination needs a reviewed calibration sample. Regular Roboto28/30 files are byte-identical; their CJK fallback TTCs differ. Simplified-Chinese Noto face is index2 and must be checked against actual owning `fonts.xml` and PostScript identity. DFP King Gothic also contains Han; do not presume all Chinese uses Noto. An unsupported font/script/style remains blocked.

The independent renderer uses the expected owning production copy and actual UTF16 line ranges, baselines and content viewport. It derives edge tolerance at most2px and advance tolerance at most2.25px from stock only. It rejects clipping, missing/extra glyph ink, wrong calibrated family, spacing, native style/alignment and font identity. All original SDK provenance is rechecked. Hardware fidelity and publication approval remain false in criterion receipts.

`SettingsRasterAcceptance.inventory(sourceCopyProof, caseNumber, language, selectedStartupMode)` constructs the complete required role set from the fresh owning source producer. Its samples join authenticated `SettingsOemMeasurements` before/after with an actual original SDK capture; source/root/window/boot/PID/generation cannot change across the bracket. Case20 also checks source checkbox/radio/section geometry. Case21 requires separate panel and dialog windows, both actions and the actual message. Action wrapping and bilateral insets are measured, not inferred from resource XML. `write` retains executable compact inputs; `readProof` reopens PNGs/manifests and recomputes the entire result. Its accepted result is a visual/layout criterion; the caller separately admits source freshness, locale and click/cancel programs.
