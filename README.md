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
- Дальше: растительность, измерение и ландшафт, структуры, Тёмный замок.

Биомы пока не размещены в мире: это произойдёт вместе с измерением на следующем этапе. Текстуры блоков и предметов временные.
