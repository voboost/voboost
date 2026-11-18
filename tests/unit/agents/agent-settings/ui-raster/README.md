# Host raster regression input

`cjk-dfp-harfbuzz.png` is an independent HarfBuzz host rendering of `恢复出厂设置`, not an Android capture, OEM golden, hardware receipt or approved emulator baseline.

Input: owning `voboost-components/src/main/java/ru/voboost/components/font/Font_bold_ascii.ttf`, SHA256 `305f942097a1b65d1dca1c357c0197acf62346eea83e2fbe95cc55f62821f000`, PostScript face `DFPKingGothicGB-Semibold`.

Generation: `hb-view Font_bold_ascii.ttf '恢复出厂设置' --font-size=28 --font-extents=35,10,0 --margin=0 --background=000000 --foreground=ffffff --logical --output-format=png --output-file=cjk-dfp-harfbuzz.png`.

The 168×45 raster has baseline35 and six 28px advances. Tests place it inside full-frame host fixtures to exercise the portable stock-reference loader and negative controls. Runtime admission uses only reviewed original stock SDK frames in `tests/emulator/agents/agent-settings/reference`; this host fixture is never such an input.

`cjk-noto-sc-wrong-font.png` renders the same six genuine Chinese glyphs and advances with the wrong normative family: SDK30 `NotoSansCJK-Regular.ttc`, face2 `NotoSansCJKsc-Regular`, font SHA256 `036e8917a3f6ee3c29fac5e0da185dc857093d97c60b89baf207cbaad5895686`. The command is the same with `--face-index=2`. It is a negative input, not a missing-glyph/tofu substitute.
