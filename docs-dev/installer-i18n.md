# Internationalization (i18n)

**English** | [Русский](installer-i18n.ru.md)

## Overview

The installer supports English and Russian. The canonical dictionaries are `src/i18n/en.json` and `src/i18n/ru.json`, and both contain the same key set. The selected language is stored in application state and does not change operation semantics, release identity, or verified data.

## Configuration

`src/i18n/index.ts` initializes i18next, connects React, and selects a supported locale. Components obtain strings through `useTranslation`; operation text must not be assembled by concatenating localized fragments. Add natural English and Russian wording together for every new key.

## English and Russian translations

The English and Russian dictionaries are equal user-facing surfaces. Keep placeholders, units, file names, error codes, and technical identifiers identical. Russian wording should be natural rather than copying English word order; check terminology against [English glossary](../GLOSSARY.md) and [Russian glossary](../GLOSSARY.ru.md).

## Use in components

A component calls `const { t } = useTranslation()` and passes the complete key with substitution parameters. User messages, button labels, loading states, warnings, and accessibility labels must come from the dictionary. Tests do not depend on key order, but they verify both locales and the meaning of critical messages.

## Language selector

`LanguageSelector` lists only supported locales, changes the language through the shared i18n instance, and saves the choice in application state. Changing the language updates the open screen without resetting the selected device or release.

## Adding a language

1. Add a complete dictionary with the same keys and placeholders
2. Register the locale in the i18n configuration and selector
3. Add localized Markdown for the license and every screen in the current workflow
4. Update locale and content tests, then check the layout of long strings
5. Run TypeScript, UI tests, and documentation parity checks

## Date and number formatting

Format dates and numbers with `Intl` and the active locale. Do not localize versions, SHA-256 values, transaction IDs, paths, artifact names, or protocol values. Network and signed formats remain canonical and locale-neutral.
