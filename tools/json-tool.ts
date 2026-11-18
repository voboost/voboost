#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";

type JsonObject = Record<string, unknown>;

function readObject(path: string): JsonObject {
    const value: unknown = JSON.parse(readFileSync(path, "utf8"));
    if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error("JSON root must be an object");
    return value as JsonObject;
}

function scalar(value: unknown): string {
    if (value === undefined || value === null) return "";
    if (typeof value === "boolean") return value ? "True" : "False";
    return typeof value === "object" ? JSON.stringify(value) : String(value);
}

function getPath(document: unknown, path: string): unknown {
    return path.split(".").filter(Boolean).reduce<unknown>((value, part) => {
        if (Array.isArray(value) && /^\d+$/.test(part)) return value[Number(part)];
        if (value !== null && typeof value === "object") return (value as JsonObject)[part];
        return undefined;
    }, document);
}

function injections(document: JsonObject): JsonObject[] {
    const value = document.injections;
    return Array.isArray(value) ? value.filter((item): item is JsonObject => item !== null && typeof item === "object" && !Array.isArray(item)) : [];
}

function updateYamlScalar(key: string, value: string, source: string, destination: string): void {
    const lines = readFileSync(source, "utf8").split(/(?<=\n)/);
    let found = false;
    const updated = lines.map((line) => {
        if (line.replace(/\n$/, "") === `${key}: ${value}`) {
            found = true;
            return line;
        }
        if (line.startsWith(`${key}:`)) {
            found = true;
            return `${key}: ${value}\n`;
        }
        return line;
    });
    if (!found) updated.push(`${key}: ${value}\n`);
    writeFileSync(destination, updated.join(""), "utf8");
}

function usage(): never {
    throw new Error("usage: json-tool.ts get FILE PATH | injection-state FILE ID | nonfailed FILE | wait-state FILE IDS active|gone|gone-absent|any | yaml-set KEY VALUE SOURCE DESTINATION");
}

try {
    const [command, ...args] = process.argv.slice(2);
    if (command === "get" && args.length === 2) {
        console.log(scalar(getPath(readObject(args[0]), args[1])));
    } else if (command === "injection-state" && args.length === 2) {
        const row = injections(readObject(args[0])).find((item) => item.id === args[1]);
        console.log(scalar(row?.state));
    } else if (command === "nonfailed" && args.length === 1) {
        console.log(JSON.stringify(injections(readObject(args[0])).filter((item) => item.state !== "failed")));
    } else if (command === "wait-state" && args.length === 3) {
        const [path, idsText, desired] = args;
        const rows = new Map(injections(readObject(path)).map((item) => [String(item.id), String(item.state)]));
        const ids = idsText.split(",");
        const matches = desired === "active"
            ? ids.every((id) => rows.get(id) === "active")
            : desired === "gone"
                ? ids.every((id) => !rows.has(id) || ["removed", "rolled_back"].includes(rows.get(id)!))
                : desired === "gone-absent"
                    ? ids.every((id) => !rows.has(id))
                : desired === "any"
                    ? ids.every((id) => rows.has(id))
                    : false;
        process.exitCode = matches ? 0 : 1;
    } else if (command === "yaml-set" && args.length === 4) {
        updateYamlScalar(args[0], args[1], args[2], args[3]);
    } else {
        usage();
    }
} catch (error) {
    console.error(`json-tool: ${error instanceof Error ? error.message : String(error)}`);
    process.exitCode = 2;
}
