# Installer themes and styling

**English** | [Русский](installer-themes.ru.md)

The React interface is wrapped in Fluent UI `FluentProvider`. [`App.tsx`](../../voboost-installer/src/App.tsx) selects `webLightTheme` or `webDarkTheme` from the system `prefers-color-scheme: dark` preference. [`useDarkMode`](../../voboost-installer/src/hooks/useDarkMode.ts) reads the initial preference, subscribes to changes and removes the listener when unmounted.

The platform palettes in [`src/themes`](../../voboost-installer/src/themes) remain separate helpers. `getTheme` selects the macOS palette for `macos` and the Windows palette otherwise, but the application does not call it. These helpers do not describe the active application theme. Platform and USB detection in `usePlatform` serves connection guidance independently of theme selection.

Keep custom styles in co-located CSS with the existing BEM naming. [`App.css`](../../voboost-installer/src/App.css) uses Fluent color tokens for the application background and border and defines `--layout-horizontal-padding`. Use the current component styles and theme tokens as references when changing spacing, typography or colors.

Verify appearance in light and dark modes and at the supported window sizes. A frontend check alone does not establish the appearance of a packaged native application.
