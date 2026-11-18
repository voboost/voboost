import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { validateTargetDocs } from "./docs-target-structure.ts";
import { formatRussianMarkdown } from "./format-russian-docs.ts";
import { checkAgentStatus } from "./generate-agent-status.ts";

const root = resolve(fileURLToPath(new URL("..", import.meta.url)));
const requested = process.argv[2];
const kind = requested === "user" || requested === "developer" ? requested : undefined;
const directories = kind === "user"
    ? ["docs-user"]
    : kind === "developer"
      ? ["docs-dev"]
      : ["docs-user", "docs-dev"];
const errors = validateTargetDocs(kind);

if (kind !== "user" && !checkAgentStatus(root)) {
    errors.push(`${join(root, "docs-dev/agents-status.md")}: generated agent status is stale`);
}

for (const directory of directories) {
    for (const name of readdirSync(join(root, directory))) {
        if (!name.endsWith(".ru.md")) continue;
        const file = join(root, directory, name);
        const document = readFileSync(file, "utf8");
        if (formatRussianMarkdown(document) !== document) {
            errors.push(`${file}: Russian Markdown is not formatted`);
        }
    }
}

if (errors.length > 0) {
    console.error(errors.join("\n"));
    process.exitCode = 1;
} else {
    const generated = kind === "user" ? "" : ", generated agent status";
    console.log(`Validated ${directories.join(" and ")} structure, links, pairs, Russian formatting${generated}.`);
}
