# JSON bootstrap resources

Java server mặc định nạp static bootstrap data từ `resources/json`. `GameResourcesLoader` gọi loader tương ứng cho từng file đang được sử dụng:

| File | Java loader |
| --- | --- |
| `EffectBootstrap.json` | `EffectLoader` |
| `Frame.json` | `FrameLoader` |
| `LevelBootstrap.json` | `LevelLoader` |
| `MapBootstrap.json` | `MapLoader` |
| `MonsterBootstrap.json` | `MonsterLoader` |
| `MonsterCombatBootstrap.json` | `MonsterCombatLoader` |
| `PlayerSkillBootstrap.json` | `SkillLoader` |

Các loader parse và validate dữ liệu trước khi đưa vào `GameResources`.

Thư mục hiện cũng có `Dart.json`, `MonsterDartTemplate.json`, `SkillEffect.json` và `SkillPaint.json`. Các file này hiện không được Java runtime loaders sử dụng.
