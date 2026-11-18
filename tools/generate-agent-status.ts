import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { formatRussianMarkdown } from "./format-russian-docs.ts";

const defaultRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export type AgentsCheckoutProbe = (root: string) => { head: string; status: string };

function checkoutHead(root: string): string {
    const marker = join(root, ".git");
    const metadata = statSync(marker);
    const gitDirectory = metadata.isDirectory()
        ? marker
        : resolve(root, readFileSync(marker, "utf8").trim().replace(/^gitdir:\s*/u, ""));
    const head = readFileSync(join(gitDirectory, "HEAD"), "utf8").trim();
    if (/^[0-9a-f]{40}$/u.test(head)) return head;
    const match = /^ref:\s+(.+)$/u.exec(head);
    if (!match) throw new Error("release agents checkout HEAD is malformed");
    const loose = join(gitDirectory, match[1]);
    if (existsSync(loose)) return readFileSync(loose, "utf8").trim();
    const packed = readFileSync(join(gitDirectory, "packed-refs"), "utf8")
        .split("\n")
        .find((line) => line.endsWith(` ${match[1]}`));
    if (packed === undefined) throw new Error("release agents checkout ref is unresolved");
    return packed.slice(0, 40);
}

const gitProbe: AgentsCheckoutProbe = (root) => ({
    head: checkoutHead(root),
    status: process.env.VOBOOST_AGENTS_CHECKOUT_CLEAN === "1" ? "" : "unverified",
});

export function validateReleaseAgentsCheckout(
    root: string,
    expectedCommit: string,
    probe: AgentsCheckoutProbe = gitProbe,
): string {
    if (!isAbsolute(root)) throw new Error("release VOBOOST_AGENTS_ROOT must be absolute");
    if (!/^[0-9a-f]{40}$/u.test(expectedCommit)) throw new Error("release VOBOOST_AGENTS_COMMIT must be a full lowercase commit");
    if (!existsSync(root) || !statSync(root).isDirectory()) throw new Error(`release agents checkout is absent: ${root}`);
    for (const required of ["src/main/java/ru/voboost/agents", "openspec/specs"]) {
        if (!existsSync(join(root, required))) throw new Error(`release agents checkout is incomplete: ${required}`);
    }
    const actual = probe(root);
    if (actual.head !== expectedCommit) throw new Error(`release agents checkout HEAD ${actual.head} differs from ${expectedCommit}`);
    if (actual.status !== "") throw new Error("release agents checkout must be clean");
    return root;
}

function selectedAgentsRoot(productRoot: string): string {
    const configured = process.env.VOBOOST_AGENTS_ROOT;
    if (process.env.VOBOOST_DOCS_RELEASE_MODE === "1") {
        if (configured === undefined || process.env.VOBOOST_AGENTS_COMMIT === undefined) {
            throw new Error("release docs require explicit VOBOOST_AGENTS_ROOT and VOBOOST_AGENTS_COMMIT");
        }
        if (process.env.VOBOOST_AGENTS_CHECKOUT_CLEAN !== "1") {
            throw new Error("release docs require an explicit clean-checkout assertion");
        }
        return validateReleaseAgentsCheckout(configured, process.env.VOBOOST_AGENTS_COMMIT);
    }
    return configured === undefined ? resolve(productRoot, "../voboost-agents") : resolve(configured);
}

type Locale = "ru" | "en";

type Metadata = {
    id: string;
    status: "stable" | "unstable" | "planned";
    class: string;
    target: string | string[];
    applicability: Record<string, Array<string | number>>;
};

type Entry = {
    path: string;
    agentsRoot: string;
    metadata: Metadata;
    specPath: string;
    unverified: string[];
};

function files(directory: string): string[] {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const path = join(directory, entry.name);
        return entry.isDirectory() ? files(path) : [path];
    });
}

function compact(values: Array<string | number>): string {
    return values.join("/");
}

function target(value: string | string[], locale: Locale): string {
    const values = Array.isArray(value) ? value : [value];
    if (values.length > 3) {
        return locale === "ru"
            ? `${values.length} процессов; точный список — в документе агента`
            : `${values.length} processes; see the agent document for the exact list`;
    }
    return values.map((item) => `\`${item}\``).join(", ");
}

function applicability(value: Metadata["applicability"]): string {
    return [
        compact(value.chip),
        compact(value.vehicle),
        `A${compact(value.android)}`,
        compact(value.powertrain),
        compact(value.market),
    ].join("; ");
}

function unverified(spec: string): string[] {
    return [...new Set(
        [...spec.matchAll(/^#{1,6} Unverified: (.+)$/gm)].map((match) => match[1]),
    )];
}

function slug(value: string): string {
    return value
        .toLowerCase()
        .replace(/[^a-z0-9\s-]/g, "")
        .trim()
        .replace(/\s+/g, "-")
        .replace(/-+/g, "-");
}

function relativeFromDeveloper(productRoot: string, path: string): string {
    return relative(join(productRoot, "docs-dev"), path).replaceAll("\\", "/");
}

function canonicalOwnerPath(entry: Entry, productRoot: string, sourcePath: string): string {
    const canonicalRoot = resolve(productRoot, "../voboost-agents");
    return join(canonicalRoot, relative(entry.agentsRoot, sourcePath));
}

function links(entry: Entry, productRoot: string, locale: Locale): string {
    const simple = entry.metadata.class.slice(entry.metadata.class.lastIndexOf(".") + 1);
    const agentDocument = join(
        dirname(entry.path),
        `${simple}.${locale === "ru" ? "ru." : ""}md`,
    );
    const canonicalDocument = canonicalOwnerPath(entry, productRoot, agentDocument);
    const canonicalSpec = canonicalOwnerPath(entry, productRoot, entry.specPath);
    const agentLabel = locale === "ru" ? "Документ" : "Document";
    return `[${agentLabel}](${relativeFromDeveloper(productRoot, canonicalDocument)}) · `
        + `[OpenSpec](${relativeFromDeveloper(productRoot, canonicalSpec)})`;
}

function contribution(entry: Entry, locale: Locale): string {
    if (entry.unverified.length > 0) {
        return locale === "ru"
            ? `Открытых проверок: ${entry.unverified.length}`
            : `Open checks: ${entry.unverified.length}`;
    }
    if (entry.metadata.status === "planned") {
        return locale === "ru"
            ? "Проверить на устройстве и в автомобиле перед переводом в stable"
            : "Verify on a device and in a vehicle before promotion to stable";
    }
    return locale === "ru"
        ? "Закрыть обязательные проверки выпуска на устройстве и в автомобиле"
        : "Complete required release checks on a device and in a vehicle";
}

function openChecks(entries: Entry[], productRoot: string, locale: Locale): string {
    const sections = entries
        .filter((entry) => entry.unverified.length > 0)
        .map((entry) => {
            const spec = relativeFromDeveloper(
                productRoot,
                canonicalOwnerPath(entry, productRoot, entry.specPath),
            );
            const checks = entry.unverified.map((title, index) => {
                const label = locale === "ru"
                    ? `Непроверенное требование ${index + 1}`
                    : `Unverified requirement ${index + 1}`;
                return `- [${label}](${spec}#unverified-${slug(title)})`;
            });
            return `### \`${entry.metadata.id}\`\n\n${checks.join("\n")}`;
        });
    if (sections.length === 0) return "";
    const heading = locale === "ru" ? "## Непроверенные требования" : "## Unverified requirements";
    const intro = locale === "ru"
        ? "Ссылки ведут на точные требования OpenSpec. Названия не копируются в этот файл, поэтому источником формулировки остаётся спецификация."
        : "Links point to the exact OpenSpec requirements. Titles are not copied here, so the specification remains the wording authority.";
    return `${heading}\n\n${intro}\n\n${sections.join("\n\n")}`;
}

const rank = { stable: 0, unstable: 1, planned: 2 } as const;

function entries(agentsRoot: string): Entry[] {
    const sourceRoot = join(agentsRoot, "src/main/java/ru/voboost/agents");
    return files(sourceRoot)
        .filter((path) => path.endsWith("Agent.json"))
        .map((path) => {
            const metadata = JSON.parse(readFileSync(path, "utf8")) as Metadata;
            const specPath = join(agentsRoot, `openspec/specs/agent-${metadata.id}/spec.md`);
            return {
                path,
                agentsRoot,
                metadata,
                specPath,
                unverified: unverified(readFileSync(specPath, "utf8")),
            };
        })
        .sort((left, right) => rank[left.metadata.status] - rank[right.metadata.status]
            || left.metadata.id.localeCompare(right.metadata.id));
}

function generate(productRoot: string, agentsRoot: string, locale: Locale): string {
    const all = entries(agentsRoot);
    const counts = Object.fromEntries(
        ["stable", "unstable", "planned"].map((status) => [
            status,
            all.filter((entry) => entry.metadata.status === status).length,
        ]),
    );
    const rows = all.map((entry) => `| \`${entry.metadata.id}\` | \`${entry.metadata.status}\` | ${target(entry.metadata.target, locale)} | ${applicability(entry.metadata.applicability)} | ${contribution(entry, locale)} | ${links(entry, productRoot, locale)} |`);
    const checks = openChecks(all, productRoot, locale);

    if (locale === "en") {
        return `# Agent status

**English** | [Русский](agents-status.ru.md)

This page is generated from metadata and applied OpenSpec in the sibling \`voboost-agents\` repository. There is no second manually maintained status list.

Composition: **${counts.stable} stable**, **${counts.unstable} unstable**, **${counts.planned} planned**.

Applicability notation: chip (\`8155\` or \`nxp\`); vehicle (\`free\` or \`dreamer\`); Android version; powertrain (\`ev\` or \`hybrid\`); market (\`china\` or \`global\`).

The Next contribution column shows the number of OpenSpec Unverified requirements. A planned agent without an explicit Unverified block still requires device and vehicle verification before promotion to stable.

| Agent | Status | Processes | Applicability | Next contribution | Documents |
| --- | --- | --- | --- | --- | --- |
${rows.join("\n")}

${checks}

## Changing a status

Status changes only in agent metadata. Before promotion to stable, the contract, stop and removal behavior, stubs, tests, paired documentation, OpenSpec, and required target-environment checks must be ready.

After a change, run the generator and the complete agent checks. The generated page must match metadata and OpenSpec byte for byte.
`;
    }

    return formatRussianMarkdown(`# Статус агентов

[English](agents-status.md) | **Русский**

Страница создаётся из метаданных и применённого OpenSpec в соседнем репозитории \`voboost-agents\`. Ручного второго списка статусов нет.

Состав: **${counts.stable} stable**, **${counts.unstable} unstable**, **${counts.planned} planned**.

Обозначения применимости: chip — процессор \`8155\` или \`nxp\`; vehicle — \`free\` или \`dreamer\`; версия Android; powertrain — \`ev\` или \`hybrid\`; market — \`china\` или \`global\`.

Колонка «Следующий вклад» показывает число требований OpenSpec со статусом Unverified. Если такого блока нет, planned-агент всё равно требует проверки на устройстве и в автомобиле перед переводом в stable.

| Агент | Статус | Процессы | Применимость | Следующий вклад | Документы |
| --- | --- | --- | --- | --- | --- |
${rows.join("\n")}

${checks}

## Как изменить статус

Статус меняется только в метаданных агента. Перед переводом в stable должны быть готовы контракт, остановка и удаление, заглушки, тесты, парная документация, OpenSpec и обязательные проверки целевой среды.

После изменения выполните генератор и полные проверки агентов. Сгенерированная страница должна совпасть с метаданными и OpenSpec байт-в-байт.
`);
}

export function generateAgentStatus(
    productRoot = defaultRoot,
    agentsRoot = selectedAgentsRoot(productRoot),
): string {
    return generate(productRoot, agentsRoot, "ru");
}

export function generateEnglishAgentStatus(
    productRoot = defaultRoot,
    agentsRoot = selectedAgentsRoot(productRoot),
): string {
    return generate(productRoot, agentsRoot, "en");
}

export function checkAgentStatus(
    productRoot = defaultRoot,
    agentsRoot = selectedAgentsRoot(productRoot),
): boolean {
    const russian = join(productRoot, "docs-dev/agents-status.ru.md");
    const english = join(productRoot, "docs-dev/agents-status.md");
    return readFileSync(russian, "utf8") === generateAgentStatus(productRoot, agentsRoot)
        && readFileSync(english, "utf8") === generateEnglishAgentStatus(productRoot, agentsRoot);
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const russian = join(defaultRoot, "docs-dev/agents-status.ru.md");
    const english = join(defaultRoot, "docs-dev/agents-status.md");
    const release = process.argv.includes("--release");
    const rootIndex = process.argv.indexOf("--agents-root");
    const commitIndex = process.argv.indexOf("--agents-commit");
    if (release && !process.argv.includes("--check")) throw new Error("release mode is check-only and cannot author documentation evidence");
    let agentsRoot: string;
    if (release) {
        if (rootIndex < 0 || commitIndex < 0 || process.argv[rootIndex + 1] === undefined || process.argv[commitIndex + 1] === undefined) {
            throw new Error("release mode requires --agents-root and --agents-commit");
        }
        agentsRoot = validateReleaseAgentsCheckout(process.argv[rootIndex + 1], process.argv[commitIndex + 1]);
    } else {
        agentsRoot = selectedAgentsRoot(defaultRoot);
    }
    if (process.argv.includes("--check")) {
        if (!checkAgentStatus(defaultRoot, agentsRoot)) {
            console.error(`${russian} or ${english} is stale; run node tools/generate-agent-status.ts`);
            process.exitCode = 1;
        }
    } else {
        writeFileSync(russian, generateAgentStatus(defaultRoot, agentsRoot));
        writeFileSync(english, generateEnglishAgentStatus(defaultRoot, agentsRoot));
    }
}
