# Navi normal-search source evidence

Owner: root case19. This record cites retained OEM source; it is not runtime acceptance or a firmware inventory gate. No APK or source corpus is copied here.

Retained source root: `/Users/vitaly/Yandex.Disk.localized/voyah-source/decompiled/PateoNaviAuto_C53_Release-c0adba8fd784-free-09-chinese-20260106-6.0.3/app/src/main`. The directory identity identifies this evidence only; executable probes never compare its hash or enumerate firmware versions.

| Source | Observed contract | Root runtime probe |
| --- | --- | --- |
| `res/layout/p_nv_half_search.xml:10` | Normal search header `ll_normal_search` owns `iv_back` ImageView and `dsie_normal_search` EditText as siblings. | Exact package/resource/class, common nonempty parent, current focused primary Navi window, enabled visible clickable Back. |
| `java/com/pateo/lightnavi/view/frameview/HistoryViewHalf.java:276` | `onPerformClick` handles `iv_back` by hiding input and calling `goBack()`. | One accepted observed Back; unknown outcome is never replayed. |
| `java/com/pateo/lightnavi/view/frameview/HistoryViewHalf.java:210` | `goBack()` has state-dependent routing and may clear the current search. | Reopen through observed normal-search entry and independently require actual empty committed text with UTF16 end selection; source intent never substitutes for readback. |
| `java/com/pateo/lightnavi/adapter/ResultSearchHelper.java:100` | Search helper binds the real `dsie_normal_search` EditText. | Every case19 key observation requires this exact normal-search resource, not any focused Navi text field. |

The runner admits only an initially empty observed normal-search editor. It resets only its exact committed `Москва` or `Ёё` sample, then verifies empty readback before the next sample. It does not guess an IME delete key, inject text, clear preexisting user input or retry an accepted Back. Semantic input and current glyph/font provenance or pixel spacing are independent outcomes.
