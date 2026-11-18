import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";
import { parseReleaseDefinition, type ReleaseDefinition } from "./release-identity.ts";

const COMPONENTS = ["app", "agents", "inject", "installer", "logo"] as const;
type Pins = ReleaseDefinition["repositories"];

export function replaceSourcePins(source: string, pins: Pins): string {
  parseReleaseDefinition(source);
  let updated = source;
  for (const name of COMPONENTS) {
    if (!/^[0-9a-f]{40}$/.test(pins[name])) throw new Error(`Invalid ${name} commit`);
    const pattern = new RegExp(`^${name} = "[0-9a-f]{40}"$`, "gm");
    if ([...updated.matchAll(pattern)].length !== 1) throw new Error(`Ambiguous ${name} pin`);
    updated = updated.replace(pattern, `${name} = "${pins[name]}"`);
  }
  parseReleaseDefinition(updated);
  return updated;
}

export function prepareSourcePins(workspace: string, write: boolean): void {
  const root = resolve(workspace);
  const git = (repo: string, args: string[]) =>
    execFileSync("git", ["-C", join(root, repo), ...args], { encoding: "utf8" }).trim();
  const pins = {} as Pins;
  for (const name of [...COMPONENTS, "codestyle"] as const) {
    const repo = `voboost-${name}`;
    if (git(repo, ["status", "--porcelain"]) !== "")
      throw new Error(`${repo} has uncommitted changes; commit components before freezing pins`);
    const head = git(repo, ["rev-parse", "HEAD"]);
    if (!/^[0-9a-f]{40}$/.test(head)) throw new Error(`Invalid ${repo} HEAD`);
    if (name === "codestyle") console.log(`codestyle_sha=${head}`);
    else pins[name] = head;
  }
  const current = join(root, "voboost", "release", "version.toml");
  const source = readFileSync(current, "utf8");
  const updated = replaceSourcePins(source, pins);
  const release = parseReleaseDefinition(updated).release;
  const snapshot = join(root, "voboost", "release", `${release}.toml`);
  const differs = source !== updated || readFileSync(snapshot, "utf8") !== updated;
  if (write) {
    writeFileSync(current, updated);
    writeFileSync(snapshot, updated);
  } else if (differs) {
    throw new Error("Release pins differ from committed components; rerun with --write before the product commit");
  }
  console.log(JSON.stringify({ release, pins, changed: differs, written: write }));
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [workspace, flag, extra] = process.argv.slice(2);
  if (!workspace || (flag !== undefined && flag !== "--write") || extra !== undefined)
    throw new Error("Usage: node tools/release/prepare-source-pins.ts WORKSPACE [--write]");
  prepareSourcePins(workspace, flag === "--write");
}
