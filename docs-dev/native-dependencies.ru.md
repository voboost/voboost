# Native-зависимости

[English](native-dependencies.md) | **Русский**

Команды выполняются из `voboost-inject` с NDK r29, CMake и Ninja. Android native-код собирается для API 28; LSPlant и implant используют `c++_static`. Перед проверками контракта исходников задайте временный каталог внутри проекта:

```sh
mkdir -p build/tmp
export TMPDIR="$PWD/build/tmp"
```

## Dobby и LSPlant

Родительский репозиторий закрепляет Dobby `5dfc8546954ce3b3198132ab13fddb89ee92cdd7` и LSPlant `a96c7978a9fe578e0e265cc8e45cb117fa8250a3` через [`.gitmodules`](../../voboost-inject/.gitmodules) и gitlinks. Точные патчи проекта добавляют изменения для NDK r29. [Подготовка](../../voboost-inject/tools/prepare-native-third-party-source.ts) проверяет checkout, патчи и состояние исходников, затем создаёт входы сборки с Git identity в `build/third-party-source/`, не меняя live checkout. Неожиданные tracked changes и untracked source files отклоняются.

| Результат | `arm64-v8a` | `armeabi-v7a` | Потребитель |
| --- | --- | --- | --- |
| `prebuilt/<abi>/libdobby.a` | сборка из закреплённых исходников | сохранённое prebuilt-исключение | статически включается в `libimplant.so` |
| `prebuilt/<abi>/liblsplant_static.a` | сборка из закреплённых исходников | сборка из закреплённых исходников | статически включается в `libimplant.so` |
| `prebuilt/<abi>/libdex_builder_static.a` | собирается с LSPlant | собирается с LSPlant | статически включается в `libimplant.so` |

Implant вызывает LSPlant напрямую после подключения к ART; отдельная shared library LSPlant не загружается. Каждый ABI содержит одну копию статического C++ runtime. Исключения C++ и RTTI остаются включёнными. Результаты сборки исключены из Git, кроме описанного armv7-архива Dobby и записи его происхождения.

```sh
make test-third-party-source-contract
make build-native
make check-prebuilt
```

`make build-native` проверяет контракт исходников перед компиляцией сторонних библиотек, затем собирает injector и implant. `make check-prebuilt` проверяет shared objects обоих ABI: ET_DYN, ожидаемые machine и SONAME, читаемую dynamic section и точный набор `DT_NEEDED`. `libc++_shared.so` не допускается: implant и LSPlant используют статический C++ runtime. Это host-проверки сборки, а не доказательство внедрения в ART или работы в автомобиле.

## Сохранённый armv7-архив Dobby

Закреплённая ревизия Dobby сохраняет upstream-ошибку ARM-сборки, поэтому `prebuilt/armeabi-v7a/libdobby.a` остаётся единственным сохранённым native prebuilt-исключением. Это точный файл `prefab/modules/dobby/libs/android.armeabi-v7a/libdobby.a` из Maven Central [`io.github.vvb2060.ndk:dobby:1.2`](https://repo.maven.apache.org/maven2/io/github/vvb2060/ndk/dobby/1.2/dobby-1.2.aar). SHA-256 AAR — `251f48ae21686d7f69276c50644ca345f450e45110057437f7d76bb14cddf3a1`; опубликованные metadata — API 16, NDK 22 и STL `none`. [POM](https://repo.maven.apache.org/maven2/io/github/vvb2060/ndk/dobby/1.2/dobby-1.2.pom) указывает Apache-2.0 и [upstream license](https://github.com/LSPosed/Dobby/blob/master/LICENSE). Эти metadata не заменяют требование NDK r29 для текущего проекта.

Исходный commit, из которого собран архив, неизвестен. Репозиторий не заявляет, что его байты воспроизводятся из закреплённой ревизии Dobby или другой известной ревизии. [Запись происхождения](../../voboost-inject/prebuilt/armeabi-v7a/libdobby.provenance.json) фиксирует опубликованный источник и identity архива: SHA-256 `fbaeb9cfe18f6953ad4d24cad34ed4fdd04964fcea0f74ce09285e5dbd11e2fd`, Git blob `e9f6677dcbb3a859f742bbf6a4e681eb536c3e67`.

```sh
make test-armv7-dobby-prebuilt
```

Для замены нужно проверяемое происхождение исходников; эквивалентного бинарника или переименованного архива недостаточно.

## libsepol

`third_party/libsepol` закреплён на Android commit `c4882b81f186bc131f79ad0b28808bbeef6fc678` из `https://android.googlesource.com/platform/external/libsepol`. [Патч проекта](../../voboost-inject/patches/libsepol/0001-android-xperms-layout.patch), SHA-256 `d12b749a57b7eee47eae4b7b52aaac986ae516145bf161101dca0ae1370d04d9`, добавляет итоговый Android ioctl xperms layout, разделяет kind и driver при expansion и содержит регрессию expander.

```sh
node tools/prepare-libsepol-source.ts --apply
make test-third-party-source-contract
```

[Подготовка](../../voboost-inject/tools/prepare-libsepol-source.ts) принимает чистый закреплённый checkout, если точный патч применяется, либо байт-в-байт пропатченный checkout, если тот же патч корректно обращается. Другие tracked changes и untracked source files отклоняются. Неотслеживаемые `.o`, `.a` и `.d` считаются остатками компиляции и не доказывают состояние исходников или результат тестов. Полный third-party gate также проверяет `.gitmodules`, gitlinks и применение патча в одноразовом чистом checkout.

[Third-party terms](../../voboost-inject/THIRD_PARTY.md), исходные лицензии, патчи и provenance остаются рядом с владельцами исходников. Identity зависимостей описывают происхождение входов сборки, а не выбор совместимой прошивки. См. [native runtime](native.ru.md) и [QNX link inputs](cluster-link-inputs.ru.md).
