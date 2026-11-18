#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const CAPABILITIES = new Set([
    "ci-protected-workflows",
    "product-release",
    "product-verification",
]);
const FORBIDDEN_ALIASES = new Set([
    "agent-behavior",
    "settings-surface",
    "headless-app",
    "ota-client",
    "verification",
]);
const ALLOWED_COMMANDS = new Set(["./gradlew", "cargo", "node"]);

function splitCommandLine(command: string): string[] {
    const argv: string[] = [];
    let value = "";
    let quote = "";
    let escaped = false;
    for (const character of command) {
        if (escaped) {
            value += character;
            escaped = false;
        } else if (character === "\\" && quote !== "'") {
            escaped = true;
        } else if (quote) {
            if (character === quote) quote = "";
            else value += character;
        } else if (character === "'" || character === '"') {
            quote = character;
        } else if (/\s/.test(character)) {
            if (value) {
                argv.push(value);
                value = "";
            }
        } else {
            value += character;
        }
    }
    if (quote || escaped) throw new Error("unterminated quote or escape");
    if (value) argv.push(value);
    return argv;
}

function validateGate(errors: string[], location: string, command: string, repository: string): void {
    if (["&&", ";", "|", "`", "$("].some((operator) => command.includes(operator))) {
        errors.push(`${location}: Gate contains a shell composition operator: ${command}`);
        return;
    }
    let argv: string[];
    try {
        argv = splitCommandLine(command);
    } catch (error) {
        errors.push(`${location}: Gate is not a valid command line: ${String(error)}`);
        return;
    }
    if (argv.length === 0 || !ALLOWED_COMMANDS.has(argv[0])) {
        errors.push(`${location}: Gate must start with one allowed executable: ${command}`);
    }
    if (argv.includes("and")) {
        errors.push(`${location}: Gate joins commands with prose 'and': ${command}`);
    }
    if (argv[0] === "./gradlew") {
        const selector = argv.indexOf("--tests");
        if (selector < 0 || selector + 1 >= argv.length || !/^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+\.[A-Za-z_$][\w$]*$/.test(argv[selector + 1])) {
            errors.push(`${location}: Gradle Gate must use --tests with one exact FQCN.method selector: ${command}`);
        }
        if (argv.filter((argument) => argument === "--tests").length !== 1) {
            errors.push(`${location}: Gradle Gate must contain exactly one --tests selector: ${command}`);
        }
    } else if (argv[0] === "cargo") {
        const separator = argv.indexOf("--");
        const selectors = argv.slice(1, separator < 0 ? undefined : separator)
            .filter((argument) => /^[A-Za-z_][\w]*(?:::[A-Za-z_][\w]*)+$/.test(argument));
        const manifest = argv.indexOf("--manifest-path");
        if (argv[1] !== "test" || manifest < 0 || argv[manifest + 1] !== "release/Cargo.toml"
            || selectors.length !== 1 || separator < 0 || argv[separator + 1] !== "--exact") {
            errors.push(`${location}: Cargo Gate must select one exact Rust test through release/Cargo.toml: ${command}`);
        }
    } else if (argv[0] === "node") {
        const selectors = argv.filter((argument) => argument === "--test-name-pattern" || argument.startsWith("--test-name-pattern="));
        const testFiles = argv.filter((argument) => /^tools\/(?:tests|emulator|release)\/[^*?]+\.test\.ts$/.test(argument));
        if (!argv.includes("--test") || selectors.length !== 1 || testFiles.length !== 1) {
            errors.push(`${location}: Node Gate must select one named test in one exact test file: ${command}`);
        }
    }
    for (const argument of argv.slice(1)) {
        if (/^(tools|contracts|openspec|release)\//.test(argument) && !existsSync(resolve(repository, argument))) {
            errors.push(`${location}: Gate path does not exist: ${argument}`);
        }
    }
}

function parseNames(value: string): string[] {
    return [...value.matchAll(/`([^`]+)`/g)].map((match) => match[1]);
}

function validateGateContract(
    errors: string[],
    location: string,
    body: string,
    requirement: string,
    scenarios: string[],
    coveredScenarios: Set<string>,
): void {
    const contract = body.match(/^\s*Gate contract:\s*Maps:\s*(.+?)\s+Preconditions:\s*(.+?)\s+Observable result:\s*(.+?)\s+Evidence boundary:\s*(.+?)\s*$/s);
    if (!contract) {
        errors.push(`${location}: Gate must be followed by one Gate contract with Maps, Preconditions, Observable result, and Evidence boundary`);
        return;
    }
    const mapped = parseNames(contract[1]);
    if (!mapped.includes(requirement)) errors.push(`${location}: Gate Maps does not name Requirement: ${requirement}`);
    const mappedScenarios = mapped.filter((name) => scenarios.includes(name));
    if (mappedScenarios.length === 0) errors.push(`${location}: Gate Maps names no Scenario from Requirement: ${requirement}`);
    for (const scenario of mappedScenarios) coveredScenarios.add(scenario);
    for (const label of contract.slice(2)) {
        if (!label.trim()) errors.push(`${location}: Gate contract labels must have non-empty values`);
    }
}

function lineNumber(text: string, offset: number): number {
    return text.slice(0, offset).split("\n").length;
}

function validateSpec(
    errors: string[],
    path: string,
    repository: string,
    requirementTitles: Map<string, string>,
    scenarioTitles: Map<string, string>,
): [number, number] {
    const text = readFileSync(path, "utf8");
    if (/[А-Яа-яЁё]/.test(text)) errors.push(`${path}: current OpenSpec contains Cyrillic`);
    if (/<!--\s*#\s*(Gate|Unverified):/.test(text)) {
        errors.push(`${path}: evidence must not be hidden in comments`);
    }
    if (/^# Unverified:\s*$/m.test(text)) {
        errors.push(`${path}: empty Unverified marker is forbidden; name its scope and provide a complete mapped subsection`);
    }

    const requirements = [...text.matchAll(/^### Requirement: (.+)$/gm)];
    if (requirements.length === 0) {
        errors.push(`${path}: capability contains no requirements`);
        return [0, 0];
    }
    const unverifiedOffset = text.indexOf("\n## Unverified\n");
    const requirementsEnd = unverifiedOffset < 0 ? text.length : unverifiedOffset;
    const markerNames: string[] = [];
    let scenarioCount = 0;

    requirements.forEach((requirement, index) => {
        const start = requirement.index ?? 0;
        const end = index + 1 < requirements.length ? (requirements[index + 1].index ?? requirementsEnd) : requirementsEnd;
        const title = requirement[1];
        const previousRequirement = requirementTitles.get(title);
        if (previousRequirement) {
            errors.push(`${path}: duplicate requirement title also used in ${previousRequirement}: ${title}`);
        } else {
            requirementTitles.set(title, path);
        }

        const block = text.slice(start, end).trim();
        const scenarios = [...block.matchAll(/^#### Scenario: (.+)$/gm)];
        scenarioCount += scenarios.length;
        if (scenarios.length === 0) {
            errors.push(`${path}:${lineNumber(text, start)}: requirement has no scenario`);
        }
        scenarios.forEach((scenario, scenarioIndex) => {
            const scenarioTitle = scenario[1];
            const previousScenario = scenarioTitles.get(scenarioTitle);
            if (previousScenario) {
                errors.push(`${path}: duplicate scenario title also used in ${previousScenario}: ${scenarioTitle}`);
            } else {
                scenarioTitles.set(scenarioTitle, path);
            }
            const scenarioStart = scenario.index ?? 0;
            const scenarioEnd = scenarioIndex + 1 < scenarios.length ? (scenarios[scenarioIndex + 1].index ?? block.length) : block.length;
            if (/\b(?:SHALL|MUST)\b/.test(block.slice(scenarioStart, scenarioEnd))) {
                errors.push(`${path}: scenario contains normative SHALL or MUST: ${scenarioTitle}`);
            }
        });

        const evidence = [...block.matchAll(/^# (Gate|Unverified): (.+)$/gm)];
        if (evidence.length === 0) {
            errors.push(`${path}:${lineNumber(text, start)}: requirement has no visible evidence`);
            return;
        }
        if (scenarios.length > 0 && (evidence[0].index ?? 0) < (scenarios.at(-1)?.index ?? 0)) {
            errors.push(`${path}: evidence appears before the final scenario in Requirement: ${title}`);
        }
        const seenGates = new Set<string>();
        const coveredScenarios = new Set<string>();
        for (const [evidenceIndex, marker] of evidence.entries()) {
            const [markerText, kind, value] = marker;
            const location = `${path}:${lineNumber(text, start + (marker.index ?? 0))}`;
            if (kind === "Gate") {
                if (seenGates.has(value)) errors.push(`${location}: duplicate Gate in one requirement: ${value}`);
                seenGates.add(value);
                validateGate(errors, location, value, repository);
                const bodyStart = (marker.index ?? 0) + marker[0].length;
                const bodyEnd = evidenceIndex + 1 < evidence.length ? (evidence[evidenceIndex + 1].index ?? block.length) : block.length;
                validateGateContract(errors, location, block.slice(bodyStart, bodyEnd), title, scenarios.map((scenario) => scenario[1]), coveredScenarios);
            } else {
                markerNames.push(value);
                for (const scenario of scenarios) coveredScenarios.add(scenario[1]);
            }
            void markerText;
        }
        for (const scenario of scenarios) {
            if (!coveredScenarios.has(scenario[1])) errors.push(`${path}: Scenario has no Gate or Unverified coverage: ${scenario[1]}`);
        }
    });

    const subsectionNames = [...text.slice(requirementsEnd).matchAll(/^### Unverified: (.+)$/gm)].map((match) => match[1]);
    const markers = [...new Set(markerNames)].sort();
    const subsections = [...new Set(subsectionNames)].sort();
    if (JSON.stringify(markers) !== JSON.stringify(subsections)) {
        errors.push(`${path}: Unverified markers ${JSON.stringify(markers)} do not match subsections ${JSON.stringify(subsections)}`);
    }
    if (subsectionNames.length !== new Set(subsectionNames).size) errors.push(`${path}: duplicate Unverified subsections`);
    for (const subsection of [...text.slice(requirementsEnd).matchAll(/^### Unverified: (.+)$([\s\S]*?)(?=^### Unverified: |(?![\s\S]))/gm)]) {
        const body = subsection[2];
        for (const label of ["Maps:", "Unavailable observation:", "Why unavailable:", "Closure evidence:"]) {
            if (!body.includes(label)) errors.push(`${path}: Unverified subsection ${subsection[1]} lacks ${label}`);
        }
        if (/\b(?:SHALL|MUST)\b/.test(body)) errors.push(`${path}: Unverified subsection contains normative SHALL or MUST: ${subsection[1]}`);
    }
    return [requirements.length, scenarioCount];
}

export function validateOpenSpec(specs = "openspec/specs"): string[] {
    const root = resolve(specs);
    const repository = resolve(root, "../..");
    const actual = new Set(
        readdirSync(root, { withFileTypes: true })
            .filter((entry) => entry.isDirectory() && existsSync(resolve(root, entry.name, "spec.md")))
            .map((entry) => entry.name),
    );
    const errors: string[] = [];
    const missing = [...CAPABILITIES].filter((name) => !actual.has(name));
    const extra = [...actual].filter((name) => !CAPABILITIES.has(name));
    if (missing.length || extra.length) {
        errors.push(`${root}: capabilities differ; missing=${JSON.stringify(missing.sort())}, extra=${JSON.stringify(extra.sort())}`);
    }
    const aliases = [...actual].filter((name) => FORBIDDEN_ALIASES.has(name));
    if (aliases.length) errors.push(`${root}: superseded aliases remain: ${JSON.stringify(aliases.sort())}`);

    const requirementTitles = new Map<string, string>();
    const scenarioTitles = new Map<string, string>();
    for (const capability of [...actual].filter((name) => CAPABILITIES.has(name)).sort()) {
        validateSpec(errors, resolve(root, capability, "spec.md"), repository, requirementTitles, scenarioTitles);
    }
    return errors;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
    const errors = validateOpenSpec(process.argv[2]);
    if (errors.length) {
        console.error(errors.join("\n"));
        process.exitCode = 1;
    } else {
        console.log("Validated current OpenSpec capability and evidence structure.");
    }
}
