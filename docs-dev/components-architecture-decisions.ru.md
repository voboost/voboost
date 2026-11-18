# Архитектурные решения компонентов

[English](components-architecture-decisions.md) | **Русский**

Эти принятые решения объясняют архитектуру компонентов. Текущие контракты находятся в [OpenSpec компонентов](../../voboost-components/openspec/specs); исходники определяют текущие API. Публичные точки входа описаны в [руководстве компонентов](components.ru.md).

## Самостоятельные компоненты

Сохраняйте одну Java-реализацию каждого компонента и явные значения темы и языка. Общие интерфейсы, неизменяемые значения и stateless utilities допустимы; общий implementation base и singleton темы/языка — нет. Компоненты уже используют разные Android-базы: `Panel`/`Button` — `LinearLayout`, `Section`/`Screen` — `ViewGroup`, `Tabs` — `View`. Распространение состояния тоже различается: контейнеры обходят прикреплённых детей, а Screen владеет типизированными panels, tabs и toast с lifecycle guards.

Общий base меняет эти контракты; интерфейс перечисления Android children связывает leaf-контракты с обходом контейнеров. Небольшое дублирование сохраняет самостоятельность компонентов, одновременные темы и отсутствие глобального observer/lifecycle state. Рассматривайте небольшую utility только при нескольких контейнерах с одинаковой доказанной семантикой; не расширяйте интерфейсы ради сокрытия разных моделей владения.

## Явные builders

Сохраняйте собственные Java builders, включая margin setters и null checks, вместо общего `BaseBuilder`. Рекурсивный generic base или delegate state усложняет типы и API, а индивидуальные setters всё равно нужны. Текущее дублирование оставляет fluent return types и публичный контракт каждого builder видимыми. `Buttons` использует конфигурацию каждой кнопки; другие container builders не разделяют margin API. Пересматривайте решение, когда реальное общее поведение оправдает это.

## Отдельная Compose-интеграция

Сохраняйте View extensions без Compose в `Panel.kt` и `Section.kt`, factories — в `ComponentFactory.kt`, а `AndroidView` wrappers и Compose-only helpers — в `Component.compose.kt`. Имена файлов показывают границу зависимостей. Kotlin разрешает top-level calls по package/name, поэтому перенос между этими файлами сам по себе не меняет API; импорты и потребители всё равно требуют проверки. Общие данные и View extensions не должны получать Compose runtime dependency.

## Три публичных слоя над одним ядром

Java-потребители используют constructors или `Component.create(...).build()`. Kotlin-потребители используют receiver-lambda factories/extensions над тем же Java builder. Compose-потребители используют `@Composable` wrappers, делегирующие той же View. Три поверхности сохраняют одно поведение, конфигурацию и listener contract; адаптеры не владеют параллельной машиной состояний.

Библиотека компилирует wrappers с Compose dependencies `compileOnly`; Compose-приложение предоставляет собственный runtime. [Java demo](components-demo-java.ru.md), [Kotlin demo](components-demo-kotlin.ru.md) и [Compose demo](components-demo-compose.ru.md) показывают эти границы. Фоновое продуктовое приложение находится в отдельном репозитории и не является потребителем component demo.
