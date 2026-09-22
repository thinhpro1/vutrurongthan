# JSON bootstrap resources

Java server mặc định nạp static bootstrap data từ `resources/json`. `GameResourcesLoader` gọi loader tương ứng cho từng file đang được sử dụng:

| File | Java loader |
| --- | --- |
| `EffectBootstrap.json` | `EffectLoader` |
| `Frame.json` | `FrameLoader` |
| `LevelBootstrap.json` | `LevelLoader` |
| `MonsterBootstrap.json` | `MonsterLoader` |
| `MonsterCombatBootstrap.json` | `MonsterCombatLoader` |
| `PlayerSkillBootstrap.json` | `SkillLoader` |

Các loader parse và validate dữ liệu trước khi đưa vào `GameResources`.

Map identity, metadata, and waypoint topology are loaded from the database;
canonical terrain data is loaded from `resources/maps/{data}.json` before the
remaining JSON resource families are composed into `GameResources`.

Thư mục hiện cũng có `Dart.json`, `MonsterDartTemplate.json`, `SkillEffect.json` và `SkillPaint.json`. Các file này hiện không được Java runtime loaders sử dụng.
