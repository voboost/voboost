import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const roots = {
    user: join(root, "docs-user"),
    developer: join(root, "docs-dev"),
} as const;

function markdownFiles(directory: string): string[] {
    return readdirSync(directory, { withFileTypes: true })
        .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
        .map((entry) => join(directory, entry.name))
        .sort();
}

function markdownLinks(document: string): string[] {
    return [...document.matchAll(/!??\[[^\]]*\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g)]
        .map((match) => match[1]);
}

function localTarget(file: string, link: string): string | undefined {
    if (link.startsWith("#") || /^[a-z][a-z\d+.-]*:/i.test(link) || link.startsWith("//")) {
        return undefined;
    }
    const decoded = decodeURIComponent(link.split(/[?#]/, 1)[0]);
    if (decoded === "") return undefined;
    let target = resolve(dirname(file), decoded);
    const configuredAgents = process.env.VOBOOST_AGENTS_ROOT;
    const canonicalAgents = resolve(root, "../voboost-agents");
    if (configuredAgents !== undefined && (target === canonicalAgents || target.startsWith(`${canonicalAgents}/`))) {
        target = join(resolve(configuredAgents), target.slice(canonicalAgents.length));
    }
    if (existsSync(target)) return target;
    return extname(target) === "" ? join(target, "index.md") : target;
}

function pair(file: string): string {
    return file.endsWith(".ru.md") ? file.replace(/\.ru\.md$/, ".md") : file.replace(/\.md$/, ".ru.md");
}

export function validateTargetDocs(kind?: keyof typeof roots): string[] {
    const errors: string[] = [];
    const selected = kind === undefined ? Object.entries(roots) : [[kind, roots[kind]]];
    for (const [name, directory] of selected) {
        if (!existsSync(directory) || !statSync(directory).isDirectory()) {
            errors.push(`missing ${name} documentation root ${directory}`);
            continue;
        }
        for (const required of ["index.md", "index.ru.md"]) {
            if (!existsSync(join(directory, required))) errors.push(`${directory}: missing ${required}`);
        }
        if (!existsSync(join(directory, "assets")) || !statSync(join(directory, "assets")).isDirectory()) {
            errors.push(`${directory}: missing independent assets directory`);
        }
        for (const forbidden of ["GLOSSARY.md", "GLOSSARY.ru.md"]) {
            if (existsSync(join(directory, forbidden))) errors.push(`${directory}: root glossary must not be copied as ${forbidden}`);
        }
        for (const file of markdownFiles(directory)) {
            const counterpart = pair(file);
            if (!existsSync(counterpart)) errors.push(`${file}: missing paired document ${basename(counterpart)}`);
            const document = readFileSync(file, "utf8");
            for (const forbidden of ["(../app/", "(../agents/", "(../reflect/", "(../ui/", "(../ui-res/"]) {
                if (document.includes(forbidden)) errors.push(`${file}: target docs link to legacy source root ${forbidden}`);
            }
            for (const forbidden of ["VoboostService", "AgentsExtractor", "AgentStagingCoordinator", "voboost-inject.json"]) {
                if (document.includes(forbidden)) errors.push(`${file}: target docs retain legacy architecture identifier ${forbidden}`);
            }
            for (const link of markdownLinks(document)) {
                const target = localTarget(file, link);
                if (target !== undefined && !existsSync(target)) {
                    errors.push(`${file}: missing relative link target ${link}`);
                }
            }
        }
    }
    for (const glossary of ["GLOSSARY.md", "GLOSSARY.ru.md"]) {
        if (!existsSync(join(root, glossary))) errors.push(`missing root product glossary ${glossary}`);
    }
    return errors;
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const argument = process.argv[2];
    const kind = argument === "user" || argument === "developer" ? argument : undefined;
    const errors = validateTargetDocs(kind);
    if (errors.length > 0) {
        console.error(errors.join("\n"));
        process.exitCode = 1;
    }
}
