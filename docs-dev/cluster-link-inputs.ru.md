# QNX link inputs

[English](cluster-link-inputs.md) | **Русский**

QNX product builder использует проверенный [`qnx-link-interface.json`](../../voboost-inject/cluster/tools/qnx-link-interface.json). Inventory содержит strong undefined symbols, упорядоченные `DT_NEEDED`, provider и symbol-type metadata для `voboost-cluster-daemon` и `libvoboost-cluster-inject.so`, а также динамические TLS lookups. Текущие Java-реализации [`QnxLinkInterfaces`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxLinkInterfaces.java) и [`QnxProductBuild`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxProductBuild.java) вызываются через существующую Gradle-сборку агентов.

## Входы и проверки сборки

Режим по умолчанию, `audited-metadata`, создаёт interface libraries из inventory; исходные байты QNX-библиотек в CI не нужны. Эти временные входы линкера не входят в продукт и не заменяют исполняемые компоненты при тестировании. Builder удаляет собственный рабочий каталог. Во время выполнения используются оригинальные библиотеки прошивки.

Implant экспортирует только точные OEM-перехваты и именованные SDK-входы из [`QnxProductExports`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxProductExports.java). Генерируемая linker version script делает остальные символы локальными, включая внутренние имена Rust и вход daemon. Сборка проверяет полный набор defined dynamic exports, тип функции, global binding и default visibility. После этого section garbage collection может убрать серверный код из implant.

Implant сохраняет Rust unwind tables по умолчанию QNX target и существующую обработку C++ исключений. Для standalone daemon Rust archive собирается повторно с `-C force-unwind-tables=no`: его launcher написан на C, а panic/allocation handlers в `no_std` Rust вызывают abort. Builder линкует implant до пересборки archive для daemon и использует тот же Cargo target directory. `build-evidence.json` записывает отдельные unwind settings и точные implant exports. C++ exception support во внедряемом коде сохраняется.

Inventory описывает `libc.so.4`, `libsocket.so.3`, `libssl1_1.so.2.1`, `libcrypto1_1.so.2.1` и проверенный GNU interface `libstdc++.so.6`. Неизвестный import приводит к ошибке линковки. Проверка отклоняет изменения strong imports, новые weak imports, COPY relocations, изменения порядка зависимостей, provider/type и TLS lookups. Product imports проверяются по полной LLVM dynamic symbol table; одного GNU hash недостаточно для перечисления undefined symbols. Это проверка контракта линковки, а не C signatures или выполнения на QNX; ABI declarations остаются в исходниках.

Для локального `verified-originals` нужны все четыре пути: `--qnx-libc`, `--qnx-socket`, `--qnx-ssl`, `--qnx-crypto`. Builder проверяет существующие записанные identity входов, SONAME и metadata допущенных символов, затем создаёт те же interfaces, что и metadata mode. Default symbol-version aliases оригиналов сохраняются как provenance, входы — как unversioned recipe. Fallback для оригинала без section headers читает ограниченные ELF program-header/GNU-hash metadata после проверки записанной identity. GNU-запись переносится отдельно без локального original-byte input. Это проверки происхождения входов сборки, а не правила допуска прошивок.

## Команды

Команды выполняются из `voboost-agents` с NDK `29.0.14206865` и штатными Gradle/Java prerequisites. Builder вызывает Rust `1.98.0` для `aarch64-unknown-nto-qnx700` с пересборкой `core` и `alloc`. Задайте `VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM`, `VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM`, `VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM` и `VOBOOST_RESTORE_PUBLIC_KEY_PEM`: каждый путь указывает на явный отдельный публичный корень соответствующей роли.

```sh
./gradlew verifyQnxLinkInterfaces --offline --no-daemon --console=plain
./gradlew runQnxProductBuild --offline --no-daemon --console=plain \
  '-PqnxProductArguments=["--product","--inject","/absolute/path/to/voboost-inject","--ndk","/absolute/path/to/ndk/29.0.14206865","--output","/absolute/path/to/voboost-inject/build/cluster/product"]'
```

Замените примеры путей. Для проверки оригиналов добавьте все четыре пары flag/path внутрь JSON-массива. [Сборщик product closure](../tools/release/build-closure.ts) вызывает ту же задачу и сохраняет `build-evidence.json`. Он различает `audited-metadata` и `verified-originals`, записывает `qnxExecuted=false`, `runtimeExecuted=false` и оставляет `native-startup-tls-ui`, `native-removal-final-absence` непроверенными. Host-сборка не доказывает приёмку на устройстве или в автомобиле.

`verifyQnxLinkInterfaces` запускает Java-регрессии inventory/import и load layout. Layout-тесты используют LLVM fixture при наличии NDK tools, иначе — синтетическую fixture; одно число тестов не показывает выполненный вариант.

## Изменение inventory

Проверьте новый import по исходным provider/type metadata перед добавлением в локальный candidate. Соберите свежие unsigned products, изучите import tables и проверьте обновлённый candidate перед заменой файла. Обычная сборка не обходит итоговую проверку imports.

[`QnxLinkAudit`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxLinkAudit.java) реализует re-audit с `--inject`, `--llvm`, `--products`, `--evidence`, `--output`. Evidence сохраняет все четыре пути проверенных оригиналов; одного inventory недостаточно. Audit переносит GNU-запись и отказывается перезаписывать output. Отдельная Gradle-задача audit не зарегистрирована; прежние Python-команды audit/test устарели.

[Third-party terms](../../voboost-inject/THIRD_PARTY.md) сохраняют права OEM и upstream. MIT не предоставляет права распространять оригинальные OEM-библиотеки. См. [native-зависимости](native-dependencies.ru.md), [native runtime](native.ru.md) и [проверки](testing.ru.md).
