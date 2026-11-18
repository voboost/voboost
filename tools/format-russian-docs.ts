import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const LIST_ITEM = /^(\s*(?:[-+*]|\d+\.)\s+)(.*)$/u;
const FENCE = /^\s*(```|~~~)/u;

function isStandalone(line: string): boolean {
    const trimmed = line.trim();
    return /^#{1,6}\s/u.test(trimmed)
        || /^(?:Агент|Статус|Целевой процесс):\s/u.test(trimmed)
        || /^(?:chip|vehicle|android|powertrain|market)\s*=\s/u.test(trimmed)
        || /^\|/u.test(trimmed)
        || /^-{3,}$/u.test(trimmed)
        || /^>\s?/u.test(trimmed)
        || /^<!--/u.test(trimmed)
        || /^<\/?[A-Za-z][^>]*>$/u.test(trimmed);
}

function withoutListPunctuation(text: string): string {
    return text.trimEnd().replace(/[.;]$/u, "");
}

function withParagraphPunctuation(text: string): string {
    if (/^\[English\]\([^)]+\)\s*\|\s*\*\*Русский\*\*$/u.test(text)) return text;
    return /[.!?…:]$/u.test(text) ? text : `${text}.`;
}

export function formatRussianMarkdown(source: string): string {
    const output: string[] = [];
    let paragraph: string[] = [];
    let listPrefix: string | undefined;
    let listText: string[] = [];
    let fence: string | undefined;

    const appendBlank = (): void => {
        if (output.length > 0 && output.at(-1) !== "") output.push("");
    };
    const flushParagraph = (): void => {
        if (paragraph.length === 0) return;
        const text = paragraph.join(" ").replace(/\s+/gu, " ").trim();
        output.push(withParagraphPunctuation(text));
        paragraph = [];
    };
    const flushList = (): void => {
        if (listPrefix === undefined) return;
        const text = withoutListPunctuation(listText.join(" ").replace(/\s+/gu, " "));
        output.push(`${listPrefix}${text}`.trimEnd());
        listPrefix = undefined;
        listText = [];
    };
    const flush = (): void => {
        flushParagraph();
        flushList();
    };

    for (const rawLine of source.replace(/\r\n?/gu, "\n").split("\n")) {
        const line = rawLine.trimEnd();
        const fenceMatch = FENCE.exec(line);
        if (fence !== undefined) {
            output.push(line);
            if (fenceMatch?.[1] === fence) fence = undefined;
            continue;
        }
        if (fenceMatch !== null) {
            flush();
            output.push(line);
            fence = fenceMatch[1];
            continue;
        }
        if (line.trim() === "") {
            flush();
            appendBlank();
            continue;
        }
        const listMatch = LIST_ITEM.exec(line);
        if (listMatch !== null) {
            flush();
            listPrefix = listMatch[1];
            listText = [listMatch[2]];
            continue;
        }
        if (listPrefix !== undefined && /^\s+/u.test(line) && !isStandalone(line)) {
            listText.push(line.trim());
            continue;
        }
        if (isStandalone(line)) {
            flush();
            output.push(line);
            continue;
        }
        flushList();
        paragraph.push(line.trim());
    }
    flush();
    while (output.at(-1) === "") output.pop();
    return `${output.join("\n")}\n`;
}

function markdownFiles(path: string): string[] {
    if (!existsSync(path)) return [];
    if (statSync(path).isFile()) return path.endsWith(".ru.md") ? [path] : [];
    return readdirSync(path, { withFileTypes: true }).flatMap((entry) =>
        markdownFiles(resolve(path, entry.name))
    );
}

function main(): void {
    const check = process.argv.includes("--check");
    const arguments_ = process.argv.slice(2).filter((argument) => argument !== "--check");
    const files = arguments_.flatMap((argument) => markdownFiles(resolve(argument))).sort();
    const stale: string[] = [];
    for (const file of files) {
        const source = readFileSync(file, "utf8");
        const formatted = formatRussianMarkdown(source);
        if (source === formatted) continue;
        if (check) stale.push(file);
        else writeFileSync(file, formatted);
    }
    if (stale.length > 0) {
        for (const file of stale) console.error(`${file}: run npm run docs:format`);
        process.exitCode = 1;
    }
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === resolve(import.meta.filename)) {
    main();
}
