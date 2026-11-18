/** Compare every visible slot; deliberately makes no claim about unobserved pages or pixels. */
export function observedLauncherOrder(before: readonly { label: string }[], after: readonly { label: string }[]): string[] {
  const labels = (rows: readonly { label: string }[]) => {
    if (!Array.isArray(rows) || rows.length === 0 || rows.length > 128) throw new Error("launcher observed page unavailable");
    const result = rows.map(row => row?.label);
    if (result.some(label => typeof label !== "string" || !label.length || label.length > 256) || new Set(result).size !== result.length) throw new Error("launcher observed labels ambiguous");
    return result;
  };
  const expected = labels(before), actual = labels(after);
  if (expected.length !== actual.length || expected.some((label, slot) => label !== actual[slot])) throw new Error("launcher observed page order differs after reboot");
  return expected;
}
