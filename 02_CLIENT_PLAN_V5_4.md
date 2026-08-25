# UNITY CLIENT PLAN — V5.4

> V5 giữ nguyên tư tưởng V4: mọi map/dungeon/event vẫn dùng chung `MAP_INFO -> common map renderer`.
>
> Chỉ cập nhật để đồng bộ Server V7:
> - Security/build/install/ticket vẫn là hướng dài hạn.
> - Dungeon reconnect V1 đưa player về safe map.
> - BossEvent là extension, không phải client Core DoD.
> - Không cleanup network/client quá sớm.

---

# 1. Common map renderer

```text
MAP_INFO
→ Map parser/cache
→ GameScreen
→ Common Renderer
   ├── background/tile
   ├── Player
   ├── Monster/Boss
   ├── NPC
   ├── Item
   └── Waypoint
```

Không tạo renderer riêng cho:
- Dungeon.
- Arena.
- BossEvent.
- Survival.

---

# 2. Server runtime không cần lộ cho client

Server có thể có:

```text
Clan A
→ MapTemplate 50
→ MapInstance 1001

Clan B
→ MapTemplate 50
→ MapInstance 1002
```

Client A và B đều chỉ cần:

```text
mapId = 50
```

Server chịu trách nhiệm entity isolation.

Client không cần:
- `instanceId`.
- `MAP_IDS`.
- `ClanDungeon` class.
- runtime owner metadata.

---

# 3. Dungeon multi-map

Nếu server dùng:

```java
MAP_IDS = [
    GATE,
    INSIDE,
    BOSS_ROOM
]
```

client chỉ dùng normal change-map flow:

```text
MAP_INFO Gate
→ render

CHANGE_MAP / MAP_INFO Inside
→ render

CHANGE_MAP / MAP_INFO Boss Room
→ render
```

Không có multi-map dungeon engine riêng phía client.

---

# 4. Overlay UI

Chỉ thêm khi gameplay cần.

## DungeonOverlay
- wave.
- timer.
- objective.
- boss status.

## ArenaOverlay — extension
- countdown.
- opponent.
- score.
- winner.

## BossEventOverlay — extension
- event timer.
- boss status.
- notice.

Overlay không thay renderer.

---

# 5. Network core giữ ổn định

Trong giai đoạn server network compatibility, ưu tiên giữ:
- `Networks/Session.cs`.
- `Networks/ServerManager.cs`.
- `IOs/Message.cs`.
- `MyReader.cs`.
- `MyWriter.cs`.
- `Services/Service.cs`.
- `Controllers/Controller.cs`.

Không vừa:
- refactor Session;
- thêm TLS;
- xóa feature;
- đổi protocol;

trong cùng một bước.

---

# 6. Protocol compatibility

Giữ command hiện tại cho feature dùng:
- Connect `-128`.
- Version Source `-127`.
- Update Data `-125`.
- Login `-124`.
- Create Player `-122`.
- Map Info `-121`.
- Register `-118`.
- Finish Load Map `-115`.
- Move `-114`.
- Change Map `-113`.
- Player Info `-109`.
- Attack `-108`.
- Change Area `-84`.
- Pick Item `-79`.
- Buy Item `-78`.
- Skill `-72`.
- Clan `-44`.
- Quest `-23`.

---

# 7. Feature matrix

## KEEP
- Session/network.
- Login/Register/Create Player.
- PlayerInfo.
- Map/GameScreen.
- Common map renderer.
- Movement.
- Monster/Boss renderer.
- Combat/Skill.
- Inventory.
- NPC rendering/template.
- NPC chat/menu.
- Shop.
- Upgrade panel/flow.
- Quest.
- Clan.

## ADD/SIMPLIFY — Core
- TLS support.
- Admission/login API flow.
- Build/version gate.
- Installation identity.
- ConnectionTicket.
- Daily.
- GiftCode.
- Clan Dungeon controls.
- DungeonOverlay nếu thật sự cần.

## EXTENSION
- BossEvent notification/overlay.
- Arena UI.
- Personal/Party Dungeon UI.
- Dungeon rejoin UI/flow.

## DISABLE/REMOVE SAU KHI CORE ỔN
- Trade.
- Auction/Consignment.
- old Tournament nếu không reuse.
- Manor old feature.
- Clan War.
- unused seasonal events.
- Pet/Disciple phức tạp.
- Achievement phức tạp.

---

# 8. Cleanup rule

```text
disable
→ compile
→ login/map/combat regression
→ remove code/assets
```

Không xóa hàng loạt trước khi server mới đạt core compatibility.

---

# 9. NPC flow

Client đã có sẵn:
- `Npc`.
- `NpcTemplate`.
- `NpcManager`.
- `MenuNpc`.

Reuse thay vì viết mới.

Protocol hiện có:

```text
UPDATE_DATA subtype 4
→ NPC template list

MAP_INFO
→ NPC templateId + x + y

OPEN_NPC -93
CONFIRM_MENU -92
NPC_CHAT -91
ADD_NPC -48
REMOVE_NPC -47
```

Flow:

```text
Player focus NPC
→ Service.OpenMenu(npcId)
→ server sends chat/menu
→ MenuNpc
→ Service.ConfirmMenu(npcId, index)
→ server routes Shop/Quest/Upgrade/etc.
```

Không tạo NPC UI framework mới.

---

# 10. Upgrade client

Với các mechanic có layout nguyên liệu khác nhau, client **không ép tất cả vào một panel chung**.

Target:

```text
upgrade/
├── UpgradeItemPanel.cs
├── UpgradeStarPanel.cs
├── DrillSocketPanel.cs
└── UpgradeQualityPanel.cs
```

Có thể reuse component `ItemSlot`/inventory selection chung, nhưng mỗi panel giữ layout và UX riêng.

## 10.1 Các mechanic đã chốt

```text
UPGRADE_ITEM
→ nâng +1/+2/...
→ dự kiến 3 ô:
   [Item] [Đá nâng cấp] [Đá bảo vệ]

UPGRADE_STAR
→ nâng sao
→ panel riêng, số ô chốt theo material rule

DRILL_SOCKET
→ đục lỗ
→ dự kiến 2 ô:
   [Item] [Vật phẩm đục lỗ]

UPGRADE_QUALITY
→ nâng phẩm
→ dự kiến 5 ô:
   [Item] [NL1] [NL2] [NL3] [Bảo vệ/Phụ]

MECHANIC THỨ 5
→ chưa chốt
→ chưa thêm UpgradeType/panel cho tới khi gameplay được xác định
```

## 10.2 `-27` mở đúng panel

Giữ command cũ:

```text
SHOW/CONFIG UPGRADE PANEL = -27
```

Server gửi `uiType/formatType` để client chọn panel:

```text
0 → UpgradeItemPanel
1 → UpgradeStarPanel
2 → DrillSocketPanel
3 → UpgradeQualityPanel
4 → reserved; chưa dùng
```

Client:

```csharp
switch (uiType)
{
    case 0:
        upgradeItemPanel.Show();
        break;

    case 1:
        upgradeStarPanel.Show();
        break;

    case 2:
        drillSocketPanel.Show();
        break;

    case 3:
        upgradeQualityPanel.Show();
        break;
}
```

Không cần tạo command network riêng cho mỗi panel.

## 10.3 Item slots chỉ là UX

Panel client giúp player chọn đúng loại item, nhưng server vẫn validate lại.

Ví dụ `UpgradeItemPanel` có 3 ô rõ ràng, nhưng client vẫn chỉ gửi `indexUI`.

Không coi:

```text
slot 0 chắc chắn equipment
slot 1 chắc chắn upgrade stone
```

là security rule phía server.

## 10.4 `UPGRADE -37`

Mỗi panel gom các item đang chọn:

```text
count
indexUI...
```

rồi dùng chung:

```text
UPGRADE -37
```

Client không gửi:
- UpgradeType;
- cost;
- success rate;
- success/fail;
- resulting level/star/socket/quality.

Server đã biết mechanic qua `currentUpgradeType`.

## 10.5 Preview và confirm

Flow:

```text
panel riêng
→ player chọn item
→ UPGRADE -37
→ server preview
→ server gửi text/menu xác nhận
→ player chọn
→ CONFIRM_MENU -92
→ server execute
```

Giữ menu confirm chung nếu đủ dùng. Không cần tạo confirm dialog class riêng cho từng mechanic trừ khi UX sau này thực sự khác.

## 10.6 Result

Sau confirm:
- server gửi message/result;
- server gửi lại item/inventory/equipment state;
- client render state mới.

Client không tự:
- + upgrade level;
- + star;
- + socket;
- đổi quality;

trước response server.

## 10.7 Reuse thay vì duplicate

Panel riêng không có nghĩa copy toàn bộ code.

Có thể dùng component/helper chung cho:
- ItemSlot.
- kéo/thả/chọn item.
- clear slot.
- render icon/quantity.
- gửi danh sách index.

Chỉ **layout + rule hiển thị** là riêng theo mechanic.



# 11. TLS transport roadmap

Legacy development:

```text
TcpClient
→ NetworkStream
→ legacy packet framing/XOR
```

Final:

```text
TcpClient
→ SslStream
→ legacy packet framing/XOR
```

Khi thêm TLS, đổi Session để làm việc với `Stream` hoặc wrapper nhỏ.

Không rewrite packet protocol.

---

# 12. Admission flow

Client có 2 mode trong quá trình phát triển.

## LEGACY_DEV

```text
connect game TCP
→ legacy LOGIN/REGISTER payload
```

Chỉ dùng để bring-up/debug compatibility.

## SECURE_ONLINE

```text
Client start
→ load/create installation identity
→ HTTPS Register/Login
→ username/password chỉ gửi qua HTTPS/TLS
→ build version
→ installation proof
→ receive one-time ConnectionTicket
→ TLS GameServer
→ send ticket
→ accepted/authenticated Session
→ load/create Player
→ normal game protocol
```

Khi chạy `SECURE_ONLINE`, client không gửi username/password qua game TCP nữa.

Các legacy LOGIN/REGISTER command vẫn có thể tồn tại trong code để phục vụ local compatibility mode.

---

# 13. Installation identity

Không dùng một global client secret làm bằng chứng chính.

Client lưu:
- installationId.
- server-issued credential/proof nếu triển khai.

Server hỗ trợ:
- revoke installation.
- wrong installation rejection.

---

# 14. ConnectionTicket

Client:
- giữ ticket trong memory.
- ticket ngắn hạn.
- one-time.
- không lưu dài hạn.
- reconnect → xin ticket mới.

Backend ticket là chi tiết server; client không quan tâm RAM/DB/Redis.

---

# 15. Certificate validation

Final client:
- reject untrusted.
- reject wrong hostname.
- reject expired.

Không để callback accept-all.

---

# 16. Server authoritative

Client chỉ gửi intention.

Không quyết định:
- damage.
- cooldown authority.
- reward.
- shop price.
- quest completion.
- dungeon completion/reward.

---

# 17. Reconnect policy

## V1

Normal map:

```text
disconnect
→ login lại
→ server restore normal map/position
```

Dungeon/Event:

```text
disconnect
→ login lại
→ server đưa về safe map
```

Client không cần UI rejoin.

## V2 extension

Sau này server có thể trả:
- active dungeon info.
- Rejoin button.
- remaining timer.

Không phải Core V1.

---

# 18. Boss rendering

5 Boss server có mechanics khác nhau nhưng client dùng generic rendering:
- template.
- name.
- HP.
- animations/effects.
- summon entities.
- phase notice.

Không tạo 5 networking implementations riêng.

---

# 19. Client test gates

## C1 Legacy compatibility
- TCP connect.
- key/XOR.
- version.
- update-data bootstrap.
- Login screen.

## C2 Auth
- Register.
- Login.
- Create Player.

## C3 Map
- PlayerInfo.
- normal MAP_INFO.
- same template reused server-side.
- movement/change area/map.

## C4 TLS
- valid cert.
- invalid cert.
- wrong hostname.

## C5 Admission
- valid ticket.
- expired ticket.
- replay ticket.
- old build.
- revoked installation.

## C6 Gameplay
- Monster.
- Attack.
- Skill.
- Item.
- NPC menu/chat.
- Shop.
- Upgrade.
- Quest.
- 5 Boss.
- Daily.
- GiftCode.
- Clan.

## C7 Clan Dungeon
- Open/Join.
- normal MAP_INFO.
- multi-map change nếu dùng MAP_IDS.
- optional DungeonOverlay.
- disconnect → login → safe map.

## C8 Cleanup
- no dead navigation.
- no missing references.
- stable final build.

---

# 20. Client Core Definition of Done

- [ ] Existing protocol compatibility.
- [ ] Common map renderer.
- [ ] No mode-specific map renderer.
- [ ] Register/Login/Create Player.
- [ ] TLS/admission.
- [ ] Build/install/ticket.
- [ ] Core gameplay UI.
- [ ] NPC template/render/menu/chat.
- [ ] Separate UpgradeItem/Star/Socket/Quality panels where layouts differ.
- [ ] `-27` selects panel by uiType/formatType.
- [ ] Item selection sends only `UPGRADE -37` indexes.
- [ ] Confirmation uses existing `CONFIRM_MENU -92`.
- [ ] Client never calculates authoritative cost/rate/result.
- [ ] Fifth upgrade panel only after mechanic is defined.
- [ ] Unsupported old upgrade modes hidden/disabled.
- [ ] Clan Dungeon via normal MAP_INFO.
- [ ] Multi-map switching works if used.
- [ ] Special-map reconnect V1 safe-map behavior accepted.
- [ ] Required old feature cleanup.
- [ ] Stable final build.

---

# 21. Client Extension

Không chặn Core:
- BossEventOverlay.
- Arena UI.
- Dungeon rejoin.
- Party/Personal Dungeon-specific menus.
