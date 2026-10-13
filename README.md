# Cursed Lands (Проклятые Земли)

Fabric-мод для Minecraft Java Edition 1.21.11: готический проклятый биом с мёртвым лесом, некрополем, каньонами с Рекой Крови и Тёмным замком.

## Стек

- Minecraft 1.21.11
- Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11
- Loom 1.17-SNAPSHOT, Gradle 9.5.1
- Java 21, официальные маппинги Mojang

## Сборка

```
gradle build
```

Готовый jar появится в `build/libs/`. Для запуска клиента: `gradle runClient`.

Gradle wrapper пока не добавлен. Чтобы создать его, один раз выполните `gradle wrapper --gradle-version 9.5.1` и закоммитьте результат.

## Статус

- Этап 1: каркас проекта (готов).
- Этап 2: биомы `cursed_lands`, `dead_forest`, `necropolis`, `blood_canyon` (JSON датапака), блоки `blood_moss_block`, `black_wood_log`, `darkness_crystal_block`, предметы `ash`, `darkness_crystal`, `ruins_heart`, творческая вкладка (готов, ждёт проверки сборки).
- Этап 3: мёртвые деревья, пятна кровавого мха, кристаллы тьмы, измерение `cursed_lands:cursed_lands` с картой четырёх биомов (готов, ждёт проверки в игре).
- Этап 4: каньоны в горных районах измерения (низкая эрозия), заполненные до уровня моря красноватой водой биома `blood_canyon` (готов, ждёт проверки в игре).
- Дальше: структуры (некрополь, руины, мосты), Тёмный замок, парящие острова.

Текстуры блоков и предметов временные.

## Настройка каньонов

Рельеф задан файлами в `src/main/resources/data/cursed_lands/worldgen/`:

| Файл | Что меняет |
| --- | --- |
| `noise/canyon.json` | форма и масштаб линий каньонов (`firstOctave` меньше по модулю = мельче) |
| `density_function/canyon_line.json` | ширина каньона: число `-24.0` (больше по модулю = уже) |
| `density_function/canyon_gate.json` | где каньоны разрешены: значения `-5.0` и `0.1` задают порог по эрозии |
| `density_function/canyon_cut.json` | глубина и крутизна: `-8.0` (сила) и `y_clamped_gradient` (`from_y: 44` = дно) |
| `noise_settings/cursed_lands.json` | копия ванильных настроек, изменено только `noise_router.final_density` |

Уровень моря 63, поэтому дно каньона на высоте 44 заливается водой на 19 блоков.

## Как попасть в измерение

Команда в игре (нужны права оператора):

```
/execute in cursed_lands:cursed_lands run spreadplayers 0 0 0 100 false @s
```
