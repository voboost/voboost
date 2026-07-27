# Архитектура Voboost — функционирование системы

Документ описывает, как устроена система Voboost от пользовательского выбора в
приложении до выполнения агента в целевом процессе автомобиля, включая миграцию
агентов из Frida/JS в Java/DEX+LSPlant.

---

## 1. Репозитории и зоны ответственности

| Репозиторий       | Стек                            | Зона                                                                                  |
|-------------------|---------------------------------|---------------------------------------------------------------------------------------|
| `voboost`         | Kotlin (Android app)            | Пользовательский UI, выбор фич, формирование inject-плана, OTA self-update            |
| `voboost-config`  | Kotlin (Android library)        | Типобезопасная модель конфига (плоская), чтение/запись/watch YAML                     |
| `voboost-inject`  | Rust + C injector + C++ implant (+ LSPlant/Dobby) | Демон инъекции: ptrace, dex-загрузка, LSPlant init, шов к агентам                     |
| `voboost-script`  | JS (Frida) → Java (DEX+LSPlant) | Агенты + рантайм (`Agent`, `Hooker`, `Config`…), сборка в dex                         |
| `voboost-install` | Tauri 2 (Rust + React/TS)       | Десктоп-установщик: APK + voboost-inject + agents + manifest + init-хук на устройство |

Граница между `voboost-inject` и `voboost-script` — **единственный фиксированный
шов** `ru.voboost.inject.Implant.init(String configPath)`. Implant не знает про
агентов; набор агентов меняется без пересборки implant.

---

## 2. Конфиг приложения (voboost-config)

App хранит выбор пользователя в **плоском** YAML-файле (`voboost-config`,
Kotlin-библиотека, Hoplite, kebab-case ключи). Каждая фича — одно поле; значение
`original` = «не переопределять, пропустить» (поведение машины по умолчанию).
Модель — data class, все поля nullable (graceful degradation, partial updates,
diff). `ConfigManager` (Kotlin): type-safe load/save YAML, file watching
(реал-тайм через `ReloadableConfig`), diff расчёт, Result-based API (не throws
в public API). Вложенных объектов нет — только плоские поля.

Релевантные для примеров поля (из `voboost-config/src/config.yaml`):

```yaml
# Звук предупреждения пешеходов: original = не трогать (enabled: false),
# off = выключить (enabled: true) — маппинг в agent-конфиг см. §9.2
vehicle-pedestrian-warning: original, off

# Язык интерфейса (локализация заголовков агентов в машине)
settings-language: en                      # en | ru

# Запуск Voboost и внедрение в интерфейс машины:
#   off       — запускается, читает конфиг и выходит (внедрения нет)
#   hidden    — работает без внедрения в интерфейс машины
#   interface — внедряется в интерфейс машины (добавляет пункт меню настроек)
settings-startup: off, hidden, interface
```

Полный список фич — в `voboost-config/src/config.yaml` (`applications-launcher`,
`applications-dpi`, `applications-dns`, `interface-keyboard`,
`interface-multidisplay`, `interface-phone-numbers`,
`interface-widget-weather`, `vehicle-fuel-mode`, `vehicle-drive-mode`,
`settings-theme`, `settings-interface-shift-*`, `settings-active-tab`,
`settings-car-model`).

### 2.1. Связь конфига и агентов

Конфиг `voboost-config` — это **настройки приложения** (что выбрал
пользователь). Inject-план (§3) — производное: app по `Config` + таблице
процессов (§8) определяет, какие агенты включить в каком процессе и с какими
параметрами. Всё, что есть в UI app, есть в voboost-config; настроек вне конфига
не существует. В целевом процессе `Implant` читает per-process JSON, делит его
на per-agent срезы и через `AgentContext.config()` передаёт каждому агенту
**только его срез** (`config.agents[id]` + общие настройки `logLevel`,
`language`). Агент работает со своим `Config` и видит только свою
функциональность. Детали — в §7.1.

**Два принципа маппинга фича → агент:**
- **Имя поля конфига ≠ id агента.** Поле в `voboost-config` и id агента в
  `voboost-script` могут не совпадать: `vehicle-pedestrian-warning` (поле) ↔
  `pedestrian-warning` (id агента), `interface-widget-weather` (поле) ↔
  `weather-widget` (id). Соответствие — в таблице процессов §8.
- **Значение поля описывает целевое состояние фичи машины, не состояние
  агента.** Поэтому `off` для `vehicle-pedestrian-warning` = «выключить звук в
  машине» → `enabled: true` (агент **работает**, чтобы выключить). А `off` для
  `settings-startup` = «не внедрять интерфейс» → `settings-menu.enabled: false`
  (агент **не стартует**). Какое значение поля маппится в `enabled: false`,
  определяется per-фичей в §8/§9; `off` не универсально значит «выключить
  агента».

---

## 3. inject-план: от конфига к per-process JSON (app)

App читает `voboost-config`, и по таблице процессов (§8) определяет, какие
агенты включить в каком процессе. Результат — **inject-план**: набор
per-process JSON-конфигов, по одному на каждый целевой процесс. Это производное
от `voboost-config`, не отдельный пользовательский конфиг.

Конфиг процесса (одна запись на процесс) — пример для
`com.qinggan.app.vehiclesetting` (там settings-menu):

```json
{
  "logLevel": "info",
  "language": "en",
  "agents": {
    "settings-menu": { "enabled": true }
  }
}
```

Для `com.qinggan.systemservice` (там pedestrian-warning) — отдельный конфиг:

```json
{
  "logLevel": "info",
  "agents": {
    "pedestrian-warning": { "enabled": true }
  }
}
```

- `enabled: true/false` — агент включён/выключен. `false` (в app выбрано значение
  по умолчанию: `original` или эквивалент вроде `off`/`hidden` для settings-menu)
  → агент не стартует, хук не ставится, оригинальная логика машины работает как
  есть. `true` → агент делает свою работу.
- `config` — per-agent параметры (значение, выбранное пользователем), только
  когда у агента есть параметры. У бинарных агентов (settings-menu,
  pedestrian-warning) его нет — состояние в самом `enabled`. Пример агента с
  параметрами:
  ```json
  {
    "logLevel": "info",
    "language": "en",
    "agents": {
      "media-source": { "enabled": true, "config": { "source": "spotify" } }
    }
  }
  ```
  Здесь `config.source` — per-agent параметр, доступен агенту через
  `ctx.config().get("source", String.class)` (§7.7).
- `logLevel`, `language` — общие настройки процесса (язык — для локализации
  агентами, плоская строка как в `voboost-config`).

### 3.1. Как settings-menu отражается в плане

settings-menu управляется полем `settings-startup` в `voboost-config`.
Значение `interface` = «внедрять в интерфейс машины» → app ставит
`settings-menu.enabled: true` в конфиг процесса `com.qinggan.app.vehiclesetting`.
Значения `off`/`hidden` → `enabled: false` → агент не стартует, пункт меню не
добавляется. Отдельного флага для settings-menu в конфиге нет — фича
включается значением `settings-startup: interface`.

---

## 4. Доставка в целевой процесс (через файлы)

Взаимодействие app ↔ voboost-inject ↔ целевой процесс — **только файлами**, не
сокетом и не binder. Причина: целевой процесс живёт в app-домене SELinux,
voboost-inject — в root-домене; сокет потребует allow-правил с обеих сторон,
файл — нет.

App пишет свой inject-план (все per-process конфиги) в файл, доступный
voboost-inject (зона app `/data/user/0/ru.voboost/files/`, подпись ed25519).
voboost-inject читает его, верифицирует подпись, и для каждого целевого процесса
пишет его срез в `/data/voboost-inject/shared/<process>.json` (world-readable
0644, атомарно: write-tmp + rename). Этот путь передаётся в
`Implant.init(configPath)` при инъекции.

Директория `shared/` — общая точка обмена через SELinux-границу. Два независимых
механизма доступа работают вместе: DAC (POSIX-права 0644 на файле, world-readable)
и MAC (SELinux-метка `media_rw_data_file` на директории `shared/`, которую любой
SELinux-домен, включая инъецированный app-процесс, читает без дополнительных
allow-правил). Оба нужны одновременно: без 0644 SELinux-allow не поможет
(DAC проверяется первым), а обычный root-файл (0700) в root-домене (без метки
`media_rw_data_file`) инъецированный процесс не прочитал бы даже при 0644 —
MAC-проверка SELinux запретила бы доступ, и DAC-разрешение не помогло бы.

`<process>` — имя целевого Android-процесса (имя пакета/процесса), в который
идёт инъекция. Для каждого процесса — свой конфиг-файл, т.к. в каждом работают
свои агенты. Целевые процессы — 8 (§8): например
`com.qinggan.app.vehiclesetting` (меню настроек, там settings-menu),
`com.qinggan.systemservice` (системный сервис, там pedestrian-warning).

**Отличие от JS/Frida-реализации:** в старой схеме каждый агент читал свой
конфиг по hardcoded per-agent пути (по файлу на фичу). В новой схеме hardcoded
путей нет — конфиг процесса один (`shared/<process>.json`), `Implant` делит его
на срезы и через `ctx.config()` передаёт агенту только его часть
(`config.agents[id]`).

---

## 5. Инъекция (voboost-inject)

`voboost-inject` — root-процесс (Rust + native C/C++ payload). Три слоя:

1. **Демон** (Rust, root): poll-loop, /proc watching, reconcile (по maps-маркеру),
   bitness-detect, safety (rate-limit + circuit breaker + panic-quarantine),
   trust (ed25519 + sha256), release-manifest verify.
2. **Injector** (C, `libinjector.a`, статически в демон): ptrace attach,
   `remote_call64/32`, `__loader_dlopen(libimplant.so)`, remote-call `implant_init`.
3. **Implant** (C++, `libimplant.so`, per ABI): ART attach, hidden-API exempt
   (`setHiddenApiExemptions("L")`), LSPlant init, **грузит `agents.dex` через
   `InMemoryDexClassLoader`**, вызывает `Implant.init(configPath)`.

Implant не ссылается ни на один класс агента — только на `Implant.init`.
Падение шва перехватывается (JNI exception cleared), целевой процесс не
роняется.

### 5.1. Битность и процессы

Устройство Voyah — multi-ABI (`arm64-v8a` + `armeabi-v7a`). Демон определяет
битность целевого процесса по `/proc/<pid>/exe` (`app_process32`/`app_process64`)
и выбирает payload нужной ABI. Cross-bitness regsets: `arm64_regs` (64) /
`user_regs_32` (32, Thumb через CPSR.T — не PC LSB).

---

## 6. Нативные библиотеки (lib*)

Три нативных артефакта, разная роль:

**`libimplant.so`** (C++, per ABI) — in-process загрузчик, живёт в целевом
процессе после инъекции. Присоединяется к ART (JNI), снимает hidden-API
политику, инициализирует LSPlant, грузит `agents.dex` через
`InMemoryDexClassLoader` и вызывает `Implant.init(configPath)` (точка входа в
рантайм агентов). Содержит JNI-мост LSPlant (native-методы `Hooker`). **Runtime
payload** — доставляется на устройство и dlopen'ится в каждом инъецируемом
процессе.

**`liblsplant.so`** (C++, per ABI) — LSPlant, движок Java-хуков (Xposed-style
inline hooking ART-методов: `Hook`/`UnHook`/`Deoptimize`). Даёт рантайму
агентов возможность подменять реализации Java-методов. **Runtime payload** —
`libimplant.so` dlopen'ит её по полному пути в payload-директории (рядом с собой).
Собирается из source (NDK r29, `c++_static`), чтобы `DT_NEEDED` не содержал
`libc++_shared.so` (иначе конфликт с in-process system libc++ на Android 9).

**`libdobby.a`** (C++, статическая библиотека) — Dobby, движок native inline
hooks (для native-функций ART и самой LSPlant). Нужен LSPlant для низкоуровневой
подмены native-методов. **Build-time only** — статически линкуется в
`libimplant.so` при сборке, в runtime не существует как отдельный файл и не
доставляется на устройство. Собирается из source для `arm64-v8a`; для
`armeabi-v7a` upstream Dobby ломан (удалён `code_buffer_arm.h`, dangling
includes), поэтому используется закоммиченный prebuilt-фallback
`prebuilt/armeabi-v7a/libdobby.a` (старый Dobby с рабочим arm-портом).
**`## Unverified`** — см. build-and-signing spec в voboost-inject/openspec/specs/.

**Итог по payload:** на устройство доставляются только `libimplant.so` +
`liblsplant.so` (per ABI); `libdobby.a` исчезает на этапе линковки (внутри
`libimplant.so`). OTA-дельта качает только два runtime-файла.

**Сборка third-party** (`make build-third-party`): LSPlant + Dobby собираются из
`third_party/{lsplant,dobby}` git-субмодулей через CMake (NDK r29, `c++_static`).
Субмодули трекают чистые upstream-коммиты; NDK-r29 адаптации хранятся как
patch-файлы в `patches/{dobby,lsplant}/` и применяются сборкой идемпотентно
(пропуск если уже применены) — свежий `git submodule update --init` +
`make build-third-party` воспроизводим без fork.

---

## 7. Рантайм агентов (voboost-script, Java)

### 7.1. `Implant` — точка входа и оркестрация

`configPath` — путь к JSON-файлу из §4
(`/data/voboost-inject/shared/<process>.json`), который app подготовил (§3)
и voboost-inject доставил в целевой процесс. Это единственный способ передачи
настроек от app к агентам.

`Implant.init(configPath)` — точка входа (implant C++ зовёт через JNI). Она же
делает оркестрацию: читает файл, парсит JSON, и для каждого включённого агента
создаёт `AgentContext` со **срезом** конфига (`config.agents[id]` + общие
настройки `logLevel`, `language`). Агенты не читают файл напрямую — получают
свой `Config` через `ctx.config()` (типобезопасный доступ).

```java
package ru.voboost.inject;

public final class Implant {
    public static void init(String configPath) {
        // 1. прочитать configPath -> JSONObject (inject-план от app)
        // 2. setLogLevel(config.logLevel)
        // 3. определить текущий процесс из самого процесса (cmdline) —
        //    имя файла конфига не источник истины, он может быть переименован
        // 4. discovery: AgentRegistry.AGENTS -> filter @AgentInfo.process == current
        // 5. для каждого подходящего агента с config.agents[id].enabled == true:
        //    создать AgentContext (срез config.agents[id] + общие настройки),
        //    вызвать agent.start(ctx) в try/catch
        // 6. ConfigWatcher (inotify на configPath) -> onConfigChanged (live-update)
    }
}
```

`AgentRegistry` **генерируется на сборке** (annotation processor по
`@AgentInfo`), не рантайм-скан classpath (дорого/ненадёжно на Android). Изоляция
отказов: каждый `start()` в своём try/catch — падение одного агента не роняет
остальные и не отправляет процесс в карантин.

### 7.2. `Agent` — жизненный цикл

```java
public interface Agent {
    void start(AgentContext ctx) throws Throwable;    // ставит хуки / стартует циклы
    default void onConfigChanged(Config cfg) {}        // live-update без переинъекции
    default void stop() {}                             // idempotent; рантайм авто-снимает
}

@AgentInfo(id = "media-key", process = "com.qinggan.keymanager.service", boot = false)
public final class MediaKeyAgent implements Agent { ... }
```

`@AgentInfo` — источник истины для манифеста (как `AGENT_META` в JS).

### 7.3. `AgentContext` — сервисы рантайма

```java
public interface AgentContext {
    Config config();          // срез конфига этого агента (config.agents[id])
    Hooker hooker();          // хуки поверх LSPlant (JNI-мост)
    Scheduler scheduler();    // фоновые циклы/ретраи
    Reflect reflect();        // поля/классы (замена Java.use)
    Log log();
    ClassLoader targetClassLoader();
    android.content.Context androidContext();
}
```

Всё, что агент создаёт через `ctx` (хуки, задачи), рантайм регистрирует и снимает
автоматически в `stop()`.

### 7.4. `Hooker` — фасад над LSPlant

**LSPlant** — open-source движок хуков Java-методов в ART (Android Runtime).
Принцип (как Xposed): подменяет реализацию метода на свою «обёртку», при этом
оригинал остаётся доступен (через reflection). LSPlant делает это через inline
hooking низкоуровневых ART-структур (для native-методов ART использует Dobby —
native inline hooks). Зачем он нужен: единственный способ для Java-агента
перехватить вызов произвольного метода в чужом приложении (Frida давала это
из коробки через `Java.use().implementation = ...`; в новой схеме эту роль
берёт LSPlant).

LSPlant — C++ API: `lsplant::Hook(env, target_method, hooker_obj, callback)`.
Java-агент не может вызвать его напрямую — нужен **JNI-мост в `libimplant.so`**
(implant уже инициализирует LSPlant при инъекции). `Hooker` — Java-фасад поверх
этого моста, который агент использует:

```java
public interface Hooker {
    // Member — java.lang.reflect.Member (Method/Constructor): LSPlant хукает
    // и методы, и конструкторы, поэтому не сужаем до Method.
    Member method(String className, String name, Object... paramTypes);
    Unhook hook(Member target, MethodHook cb);     // before/after, оригинал вызывается сам
    Unhook replace(Member target, Replacer r);     // полная замена (one-shot)
}

public abstract class MethodHook {
    protected void before(Param p) throws Throwable {}
    protected void after(Param p) throws Throwable {}
}
public interface Replacer { Object replace(Param p) throws Throwable; }

public interface Param {
    Object thiz();
    Object[] args();
    <T> T arg(int i);
    Object result();  void setResult(Object r);        // в before() — пропустить оригинал
    Throwable throwable(); void setThrowable(Throwable t);
    Object invokeOriginal(Object... args) throws Throwable;  // = this.m.call(this, ...)
}
public interface Unhook { void unhook(); }
```

Семантика Xposed (как в LSPlant): `setResult`/`setThrowable` в `before()`
**пропускает** оригинал; `after()` выполняется после оригинала. Чище, чем frida
«вызвать `this.m.call` вручную». Стекирование: LSPlant API допускает несколько
хуков на один метод (`keyboard-ru` + `keyboard-lock-en` на `saveInputMode` больше
не затирают друг друга, как во frida `.implementation =` last-wins) — сам факт
стекирования гарантирован API; порядок вызовов before-хуков между собой не
проверен на устройстве (открытый вопрос, см. §15).

**JNI-мост** — нативные методы в `libimplant.so`, которые `Hooker` дёргает:
```c
// класс ru.voboost.runtime.HookerNative, RegisterNatives в libimplant.so
JNIEXPORT jobject  JNICALL nativeHook(JNIEnv*, jclass, jobject targetMethod, jobject callback);
JNIEXPORT jboolean JNICALL nativeUnhook(JNIEnv*, jclass, jobject targetMethod);
```
Под капотом вызывают `lsplant::Hook`/`UnHook` (implant dlopen'ит
`liblsplant.so` и резолвит её символы через `dlsym`).
`Hooker.replace(...)` — это `hook(...)` с `before`, который зовёт `setResult`
и не вызывает оригинал (сахар для one-shot агентов).

### 7.5. `Scheduler` — циклы/ретраи (loop-агенты)

```java
public interface Scheduler {
    Task retry(RetryPolicy policy, RetryStep step);  // step: () -> Decision {RETRY, STOP}
    Task submit(Runnable r);
    void runOnMainThread(Runnable r);                // = Java.scheduleOnMainThread
}

// RetryStep — функциональный интерфейс, безаргументный колбэк:
@FunctionalInterface
public interface RetryStep { Decision step() throws Throwable; }
public enum Decision { RETRY, STOP }
```
Замена `setTimeout`-рекурсии loop-агентов (forced-ev, pedestrian-warning). Задачи
кооперативно гасятся на `stop` (флаг + прекращение планирования).

### 7.6. `Reflect` — поля/классы

```java
public interface Reflect {
    Class<?> use(String className);                       // Class.forName в targetClassLoader
    <T> T getField(Object obj, String name);              // setAccessible + get
    void   setField(Object obj, String name, Object value);
    <T> T getStaticField(Class<?> c, String name);
    Object call(Object obj, String name, Object... args); // рефлексивный вызов
}
```

### 7.7. `Config` — per-agent + live-update

```java
public interface Config {
    boolean enabled();
    <T> T get(String key, Class<T> type);    // per-agent параметр
    String processSetting(String key);       // общая настройка процесса (logLevel, language)
    String raw();
}
```

На `inotify`-событие на `configPath` → `onConfigChanged(newConfig)`; toggling
`enabled` → `start()`/`stop()` соответствующего агента.

### 7.8. Таблица Frida JS → Java

| Frida JS                                         | Java                                      |
|------------------------------------------------|-----------------------------------------|
| `Java.use(cls)`                                  | `reflect.use(cls)`                        |
| `m.implementation = fn; return this.m.call(...)` | `hooker.hook(m, before/after)`            |
| `return null` (блок)                             | `before`: `p.setResult(null)`             |
| `.overload(sig).implementation`                  | `hooker.method(cls, name, paramTypes...)` |
| `this.m.call(this, ...)`                         | `p.invokeOriginal(...)`                   |
| `.implementation = () => fake` (one-shot)        | `hooker.replace(m, p -> fake)`            |
| `getFieldValue` / `setFieldValue`                | `reflect.getField` / `setField`           |
| `setTimeout`-ретраи                              | `scheduler.retry(policy, step)`           |
| `Java.scheduleOnMainThread`                      | `scheduler.runOnMainThread`               |
| `loadConfig(PATH)`                               | `ctx.config()` (конфиг процесса)          |
| `runAgent(main)`                                 | оркестрация `Implant`                     |
| `Logger` + `-log.js` константы                   | `Log` + `*Log`-константы                  |

---

## 8. Три формы агентов (маппинг реальных)

| Класс               | Агенты                                                                                                                    | Frida-форма                                                                       | Java-форма                                         |
|-------------------|-------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|--------------------------------------------------|
| hook (event-driven) | media-key, media-source, media-window, app-viewport, keyboard-*, app-launcher, launcher-allapps, navbar-launcher, phone-num, weather-widget, **settings-menu** | `Java.use(cls).m.implementation = fn`; `return null` блок; `this.m.call` оригинал | `hooker.hook(m, before/after)`; `p.setResult(...)` |
| loop (retry-state)  | forced-ev, pedestrian-warning                                                                                          | `setTimeout`-ретраи, мутация состояния, не хук                                    | `scheduler.retry(policy, step)`                    |
| one-shot            | adas-activation                                                                                                           | `.implementation = () => fake` (замена без оригинала)                             | `hooker.replace(m, p -> fake)`                     |

### Процессы ↔ фичи (16 агентов → 8 процессов)

| id                                                                            | process                        | хукаемый класс                                                     |
|-----------------------------------------------------------------------------|------------------------------|------------------------------------------------------------------|
| app-launcher, launcher-allapps, navbar-launcher, media-source, weather-widget | com.qinggan.app.launcher       | `com.qinggan.launcher.base.*`, `LauncherModel`, `mediaCard.home.*` |
| app-viewport                                                                  | system_server                  | `com.android.server.wm.ActivityRecord`                             |
| app-multi-display, forced-ev, pedestrian-warning                              | com.qinggan.systemservice      | `MultiDisplayImpl`, `com.qinggan.canbus.CanBusManager`             |
| keyboard-ru, keyboard-lock-en                                                 | com.qinggan.app.qgime          | `com.qinggan.app.qgime.*`                                          |
| adas-activation, **settings-menu**                                            | com.qinggan.app.vehiclesetting | `BaiduProviderUtil`, `CarSettingActivity`                          |
| media-window                                                                  | com.qinggan.media              | `SrcMediaActivity`                                                 |
| media-key                                                                     | com.qinggan.keymanager.service | `EventListenerSink`                                                |
| phone-num                                                                     | com.qinggan.bluetoothphone     | `Util`                                                             |

---

## 9. Примеры агентов: settings-menu + pedestrian-warning

Два агента-примера, иллюстрирующие две формы (hook и loop) и полный путь
(app → voboost-inject → implant → dex → LSPlant хук → эффект в машине).

### 9.1. settings-menu (hook, процесс `com.qinggan.app.vehiclesetting`)

**Что делает:** добавляет пункт «Voboost» в меню настроек машины, по клику
запускает наше приложение (`ru.voboost.MainActivity`). Управляется полем
`settings-startup` в `voboost-config`: значение `interface` → агент включён,
`off`/`hidden` → агент не стартует (§3.1).

Механика (порт `voboost-to-menu-mod.js`):
- хукает `CarSettingActivity.onCreate` (after-хук — оригинал отработал, UI готов);
- клонирует системную кнопку настроек (`mainMenuItemSystemSetting`) — её layout,
  иконку, шрифты (`BoldTextView`), padding, layout params;
- вставляет клонированную кнопку с локализованным заголовком (`language` —
  общая настройка процесса)
  **перед** системной кнопкой в `menuContainer` (LinearLayout);
- по клику → `ActivityAnimUtils.startActivityByAnim(context, intent)` с
  `FLAG_ACTIVITY_NEW_TASK` и explicit component
  `<ru.voboost, ru.voboost.MainActivity>` (Class.forName не годится — classloader
  целевого приложения не имеет наших классов).

```java
@AgentInfo(id = "settings-menu", process = "com.qinggan.app.vehiclesetting", boot = false)
public final class SettingsMenuAgent implements Agent {
    private Unhook unhook;
    private final Map<Object, View> inserted = new WeakHashMap<>(); // activity -> наша кнопка

    public void start(AgentContext ctx) {
        Member m = ctx.hooker().method(
            "com.qinggan.app.vehiclesetting.CarSettingActivity",
            "onCreate", "android.os.Bundle");
        unhook = ctx.hooker().hook(m, new MethodHook() {
            protected void after(Param p) {            // оригинал onCreate уже отработал
                View btn = createMenuItem(ctx, p.thiz());  // клонировать, вставить, клик
                inserted.put(p.thiz(), btn);              // запомнить для отката
            }
        });
    }

    public void stop() {
        if (unhook != null) unhook.unhook();               // снять хук onCreate
        for (Map.Entry<Object, View> e : inserted.entrySet()) { // убрать уже вставленные кнопки
            View btn = e.getValue();
            if (btn.getParent() instanceof ViewGroup) ((ViewGroup) btn.getParent()).removeView(btn);
        }
        inserted.clear();
    }
}
```

Если в app выбрано `settings-startup: off`/`hidden` → `enabled: false` → агент
не start → кнопка не добавляется → стандартное меню без нашего пункта.

**Откат (live-update):** при toggle `interface` → `off`/`hidden` (`enabled:
true → false`) `stop()` снимает хук `onCreate` **и** удаляет уже вставленные
кнопки из их родительских `ViewGroup` — мгновенно, без ожидания пересоздания
активности. Ссылки на созданные `View` агент хранит в `WeakHashMap` (по
активности, чтобы не течь и не удерживать `Activity` от GC). Новый `onCreate`
проходит без хука → кнопка не добавляется снова.

### 9.2. pedestrian-warning (loop, процесс `com.qinggan.systemservice`)

**Что делает:** отключает звук предупреждения пешеходов (AVAS). Система
периодически сбрасывает состояние, поэтому нужен цикл-ретраи (как `forced-ev`),
не разовый хук. Управление бинарное: `enabled: true` → агент выключает звук,
`enabled: false` → не трогает (машина по умолчанию). Соответствует
`voboost-config` `vehicle-pedestrian-warning`: `original` → `enabled: false`,
`off` → `enabled: true`.

```java
@AgentInfo(id = "pedestrian-warning", process = "com.qinggan.systemservice", boot = false)
public final class PedestrianWarningAgent implements Agent {
    public void start(AgentContext ctx) {
        Reflect r = ctx.reflect();
        ctx.scheduler().retry(new RetryPolicy(20, 1000), () -> {
            Object mgr = r.call(r.use("com.qinggan.canbus.CanBusManager"), "getInstance");
            if (mgr == null) return Decision.RETRY;
            int res = (int) r.call(mgr, "setVehicleState", PEDESTRIAN_MODESET, SOUND_OFF);
            return res == INVALID ? Decision.RETRY : Decision.STOP;
        });
    }
    public void onConfigChanged(Config c) { /* перезапустить цикл */ }
}
```

`enabled: false` → агент не start → цикл не стартует → система работает штатно,
звук пешеходера включён. На `onConfigChanged` (toggle в `off`/`original`) цикл
перезапускается или гасится через `stop()`.

---

## 10. Live-update: пользователь меняет значение в app

1. App формирует новый конфиг, подписывает, отдаёт voboost-inject.
2. Демон верифицирует подпись и атомарно переписывает срезы в
   `shared/<process>.json` (он же `configPath` для инъецированного процесса,
   write-tmp + rename).
3. `ConfigWatcher` в процессе ловит inotify на `configPath` → парсит новый конфиг.
4. Если `enabled` сменился true↔false: `Implant` вызывает `start()` (появился)
   или `stop()` (исчез) конкретного агента. Остальные не трогаются.
   - **settings-menu**: `true` → ставит хук добавления пункта; `false` → `stop()`
     снимает хук и удаляет уже вставленные кнопки из `ViewGroup` мгновенно
     (см. §9.1).
   - **pedestrian-warning**: `true` → стартует/перезапускает цикл выключения;
     `false` → `stop` гасит цикл, система возвращается к штатному звуку.
   Для бинарных агентов без per-agent параметров live-update сводится к
   toggle `enabled` — без переинъекции.

---

## 11. Сосуществование с Frida-продуктами

На головном устройстве Voyah могут быть установлены сторонние frida-продукты
(напр. VoyahTune): системное app в `/system/priv-app`, Frida-оркестратор
(`frida-inject -p <pid> -s <script> -e`, watchdog-цикл), требуют writable
`/system` (`adb disable-verity` + `adb remount`, OverlayFS) и
`setenforce 0` (SELinux Permissive). Их функциональность пересекается с Voboost
(VirtualDisplay-сплит, кнопки руля, звук пешеходера, режимы езды) и они могут
сидеть в **тех же процессах** (`system_server`, `com.qinggan.keymanager.service`,
`com.qinggan.app.launcher`), что и наши агенты. Три точки конфликта:

- **ptrace vs frida-attach.** Оба основаны на ptrace. Linux позволяет только one
  tracer на tracee — одновременно ptrace-attach двух «инъекторов» к одному
  процессу невозможен. На практике конфликт только в момент инъекции: если
  frida-оркестратор держит процесс `PTRACE_ATTACH`-ed, наш `voboost-inject` не
  сможет прицепиться (и наоборот). В установившемся режиме (frida inject через
  `-e` возвращает управление, наш implant уже dlopen'нут) оба процесса технически
  продолжают работать — но это не гарантия совместимости (см. вывод ниже),
  а лишь отсутствие ptrace-конфликта после инъекции; повторная инъекция в
  занятый процесс падает.
- **LSPlant vs frida-gum.** Оба хукают ART-методы. Frida-gum ставит свой
  inline/trampoline hook, LSPlant — свой (через Dobby для native ART-методов).
  На одном методе они не координируются: возможны двойные trampoline, гонки
  порядка вызовов, потеря оригинала. Если frida-скрипт и наш агент хукают
  **один и тот же** метод — результат непредсказуем (зависит от порядка установки
  и реализации hook-движков). На разных методах одного процесса — работают
  независимо.
- **SELinux.** Voboost работает в `Enforcing` (коммитнутая политика
  `voboost-inject.te` даёт нужные ptrace allow-правила). Frida-продукты требуют
  `setenforce 0` (Permissive) — их boot-хук глушит SELinux глобально. Если
  frida-продукт установлен и активен, SELinux устройства — Permissive, наши
  allow-правила маскируются (в Permissive всё разрешено, но и защиты нет).
  Финальная валидация Voboost — на `Enforcing`-железе без frida-продуктов;
  сосуществование на Permissive-устройстве технически может работать, но не
  гарантируется и не поддерживается.

**Вывод:** одновременная работа в одном процессе не гарантируется. Конфликт
реален при пересечении по ptrace (момент инъекции) и по хукаемым методам.
Рекомендация — не использовать frida-продукты и Voboost в одних процессах
одновременно; Voboost рассчитывает на `Enforcing` без `setenforce 0`.

---

## 12. Упаковка DEX: один bundle-dex

**Решение: один `voboost-agents.dex`** (рантайм + все агенты + LSPlant
Java-биндинг). `Implant` грузит один dex, фильтрует классы под текущий процесс.

```
voboost-script/build/
  voboost-agents.dex      ← один файл (рантайм + все агенты)
  manifest.json           ← {version, sha256, agents:[{id, class, process, boot}]}
```

**Почему bundle, а не per-agent dex:**
- Один sha256 на bundle → простой манифест/подпись/верификация. OTA = «заменил
  dex + manifest, пере-подписал».
- Рантайм не дублируется по агентам (vs per-agent dex ×16) — один
  `AgentContext`/`Hooker`/`Scheduler`/`Reflect` на bundle.
- Shared-config и стекирование хуков на один метод — тривиальны в одном
  classloader (агенты видят общие классы и хукают один метод без
  classloader-границ).
- Карантин per-agent по id — на уровне рантайма (`agent.start()` в try/catch +
  `enabled` флаг), а не dex-уровня. Это и так нужно (per-feature изоляция
  отказов).

Манифест: `@AgentInfo` → генератор на сборке → `{version, agents:[{id, class,
process, boot}]}`, `file` → `class` (bundle-модель). Подпись key-root.

---

## 13. voboost-install (десктоп-установщик, Tauri)

Десктоп-приложение (macOS/Windows/Linux), автоматизирует установку на головное
устройство Voyah через ADB. Мастер из 6 шагов (Приветствие, Лицензия, Прошивка,
Подключение, Установка, Завершение) + batch CLI (`--install`, `--uninstall`,
`--restore`).

### 13.1. Что ставит (provision scenario)

Сценарий установки (один источник истины для GUI и CLI):
1. `mkdir /data/voboost-inject/{,payload/{arm64-v8a,armeabi-v7a},shared,run,logs}`
   (root:root, 0755) — root-зона inject-инфры, отделённая от app-данных.
2. Push **voboost-inject** (ELF) → `/data/voboost-inject/voboost-inject`, chmod 755.
3. Push **runtime payload** → `/data/voboost-inject/payload/<abi>/`:
   `libimplant.so` + `liblsplant.so` (per ABI).
4. Push **agents.dex** (bundle) → `/data/voboost-inject/payload/agents.dex`.
5. Push **manifest** + **manifest.sig** (release-manifest, подпись key-root).
6. Push **release-public.pem** → `/data/data/ru.voboost/files/config/`.
7. **init-хук**: append старт-скрипта в системный init-файл, маркер
   `# >>> voboost-inject (do not edit)`, `restorecon`. Стартует voboost-inject на boot.
8. `app-zone` → `/data/user/0/ru.voboost` (статус voboost-inject).

Uninstall: остановить voboost-inject, снять init-хук, `rm -rf /data/voboost-inject`,
uninstall APK.

### 13.2. Ключи подписи

Три ключа, изолированные по зонам ответственности. Имена отражают уровень
привилегий при компрометации (сверху — критичнее):

| Ключ         | Тип              | Подписывает                                             | Верифицирует                                                   | Защита                                                                                          |
|------------|----------------|-------------------------------------------------------|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **key-root** | ed25519          | voboost-inject ELF + agents-манифест (agents.dex)       | voboost-inject (публичник вшит в ELF как константа `KEY_ROOT`) | **самый критичный**: компрометация = исполнение произвольного кода в системных процессах машины |
| **key-ota**  | ed25519          | OTA-манифест версий (список релизов, sha256 артефактов) | app + installer (вшитый pubkey)                                | указывает «какая версия свежая»; не позволяет подменить байты (sha256 в манифесте + sig)        |
| **key-app**  | RSA/X.509 (.jks) | `voboost.apk` (Android signing)                         | Android `PackageInstaller`                                     | отдельный мир Android APK-signing                                                               |
| **key-dev**  | ed25519          | локальная dev-подпись вместо `release-public.pem`       | тот же путь, что у key-root/key-ota (dev-сборка)              | **не продуктовый**: только до CI; приватник у разработчика, на устройство не уходит            |

**Принцип изоляции:** компрометация одного ключа не даёт контроля над другими
зонами:
- key-ota скомпрометирован → нельзя подделать агентов (voboost-inject верит
  key-root);
- key-root скомпрометирован → нельзя заставить клиентов скачать атаку (они
  верят key-ota для выбора версии).

**Где хранятся приватники:**
- В коде и на устройстве — **только публичники** (`key-ota-public.pem`,
  `key-root-public.pem`, .jks pubkey). Публичник key-root вшит в voboost-inject
  как константа `KEY_ROOT` (`pub const KEY_ROOT: [u8; 32]`, генерируется из
  `key-root-public.pem` при сборке) — 32 байта ed25519 прямо в ELF.
- Приватники — только в CI-секретах GitHub; никогда не попадают в бинарник,
  APK или на устройство.
- В разработке (до CI) подпись делается dev-ключом локально (`key-dev`).

### 13.3. OTA / self-update

Initial install — через `voboost-install`. Дальше — app (voboost) делает
OTA self-update: проверяет OTA-манифест (подписан key-ota), сверяет sha256
артефактов, применяет атомарно. APK проверяется по SHA256 после download,
HTTPS only, no telemetry. voboost-inject ELF и agents.dex верифицируются
против KEY_ROOT (вшитый pubkey в ELF).

---

## 14. Сборка (voboost-script, JS → Java/DEX)

**Решение: Makefile + javac + d8** (без Gradle, ближе к нынешнему npm-стилю
voboost-script, меньше toolchain).

- Java-исходники в `src/main/java/ru/voboost/...` (рантайм + агенты).
- `@AgentInfo` → annotation processor → `AgentRegistry` (список классов).
- `javac` (boot classpath = android.jar) + `d8` (из `$ANDROID_SDK/build-tools`)
  → `build/voboost-agents.dex` (bundle, `--release`).
- Генератор манифеста (бывший `manifest.mjs`, переписанный под `@AgentInfo`) →
  `build/manifest.json` `{version, sha256, agents:[{id, class, process, boot}]}`.
  `sha256` — на bundle-dex, одно значение на верхнем уровне манифеста.
- `npm run build` остаётся точкой входа (обёрнёт javac+d8) ИЛИ новый `make build`.

### 14.1. Распределение по репозиториям

- **voboost-script**: рантайм (`Agent`/`AgentContext`/`Hooker`/`Scheduler`/
  `Reflect`/`Config`/`Log`/`Implant`/`AgentRegistry`; per-process JSON, inotify
  live-update) + агенты + build в dex.
- **voboost-inject** (implant): JNI-мост LSPlant (`nativeHook`/`nativeUnhook` +
  `RegisterNatives` в `libimplant.so`), dex-грузилка (`InMemoryDexClassLoader`),
  ptrace-инъекция, LSPlant init.
- **voboost (app)**: формирование inject-плана `agents[id].enabled/config` из
  `voboost-config` + таблицы процессов, подпись, доставка в зону app.
- **voboost-install**: постановка `voboost-agents.dex` (один файл) в
  `/data/voboost-inject/payload/agents.dex`.

---

## 15. Открытые вопросы

- **app-launcher / launcher-allapps** — A/B (старая/новая реализация), не
  включать одновременно (семантика, не техника). App гарантирует только один
  из двух `enabled: true` через таблицу процессов (§8) — оба сидят в одном
  процессе `com.qinggan.app.launcher`.
- **keyboard-ru / keyboard-lock-en** на `saveInputMode` — LSPlant стекует;
  проверить порядок before-хуков на устройстве.
- **onConfigChanged для loop/one-shot** — loop перезапускает цикл? one-shot —
  no-op? зафиксировать per-class (pedestrian-warning перезапускает цикл).
