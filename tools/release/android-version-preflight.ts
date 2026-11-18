// Source-owner consistency check before expensive builds. The produced APK is
// still independently inspected by aapt2 later in the release constructor.
export function requireAndroidVersionCodeAgreement(
  gradleSource: string,
  expectedVersionCode: number,
): void {
  const declarations = [
    ...gradleSource.matchAll(/^val appVersionCode\s*=\s*([0-9][0-9_]*)\s*$/gmu),
  ];
  if (declarations.length !== 1)
    throw new Error(
      "Android version preflight requires one literal appVersionCode declaration",
    );
  const actual = Number(declarations[0][1].replaceAll("_", ""));
  if (!Number.isSafeInteger(actual) || actual < 1 || actual > 2_100_000_000)
    throw new Error(
      "Android version preflight appVersionCode is outside the product bounds",
    );
  if (actual !== expectedVersionCode)
    throw new Error(
      `Android version preflight: app source versionCode ${actual} differs from frozen product definition ${expectedVersionCode}; align both owners before building`,
    );
}
