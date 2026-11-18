import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import test from "node:test";

const product = resolve(import.meta.dirname, "../..");
const workspace = resolve(product, "..");
const agents = resolve(process.env.VOBOOST_AGENTS_ROOT ?? join(workspace, "voboost-agents"));
const components = ["Button", "Buttons", "Checkbox", "Dialog", "Font", "Hint", "Language", "Panel", "Popup", "Radio", "Screen", "Section", "Select", "Tabs", "Text", "Theme", "Toast"];
const shared = ["components", "components-architecture-decisions", "components-visual-testing", "components-demo-java", "components-demo-kotlin", "components-demo-compose", "components-demo-pixel", "translation-tooling", "component-spacing", "launcher-client-probe", "cluster-font-runtime", "cluster-pw-visual-observer", "native-dependencies", "cluster-link-inputs", "installer-themes", "installer-i18n", "installer-build", "installer-implementation-checklist", "installer-error-handling", "installer-tauri-config", "installer-troubleshooting", "installer-cross-repository-release-contract", "installer-gui-acceptance", "installer-gui-test-cases", "installer-native-adb", "installer-adb-resources", "installer-cluster-pairing", "build-modified", "codestyle-integration"];

function markdown(file: string): string {
    return readFileSync(file, "utf8");
}

function files(directory: string): string[] {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const file = join(directory, entry.name);
        return entry.isDirectory() ? files(file) : entry.isFile() ? [file] : [];
    });
}

function links(document: string): string[] {
    return [...document.matchAll(/!?\[[^\]]*\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g)].map((match) => match[1]);
}

test("general component guides have one paired product home", () => {
    for (const stem of [...shared, ...components.map((name) => `component-${name.toLowerCase()}`)]) {
        for (const language of ["", ".ru"]) {
            assert.ok(existsSync(join(product, "docs-dev", `${stem}${language}.md`)), stem + language);
        }
    }
    for (const name of components) {
        const directory = name === "Language" ? "i18n" : name.toLowerCase();
        for (const language of ["", ".ru"]) {
            assert.equal(existsSync(join(workspace, "voboost-components/src/main/java/ru/voboost/components", directory, `${name}${language}.md`)), false);
        }
    }
    const oldInstaller = join(workspace, "voboost-installer/docs");
    if (existsSync(oldInstaller)) assert.deepEqual(readdirSync(oldInstaller).filter((name) => name.endsWith(".md")), []);
});

test("every agent keeps its exact documentation pair beside metadata and code", () => {
    const metadata = files(join(agents, "src/main/java/ru/voboost/agents")).filter((file) => file.endsWith("Agent.json"));
    assert.ok(metadata.length > 0);
    for (const input of metadata) {
        for (const language of ["", ".ru"]) {
            const file = input.replace(/\.json$/, `${language}.md`);
            assert.ok(existsSync(file), file);
            assert.equal(existsSync(join(product, "docs-dev", basename(file))), false);
            for (const href of links(markdown(file))) {
                if (href.startsWith("#") || /^[a-z][a-z\d+.-]*:/i.test(href)) continue;
                const target = resolve(dirname(file), decodeURIComponent(href.split(/[?#]/, 1)[0]));
                assert.ok(existsSync(target), `${file}: ${href}`);
            }
        }
    }
});

test("component entrypoints stay concise and the glossary has one owner", () => {
    for (const repository of ["app", "agents", "inject", "installer", "components", "codestyle"]) {
        for (const language of ["", ".ru"]) {
            const file = join(workspace, `voboost-${repository}`, `README${language}.md`);
            const text = markdown(file);
            assert.ok(text.trim().split("\n").length <= 12, file);
            assert.match(text, /docs-dev\//);
            assert.match(text, /\]\(LICENSE\)/);
        }
    }
    for (const owner of ["voboost-codestyle", "voboost-installer/docs"]) {
        for (const language of ["", ".ru"]) assert.equal(existsSync(join(workspace, owner, `GLOSSARY${language}.md`)), false);
    }
    const ids = (language: string): string[] => [...markdown(join(product, `GLOSSARY${language}.md`)).matchAll(/<!-- term: ([a-z0-9-]+) -->/g)].map((match) => match[1]);
    const english = ids("");
    assert.equal(new Set(english).size, english.length);
    assert.deepEqual(ids(".ru"), english);
    for (const id of ["device-operation", "fixed-staging", "absence-check", "license-summary", "manifest-trust"]) assert.ok(english.includes(id), id);
});

test("central native guide retains source provenance and bounded build claims", () => {
    const text = markdown(join(product, "docs-dev/native-dependencies.md"));
    for (const fragment of ["5dfc8546954ce3b3198132ab13fddb89ee92cdd7", "a96c7978a9fe578e0e265cc8e45cb117fa8250a3", "source commit used to create this archive is unknown", "make test-armv7-dobby-prebuilt", "c4882b81f186bc131f79ad0b28808bbeef6fc678", "d12b749a57b7eee47eae4b7b52aaac986ae516145bf161101dca0ae1370d04d9", "make test-third-party-source-contract"]) assert.ok(text.includes(fragment), fragment);
    assert.match(text, /do not establish ART injection or vehicle readiness/);
});

test("the standalone rebuild body contains no relative guide links after navigation", () => {
    const text = markdown(join(product, "docs-dev/build-modified.md"));
    assert.ok(text.includes("**English** | [Русский](build-modified.ru.md)\n\n"));
    const standalone = text.replace("**English** | [Русский](build-modified.ru.md)\n\n", "");
    assert.deepEqual(links(standalone).filter((href) => !/^[a-z][a-z\d+.-]*:/i.test(href) && !href.startsWith("#")), []);
});
