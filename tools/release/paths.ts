// Shared path policy for release outputs. The durable output of a build run
// must live under the canonical <voboost-repo>/build/ tree, must not exist
// yet, and must not escape that tree through symlinks or dot segments. The
// same policy also guards overlaps between the output and the inputs it is
// built from (source repositories, the signing authority, SDK roots).
//
// The policy functions are pure checks over a filesystem snapshot; tests use
// temporary roots under the OS tmpdir for layout, but every symlink/ancestor
// branch is exercised against real symlinks under the product build root so a
// /tmp rejection can never mask the symlink-to-source branch. Production
// validation never relaxes these rules.
import { existsSync, lstatSync, realpathSync, statSync } from "node:fs";
import { basename, dirname, isAbsolute, join } from "node:path";

export function failPath(message: string): never {
  throw new Error(`release-paths: ${message}`);
}

export const SOURCE_REPOSITORIES = [
  "voboost",
  "voboost-app",
  "voboost-agents",
  "voboost-inject",
  "voboost-installer",
  "voboost-codestyle",
  "voboost-logo",
] as const;

export type PathPolicyOptions = {
  /** Allow roots under the OS temporary trees (tests only; never in production). */
  allowTemporaryTrees?: boolean;
};

// Canonicalize a requested path by resolving the nearest EXISTING ancestor
// and appending the remaining components. This catches symlinked parents at
// any depth, not just an already-existing immediate parent.
export function canonicalizeNearest(requested: string): string {
  let current = requested;
  const missing: string[] = [];
  while (!existsSync(current)) {
    missing.unshift(basename(current));
    const parent = dirname(current);
    if (parent === current) return requested; // hit the filesystem root
    current = parent;
  }
  const canonicalBase = realpathSync(current);
  return missing.length === 0 ? canonicalBase : join(canonicalBase, ...missing);
}

export function productBuildRoot(workspaceRoot: string): string {
  return join(workspaceRoot, "voboost", "build");
}

function temporaryRoots(): string[] {
  return ["/tmp", "/var/tmp", process.env.TMPDIR ?? "/tmp"];
}

// Pure policy: is the canonicalized requested output confined to the product
// build tree and free of overlaps with the given input roots?
export function checkOutputPolicy(
  workspaceRoot: string,
  requested: string,
  inputs: { sources?: string[]; authority?: string[]; toolchains?: string[] } = {},
  options: PathPolicyOptions = {},
): { canonical: string } {
  if (!isAbsolute(requested)) failPath("output must be an absolute path");
  if (requested.includes("/./") || requested.endsWith("/."))
    failPath("output must not contain dot segments");
  if (existsSync(requested)) failPath(`output must not exist: ${requested}`);

  const artifactRoot = productBuildRoot(workspaceRoot);
  const canonical = canonicalizeNearest(requested);

  // The canonicalized path (existing ancestors resolved through realpaths)
  // must remain inside the canonical artifact root. A symlink anywhere in the
  // existing prefix pointing at a source repository, the authority, or a
  // temporary tree will violate this.
  const canonicalArtifactRoot = existsSync(artifactRoot) ? realpathSync(artifactRoot) : artifactRoot;
  if (!canonical.startsWith(`${canonicalArtifactRoot}/`))
    failPath(
      `output must stay under the product build root ${canonicalArtifactRoot} (canonical: ${canonical})`,
    );

  // Temporary trees are never durable homes for release outputs, even when a
  // symlink or TMPDIR alias would otherwise hide them.
  for (const root of temporaryRoots()) {
    const canonicalRoot = existsSync(root) ? realpathSync(root) : root;
    if (canonical === canonicalRoot || canonical.startsWith(`${canonicalRoot}/`)) {
      if (options.allowTemporaryTrees !== true)
        failPath(`output must remain outside temporary trees: ${canonicalRoot}`);
    }
  }

  // No lexical or canonical overlap with any build input: sources, signing
  // authority, toolchain roots.
  for (const [kind, roots] of Object.entries(inputs)) {
    for (const root of roots ?? []) {
      if (!isAbsolute(root)) failPath(`input root must be absolute: ${root}`);
      const canonicalRoot = canonicalizeNearest(root);
      if (canonical === canonicalRoot || canonical.startsWith(`${canonicalRoot}/`))
        failPath(`output must not overlap the ${kind} input root: ${canonicalRoot}`);
      if (canonicalRoot === canonical || canonicalRoot.startsWith(`${canonical}/`))
        failPath(`output must not contain the ${kind} input root: ${canonicalRoot}`);
    }
  }

  return { canonical };
}

// Collect every path component of `directory` that is a symlink; used to
// reject output paths whose parents are not plain directories.
export function symlinkedComponents(directory: string): string[] {
  const symlinks: string[] = [];
  let current = directory;
  for (;;) {
    if (existsSync(current) && lstatSync(current).isSymbolicLink()) symlinks.push(current);
    const parent = dirname(current);
    if (parent === current) break;
    current = parent;
  }
  return symlinks;
}
