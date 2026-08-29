using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.ItemMaps;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Npcs;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Frames;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using Assets.Scripts.Skills;
using Assets.Scripts.Sounds;
using Assets.Scripts.Tasks;
using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Assets.Scripts.Controllers
{
    public class Controller
    {
        public static bool isStopReadMessage;

        public ScreenManager screenManager;

        public Controller(ScreenManager screenManager)
        {
            this.screenManager = screenManager;
        }

        public static int serverVersion;

        public void OnMessage(Message message)
        {
            try
            {
                switch (message.id)
                {
                    case MessageName.VERSION_SOURCE:
                        {
                            // connect ok
                            InfoDlg.Hide();
                            serverVersion = int.Parse(message.ReadUTF().Replace(".", ""));
                            if (serverVersion <= int.Parse(ServerManager.VERSION.Replace(".", "")))
                            {
                                Service.instance.UpdateData(-1);
                                ScreenManager.instance.loginScreen.SwitchToMe();
                            }
                            else
                            {
                                if (Main.isPC)
                                {
                                    GameCanvas.StartOKDlgOpenUrl(PlayerText.HAVE_NEW_VERSION);
                                }
                                else
                                {
                                    GameCanvas.StartOKDlgOpenUrl("Vui lòng cập nhật phiên bản mới tại CHplay, App Store, Testflight hoặc tải phiên bản mới tại " + ServerManager.LINKWEB);
                                }
                            }
                            break;
                        }
                    case MessageName.DIALOG_OK:
                        {
                            // dialog ok
                            InfoDlg.Hide();
                            string text = message.ReadUTF();
                            GameCanvas.StartDialogOk(text);
                            Player.isLoadingMap = false;
                            break;
                        }
                    case MessageName.UPDATE_DATA:
                        {
                            int type = message.ReadSByte();
                            if (type == -1)
                            {
                                GraphicManager.instance.versionImage = message.ReadSByte();
                                ServerManager.instance.isUpdateCompleted[0] = ItemManager.instance.versionItemTemplate == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[0])
                                {
                                    Service.instance.UpdateData(0);
                                }
                                ServerManager.instance.isUpdateCompleted[1] = ItemManager.instance.versionItemOptionTemplate == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[1])
                                {
                                    Service.instance.UpdateData(1);
                                }
                                ServerManager.instance.isUpdateCompleted[2] = NpcManager.instance.versionNpcTemplate == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[2])
                                {
                                    Service.instance.UpdateData(2);
                                }
                                ServerManager.instance.isUpdateCompleted[3] = EffectManager.instance.versionEffect == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[3])
                                {
                                    Service.instance.UpdateData(3);
                                }
                                ServerManager.instance.isUpdateCompleted[4] = MonsterManager.instance.versionMonster == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[4])
                                {
                                    Service.instance.UpdateData(4);
                                }
                                ServerManager.instance.isUpdateCompleted[5] = FrameManager.instance.versionMedal == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[5])
                                {
                                    Service.instance.UpdateData(5);
                                }
                                ServerManager.instance.isUpdateCompleted[6] = GameCanvas.versionLevel == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[6])
                                {
                                    Service.instance.UpdateData(6);
                                }
                                ServerManager.instance.isUpdateCompleted[7] = FrameManager.instance.versionFrame == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[7])
                                {
                                    Service.instance.UpdateData(7);
                                }
                                ServerManager.instance.isUpdateCompleted[8] = FrameManager.instance.versionMount == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[8])
                                {
                                    Service.instance.UpdateData(8);
                                }
                                ServerManager.instance.isUpdateCompleted[9] = FrameManager.instance.versionBag == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[9])
                                {
                                    Service.instance.UpdateData(9);
                                }
                                ServerManager.instance.isUpdateCompleted[10] = SkillManager.instance.versionSkillPaint == message.ReadSByte();
                                if (!ServerManager.instance.isUpdateCompleted[10])
                                {
                                    Service.instance.UpdateData(10);
                                }
                                if (serverVersion >= 91)
                                {
                                    ServerManager.instance.isUpdateCompleted[11] = FrameManager.instance.versionAura == message.ReadSByte();
                                    if (!ServerManager.instance.isUpdateCompleted[11])
                                    {
                                        Service.instance.UpdateData(11);
                                    }
                                }
                                else
                                {
                                    ServerManager.instance.isUpdateCompleted[11] = true;
                                }
                            }
                            else if (type == 0)
                            {
                                ItemManager.instance.versionItemTemplate = message.ReadSByte();
                                ItemManager.instance.itemTemplates.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    ItemTemplate template = new ItemTemplate();
                                    template.id = message.ReadShort();
                                    template.name = message.ReadUTF();
                                    template.description = message.ReadUTF();
                                    template.gender = message.ReadSByte();
                                    template.type = message.ReadSByte();
                                    template.iconId = message.ReadShort();
                                    template.isUp = message.ReadBool();
                                    template.levelRequire = message.ReadShort();
                                    template.isMaster = message.ReadBool();
                                    template.isDisciple = message.ReadBool();
                                    template.isPet = message.ReadBool();
                                    ItemManager.instance.itemTemplates.Add(template.id, template);
                                }
                                ItemManager.instance.SaveItemTemplate();
                            }
                            else if (type == 1)
                            {
                                ItemManager.instance.versionItemOptionTemplate = message.ReadSByte();
                                ItemManager.instance.itemOptionTemplates.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    ItemOptionTemplate template = new ItemOptionTemplate();
                                    template.id = message.ReadShort();
                                    template.name = message.ReadUTF();
                                    template.type = message.ReadSByte();
                                    ItemManager.instance.itemOptionTemplates.Add(template.id, template);
                                }
                                ItemManager.instance.SaveItemOptionTemplate();
                            }
                            else if (type == 2)
                            {
                                NpcManager.instance.versionNpcTemplate = message.ReadSByte();
                                NpcManager.instance.npcTemplates.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    NpcTemplate template = new NpcTemplate();
                                    template.id = message.ReadSByte();
                                    template.name = message.ReadUTF();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.icons.Add(message.ReadShort());
                                    }
                                    template.avatar = message.ReadShort();
                                    template.dx = message.ReadSByte();
                                    template.dy = message.ReadSByte();
                                    template.w = message.ReadShort();
                                    template.h = message.ReadShort();
                                    NpcManager.instance.npcTemplates.Add(template.id, template);
                                }
                                NpcManager.instance.SaveNpcTemplate();
                            }
                            else if (type == 3)
                            {
                                EffectManager.instance.versionEffect = message.ReadSByte();
                                EffectManager.instance.effectImages.Clear();
                                EffectManager.instance.effectTemplates.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    EffectImage effect = new EffectImage();
                                    effect.id = message.ReadShort();
                                    effect.dx = message.ReadShort();
                                    effect.dy = message.ReadShort();
                                    effect.delay = message.ReadShort();
                                    effect.icons = new List<int>();
                                    int count_icon = message.ReadSByte();
                                    for (int j = 0; j < count_icon; j++)
                                    {
                                        effect.icons.Add(message.ReadShort());
                                    }
                                    EffectManager.instance.effectImages.Add(effect.id, effect);
                                }
                                count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    EffectTimeTemplate template = new EffectTimeTemplate();
                                    template.id = message.ReadShort();
                                    int effectImageId = message.ReadShort();
                                    if (effectImageId != -1)
                                    {
                                        template.effectImage = EffectManager.instance.effectImages[effectImageId];
                                    }
                                    template.iconId = message.ReadShort();
                                    template.isClearWhenDie = message.ReadBool();
                                    template.isStun = message.ReadBool();
                                    EffectManager.instance.effectTemplates.Add(template.id, template);
                                }
                                EffectManager.instance.SaveEffect();
                            }
                            else if (type == 4)
                            {
                                MonsterManager.instance.versionMonster = message.ReadSByte();
                                MonsterManager.instance.monsterTemplates.Clear();
                                MonsterManager.instance.monsterDartTemplates.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    MonsterDartTemplate template = new MonsterDartTemplate();
                                    template.id = message.ReadShort();
                                    template.isMeteorite = message.ReadBool();
                                    template.light = new DartTemplateInfo();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.light.icon.Add(message.ReadShort());
                                    }
                                    template.light.dx = message.ReadShort();
                                    template.light.dy = message.ReadShort();
                                    template.light.delay = message.ReadShort();
                                    template.bullet = new DartTemplateInfo();
                                    count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.bullet.icon.Add(message.ReadShort());
                                    }
                                    template.bullet.dx = message.ReadShort();
                                    template.bullet.dy = message.ReadShort();
                                    template.bullet.delay = message.ReadShort();
                                    template.explode = new DartTemplateInfo();
                                    count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.explode.icon.Add(message.ReadShort());
                                    }
                                    template.explode.dx = message.ReadShort();
                                    template.explode.dy = message.ReadShort();
                                    template.explode.delay = message.ReadShort();
                                    MonsterManager.instance.monsterDartTemplates.Add(template.id, template);
                                }
                                count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    MonsterTemplate template = new MonsterTemplate();
                                    template.id = message.ReadShort();
                                    template.name = message.ReadUTF();
                                    template.rangeMove = message.ReadShort();
                                    template.speed = message.ReadSByte();
                                    template.type = message.ReadSByte();
                                    template.dart = MonsterManager.instance.monsterDartTemplates[message.ReadSByte()];
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.iconsMove.Add(message.ReadShort());
                                    }
                                    template.iconInjure = message.ReadShort();
                                    template.iconAttack = message.ReadShort();
                                    template.w = message.ReadShort();
                                    template.h = message.ReadShort();
                                    template.dx = message.ReadSByte();
                                    template.dy = message.ReadSByte();
                                    MonsterManager.instance.monsterTemplates.Add(template.id, template);
                                }
                                MonsterManager.instance.SaveMonsterTemplate();
                            }
                            else if (type == 5)
                            {
                                FrameManager.instance.versionMedal = message.ReadSByte();
                                FrameManager.instance.medals.Clear();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    Medal medal = new Medal();
                                    medal.id = message.ReadSByte();
                                    medal.dx = message.ReadShort();
                                    medal.dy = message.ReadShort();
                                    medal.delay = message.ReadShort();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        medal.icons.Add(message.ReadShort());
                                    }
                                    FrameManager.instance.medals.Add(medal.id, medal);
                                }
                                FrameManager.instance.SaveMedal();
                            }
                            else if (type == 6)
                            {
                                GameCanvas.versionLevel = message.ReadSByte();
                                GameCanvas.levels.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    Level level = new Level();
                                    level.id = message.ReadShort();
                                    level.name = message.ReadUTF();
                                    level.power = message.ReadLong();
                                    GameCanvas.levels.Add(level.id, level);
                                }
                                GameCanvas.SaveLevel();
                            }
                            else if (type == 7)
                            {
                                FrameManager.instance.versionFrame = message.ReadSByte();
                                FrameManager.instance.frames.Clear();
                                int count = message.ReadShort();
                                for (int i = 0; i < count; i++)
                                {
                                    FrameTemplate template = new FrameTemplate();
                                    template.id = message.ReadShort();
                                    template.hpBar = message.ReadShort();
                                    template.chat = message.ReadShort();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.dead.Add(message.ReadShort());
                                    }
                                    count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.stand.Add(message.ReadShort());
                                    }
                                    count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.run.Add(message.ReadShort());
                                    }
                                    template.fly = message.ReadShort();
                                    template.jump = message.ReadShort();
                                    template.fall = message.ReadShort();
                                    template.injure = message.ReadShort();
                                    count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        template.action.Add(message.ReadSByte(), message.ReadShort());
                                    }
                                    template.dx = message.ReadShort();
                                    template.dy = message.ReadShort();
                                    template.width = message.ReadShort();
                                    template.height = message.ReadShort();
                                    FrameManager.instance.frames.Add(template.id, template);
                                }
                                FrameManager.instance.SaveFrame();
                            }
                            else if (type == 8)
                            {
                                FrameManager.instance.versionMount = message.ReadSByte();
                                FrameManager.instance.mounts.Clear();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    MountTemplate mount = new MountTemplate();
                                    mount.id = message.ReadSByte();
                                    mount.dx = message.ReadShort();
                                    mount.dy = message.ReadShort();
                                    mount.delay = message.ReadShort();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        mount.icons.Add(message.ReadShort());
                                    }
                                    mount.layer = message.ReadSByte();
                                    FrameManager.instance.mounts.Add(mount.id, mount);
                                }
                                FrameManager.instance.SaveMount();
                            }
                            else if (type == 9)
                            {
                                FrameManager.instance.versionBag = message.ReadSByte();
                                FrameManager.instance.bags.Clear();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    Bag bag = new Bag();
                                    bag.id = message.ReadSByte();
                                    bag.dxFly = message.ReadShort();
                                    bag.dyFly = message.ReadShort();
                                    bag.delay = message.ReadShort();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        bag.icons.Add(message.ReadShort());
                                    }
                                    FrameManager.instance.bags.Add(bag.id, bag);
                                }
                                for (int i = 0; i < count; i++)
                                {
                                    FrameManager.instance.bags.ElementAt(i).Value.isFly = message.ReadBool();
                                }
                                FrameManager.instance.SaveBag();
                            }
                            else if (type == 10)
                            {
                                MyReader reader = message.reader();
                                SkillManager.instance.versionSkillPaint = reader.ReadSbyte();
                                if (!ServerManager.instance.isLocal)
                                {
                                    SkillManager.instance.darts.Clear();
                                    SkillManager.instance.effects.Clear();
                                    SkillManager.instance.paints.Clear();
                                    int count = reader.ReadShort();
                                    for (int i = 0; i < count; i++)
                                    {
                                        DartTemplate template = new DartTemplate();
                                        template.id = reader.ReadShort();
                                        template.isTarget = reader.ReadBool();
                                        template.isLine = reader.ReadBool();
                                        template.bullet = new DartTemplateInfo();
                                        int count_img = reader.ReadSbyte();
                                        for (int j = 0; j < count_img; j++)
                                        {
                                            template.bullet.icon.Add(reader.ReadShort());
                                        }
                                        template.bullet.dx = reader.ReadShort();
                                        template.bullet.dy = reader.ReadShort();
                                        template.bullet.delay = reader.ReadShort();
                                        template.explode = new DartTemplateInfo();
                                        count_img = reader.ReadSbyte();
                                        for (int j = 0; j < count_img; j++)
                                        {
                                            template.explode.icon.Add(reader.ReadShort());
                                        }
                                        template.explode.dx = reader.ReadShort();
                                        template.explode.dy = reader.ReadShort();
                                        template.explode.delay = reader.ReadShort();
                                        SkillManager.instance.darts.Add(template.id, template);
                                    }
                                    count = reader.ReadShort();
                                    for (int i = 0; i < count; i++)
                                    {
                                        SkillEffectInfo effectInfo = new SkillEffectInfo();
                                        effectInfo.id = reader.ReadShort();
                                        int count_img = reader.ReadSbyte();
                                        for (int j = 0; j < count_img; j++)
                                        {
                                            effectInfo.icons.Add(reader.ReadShort());
                                        }
                                        effectInfo.dx = reader.ReadShort();
                                        effectInfo.dy = reader.ReadShort();
                                        SkillManager.instance.effects.Add(effectInfo.id, effectInfo);
                                    }
                                    count = reader.ReadShort();
                                    for (int i = 0; i < count; i++)
                                    {
                                        SkillPaint skillPaint = new SkillPaint();
                                        skillPaint.id = reader.ReadShort();
                                        skillPaint.dxFly = reader.ReadShort();
                                        skillPaint.dyFly = reader.ReadShort();
                                        int count_info = reader.ReadSbyte();
                                        for (int j = 0; j < count_info; j++)
                                        {
                                            SkillPaintInfo skillPaintInfo = new SkillPaintInfo();
                                            int soundId = reader.ReadShort();
                                            if (soundId != -1)
                                            {
                                                skillPaintInfo.sound = SoundManager.instance.sounds[soundId];
                                            }
                                            int dartId = reader.ReadShort();
                                            if (dartId != -1)
                                            {
                                                skillPaintInfo.dart = SkillManager.instance.darts[dartId];
                                            }
                                            skillPaintInfo.timeOut = reader.ReadShort();
                                            int count_action = reader.ReadSbyte();
                                            for (int k = 0; k < count_action; k++)
                                            {
                                                skillPaintInfo.action.Add(reader.ReadShort());
                                            };
                                            int count_effect = reader.ReadSbyte();
                                            for (int k = 0; k < count_effect; k++)
                                            {
                                                SkillEffect skillEffect = new SkillEffect();
                                                skillEffect.effectInfo = SkillManager.instance.effects[(int)reader.ReadShort()];
                                                skillEffect.loop = reader.ReadSbyte();
                                                skillPaintInfo.effects.Add(skillEffect);
                                            }
                                            skillPaint.info.Add(skillPaintInfo);
                                        }
                                        SkillManager.instance.paints.Add(skillPaint.id, skillPaint);
                                    }
                                    SkillManager.instance.SavePaint();
                                }
                            }
                            else if (type == 11)
                            {
                                FrameManager.instance.versionAura = message.ReadSByte();
                                FrameManager.instance.auras.Clear();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    Aura aura = new Aura();
                                    aura.id = message.ReadSByte();
                                    aura.dx = message.ReadShort();
                                    aura.dy = message.ReadShort();
                                    aura.delay = message.ReadShort();
                                    int count_img = message.ReadSByte();
                                    for (int j = 0; j < count_img; j++)
                                    {
                                        aura.icons.Add(message.ReadShort());
                                    }
                                    FrameManager.instance.auras.Add(aura.id, aura);
                                }
                                FrameManager.instance.SaveAura();
                            }
                            if (type >= 0)
                            {
                                ServerManager.instance.isUpdateCompleted[type] = true;
                            }
                            break;
                        }
                    case MessageName.START_CREATE_PLAYER_SCREEN:
                        {
                            if (screenManager.createPlayerScreen == null)
                            {
                                screenManager.createPlayerScreen = new CreatePlayerScreen(screenManager);
                            }
                            screenManager.createPlayerScreen.SwitchToMe();
                            Player.isLoadingMap = false;
                            break;
                        }
                    case MessageName.MAP_INFO:
                        {
                            SoundMn.stopAll();
                            Player.isLoadingMap = true;
                            Map.waypoints.Clear();
                            screenManager.gameScreen.monsters.Clear();
                            screenManager.gameScreen.players.Clear();
                            screenManager.gameScreen.npcs.Clear();
                            screenManager.gameScreen.itemMaps.Clear();
                            screenManager.gameScreen.spaceships.Clear();
                            screenManager.gameScreen.effects.Clear();
                            BackgroudEffect.effects.Clear();
                            if (screenManager.gameScreen.cmdDie != null)
                            {
                                screenManager.gameScreen.cmdDie.isShow = false;
                            }
                            if (Player.me.dart != null)
                            {
                                Player.me.dart.Stop();
                            }
                            MonsterManager.instance.darts.Clear();
                            int mapId = message.ReadShort();
                            if (!Map.mapTemplates.ContainsKey(mapId))
                            {
                                Map.template = new MapTemplate();
                                Map.template.bgrId = message.ReadShort();
                                Map.template.id = Map.mapId = mapId;
                                Map.template.name = Map.name = message.ReadUTF();
                                Map.template.row = Map.row = message.ReadShort();
                                Map.template.column = Map.column = message.ReadShort();
                                Map.template.data = Map.data = message.ReadUTF();
                                for (int i = 0; i < 3; i++)
                                {
                                    int imgId = message.ReadShort();
                                    if (!Map.imgBgrs.ContainsKey(imgId))
                                    {
                                        Map.imgBgrs.Add(imgId, GameCanvas.LoadImage("Maps/Backgrounds/" + imgId + ".png"));
                                    }
                                    Map.template.imgsBgr.Add(imgId);
                                }
                                for (int i = 0; i < 4; i++)
                                {
                                    for (int j = 0; j < 3; j++)
                                    {
                                        Map.template.colorsBgr[i, j] = message.ReadShort();
                                    }
                                }
                                Map.template.imgBgr = GameCanvas.LoadImage("Maps/" + Map.template.bgrId);
                                // Line-based collision data - read BEFORE grid parsing
                                Map.template.isLine = message.ReadBool();
                                if (Map.template.isLine)
                                {
                                    Map.template.dataLine = message.ReadUTF();
                                    Map.template.ParseLineData();
                                    Map.template.datas = new int[Map.row, Map.column + 1]; // empty grid fallback
                                }
                                else
                                {
                                    // Grid mode: parse data string as before
                                    Map.template.datas = new int[Map.row, Map.column + 1];
                                    string data = Map.template.data;
                                    for (int i = 0; i < Map.row; i++)
                                    {
                                        for (int j = 0; j < Map.column; j++)
                                        {
                                            Map.template.datas[i, j] = int.Parse(data.Substring(0, 1));
                                            data = data.Substring(1);
                                        }
                                        Map.template.datas[i, Map.column] = 0;
                                    }
                                }
                                Map.mapTemplates.Add(mapId, Map.template);
                            }
                            else
                            {
                                Map.template = Map.mapTemplates[mapId];
                                Map.mapId = Map.template.id;
                                Map.name = Map.template.name;
                                Map.row = Map.template.row;
                                Map.column = Map.template.column;
                            }
                            Player.me.vx = 0;
                            Player.me.SetStatus(PlayerStatus.FALL);
                            Player.me.monsterFocus = null;
                            Player.me.playerFocus = null;
                            Player.me.npcFocus = null;
                            Player.me.itemFocus = null;
                            Player.me.skillPaint = null;
                            Player.me.isDie = false;
                            Map.maps = Map.template.datas;
                            if (Map.template.isLine && Map.template.mapWidth > 0)
                            {
                                Map.width = Map.template.mapWidth;
                                Map.height = Map.template.mapHeight;
                            }
                            else
                            {
                                Map.width = Map.column * Map.size;
                                Map.height = Map.row * Map.size;
                            }
                            Map.areaId = message.ReadSByte();
                            Player.me.xSend = Player.me.x = message.ReadShort();
                            Player.me.ySend = Player.me.y = message.ReadShort();
                            int count_waypoint = message.ReadSByte();
                            for (int i = 0; i < count_waypoint; i++)
                            {
                                Map.waypoints.Add(new Waypoint(message.ReadShort(), message.ReadShort(), message.ReadSByte(), message.ReadUTF()));
                            }
                            int count_npc = message.ReadSByte();
                            for (int i = 0; i < count_npc; i++)
                            {
                                Npc npc = new Npc();
                                npc.template = NpcManager.instance.npcTemplates[message.ReadSByte()];
                                npc.x = message.ReadShort();
                                npc.y = message.ReadShort();
                                npc.Init();
                                screenManager.gameScreen.npcs.Add(npc);
                            }
                            int count_mob = message.ReadSByte();
                            for (int i = 0; i < count_mob; i++)
                            {
                                Monster monster;
                                int type = message.ReadSByte();
                                int templateId = message.ReadShort();
                                if (type == 2)
                                {
                                    monster = new MonsterPet(templateId);
                                }
                                else if (type == 1)
                                {
                                    monster = new BigMonster(templateId);
                                }
                                else
                                {
                                    monster = new Monster(templateId);
                                }
                                monster.template.LoadIcons();
                                monster.id = message.ReadInt();
                                monster.level = message.ReadShort();
                                monster.levelStatus = message.ReadSByte();
                                monster.xFirst = message.ReadShort();
                                monster.yFirst = message.ReadShort();
                                monster.maxHp = message.ReadLong();
                                monster.hp = message.ReadLong();
                                monster.SetStatusServer(message.ReadSByte());
                                monster.x = monster.xFirst;
                                monster.y = monster.yFirst;
                                monster.x += 30 - i;
                                monster.dir = (monster.id % 2 == 1 ? 1 : -1);
                                monster.hpShow = monster.hp;
                                screenManager.gameScreen.monsters.Add(monster);
                            }
                            int count_item = message.ReadShort();
                            for (int i = 0; i < count_item; i++)
                            {
                                ItemMap itemMap = new ItemMap();
                                itemMap.id = message.ReadInt();
                                itemMap.template = ItemManager.instance.itemTemplates[message.ReadShort()];
                                itemMap.x = itemMap.xEnd = message.ReadShort();
                                itemMap.y = itemMap.yEnd = message.ReadShort();
                                itemMap.GetWidthAndHeight();
                                screenManager.gameScreen.itemMaps.Add(itemMap);
                            }
                            screenManager.gameScreen.isShowDragon = message.ReadBool();
                            if (Player.me.y <= 10)
                            {
                                Player.me.isSpaceship = true;
                                screenManager.gameScreen.spaceships.Add(new Spaceship(Player.me, 1));
                            }
                            InfoDlg.Hide();
                            InfoDlg.Show(Map.name, PlayerText.Area + ": " + Map.areaId, 30);
                            Player.isChangingMap = false;
                            Player.isLoadingMap = false;
                            Player.isLockKey = false;
                            Player.me.isLockMove = false;
                            screenManager.gameScreen.isPaintSkill = true;
                            ScreenManager.instance.panel.Close();
                            screenManager.gameScreen.LoadCamera(Player.me.x, Player.me.y);
                            screenManager.gameScreen.cmx = screenManager.gameScreen.cmxTo;
                            screenManager.gameScreen.cmy = screenManager.gameScreen.cmyTo;
                            screenManager.gameScreen.auto = 0;
                            screenManager.gameScreen.isAutoPlay = false;
                            Player.isMoveDown = false;
                            Player.isMoveUp = false;
                            Player.isMoveRight = false;
                            Player.isMoveLeft = false;
                            Map.LoadBgr();
                            BackgroudEffect.CreateBgrEffect(mapId);
                            screenManager.gameScreen.SwitchToMe();
                            break;
                        }
                    case MessageName.ADD_MONSTER:
                        {
                            Monster monster;
                            int type = message.ReadSByte();
                            if (type == 2)
                            {
                                monster = new MonsterPet(message.ReadShort());
                            }
                            else if (type == 1)
                            {
                                monster = new BigMonster(message.ReadShort());
                            }
                            else
                            {
                                monster = new Monster(message.ReadShort());
                            }
                            monster.template.LoadIcons();
                            monster.id = message.ReadInt();
                            monster.level = message.ReadShort();
                            monster.levelStatus = message.ReadSByte();
                            monster.xFirst = message.ReadShort();
                            monster.yFirst = message.ReadShort();
                            monster.maxHp = message.ReadLong();
                            monster.hp = message.ReadLong();
                            monster.SetStatusServer(message.ReadSByte());
                            monster.x = monster.xFirst;
                            monster.y = monster.yFirst;
                            monster.dir = (monster.id % 2 == 1 ? 1 : -1);
                            monster.hpShow = monster.hp;
                            screenManager.gameScreen.monsters.Add(monster);
                            break;
                        }
                    case MessageName.REMOVE_MONSTER:
                        {
                            int monsterId = message.ReadInt();
                            for (int i = 0; i < screenManager.gameScreen.monsters.Count; i++)
                            {
                                Monster monster = screenManager.gameScreen.monsters[i];
                                if (monster.id == monsterId)
                                {
                                    screenManager.gameScreen.monsters.RemoveAt(i);
                                    screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, monster.x, monster.y - monster.h / 2, StaticObj.VCENTER_HCENTER, 0));
                                    break;
                                }
                            }
                            break;
                        }
                    case -117:
                        {
                            Player player = new Player();
                            player.id = message.ReadInt();
                            player.name = message.ReadUTF();
                            if (player.name.StartsWith("$"))
                            {
                                player.name = player.name.Substring(1);
                                player.isDisciple = true;
                            }
                            if (player.name.StartsWith("#"))
                            {
                                player.name = player.name.Substring(1);
                                player.isBoss = true;
                            }
                            player.gender = message.ReadSByte();
                            player.head = FrameManager.instance.GetFrame(message.ReadShort());
                            player.body = FrameManager.instance.GetFrame(message.ReadShort());
                            player.mount = FrameManager.instance.GetMount(message.ReadShort());
                            player.InitBody();
                            player.bag = FrameManager.instance.GetBag(message.ReadShort());
                            player.medal = message.ReadShort();
                            if (serverVersion >= 91)
                            {
                                player.aura = message.ReadShort();
                            }
                            player.x = message.ReadShort();
                            player.y = message.ReadShort();
                            player.xSd = player.x;
                            player.ySd = player.y;
                            player.maxHp = message.ReadLong();
                            player.hp = message.ReadLong();
                            player.typePk = message.ReadSByte();
                            player.typeFlag = message.ReadSByte();
                            player.level = message.ReadShort();
                            player.spaceship = message.ReadSByte();
                            player.speed = message.ReadSByte();
                            int clanId = message.ReadInt();
                            if (clanId == -1)
                            {
                                player.clan = null;
                            }
                            else
                            {
                                Clan clan = new Clan();
                                clan.SetId(clanId);
                                clan.SetName(message.ReadUTF());
                                player.clan = clan;
                            }
                            player.levelEquip = message.ReadSByte();
                            int size_effect = message.ReadSByte();
                            for (int i = 0; i < size_effect; i++)
                            {
                                int id = message.ReadShort();
                                if (id != -1)
                                {
                                    player.effects.Add(new EffectTime(player, id, message.ReadLong()));
                                }
                            }
                            if (player.y <= 10)
                            {
                                player.isSpaceship = true;
                                Spaceship spaceship = new Spaceship(player, 1);
                                screenManager.gameScreen.spaceships.Add(spaceship);
                            }
                            else if (screenManager.gameScreen.FindPlayerInMap(player.id) == null)
                            {
                                screenManager.gameScreen.players.Add(player);
                            }
                            break;
                        }
                    case -116:
                        {
                            int playerId = message.ReadInt();
                            for (int i = 0; i < screenManager.gameScreen.players.Count; i++)
                            {
                                Player player = screenManager.gameScreen.players[i];
                                if (player.id == playerId)
                                {
                                    if (Player.me.playerFocus == player)
                                    {
                                        Player.me.playerFocus = null;
                                        Player.isManualFocus = false;
                                    }
                                    screenManager.gameScreen.players.RemoveAt(i);
                                    screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, player.x, player.y - 20, StaticObj.BOTTOM_HCENTER, 0));
                                    break;
                                }
                            }
                            break;
                        }
                    case -114:
                        {
                            int playerId = message.ReadInt();
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.MoveTo(message.ReadShort(), message.ReadShort());
                            }
                            break;
                        }
                    case -112:
                        {
                            int playerId = message.ReadInt();
                            string text = message.ReadUTF();
                            if (Player.me.id == playerId)
                            {
                                Player.me.AddChatInfo(text);
                            }
                            else
                            {
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    player.AddChatInfo(text);
                                }
                            }
                            break;
                        }
                    case -111:
                        {
                            int playerId = message.ReadInt();
                            if (screenManager.panel.cmdChatGlobals.Count > 20)
                            {
                                screenManager.panel.cmdChatGlobals.RemoveAt(0);
                            }
                            if (Player.me.id == playerId)
                            {
                                CmdMessage mess = new CmdMessage(screenManager.panel, 52, Player.me, message.ReadUTF());
                                screenManager.panel.cmdChatGlobals.Add(mess);
                                screenManager.gameScreen.textGlobal.infos.Add(mess);
                            }
                            else
                            {
                                Player player = new Player();
                                player.id = playerId;
                                player.name = message.ReadUTF();
                                player.head = FrameManager.instance.GetFrame(message.ReadShort());
                                CmdMessage mess = new CmdMessage(screenManager.panel, 52, player, message.ReadUTF());
                                screenManager.panel.cmdChatGlobals.Add(mess);
                                screenManager.gameScreen.textGlobal.infos.Add(mess);
                            }
                            if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabChatGlobal.type)
                            {
                                screenManager.panel.SetTabChatGlobal();
                            }
                            break;
                        }
                    case -109:
                        MessageInfoPlayer(message);
                        break;
                    case -107:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                int type = message.ReadSByte();
                                if (type == -1)
                                {
                                    player.monsterFocus = null;
                                    player.playerFocus = null;
                                }
                                else if (type == 0)
                                {
                                    int focusId = message.ReadInt();
                                    if (Player.me.id == focusId)
                                    {
                                        player.playerFocus = Player.me;
                                    }
                                    else
                                    {
                                        player.playerFocus = screenManager.gameScreen.FindPlayerInMap(focusId);
                                    }
                                    if (player.playerFocus != null)
                                    {
                                        player.dir = (player.x < player.playerFocus.x ? 1 : -1);
                                    }
                                    player.monsterFocus = null;
                                }
                                else if (type == 1)
                                {
                                    player.monsterFocus = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                                    if (player.monsterFocus != null)
                                    {
                                        player.dir = (player.x < player.monsterFocus.x ? 1 : -1);
                                    }
                                    player.playerFocus = null;
                                }
                                player.SetSkillPaint(message.ReadShort());
                            }
                            break;
                        }
                    case -106:
                        {
                            // monster injure
                            Monster mob = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (mob != null)
                            {
                                long hpInjure = message.ReadLong();
                                if (hpInjure != 0)
                                {
                                    mob.hp = message.ReadLong();

                                    bool isCrit = message.ReadBool();
                                    if (isCrit)
                                    {
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_yellow, "-" + Utils.GetMoneys(hpInjure), mob.x, mob.y - mob.h, 0, -2);
                                    }
                                    else
                                    {
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "-" + Utils.GetMoneys(hpInjure), mob.x, mob.y - mob.h, 0, -2);
                                    }
                                }
                                else
                                {
                                    screenManager.gameScreen.StartFlyText(MyFont.text_fly_white, PlayerText.miss, mob.x, mob.y - mob.h, 0, -2);
                                }
                            }
                            break;
                        }
                    case -105:
                        {
                            // monster start live
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null)
                            {
                                monster.levelStatus = message.ReadSByte();
                                monster.maxHp = message.ReadLong();
                                monster.hp = monster.maxHp;
                                monster.hpShow = monster.hp;
                                monster.x = monster.xFirst;
                                monster.y = monster.yFirst;
                                monster.isDie = false;
                                monster.status = MonsterStatus.MOVE;
                            }
                            break;
                        }
                    case -104:
                        {
                            Monster mob = screenManager.gameScreen.FindMonsterInMap(message.ReadSByte());
                            if (mob == null)
                            {
                                break;
                            }
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                player.dir = player.x < mob.x ? 1 : (-1);
                                mob.dir = mob.x < player.x ? 1 : (-1);
                                player.SetSkillPaint(message.ReadShort());
                                player.playerFocus = null;
                                player.monsterFocus = mob;
                                mob.hp = message.ReadLong();
                                long hpInjure = message.ReadLong();

                                if (hpInjure == 0)
                                {
                                    screenManager.gameScreen.StartFlyText(MyFont.text_fly_white, PlayerText.miss, mob.x, mob.y - mob.h, 0, -2);
                                }
                                else
                                {
                                    bool isCrit = message.ReadBool();
                                    if (isCrit)
                                    {
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_yellow, "-" + Utils.GetMoneys(hpInjure), mob.x, mob.y - mob.h, 0, -2);
                                    }
                                    else
                                    {
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "-" + Utils.GetMoneys(hpInjure), mob.x, mob.y - mob.h, 0, -2);
                                    }
                                }
                            }
                            break;
                        }
                    case -102:
                        {
                            int monsterId = message.ReadInt();
                            short x = message.ReadShort();
                            short y = message.ReadShort();
                            sbyte dir = message.ReadSByte();
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(monsterId);
                            if (monster != null)
                            {
                                monster.x = x;
                                monster.y = y;
                                monster.dir = dir;
                            }
                            break;
                        }
                    case -101:
                        {
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null)
                            {
                                int type = message.ReadSByte();
                                if (type == 0)
                                {
                                    int playerId = message.ReadInt();
                                    long damage = message.ReadLong();
                                    if (Player.me.id == playerId)
                                    {
                                        monster.SetAttack(Player.me, damage);
                                    }
                                    else
                                    {
                                        Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                        if (player != null)
                                        {
                                            monster.SetAttack(player, damage);
                                        }
                                    }
                                }
                                else
                                {
                                    int monsterId = message.ReadInt();
                                    long damage = message.ReadLong();
                                    Monster target = screenManager.gameScreen.FindMonsterInMap(monsterId);
                                    monster.SetAttack(target, damage);
                                }
                            }
                            break;
                        }
                    case -100:
                        {
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null && !monster.IsDead())
                            {
                                long hpInjure = message.ReadLong();
                                bool isCrit = message.ReadBool();
                                monster.StartDie();
                                if (isCrit)
                                {
                                    screenManager.gameScreen.StartFlyText(MyFont.text_fly_yellow, "-" + Utils.GetMoneys(hpInjure), monster.x, monster.y - monster.h, 0, -2);
                                }
                                else
                                {
                                    screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "-" + Utils.GetMoneys(hpInjure), monster.x, monster.y - monster.h, 0, -2);
                                }
                            }
                            break;
                        }
                    case -99:
                        {
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null && !monster.IsDead())
                            {
                                monster.effects.Add(new EffectTime(monster, message.ReadShort(), message.ReadLong()));
                            }
                            break;
                        }
                    case -98:
                        {
                            Player.me.isDie = true;
                            Player.me.StartDie(message.ReadShort(), message.ReadShort());
                            break;
                        }
                    case -96:
                        {
                            int playerId = message.ReadInt();
                            Player player = Player.me;
                            if (Player.me.id != playerId)
                            {
                                player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            }
                            if (player == null)
                            {
                                break;
                            }
                            player.x = message.ReadShort();
                            player.y = message.ReadShort();
                            player.hp = message.ReadLong();
                            player.mp = message.ReadLong();
                            player.SetStatus(PlayerStatus.FALL);
                            if (Player.me.id == playerId)
                            {
                                screenManager.gameScreen.cmdDie.isShow = false;
                                screenManager.gameScreen.isPaintSkill = true;
                                Player.isLockKey = false;
                                Player.me.isLockMove = false;
                                Player.me.isDie = false;
                            }
                            break;
                        }
                    case -95:
                        {
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                Player.me.maxHp = message.ReadLong();
                                Player.me.hp = message.ReadLong();
                                break;
                            }
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.maxHp = message.ReadLong();
                                player.hp = message.ReadLong();
                                if (Player.disciple != null && playerId == Player.disciple.id)
                                {
                                    Player.disciple.maxHp = player.maxHp;
                                    Player.disciple.hp = player.hp;
                                }
                            }
                            break;
                        }
                    case -94:
                        {
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                Player.me.effects.Add(new EffectTime(Player.me, message.ReadShort(), message.ReadLong()));
                                break;
                            }
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.effects.Add(new EffectTime(player, message.ReadShort(), message.ReadLong()));
                            }
                            break;
                        }
                    case -93:
                        {
                            InfoDlg.Hide();
                            int npcId = message.ReadSByte();
                            string chat = message.ReadUTF();
                            int menu_size = message.ReadSByte();
                            List<string> menus = new List<string>();
                            for (int i = 0; i < menu_size; i++)
                            {
                                menus.Add(message.ReadUTF());
                            }
                            screenManager.gameScreen.CreateMenu(chat, menus, screenManager.gameScreen.FindNpcInMap(npcId));
                            break;
                        }
                    case MessageName.NPC_CHAT:
                        {
                            InfoDlg.Hide();
                            Npc npc = screenManager.gameScreen.FindNpcInMap(message.ReadSByte());
                            if (npc != null)
                            {
                                npc.AddChatInfo(message.ReadUTF());
                            }
                            break;
                        }
                    case -87:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            InfoMe.addInfo(message.ReadUTF(), type);
                            break;
                        }
                    case -85:
                        {
                            InfoDlg.Hide();
                            screenManager.panel.areas.Clear();
                            int count_area = message.ReadSByte();
                            for (int i = 0; i < count_area; i++)
                            {
                                CmdArea area = new CmdArea(screenManager.panel, 32, i);
                                area.areaId = message.ReadSByte();
                                area.maxPlayer = message.ReadSByte();
                                area.numPlayer = message.ReadSByte();
                                area.numTeam = message.ReadSByte();
                                area.isColorRed = message.ReadBool();
                                screenManager.panel.areas.Add(area);
                            }
                            screenManager.panel.SetType(TabPanel.tabArea.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case -83:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                player.head = FrameManager.instance.GetFrame(message.ReadShort());
                                player.body = FrameManager.instance.GetFrame(message.ReadShort());
                                player.mount = FrameManager.instance.GetMount(message.ReadShort());
                                player.bag = FrameManager.instance.GetBag(message.ReadShort());
                                player.medal = message.ReadShort();
                                player.aura = message.ReadShort();
                            }
                            break;
                        }
                    case -82:
                        {
                            screenManager.panel.itemsShop.Clear();
                            screenManager.panel.typeShop = message.ReadSByte();
                            int count_tab = message.ReadSByte();
                            for (int i = 0; i < count_tab; i++)
                            {
                                string name_tab = message.ReadUTF();
                                int count_shop = message.ReadShort();
                                List<Item> items = new List<Item>();
                                for (int j = 0; j < count_shop; j++)
                                {
                                    Item item = new Item();
                                    item.id = message.ReadInt();
                                    item.template = ItemManager.instance.itemTemplates[message.ReadShort()];
                                    item.typePrice = message.ReadSByte();
                                    item.price = message.ReadInt();
                                    item.quantity = message.ReadInt();
                                    item.isLock = message.ReadBool();
                                    int count_option = message.ReadSByte();
                                    for (int k = 0; k < count_option; k++)
                                    {
                                        ItemOption option = new ItemOption();
                                        option.template = ItemManager.instance.itemOptionTemplates[message.ReadShort()];
                                        option.param = message.ReadInt();
                                        item.options.Add(option);
                                    }
                                    if (screenManager.panel.typeShop == 1)
                                    {
                                        item.sellerName = message.ReadUTF();
                                        item.expiryInfo = message.ReadUTF();
                                        item.status = message.ReadSByte();
                                    }
                                    items.Add(item);
                                }
                                screenManager.panel.itemsShop.Add(name_tab, items);
                            }
                            if (screenManager.panel.typeShop >= 2)
                            {
                                screenManager.panel.pointShop = message.ReadInt();
                            }
                            screenManager.panel.SetType(TabPanel.tabShop.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case MessageName.ADD_ITEM_MAP:
                        {
                            ItemMap itemMap = new ItemMap();
                            itemMap.id = message.ReadInt();
                            itemMap.template = ItemManager.instance.itemTemplates[message.ReadShort()];
                            itemMap.x = itemMap.xEnd = message.ReadShort();
                            itemMap.y = itemMap.yEnd = message.ReadShort();
                            itemMap.playerId = message.ReadInt();
                            itemMap.GetWidthAndHeight();
                            screenManager.gameScreen.itemMaps.Add(itemMap);
                            break;
                        }
                    case MessageName.REMOVE_ITEM_MAP:
                        {
                            int itemMapId = message.ReadInt();
                            for (int i = 0; i < screenManager.gameScreen.itemMaps.Count; i++)
                            {
                                if (screenManager.gameScreen.itemMaps[i].id == itemMapId)
                                {
                                    screenManager.gameScreen.itemMaps.RemoveAt(i);
                                    break;
                                }
                            }
                            break;
                        }
                    case -79:
                        {
                            int itemMapId = message.ReadInt();
                            int quantity = message.ReadInt();
                            Player.me.itemFocus = null;
                            foreach (ItemMap itemMap in screenManager.gameScreen.itemMaps)
                            {
                                if (itemMap.id == itemMapId)
                                {
                                    itemMap.SetPoint(Player.me.x, Player.me.y - (Player.me.h / 2 - itemMap.h) / 2);
                                    SoundMn.pickItem();
                                    if (itemMap.template.type == Item.TYPE_YEN)
                                    {
                                        Player.me.coin += quantity;
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_yellow, "+" + Utils.GetMoneys(quantity), Player.me.x, Player.me.y - Player.me.h - 20, 0, -2);
                                    }
                                    else if (itemMap.template.type == Item.TYPE_DIAMOND)
                                    {
                                        Player.me.diamond += quantity;
                                        screenManager.gameScreen.StartFlyText(MyFont.text_fly_blue, "+" + Utils.GetMoneys(quantity), Player.me.x, Player.me.y - Player.me.h - 20, 0, -2);
                                    }
                                    /*else
                                    {
                                        InfoMe.AddInfo("Bạn nhận được " + (quantity > 1 ? (quantity + " ") : "") + itemMap.part.name, 1);
                                    }*/
                                    break;
                                }
                            }
                            break;
                        }
                    case -77:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                int itemMapId = message.ReadInt();
                                foreach (ItemMap itemMap in screenManager.gameScreen.itemMaps)
                                {
                                    if (itemMap.id == itemMapId)
                                    {
                                        itemMap.SetPoint(player.x, player.y - (player.h / 2 - itemMap.h) / 2);
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    case -73:
                        {
                            InfoDlg.Hide();
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                isStopReadMessage = true;
                                screenManager.gameScreen.lockTick = 500;
                                Spaceship spaceship = new Spaceship(Player.me, 0);
                                screenManager.gameScreen.spaceships.Add(spaceship);
                            }
                            else
                            {
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    Spaceship spaceship = new Spaceship(player, 0);
                                    screenManager.gameScreen.spaceships.Add(spaceship);
                                }
                            }
                            break;
                        }
                    case -70:
                        {
                            int mobId = message.ReadSByte();
                            if (mobId != -1)
                            {
                                Monster mob = screenManager.gameScreen.FindMonsterInMap(mobId);
                                if (mob == null)
                                {
                                    break;
                                }
                                Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                                if (player != null)
                                {
                                    if (player.x < mob.x)
                                    {
                                        player.dir = 1;
                                    }
                                    else
                                    {
                                        player.dir = -1;
                                    }
                                    player.SetSkillPaint(message.ReadShort());
                                    player.playerFocus = null;
                                    player.monsterFocus = mob;
                                }
                            }
                            break;
                        }
                    case -69:
                        {
                            int playerId = message.ReadInt();
                            Player player = Player.me;
                            if (Player.me.id != playerId)
                            {
                                player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            }
                            if (player != null)
                            {
                                long hpInjure = message.ReadLong();
                                bool isCrit = (hpInjure > 0 ? message.ReadBool() : false);
                                player.DoInjure(hpInjure, isCrit);
                            }
                            break;
                        }
                    case -68:
                        {
                            int playerId = message.ReadInt();
                            Player playerUnderAttack = Player.me;
                            if (Player.me.id != playerId)
                            {
                                playerUnderAttack = screenManager.gameScreen.FindPlayerInMap(playerId);
                            }
                            if (playerUnderAttack == null)
                            {
                                break;
                            }
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                if (player.x < playerUnderAttack.x)
                                {
                                    player.dir = 1;
                                }
                                else
                                {
                                    player.dir = -1;
                                }
                                player.SetSkillPaint(message.ReadShort());
                                player.playerFocus = playerUnderAttack;
                                player.monsterFocus = null;
                                long hpInjure = message.ReadLong();
                                bool isCrit = (hpInjure > 0 ? message.ReadBool() : false);
                                playerUnderAttack.DoInjure(hpInjure, isCrit);
                            }
                            break;
                        }
                    case -67:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                player.isCharge = false;
                            }
                            break;
                        }
                    case -66:
                        {
                            int playerId1 = message.ReadInt();
                            int playerId2 = message.ReadInt();
                            if (Player.me.id == playerId1)
                            {
                                Player player2 = screenManager.gameScreen.FindPlayerInMap(playerId2);
                                if (player2 != null)
                                {
                                    player2.typePk = 1;
                                    Player.me.FocusManualTo(player2);
                                    Player.me.typePk = 1;
                                    Player.me.killId = playerId2;
                                    screenManager.gameScreen.cmdQues.isShow = false;
                                }
                            }
                            else if (Player.me.id == playerId2)
                            {
                                Player player1 = screenManager.gameScreen.FindPlayerInMap(playerId1);
                                if (player1 != null)
                                {
                                    player1.typePk = 1;
                                    Player.me.FocusManualTo(player1);
                                    Player.me.typePk = 1;
                                    Player.me.killId = playerId1;
                                    screenManager.gameScreen.cmdQues.isShow = false;
                                }
                            }
                            else
                            {
                                Player player1 = screenManager.gameScreen.FindPlayerInMap(playerId1);
                                if (player1 != null)
                                {
                                    player1.typePk = 1;
                                }
                                Player player2 = screenManager.gameScreen.FindPlayerInMap(playerId2);
                                if (player2 != null)
                                {
                                    player2.typePk = 1;
                                }
                            }
                            break;
                        }
                    case -65:
                        {
                            int playerId = message.ReadInt();
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.typePk = message.ReadSByte();
                            }
                            else
                            {
                                foreach (Spaceship spaceship in screenManager.gameScreen.spaceships)
                                {
                                    if (spaceship.player.id == playerId)
                                    {
                                        spaceship.player.typePk = message.ReadSByte();
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    case -64:
                        {
                            int playerId = message.ReadInt();
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.typeFlag = message.ReadSByte();
                            }
                            else
                            {
                                foreach (Spaceship spaceship in screenManager.gameScreen.spaceships)
                                {
                                    if (spaceship.player.id == playerId)
                                    {
                                        spaceship.player.typeFlag = message.ReadSByte();
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    case -63:
                        {
                            if (Player.me.clan == null)
                            {
                                break;
                            }
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                screenManager.panel.cmdChatClans.Add(new CmdMessage(screenManager.panel, 52, Player.me, message.ReadUTF()));
                            }
                            else
                            {
                                string content = message.ReadUTF();
                                Player player = new Player();
                                player.id = playerId;
                                player.name = message.ReadUTF();
                                player.head = FrameManager.instance.GetFrame(message.ReadShort());
                                screenManager.panel.cmdChatClans.Add(new CmdMessage(screenManager.panel, 52, player, content));
                                screenManager.panel.isNewMessageClan = true;
                            }
                            if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabChatClan.type)
                            {
                                screenManager.panel.SetTabChatClan();
                            }
                            break;
                        }
                    case -62:
                        {
                            if (Player.me.team == null)
                            {
                                break;
                            }
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                screenManager.panel.cmdChatTeams.Add(new CmdMessage(screenManager.panel, 52, Player.me, message.ReadUTF()));
                            }
                            else
                            {
                                Player player = Player.me.team.members.First(i => i.player.id == playerId).player;
                                if (player != null)
                                {
                                    string content = message.ReadUTF();
                                    player.head = FrameManager.instance.GetFrame(message.ReadShort());
                                    screenManager.panel.cmdChatTeams.Add(new CmdMessage(screenManager.panel, 52, player, content));
                                    screenManager.panel.isNewMessageTeam = true;
                                }
                            }
                            if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabChatTeam.type)
                            {
                                screenManager.panel.SetTabChatTeam();
                            }
                            break;
                        }
                    case -61:
                        {
                            int playerId = message.ReadInt();
                            Player player = new Player();
                            player.id = playerId;
                            player.name = message.ReadUTF();
                            player.head = FrameManager.instance.GetFrame(message.ReadShort());
                            screenManager.panel.AddChat(playerId == Player.me.id ? Player.me : player, message.ReadUTF());
                            break;
                        }
                    case -60:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                player.level = message.ReadShort();
                            }
                            break;
                        }
                    case -57:
                        {
                            screenManager.panel.notifications.Clear();
                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                screenManager.panel.notifications.Add(message.ReadUTF());
                            }
                            screenManager.panel.SetType(TabPanel.tabNotification.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case -55:
                        {
                            int type = message.ReadSByte();
                            if (type == 1)
                            {
                                int playerId = message.ReadInt();
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    Player.me.FocusManualTo(player);
                                    screenManager.panel.playerMenu = player;
                                    screenManager.panel.SetType(TabPanel.tabTrade.type);
                                    screenManager.panel.Show();
                                }
                            }
                            else if (type == 2)
                            {
                                screenManager.panel.Close();
                                InfoMe.addInfo("Giao dịch bị hủy bỏ", 0);
                            }
                            else if (type == 3)
                            {
                                int index = message.ReadSByte();
                                if (Player.me.itemsBag[index] != null)
                                {
                                    Item item = Player.me.itemsBag[index].Clone();
                                    item.quantity = message.ReadInt();
                                    screenManager.panel.itemsTrade.Add(item);
                                }
                            }
                            else if (type == 4)
                            {
                                int index = message.ReadSByte();
                                for (int i = 0; i < screenManager.panel.itemsTrade.Count; i++)
                                {
                                    Item item = screenManager.panel.itemsTrade[i];
                                    if (item != null && item.indexUI == index)
                                    {
                                        screenManager.panel.itemsTrade.Remove(item);
                                    }
                                }
                            }
                            else if (type == 5)
                            {
                                screenManager.panel.coinTrade = message.ReadLong();
                            }
                            else if (type == 6)
                            {
                                screenManager.panel.isLock = true;
                            }
                            else if (type == 7)
                            {
                                screenManager.panel.itemsPlayerTrade.Clear();
                                int count_item = message.ReadSByte();
                                for (int i = 0; i < count_item; i++)
                                {
                                    Item item = new Item(message);
                                    item.indexUI = i;
                                    screenManager.panel.itemsPlayerTrade.Add(item);
                                }
                                screenManager.panel.coinPlayerTrade = message.ReadLong();
                                screenManager.panel.isPlayerLock = true;
                            }
                            else if (type == 8)
                            {
                                if (!screenManager.panel.isPlayerLock)
                                {
                                    InfoMe.addInfo("Vui lòng chờ đối phương đồng ý", 1);
                                }
                            }
                            else if (type == 9)
                            {
                                screenManager.panel.Close();
                                InfoMe.addInfo("Giao dịch thành công", 1);
                            }
                            break;
                        }
                    case -54:
                        {
                            Player player = new Player();
                            player.id = message.ReadInt();
                            player.name = message.ReadUTF();
                            player.head = FrameManager.instance.GetFrame(message.ReadShort());
                            player.body = FrameManager.instance.GetFrame(message.ReadShort());
                            player.InitBody();
                            player.x = message.ReadShort();
                            player.y = message.ReadShort();
                            player.xSd = player.x;
                            player.ySd = player.y;
                            player.maxHp = message.ReadLong();
                            player.hp = message.ReadLong();
                            player.typePk = message.ReadSByte();
                            player.mount = FrameManager.instance.GetMount(message.ReadShort());
                            player.level = message.ReadShort();
                            int size_effect = message.ReadSByte();
                            for (int i = 0; i < size_effect; i++)
                            {
                                int id = message.ReadShort();
                                if (id != -1)
                                {
                                    player.effects.Add(new EffectTime(player, id, message.ReadLong()));
                                }
                            }
                            if (screenManager.gameScreen.FindPlayerInMap(player.id) == null)
                            {
                                screenManager.gameScreen.players.Add(player);
                            }
                            break;
                        }
                    case -53:
                        {
                            Player player = new Player();
                            player.name = "Thông báo Server";
                            CmdMessage mess = new CmdMessage(screenManager.panel, 52, player, message.ReadUTF());
                            screenManager.panel.cmdChatServers.Add(mess);
                            screenManager.gameScreen.ChatVip(mess.message);
                            if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabChatServer.type)
                            {
                                screenManager.panel.SetTabChatServer();
                            }
                            break;
                        }
                    case -52:
                        {
                            InfoDlg.Hide();
                            DisplayManager.instance.StartDialogYesNo(message.reader().ReadUTF(), message.reader().ReadUTF(), message.reader().ReadUTF());
                            break;
                        }
                    case -51:
                        {
                            screenManager.gameScreen.itemSpins.Clear();
                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                Item item = new Item();
                                item.template = ItemManager.instance.itemTemplates[message.ReadShort()];
                                item.quantity = message.ReadInt();
                                screenManager.gameScreen.itemSpins.Add(item);
                            }
                            screenManager.gameScreen.StartItemSpin();
                            break;
                        }
                    case -50:
                        {
                            Player[] players = new Player[2];
                            players[0] = new Player();
                            players[0].id = message.ReadInt();
                            players[0].name = message.ReadUTF();
                            players[0].gender = message.ReadSByte();
                            players[0].power = message.ReadLong();
                            players[0].level = message.ReadShort();
                            players[0].head = FrameManager.instance.GetFrame(message.ReadShort());
                            players[0].body = FrameManager.instance.GetFrame(message.ReadShort());
                            players[0].InitBody();
                            players[0].maxHp = message.ReadLong();
                            players[0].maxMp = message.ReadLong();
                            players[0].hp = message.ReadLong();
                            players[0].mp = message.ReadLong();
                            players[0].speed = message.ReadSByte();
                            players[0].pointPk = message.ReadSByte();
                            players[0].pointActivity = message.ReadShort();
                            players[0].countBarrack = message.ReadSByte();
                            players[0].dodge = message.ReadUTF();
                            players[0].critical = message.ReadUTF();
                            players[0].reduceDamage = message.ReadUTF();
                            players[0].bloodsucking = message.ReadUTF();
                            players[0].manaSucking = message.ReadUTF();
                            players[0].strikeBack = message.ReadUTF();
                            //players[0].accurate = message.ReadUTF();//mxh chính xác
                            //players[0].critDamage = message.ReadUTF();//mxh chính xác
                            //players[0].ignoreCrit = message.ReadUTF();//mxh chính xác
                            //players[0].pierceArmor = message.ReadUTF();//mxh chính xác
                            players[0].maxDamage = message.ReadLong();
                            int count_item_body = message.ReadSByte();
                            for (int i = 0; i < count_item_body; i++)
                            {
                                int itemId = message.ReadShort();
                                if (itemId == -1)
                                {
                                    players[0].itemsBody.Add(null);
                                }
                                else
                                {
                                    Item item = new Item(itemId, message);
                                    item.indexUI = i;
                                    players[0].itemsBody.Add(item);
                                }
                            }
                            count_item_body = message.ReadSByte();
                            for (int i = 0; i < count_item_body; i++)
                            {
                                int itemId = message.ReadShort();
                                if (itemId == -1)
                                {
                                    players[0].itemsOther.Add(null);
                                }
                                else
                                {
                                    Item item = new Item(itemId, message);
                                    item.indexUI = i;
                                    players[0].itemsOther.Add(item);
                                }
                            }
                            players[0].clan = new Clan();
                            players[0].clan.name = message.ReadUTF();

                            int discipleId = message.ReadInt();
                            if (discipleId != 1)
                            {
                                players[1] = new Player();
                                players[1].id = discipleId;
                                players[1].name = message.ReadUTF();
                                players[1].gender = message.ReadSByte();
                                players[1].power = message.ReadLong();
                                players[1].level = message.ReadShort();
                                players[1].head = FrameManager.instance.GetFrame(message.ReadShort());
                                players[1].body = FrameManager.instance.GetFrame(message.ReadShort());
                                players[1].InitBody();
                                players[1].maxHp = message.ReadLong();
                                players[1].maxMp = message.ReadLong();
                                players[1].hp = message.ReadLong();
                                players[1].mp = message.ReadLong();
                                players[1].speed = message.ReadSByte();
                                players[1].pointPk = message.ReadSByte();
                                players[1].pointActivity = message.ReadShort();
                                players[1].countBarrack = message.ReadSByte();
                                players[1].dodge = message.ReadUTF();
                                players[1].critical = message.ReadUTF();
                                players[1].reduceDamage = message.ReadUTF();
                                players[1].bloodsucking = message.ReadUTF();
                                players[1].manaSucking = message.ReadUTF();
                                players[1].strikeBack = message.ReadUTF();
                                //players[1].accurate = message.ReadUTF();//mxh chính xác
                                //players[1].critDamage = message.ReadUTF();//mxh chính xác
                                //players[1].ignoreCrit = message.ReadUTF();//mxh chính xác
                                //players[1].pierceArmor = message.ReadUTF();//mxh chính xác
                                players[1].maxDamage = message.ReadLong();
                                int count_item_body_dis = message.ReadSByte();
                                for (int i = 0; i < count_item_body_dis; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        players[1].itemsBody.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        players[1].itemsBody.Add(item);
                                    }
                                }
                                count_item_body = message.ReadSByte();
                                for (int i = 0; i < count_item_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        players[1].itemsOther.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        players[1].itemsOther.Add(item);
                                    }
                                }
                                players[1].clan = new Clan();
                                players[1].clan.name = message.ReadUTF();
                                screenManager.panel.viewers = players;
                            }
                            else
                            {
                                screenManager.panel.viewers = new Player[] { players[0] };
                            }
                            int petId = message.ReadShort();
                            if (petId == -1)
                            {
                                screenManager.panel.petMenu = null;
                            }
                            else
                            {
                                MonsterPet pet = new MonsterPet(petId);
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    ItemOption option = new ItemOption();
                                    option.template = ItemManager.instance.itemOptionTemplates[message.ReadShort()];
                                    option.param = message.ReadInt();
                                    pet.options.Add(option);
                                }
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        pet.itemsBody.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        pet.itemsBody.Add(item);
                                    }
                                }
                                pet.maxStamina = message.ReadShort();
                                pet.stamina = message.ReadShort();
                                pet.maxHp = message.ReadLong();
                                pet.hp = message.ReadLong();
                                pet.damage = message.ReadLong();
                                pet.exp = message.ReadInt();
                                pet.maxExp = message.ReadInt();
                                screenManager.panel.petMenu = pet;
                            }
                            screenManager.panel.playerMenu = players[0];
                            screenManager.panel.SetType(TabPanel.tabViewPlayer.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case MessageName.ADD_NPC:
                        {
                            int npcId = message.ReadSByte();
                            Npc npc = screenManager.gameScreen.FindNpcInMap(npcId);
                            if (npc != null)
                            {
                                screenManager.gameScreen.npcs.Remove(npc);
                                screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, npc.x, npc.y - 20, StaticObj.BOTTOM_HCENTER, 0));
                            }
                            npc = new Npc();
                            npc.template = NpcManager.instance.npcTemplates[npcId];
                            npc.Init();
                            npc.x = message.ReadShort();
                            npc.y = message.ReadShort();
                            screenManager.gameScreen.npcs.Add(npc);
                            break;
                        }
                    case MessageName.REMOVE_NPC:
                        {
                            int npcId = message.ReadSByte();
                            Npc npc = screenManager.gameScreen.FindNpcInMap(npcId);
                            if (npc != null)
                            {
                                screenManager.gameScreen.npcs.Remove(npc);
                                screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, npc.x, npc.y - 20, StaticObj.BOTTOM_HCENTER, 0));
                            }
                            break;
                        }
                    case -46:
                        {
                            InfoDlg.Hide();
                            string name = message.ReadUTF();
                            List<TextField> texts = new List<TextField>();
                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                TextField textField = new TextField();
                                textField.name = message.ReadUTF();
                                textField.type = message.ReadSByte();
                                textField.isFocus = false;
                                texts.Add(textField);
                            }
                            GameCanvas.clientInput.Show(name, texts);
                            break;
                        }
                    case -45:
                        {
                            int playerId = message.ReadInt();
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player != null)
                            {
                                player.StartDie(message.ReadShort(), message.ReadShort());
                            }
                            break;
                        }
                    case -44:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                InfoDlg.Hide();
                                int clanId = message.ReadInt();
                                if (clanId == -1)
                                {
                                    Player.me.clan = null;
                                    break;
                                }
                                Clan clan = new Clan();
                                clan.SetId(clanId);
                                clan.SetName(message.ReadUTF());
                                clan.SetSlogan(message.ReadUTF());
                                clan.coin = message.ReadLong();
                                clan.SetNotification(message.ReadUTF());
                                clan.SetLevel(message.ReadSByte());
                                clan.SetExp(message.ReadLong());
                                clan.SetMaxMember(message.ReadShort());
                                clan.SetMaxExp(message.ReadLong());
                                clan.SetCreateTime(message.ReadUTF());
                                clan.roleId = message.ReadSByte();
                                int count_member = message.ReadSByte();
                                for (int i = 0; i < count_member; i++)
                                {
                                    CmdClanMember member = new CmdClanMember(screenManager.panel, 68, i);
                                    member.playerId = message.ReadInt();
                                    member.roleId = message.ReadSByte();
                                    member.name = message.ReadUTF();
                                    member.gender = message.ReadSByte();
                                    member.power = message.ReadLong();
                                    member.point = message.ReadInt();
                                    member.pointDay = message.ReadInt();
                                    member.isOnline = message.ReadBool();
                                    member.joinTime = message.ReadUTF();
                                    clan.members.Add(member);
                                }
                                Player.me.clan = clan;
                                if (message.ReadBool())
                                {
                                    screenManager.panel.SetType(TabPanel.tabClanInfo.type);
                                    screenManager.panel.Show();
                                }
                            }
                            else if (type == 1)
                            {
                                if (Player.me.clan != null)
                                {
                                    Player.me.clan.coin = message.ReadLong();
                                }
                            }
                            else if (type == 3)
                            {
                                if (Player.me.clan != null)
                                {
                                    Player.me.clan.slogan = message.ReadUTF();
                                }
                            }
                            else if (type == 4)
                            {
                                if (Player.me.clan != null)
                                {
                                    Player.me.clan.notification = message.ReadUTF();
                                }
                            }
                            break;
                        }
                    case -43:
                        {
                            int playerId = message.ReadInt();
                            Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                            if (player == null)
                            {
                                break;
                            }
                            int clanId = message.ReadInt();
                            if (clanId == -1)
                            {
                                player.clan = null;
                            }
                            else
                            {
                                Clan clan = new Clan();
                                clan.SetId(clanId);
                                clan.SetName(message.ReadUTF());
                                player.clan = clan;
                            }
                            break;
                        }
                    case -42:
                        {
                            InfoDlg.Hide();
                            ScreenManager.instance.gameScreen.isLockAction = message.ReadBool();
                            break;
                        }
                    case -40:
                        {
                            screenManager.gameScreen.timeSolo = message.ReadInt();
                            screenManager.gameScreen.timeEndSolo = DateTime.UtcNow.AddSeconds(screenManager.gameScreen.timeSolo);
                            break;
                        }
                    case -39:
                        {
                            screenManager.gameScreen.TimeRemaining = message.ReadLong();
                            screenManager.gameScreen.TimeEndRemaining = DateTime.UtcNow.AddMilliseconds(screenManager.gameScreen.TimeRemaining);
                            break;
                        }
                    case -38:
                        {
                            InfoDlg.Hide();
                            screenManager.panel.tops.Clear();
                            int count_top = message.ReadSByte();
                            for (int i = 0; i < count_top; i++)
                            {
                                CmdTop top = new CmdTop(screenManager.panel, 38, i);
                                top.topId = i;
                                top.player = new Player();
                                top.player.id = message.ReadInt();
                                top.player.name = message.ReadUTF();
                                top.player.gender = message.ReadSByte();
                                top.description = message.ReadUTF();
                                top.isOnline = message.ReadBool();
                                screenManager.panel.tops.Add(top);
                            }
                            screenManager.panel.SetType(TabPanel.tabTop.type);
                            screenManager.panel.Show();
                            break;
                        }
                    /*case -36:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Monster monster = new Monster(message.ReadShort());
                                monster.level = message.ReadShort();
                                monster.id = message.ReadInt();
                                monster.levelStatus = message.ReadSByte();
                                monster.xFirst = message.ReadShort();
                                monster.yFirst = message.ReadShort();
                                monster.maxHp = message.ReadLong();
                                monster.hp = message.ReadLong();
                                monster.SetStatusServer(message.ReadSByte());
                                monster.x = monster.xFirst;
                                monster.y = monster.yFirst;
                                monster.dir = (monster.id % 2 == 1 ? 1 : -1);
                                monster.hpShow = monster.hp;
                                screenManager.gameScreen.monsters.Add(monster);
                            }
                            else if (type == 1)
                            {
                                Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadShort());
                                if (monster != null)
                                {
                                    screenManager.gameScreen.monsters.Remove(monster);
                                    screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, monster.x, monster.y - monster.h / 2));
                                }
                            }
                            break;
                        }*/
                    case -35:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                BigMonster monster = new BigMonster(message.ReadShort());
                                monster.id = message.ReadInt();
                                monster.level = message.ReadShort();
                                monster.levelStatus = message.ReadSByte();
                                monster.xFirst = message.ReadShort();
                                monster.yFirst = message.ReadShort();
                                monster.maxHp = message.ReadLong();
                                monster.hp = message.ReadLong();
                                monster.SetStatusServer(message.ReadSByte());
                                monster.x = monster.xFirst;
                                monster.y = monster.yFirst;
                                monster.dir = (monster.id % 2 == 1 ? 1 : -1);
                                monster.hpShow = monster.hp;
                                screenManager.gameScreen.monsters.Add(monster);
                            }
                            else if (type == 1)
                            {
                                Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                                if (monster != null)
                                {
                                    screenManager.gameScreen.monsters.Remove(monster);
                                    screenManager.gameScreen.effects.Add(new EffectLoop(17, 1, monster.x, monster.y - monster.h / 2, StaticObj.VCENTER_HCENTER, 0));
                                }
                            }
                            else if (type == 2)
                            {
                                Dictionary<Player, long> targets = new Dictionary<Player, long>();
                                int size = message.ReadSByte();
                                for (int i = 0; i < size; i++)
                                {
                                    int playerId = message.ReadInt();
                                    long damage = message.ReadLong();
                                    if (Player.me.id == playerId)
                                    {
                                        targets.Add(Player.me, damage);
                                    }
                                    else
                                    {
                                        Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                        if (player != null)
                                        {
                                            targets.Add(player, damage);
                                        }
                                    }
                                }
                                if (targets.Count > 0)
                                {
                                    BigMonster bigMonster = screenManager.gameScreen.FindBigMonster();
                                    if (bigMonster != null)
                                    {
                                        bigMonster.SetAttack(targets);
                                    }
                                }
                            }
                            break;
                        }
                    case -34:
                        {
                            /*int[][] iconId = new int[][]
                               {
                                    new int[] { 870, 871, 872, 873, 874, 875, 876, 877, 878 },
                                    new int[] { 879, 880, 881, 882, 883, 884, 885, 886, 887 },
                                    new int[] { 888, 889, 890, 891, 892, 893, 894, 895, 896 },
                                    new int[] { 897, 898, 899, 900, 901, 902, 903, 904, 905 },
                                    new int[] { 906, 907, 908, 909, 910, 911, 912, 913, 914 },
                                    new int[] { 915, 916, 917, 918, 919, 920, 921, 922, 923, 924 },
                                    new int[] { 934, 935, 936, 937, 938, 939, 940, 941, 942, 943 },
                                    new int[] { 944, 945, 946, 947, 948, 949, 950, 951, 952, 953 },
                                    new int[] { 954, 955, 956, 957, 958, 959, 960, 961, 962, 963 },
                                    new int[] { 964, 965, 966, 967, 968, 969, 970, 971, 972, 973 }
                               };
                            int xStart = message.ReadShort();
                            GameScr.isAddFirework = true;
                            Thread thread = new Thread(() =>
                            {
                                int num = 0;
                                while (GameScr.isAddFirework && num < 50)
                                {
                                    num++;
                                    GameScr.effects.Add(new Effect(xStart + Utils.random(-300, 300), TileMap.getYSd(xStart) - Utils.random(300, 500), iconId[Utils.random(0, 9)]));
                                    Thread.Sleep(100);
                                }
                            });
                            thread.IsBackground = true;
                            thread.Start();*/
                            break;
                        }
                    case -33:
                        {
                            Player player = screenManager.gameScreen.FindPlayerInMap(message.ReadInt());
                            if (player != null)
                            {
                                player.name = message.ReadUTF();
                            }
                            break;
                        }
                    case MessageName.UPDATE_CLAN_PART:
                        {
                            int playerId = message.ReadInt();
                            if (playerId == Player.me.id)
                            {
                                Player.me.bag = FrameManager.instance.GetBag(message.ReadShort());
                            }
                            else
                            {
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    player.bag = FrameManager.instance.GetBag(message.ReadShort());
                                }
                            }
                            break;
                        }
                    case -31:
                        {
                            // open url
                            Application.OpenURL(message.ReadUTF());
                            break;
                        }
                    case -30:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                screenManager.gameScreen.mapDragon = message.ReadShort();
                                screenManager.gameScreen.areaDragon = message.ReadShort();
                                int x = message.ReadShort();
                                int y = message.ReadShort();
                                screenManager.gameScreen.iconsDragon.Clear();
                                int size = message.ReadSByte();
                                for (int i = 0; i < size; i++)
                                {
                                    screenManager.gameScreen.iconsDragon.Add(message.ReadShort());
                                }
                                screenManager.gameScreen.dragonballs.Clear();
                                size = message.ReadSByte();
                                for (int i = 0; i < size; i++)
                                {
                                    screenManager.gameScreen.dragonballs.Add(message.ReadShort());
                                }
                                screenManager.gameScreen.StartCallDragon(x, y);
                            }
                            else if (type == 1)
                            {
                                screenManager.gameScreen.CloseDragon();
                            }
                            break;
                        }
                    case -29:
                        {
                            InfoDlg.Hide();
                            screenManager.panel.cmdPlayerMiniGames.Clear();
                            int count_player = message.ReadShort();
                            for (int i = 0; i < count_player; i++)
                            {
                                int playerId = message.ReadInt();
                                CmdPlayerMiniGame player = new CmdPlayerMiniGame(screenManager.panel, 75, playerId);
                                player.id = i;
                                player.player = new Player();
                                player.player.id = playerId;
                                player.player.name = message.ReadUTF();
                                player.player.gender = message.ReadSByte();
                                player.isOnline = message.ReadBool();
                                player.description = message.ReadUTF();
                                screenManager.panel.cmdPlayerMiniGames.Add(player);
                            }
                            screenManager.panel.SetType(TabPanel.tabMiniGame.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case MessageName.SET_POSITION_PLAYER:
                        {
                            int playerId = message.ReadInt();
                            if (Player.me.id == playerId)
                            {
                                Player.me.x = message.ReadShort();
                                Player.me.y = message.ReadShort();
                                Player.isChangingMap = false;
                                Player.isLockKey = false;
                                InfoDlg.Hide();
                            }
                            else
                            {
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    player.x = message.ReadShort();
                                    player.y = message.ReadShort();
                                }
                            }
                            break;
                        }
                    case -27:
                        {
                            int type = message.ReadSByte();
                            if (type == -1)
                            {
                                screenManager.panel.Close();
                            }
                            else
                            {
                                try
                                {
                                    if (type == 2)
                                    {
                                        TabPanel.tabUpgrade.name = message.ReadUTF();
                                        screenManager.panel.cmdUpgrade.caption = message.ReadUTF();
                                        screenManager.panel.typeUpgrade = message.ReadSByte();
                                        screenManager.panel.infosUpgrade.Clear();
                                        int size = message.ReadSByte();
                                        for (int i = 0; i < size; i++)
                                        {
                                            screenManager.panel.infosUpgrade.Add(message.ReadUTF());
                                        }
                                    }
                                }
                                catch
                                {
                                }
                                screenManager.panel.SetType(type);
                                screenManager.panel.Show();
                            }
                            break;
                        }
                    case -26:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                bool flag = message.ReadBool();
                                if (!flag)
                                {
                                    Player.disciple = null;
                                    break;
                                }
                                Player.disciple = new Player();
                                Player.disciple.id = message.ReadInt();
                                Player.disciple.name = message.ReadUTF();
                                Player.disciple.gender = message.ReadSByte();
                            }
                            else if (type == 1)
                            {
                                Player.disciple.baseDamage = message.ReadInt();
                                Player.disciple.baseHp = message.ReadInt();
                                Player.disciple.baseMp = message.ReadInt();
                                Player.disciple.baseConstitution = message.ReadInt();
                                Player.disciple.potentialUpDamage = message.ReadLong();
                                Player.disciple.potentialUpHp = message.ReadLong();
                                Player.disciple.potentialUpMp = message.ReadLong();
                                Player.disciple.potentialUpConstitution = message.ReadLong();
                            }
                            else if (type == 2)
                            {
                                Player.disciple.skills = new List<Skill>();
                                int count_skill = message.ReadSByte();
                                for (int i = 0; i < count_skill; i++)
                                {
                                    Skill skill = new Skill();
                                    skill.template = new SkillTemplate();
                                    skill.template.id = message.ReadSByte();
                                    int num = message.ReadSByte();
                                    for (int j = 0; j < num; j++)
                                    {
                                        skill.template.name.Add(message.ReadUTF());
                                    }
                                    num = message.ReadSByte();
                                    for (int j = 0; j < num; j++)
                                    {
                                        skill.template.description.Add(message.ReadUTF());
                                    }
                                    skill.template.type = message.ReadSByte();
                                    skill.template.isProactive = message.ReadBool();
                                    skill.template.iconId = new int[message.ReadSByte()];
                                    for (int j = 0; j < skill.template.iconId.Length; j++)
                                    {
                                        skill.template.iconId[j] = message.ReadShort();
                                    }
                                    skill.template.dx = new int[message.ReadSByte()][];
                                    for (int j = 0; j < skill.template.dx.Length; j++)
                                    {
                                        skill.template.dx[j] = new int[message.ReadSByte()];
                                        for (int k = 0; k < skill.template.dx[j].Length; k++)
                                        {
                                            skill.template.dx[j][k] = message.ReadShort();
                                        }
                                    }
                                    skill.template.dy = new int[message.ReadSByte()][];
                                    for (int j = 0; j < skill.template.dy.Length; j++)
                                    {
                                        skill.template.dy[j] = new int[message.ReadSByte()];
                                        for (int k = 0; k < skill.template.dy[j].Length; k++)
                                        {
                                            skill.template.dy[j][k] = message.ReadShort();
                                        }
                                    }
                                    skill.template.levelRequire = message.ReadShort();
                                    skill.template.maxLevel = message.ReadSByte();
                                    skill.template.maxUpgrade = message.ReadSByte();
                                    skill.template.pointUpgrade = new int[message.ReadSByte()];
                                    for (int j = 0; j < skill.template.pointUpgrade.Length; j++)
                                    {
                                        skill.template.pointUpgrade[j] = message.ReadInt();
                                    }
                                    skill.template.coolDown = new int[message.ReadSByte()][];
                                    for (int j = 0; j < skill.template.coolDown.Length; j++)
                                    {
                                        skill.template.coolDown[j] = new int[message.ReadSByte()];
                                        for (int k = 0; k < skill.template.coolDown[j].Length; k++)
                                        {
                                            skill.template.coolDown[j][k] = message.ReadInt();
                                        }
                                    }
                                    skill.template.typeMana = message.ReadSByte();
                                    skill.template.manaUse = new int[message.ReadSByte()][];
                                    for (int j = 0; j < skill.template.manaUse.Length; j++)
                                    {
                                        skill.template.manaUse[j] = new int[message.ReadSByte()];
                                        for (int k = 0; k < skill.template.manaUse[j].Length; k++)
                                        {
                                            skill.template.manaUse[j][k] = message.ReadInt();
                                        }
                                    }
                                    num = message.ReadSByte();
                                    long now = Utils.CurrentTimeMillis();
                                    for (int j = 0; j < num; j++)
                                    {
                                        SkillOption option = new SkillOption();
                                        option.template = new SkillOptionTemplate();
                                        option.template.id = message.ReadSByte();
                                        option.template.name = message.ReadUTF();
                                        int num_param = message.ReadSByte();
                                        for (int k = 0; k < num_param; k++)
                                        {
                                            option.paramNormal.Add(message.ReadShort());
                                        }
                                        num_param = message.ReadSByte();
                                        for (int k = 0; k < num_param; k++)
                                        {
                                            option.paramUpgrade.Add(message.ReadShort());
                                        }
                                        skill.template.options.Add(option);
                                    }
                                    skill.level = message.ReadSByte();
                                    skill.upgrade = message.ReadSByte();
                                    skill.point = message.ReadInt();
                                    skill.coolDownReduction = message.ReadSByte();
                                    if (skill.level > 0 && skill.template.isProactive)
                                    {
                                        skill.timeCanUse = message.ReadLong();
                                    }
                                    Player.disciple.skills.Add(skill);
                                }
                            }
                            else if (type == 3)
                            {
                                Player.disciple.power = message.ReadLong();
                                Player.disciple.potential = message.ReadLong();
                                Player.disciple.level = message.ReadShort();
                            }
                            else if (type == 4)
                            {
                                Player.disciple.maxHp = message.ReadLong();
                                Player.disciple.maxMp = message.ReadLong();
                                Player.disciple.hp = message.ReadLong();
                                Player.disciple.mp = message.ReadLong();
                                Player.disciple.speed = message.ReadSByte();
                                Player.disciple.dodge = message.ReadUTF();
                                Player.disciple.critical = message.ReadUTF();
                                Player.disciple.reduceDamage = message.ReadUTF();
                                Player.disciple.bloodsucking = message.ReadUTF();
                                Player.disciple.manaSucking = message.ReadUTF();
                                Player.disciple.strikeBack = message.ReadUTF();
                                //Player.disciple.accurate = message.ReadUTF();//mxh chính xác
                                //Player.disciple.critDamage = message.ReadUTF();//mxh chính xác
                                //Player.disciple.ignoreCrit = message.ReadUTF();//mxh chính xác
                                //Player.disciple.pierceArmor = message.ReadUTF();//mxh chính xác
                                Player.disciple.maxDamage = message.ReadLong();
                            }
                            else if (type == 5)
                            {
                                Player.disciple.itemsBody.Clear();
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.disciple.itemsBody.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.disciple.itemsBody.Add(item);
                                    }
                                }
                            }
                            else if (type == 6)
                            {
                                Player.disciple.head = FrameManager.instance.GetFrame(message.ReadShort());
                                Player.disciple.body = FrameManager.instance.GetFrame(message.ReadShort());
                                Player.disciple.mount = FrameManager.instance.GetMount(message.ReadShort());
                                Player.disciple.bag = FrameManager.instance.GetBag(message.ReadShort());
                                Player.disciple.medal = message.ReadShort();
                                Player.disciple.aura = message.ReadShort();
                            }
                            else if (type == 7)
                            {
                                Player.disciple.statusDisciple = message.ReadUTF();
                            }
                            else if (type == 8)
                            {
                                Player.disciple.name = message.ReadUTF();
                            }
                            else if (type == 9)
                            {
                                Player.disciple.stamina = message.ReadShort();
                                Player.disciple.maxStamina = message.ReadShort();
                            }
                            else if (type == 10)
                            {
                                Player.disciple.itemsOther.Clear();
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.disciple.itemsOther.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.disciple.itemsOther.Add(item);
                                    }
                                }
                            }
                            break;
                        }
                    case -25:
                        {
                            int playerId = message.ReadInt();
                            int type = message.ReadSByte();
                            if (Player.me.id == playerId)
                            {
                                Player.me.SetFusion(type);
                            }
                            else
                            {
                                Player player = screenManager.gameScreen.FindPlayerInMap(playerId);
                                if (player != null)
                                {
                                    player.SetFusion(type);
                                }
                            }
                            break;
                        }
                    case -24:
                        {
                            InfoDlg.Hide();
                            screenManager.panel.cmdRewards.Clear();
                            int size = message.ReadShort();
                            for (int i = 0; i < size; i++)
                            {
                                CmdReward cmd = new CmdReward(screenManager.panel, 80, i);
                                cmd.index = i + 1;
                                cmd.name = message.ReadUTF();
                                cmd.description = message.ReadUTF();
                                int count = message.ReadSByte();
                                for (int j = 0; j < count; j++)
                                {
                                    cmd.gifts.Add(new Item(message));
                                    cmd.items.Add(new ItemPanel(screenManager.panel, 81, new int[] { i, j }));
                                }
                                screenManager.panel.cmdRewards.Add(cmd);
                            }
                            screenManager.panel.SetType(TabPanel.tabReward.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case -23:
                        {
                            screenManager.panel.gifts.Clear();
                            screenManager.panel.giftType = message.ReadSByte();
                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                CmdGift gift = new CmdGift(screenManager.panel, 83, i);
                                gift.index = i + 1;
                                gift.name = message.ReadUTF();
                                gift.description = message.ReadUTF();
                                gift.type = message.ReadSByte();
                                int size = message.ReadSByte();
                                for (int j = 0; j < size; j++)
                                {
                                    Item item = new Item(message);
                                    gift.gifts.Add(item);
                                    gift.items.Add(new ItemPanel(screenManager.panel, 92, new int[] { i, j }));
                                }
                                screenManager.panel.gifts.Add(gift);
                            }
                            screenManager.panel.SetType(TabPanel.tabGift.type);
                            screenManager.panel.Show();
                            break;
                        }

                    case -22:
                        {
                            // request icon
                            int iconId = message.ReadShort();
                            int length = message.ReadInt();
                            sbyte[] data = new sbyte[length];
                            message.reader().Read(ref data);
                            if (data != null)
                            {
                                if (GraphicManager.instance.datas.ContainsKey(iconId))
                                {
                                    GraphicManager.instance.datas.Remove(iconId);
                                }
                                GraphicManager.instance.datas.Add(iconId, data);
                                Rms.SaveString("icon_" + GraphicManager.instance.versionImage + "_" + iconId,
                                    GraphicManager.instance.StringToHex(GraphicManager.instance.Encrypt(Convert.ToBase64String(Utils.Cast(data)), GraphicManager.instance.versionImage + "" + GraphicManager.instance.versionImage)));
                            }
                            break;
                        }

                    case -21:
                        {
                            // bật tắt show cải trang
                            InfoDlg.Hide();
                            screenManager.gameScreen.isHideMark = message.ReadBool();
                            break;
                        }

                    case -20:
                        {
                            InfoDlg.Hide();
                            screenManager.gameScreen.isSaveMapAutoPlay = message.ReadBool();
                            break;
                        }

                    case -18:
                        {
                            screenManager.gameScreen.effects.Add(new EffectFly(message.ReadShort(), message.ReadShort(), message.ReadShort(), message.ReadShort(), message.ReadShort()));
                            break;
                        }

                    case -17:
                        {
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null && monster is MonsterPet)
                            {
                                MonsterPet pet = (MonsterPet)monster;
                                pet.Move(message.ReadShort(), message.ReadShort(), message.ReadSByte());
                            }
                            break;
                        }

                    case -16:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                if (!message.ReadBool())
                                {
                                    Player.pet = null;
                                    break;
                                }
                                Player.pet = new MonsterPet(message.ReadShort());
                                Player.pet.id = message.ReadInt();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    ItemOption option = new ItemOption();
                                    option.template = ItemManager.instance.itemOptionTemplates[message.ReadShort()];
                                    option.param = message.ReadInt();
                                    Player.pet.options.Add(option);
                                }
                            }
                            else if (type == 1)
                            {
                                Player.pet.itemsBody.Clear();
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.pet.itemsBody.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.pet.itemsBody.Add(item);
                                    }
                                }
                            }
                            else if (type == 2)
                            {
                                Player.pet.maxStamina = message.ReadShort();
                                Player.pet.stamina = message.ReadShort();
                            }
                            else if (type == 3)
                            {
                                Player.pet.maxHp = message.ReadLong();
                                Player.pet.hp = message.ReadLong();
                                Player.pet.damage = message.ReadLong();
                            }
                            else if (type == 4)
                            {
                                Player.pet.exp = message.ReadInt();
                                Player.pet.maxExp = message.ReadInt();
                            }
                            break;
                        }

                    case -15:
                        {
                            Monster monster = screenManager.gameScreen.FindMonsterInMap(message.ReadInt());
                            if (monster != null)
                            {
                                monster.maxHp = message.ReadLong();
                                monster.hp = message.ReadLong();
                                if (Player.pet != null && Player.pet.id == monster.id)
                                {
                                    Player.pet.maxHp = monster.maxHp;
                                    Player.pet.hp = monster.hp;
                                }
                            }
                            break;
                        }

                    case -14:
                        {
                            int id = message.ReadShort();
                            long time = message.ReadLong();
                            string text = "";
                            if (time != 0)
                            {
                                text = message.ReadUTF();
                            }
                            MessageTime messageTime = GameScreen.GetMessageTimeById(id);
                            if (messageTime != null)
                            {
                                if (time > 0 || time == -1)
                                {
                                    messageTime.setInfo(text, time);
                                }
                                else
                                {
                                    GameScreen.messageTimes.Remove(messageTime);
                                }
                            }
                            else
                            {
                                messageTime = new MessageTime(id);
                                messageTime.setInfo(text, time);
                                GameScreen.messageTimes.Add(messageTime);
                            }
                            return;
                        }

                    case -13:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                screenManager.panel.intrinsics.Clear();
                                int count = message.ReadSByte();
                                for (int i = 0; i < count; i++)
                                {
                                    CmdIntrinsic intrinsic = new CmdIntrinsic(screenManager.panel, 91, i);
                                    intrinsic.templateId = message.ReadSByte();
                                    intrinsic.iconId = message.ReadShort();
                                    intrinsic.name = message.ReadUTF();
                                    intrinsic.description = message.ReadUTF();
                                    intrinsic.param = message.ReadShort();
                                    intrinsic._object = intrinsic.templateId;
                                    screenManager.panel.intrinsics.Add(intrinsic);
                                }
                                if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabIntrinsic.type)
                                {
                                    screenManager.panel.SetTabIntrinsic();
                                }
                            }
                            else if (type == 1)
                            {
                                int templateId = message.ReadSByte();
                                List<CmdIntrinsic> intrinsics = screenManager.panel.intrinsics;
                                for (int i = 0; i < intrinsics.Count; i++)
                                {
                                    if (intrinsics[i].templateId == templateId)
                                    {
                                        intrinsics[i].param = message.ReadShort();
                                        break;
                                    }
                                }
                            }
                            break;
                        }

                    case -12:
                        {
                            Panel panel = screenManager.panel;
                            int pickMeID = message.ReadInt();
                            if (pickMeID == -1)
                            {
                                panel.SetType(33);
                                panel.Show();
                            }
                            else
                            {
                                long endTimePickMe = message.ReadLong();
                                int status = message.ReadSByte();
                                if (status == 0 || status == 1)
                                {
                                    panel.randomsPickMe.Clear();
                                    panel.winnerNamePickMe = message.ReadUTF();
                                    panel.winnerCoinPickMe = message.ReadUTF();
                                    panel.winnerCoinJoinedPickMe = message.ReadUTF();
                                    panel.totalPickMe = message.ReadLong();
                                    panel.countPlayerPickMe = message.ReadShort();
                                    panel.coinJoinPickMe = message.ReadLong();
                                    panel.cmyPickMe = 0;
                                    panel.speedPickMe = 0;
                                    panel.indexPickMe = 0;
                                    panel.resultPickMe = "";
                                    panel.statusPickMe = status;
                                }
                                else if (status == 2)
                                {
                                    if (panel.pickMeID != pickMeID)
                                    {
                                        panel.cmyPickMe = 0;
                                        panel.speedPickMe = 12;
                                        panel.indexPickMe = 0;
                                        panel.resultPickMe = "";
                                    }
                                    int size = message.ReadShort();
                                    List<string> texts = new List<string>();
                                    for (int i = 0; i < size; i++)
                                    {
                                        texts.Add(message.ReadUTF());
                                    }
                                    while (texts.Count < 30)
                                    {
                                        texts.Add(texts[Utils.r.Next(texts.Count)]);
                                    }
                                    panel.randomsPickMe = texts;
                                    panel.statusPickMe = status;
                                }
                                else if (status == 3)
                                {
                                    panel.resultPickMe = message.ReadUTF();
                                    if (panel.pickMeID != pickMeID || panel.statusPickMe == 0 || panel.statusPickMe == 1)
                                    {
                                        panel.statusPickMe = status;
                                    }
                                }
                                panel.pickMeID = pickMeID;
                                panel.endTimePickMe = endTimePickMe;
                            }
                            break;
                        }

                    case -10:
                        {
                            int action = message.ReadSByte();
                            if (action == 0)
                            {
                                screenManager.panel.requireLucky = message.ReadUTF();
                                screenManager.panel.SetType(TabPanel.tabLucky.type);
                                screenManager.panel.Show();
                            }
                            else if (action == 1)
                            {
                                screenManager.panel.itemsLucky.Clear();
                                List<Item> items = new List<Item>();
                                int size = message.ReadSByte();
                                for (int i = 0; i < size; i++)
                                {
                                    items.Add(new Item(message));
                                }
                                if (screenManager.panel.indexLucky == -1)
                                {
                                    screenManager.panel.indexLucky = Utils.r.Next(25);//lucky box hoang
                                }
                                if (screenManager.panel.indexLucky != 0)
                                {
                                    Item item = items[screenManager.panel.indexLucky];
                                    items[screenManager.panel.indexLucky] = items[0];
                                    items[0] = item;
                                }
                                screenManager.panel.itemsLucky.AddRange(items);
                                screenManager.panel.SetType(TabPanel.tabLucky.type);
                                screenManager.panel.Show();
                            }
                            break;
                        }

                    default:
                        Debug.Log(message.id.ToString());
                        break;
                }
            }
            catch (Exception e)
            {
                Debug.Log(e.ToString());
            }
        }

        public void MessageInfoPlayer(Message message)
        {
            try
            {
                sbyte subCmd = message.ReadSByte();
                switch (subCmd)
                {
                    case 0:
                        {
                            screenManager.gameScreen.TimeEndRemaining = DateTime.UtcNow;
                            screenManager.gameScreen.TimeRemaining = 0;
                            Player.isLogin = true;
                            Map.mapTemplates.Clear();
                            Player.me.id = message.ReadInt();
                            Player.me.name = message.ReadUTF();
                            Player.me.gender = message.ReadSByte();
                            Player.me.power = message.ReadLong();
                            Player.me.potential = message.ReadLong();
                            Player.me.level = message.ReadShort();
                            Player.me.pointSkill = message.ReadShort();
                            Player.me.head = FrameManager.instance.GetFrame(message.ReadShort());
                            Player.me.body = FrameManager.instance.GetFrame(message.ReadShort());
                            Player.me.mount = FrameManager.instance.GetMount(message.ReadShort());
                            Player.me.bag = FrameManager.instance.GetBag(message.ReadShort());
                            Player.me.medal = message.ReadShort();
                            if (serverVersion >= 91)
                            {
                                Player.me.aura = message.ReadShort();
                            }
                            //Player.getWidth(Player.me);
                            //Player.getHeight(Player.me);
                            Player.me.baseDamage = message.ReadInt();
                            Player.me.baseHp = message.ReadInt();
                            Player.me.baseMp = message.ReadInt();
                            Player.me.baseConstitution = message.ReadInt();
                            Player.me.potentialUpDamage = message.ReadLong();
                            Player.me.potentialUpHp = message.ReadLong();
                            Player.me.potentialUpMp = message.ReadLong();
                            Player.me.potentialUpConstitution = message.ReadLong();
                            Player.me.maxHp = message.ReadLong();
                            Player.me.maxMp = message.ReadLong();
                            screenManager.gameScreen.hp = Player.me.hp = message.ReadLong();
                            screenManager.gameScreen.mp = Player.me.mp = message.ReadLong();
                            Player.me.speed = message.ReadSByte();
                            Player.me.pointPk = message.ReadSByte();
                            Player.me.pointActivity = message.ReadShort();
                            Player.me.countBarrack = message.ReadSByte();
                            Player.me.dodge = message.ReadUTF();
                            Player.me.critical = message.ReadUTF();
                            Player.me.reduceDamage = message.ReadUTF();
                            Player.me.bloodsucking = message.ReadUTF();
                            Player.me.manaSucking = message.ReadUTF();
                            Player.me.strikeBack = message.ReadUTF();
                            //Player.me.accurate = message.ReadUTF();//mxh chính xác
                            //Player.me.critDamage = message.ReadUTF();//mxh chính xác
                            //Player.me.ignoreCrit = message.ReadUTF();//mxh chính xác
                            //Player.me.pierceArmor = message.ReadUTF();//mxh chính xác
                            Player.me.maxDamage = message.ReadLong();
                            Player.me.coin = message.ReadLong();
                            Player.me.coinLock = message.ReadLong();
                            Player.me.diamond = message.ReadInt();
                            Player.me.ruby = message.ReadInt();
                            Player.me.spaceship = message.ReadSByte();
                            Player.me.skills = new List<Skill>();
                            int count_skill = message.ReadSByte();
                            for (int i = 0; i < count_skill; i++)
                            {
                                Skill skill = new Skill();
                                skill.template = new SkillTemplate();
                                skill.template.id = message.ReadSByte();
                                int num = message.ReadSByte();
                                for (int j = 0; j < num; j++)
                                {
                                    skill.template.name.Add(message.ReadUTF());
                                }
                                num = message.ReadSByte();
                                for (int j = 0; j < num; j++)
                                {
                                    skill.template.description.Add(message.ReadUTF());
                                }
                                skill.template.type = message.ReadSByte();
                                skill.template.isProactive = message.ReadBool();
                                skill.template.iconId = new int[message.ReadSByte()];
                                for (int j = 0; j < skill.template.iconId.Length; j++)
                                {
                                    skill.template.iconId[j] = message.ReadShort();
                                }
                                skill.template.dx = new int[message.ReadSByte()][];
                                for (int j = 0; j < skill.template.dx.Length; j++)
                                {
                                    skill.template.dx[j] = new int[message.ReadSByte()];
                                    for (int k = 0; k < skill.template.dx[j].Length; k++)
                                    {
                                        skill.template.dx[j][k] = message.ReadShort();
                                    }
                                }
                                skill.template.dy = new int[message.ReadSByte()][];
                                for (int j = 0; j < skill.template.dy.Length; j++)
                                {
                                    skill.template.dy[j] = new int[message.ReadSByte()];
                                    for (int k = 0; k < skill.template.dy[j].Length; k++)
                                    {
                                        skill.template.dy[j][k] = message.ReadShort();
                                    }
                                }
                                skill.template.levelRequire = message.ReadShort();
                                skill.template.maxLevel = message.ReadSByte();
                                skill.template.maxUpgrade = message.ReadSByte();
                                skill.template.pointUpgrade = new int[message.ReadSByte()];
                                for (int j = 0; j < skill.template.pointUpgrade.Length; j++)
                                {
                                    skill.template.pointUpgrade[j] = message.ReadInt();
                                }
                                skill.template.coolDown = new int[message.ReadSByte()][];
                                for (int j = 0; j < skill.template.coolDown.Length; j++)
                                {
                                    skill.template.coolDown[j] = new int[message.ReadSByte()];
                                    for (int k = 0; k < skill.template.coolDown[j].Length; k++)
                                    {
                                        skill.template.coolDown[j][k] = message.ReadInt();
                                    }
                                }
                                skill.template.typeMana = message.ReadSByte();
                                skill.template.manaUse = new int[message.ReadSByte()][];
                                for (int j = 0; j < skill.template.manaUse.Length; j++)
                                {
                                    skill.template.manaUse[j] = new int[message.ReadSByte()];
                                    for (int k = 0; k < skill.template.manaUse[j].Length; k++)
                                    {
                                        skill.template.manaUse[j][k] = message.ReadInt();
                                    }
                                }
                                num = message.ReadSByte();
                                long now = Utils.CurrentTimeMillis();
                                for (int j = 0; j < num; j++)
                                {
                                    SkillOption option = new SkillOption();
                                    option.template = new SkillOptionTemplate();
                                    option.template.id = message.ReadSByte();
                                    option.template.name = message.ReadUTF();
                                    int num_param = message.ReadSByte();
                                    for (int k = 0; k < num_param; k++)
                                    {
                                        option.paramNormal.Add(message.ReadShort());
                                    }
                                    num_param = message.ReadSByte();
                                    for (int k = 0; k < num_param; k++)
                                    {
                                        option.paramUpgrade.Add(message.ReadShort());
                                    }
                                    skill.template.options.Add(option);
                                }
                                skill.level = message.ReadSByte();
                                skill.upgrade = message.ReadSByte();
                                skill.point = message.ReadInt();
                                skill.coolDownReduction = message.ReadSByte();
                                if (skill.level > 0 && skill.template.isProactive)
                                {
                                    skill.timeCanUse = message.ReadLong();
                                }
                                int count_paint = message.ReadSByte();
                                double total = 0;
                                for (int j = 0; j < count_paint; j++)
                                {
                                    double percent = double.Parse(message.ReadUTF());
                                    skill.paints.Add(percent - total, message.ReadShort());
                                    total = percent;
                                }
                                Player.me.skills.Add(skill);
                            }

                            int count_key_skill = message.ReadSByte();
                            for (int i = 0; i < count_key_skill; i++)
                            {
                                int id = message.ReadSByte();
                                if (id != -1)
                                {
                                    screenManager.gameScreen.keySkills[i].skill = Player.me.GetSkillByTemplateId(id);
                                }
                                else
                                {
                                    screenManager.gameScreen.keySkills[i].skill = null;
                                }
                            }
                            if (count_key_skill < screenManager.gameScreen.keySkills.Length)
                            {
                                for (int i = count_key_skill; i < screenManager.gameScreen.keySkills.Length; i++)
                                {
                                    screenManager.gameScreen.keySkills[i].skill = null;
                                }
                            }
                            Player.me.mySkill = Player.me.GetSkillByTemplateId(message.ReadSByte());
                            screenManager.gameScreen.isCanAutoPlay = false;
                            int count_effect = message.ReadSByte();
                            for (int i = 0; i < count_effect; i++)
                            {
                                EffectTime effect = new EffectTime(Player.me, message.ReadShort(), message.ReadLong());
                                if (effect.template.id == 14)
                                {
                                    screenManager.gameScreen.isCanAutoPlay = true;
                                }
                                Player.me.effects.Add(effect);
                            }
                            ScreenManager.instance.gameScreen.isLockAction = false;
                            ScreenManager.instance.gameScreen.isHideMark = false;
                            //SkillManager.instance.CreateImage();
                            Player.isLogin = false;
                            break;
                        }
                    case 1:
                        {
                            long power_up = message.ReadLong();
                            Player.me.power += power_up;
                            Player.me.potential += power_up;
                            screenManager.gameScreen.StartFlyText(MyFont.text_fly_green, "+" + Utils.GetMoneys(power_up), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            break;
                        }
                    case 2:
                        {
                            Player.me.hp = message.ReadLong();
                            Player.me.mp = message.ReadLong();
                            break;
                        }
                    case 3:
                        {
                            Player.me.isCharge = false;
                            break;
                        }
                    case 4:
                        {
                            Task task = new Task();
                            task.id = message.ReadSByte();
                            task.name = message.ReadUTF();
                            task.index = message.ReadSByte();
                            task.param = message.ReadInt();

                            int count_sub = message.ReadSByte();

                            for (int i = 0; i < count_sub; i++)
                            {
                                TaskSub taskSub = new TaskSub();
                                taskSub.name = message.ReadUTF();
                                taskSub.param = message.ReadInt();
                                taskSub.npcId = message.ReadSByte();
                                taskSub.description = message.ReadUTF();
                                //taskSub.mapId = message.ReadInt();
                                //taskSub.xy = message.ReadUTF();
                                task.subTasks.Add(taskSub);
                            }

                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                task.items.Add(new Item(message));
                            }
                            Player.me.task = task;
                            screenManager.gameScreen.completeTask();
                            Debug.LogError("TASK");
                            break;
                        }
                    case 5:
                        {
                            Player.me.task.param = 0;
                            Player.me.task.index++;
                            screenManager.gameScreen.completeTask();
                            break;
                        }
                    case 6:
                        {
                            Player.me.task.param++;
                            break;
                        }
                    case 7:
                        Player.me.potential = message.ReadLong();
                        int indexUp = message.ReadSByte();
                        if (indexUp == 0)
                        {
                            Player.me.baseDamage = message.ReadInt();
                            Player.me.potentialUpDamage = message.ReadLong();
                        }
                        if (indexUp == 1)
                        {
                            Player.me.baseHp = message.ReadInt();
                            Player.me.potentialUpHp = message.ReadLong();
                        }
                        if (indexUp == 2)
                        {
                            Player.me.baseMp = message.ReadInt();
                            Player.me.potentialUpMp = message.ReadLong();
                        }
                        if (indexUp == 3)
                        {
                            Player.me.baseConstitution = message.ReadInt();
                            Player.me.potentialUpConstitution = message.ReadLong();
                        }
                        if (screenManager.panel.isShow && screenManager.panel.type == 1 && screenManager.panel.tabInfo.isShow)
                        {
                            screenManager.panel.ViewPotential(Player.me, indexUp);
                        }
                        break;
                    case 8:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.pointSkill = message.ReadShort();
                                Skill skill = Player.me.GetSkillByTemplateId(message.ReadSByte());
                                if (skill != null)
                                {
                                    skill.level = message.ReadSByte();
                                    if (screenManager.panel.isShow && screenManager.panel.type == 1 && screenManager.panel.tabInfo.isShow)
                                    {
                                        screenManager.panel.ViewSkill(skill);
                                    }
                                }
                            }
                            else if (type == 1)
                            {
                                Player.me.pointSkill = message.ReadShort();
                            }
                            else if (type == 2)
                            {
                                Skill skill = Player.me.GetSkillByTemplateId(message.ReadSByte());
                                if (skill != null)
                                {
                                    skill.point = message.ReadInt();
                                }
                            }
                            else if (type == 3)
                            {
                                Skill skill = Player.me.GetSkillByTemplateId(message.ReadSByte());
                                if (skill != null)
                                {
                                    skill.upgrade = message.ReadSByte();
                                    skill.point = message.ReadInt();
                                }
                            }
                            break;
                        }
                    case 9:
                        Player.me.level++;
                        Player.me.pointSkill++;
                        screenManager.gameScreen.levelup();
                        break;
                    case 10:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.itemsBody.Clear();
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.me.itemsBody.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.me.itemsBody.Add(item);
                                    }
                                }
                            }
                            else if (type == 1)
                            {
                                int index = message.ReadSByte();
                                int itemId = message.ReadShort();
                                if (itemId == -1)
                                {
                                    Player.me.itemsBody[index] = null;
                                }
                                else
                                {
                                    Item item = new Item(itemId, message);
                                    item.indexUI = index;
                                    Player.me.itemsBody[index] = item;
                                }
                            }
                            break;
                        }

                    case 11:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.itemsBag.Clear();
                                int count_i_bag = message.ReadSByte();
                                for (int i = 0; i < count_i_bag; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.me.itemsBag.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.me.itemsBag.Add(item);
                                    }
                                }
                            }
                            else if (type == 1)
                            {
                                int index = message.ReadSByte();
                                int itemId = message.ReadShort();
                                if (itemId == -1)
                                {
                                    Player.me.itemsBag[index] = null;
                                }
                                else
                                {
                                    Item item = new Item(itemId, message);
                                    item.indexUI = index;
                                    Player.me.itemsBag[index] = item;
                                }
                            }
                            else if (type == 2)
                            {
                                int index = message.ReadSByte();
                                Player.me.itemsBag[index].quantity = message.ReadInt();
                            }
                            break;
                        }

                    case 12:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.itemsBox.Clear();
                                int count_i_box = message.ReadSByte();
                                for (int i = 0; i < count_i_box; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.me.itemsBox.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.me.itemsBox.Add(item);
                                    }
                                }
                            }
                            break;
                        }

                    case 13:
                        {
                            int effectId = message.ReadShort();
                            for (int i = 0; i < Player.me.effects.Count; i++)
                            {
                                Effect effect = Player.me.effects[i];
                                if (effect is EffectTime)
                                {
                                    EffectTime effectTime = (EffectTime)effect;
                                    if (effectTime.template.id == effectId)
                                    {
                                        Player.me.effects.RemoveAt(i);
                                        break;
                                    }
                                }
                            }
                            break;
                        }

                    case 14:
                        {
                            int skillId = message.ReadSByte();
                            screenManager.gameScreen.keySkills[message.ReadSByte()].skill = Player.me.GetSkillByTemplateId(skillId);
                            break;
                        }
                    case 15:
                        {
                            Player.me.head = FrameManager.instance.GetFrame(message.ReadShort());
                            Player.me.body = FrameManager.instance.GetFrame(message.ReadShort());
                            Player.me.mount = FrameManager.instance.GetMount(message.ReadShort());
                            Player.me.bag = FrameManager.instance.GetBag(message.ReadShort());
                            Player.me.medal = message.ReadShort();
                            Player.me.aura = message.ReadShort();
                            Player.me.InitBody();
                            break;
                        }
                    case 16:
                        {
                            Player.me.x = message.ReadShort();
                            Player.me.y = message.ReadShort();
                            Player.isChangingMap = false;
                            Player.isLockKey = false;
                            InfoDlg.Hide();
                            break;
                        }
                    case 17:
                        {
                            Player.me.itemsBag[message.ReadSByte()].quantity = message.ReadInt();
                            break;
                        }
                    case 18:
                        {
                            int index = message.ReadSByte();
                            int itemId = message.ReadShort();
                            if (itemId == -1)
                            {
                                Player.me.itemsBag[index] = null;
                            }
                            else
                            {
                                Item item = new Item(itemId, message);
                                item.indexUI = index;
                                Player.me.itemsBag[index] = item;
                            }
                            break;
                        }
                    case 19:
                        {
                            Player.me.itemsBox[message.ReadSByte()].quantity = message.ReadInt();
                            break;
                        }
                    case 20:
                        {
                            int index = message.ReadSByte();
                            int itemId = message.ReadShort();
                            if (itemId == -1)
                            {
                                Player.me.itemsBox[index] = null;
                            }
                            else
                            {
                                Item item = new Item(itemId, message);
                                item.indexUI = index;
                                Player.me.itemsBox[index] = item;
                            }
                            break;
                        }
                    case 21:
                        {
                            int index = message.ReadSByte();
                            int itemId = message.ReadShort();
                            if (itemId == -1)
                            {
                                Player.me.itemsBody[index] = null;
                            }
                            else
                            {
                                Item item = new Item(itemId, message);
                                item.indexUI = index;
                                Player.me.itemsBody[index] = item;
                            }
                            break;
                        }
                    case 22:
                        {
                            Player.me.maxHp = message.ReadLong();
                            Player.me.maxMp = message.ReadLong();
                            Player.me.hp = message.ReadLong();
                            Player.me.mp = message.ReadLong();
                            Player.me.speed = message.ReadSByte();
                            Player.me.dodge = message.ReadUTF();
                            Player.me.critical = message.ReadUTF();
                            Player.me.reduceDamage = message.ReadUTF();
                            Player.me.bloodsucking = message.ReadUTF();
                            Player.me.manaSucking = message.ReadUTF();
                            Player.me.strikeBack = message.ReadUTF();
                            //Player.me.accurate = message.ReadUTF();//mxh chính xác
                            //Player.me.critDamage = message.ReadUTF();//mxh chính xác
                            //Player.me.ignoreCrit = message.ReadUTF();//mxh chính xác
                            //Player.me.pierceArmor = message.ReadUTF();//mxh chính xác
                            Player.me.maxDamage = message.ReadLong();
                            break;
                        }
                    case 23:
                        {
                            Player.me.maxDamage = message.ReadLong();
                            break;
                        }
                    case 24:
                        {
                            Player.me.coin = message.ReadLong();
                            break;
                        }
                    case 25:
                        {
                            Player.me.diamond = message.ReadInt();
                            break;
                        }
                    case 26:
                        {
                            int current_index = message.ReadSByte();
                            int move_index = message.ReadSByte();
                            Player.me.itemsBag[move_index] = Player.me.itemsBag[current_index].Clone();
                            Player.me.itemsBag[current_index] = null;
                            Player.me.itemsBag[move_index].indexUI = move_index;
                            break;
                        }
                    case 27:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                screenManager.panel.friends.Clear();
                                int count_friend = message.ReadSByte();
                                for (int i = 0; i < count_friend; i++)
                                {
                                    CmdFriend friend = new CmdFriend(screenManager.panel, 33, i);
                                    friend.player = new Player();
                                    friend.player.id = message.ReadInt();
                                    friend.player.name = message.ReadUTF();
                                    friend.player.gender = message.ReadSByte();
                                    friend.player.power = message.ReadLong();
                                    friend.isOnline = message.ReadBool();
                                    screenManager.panel.friends.Add(friend);
                                }
                                screenManager.panel.SetType(TabPanel.tabFriend.type);
                                screenManager.panel.Show();
                            }
                            else if (type == 2)
                            {
                                int playerId = message.ReadInt();
                                for (int i = 0; i < screenManager.panel.friends.Count; i++)
                                {
                                    if (screenManager.panel.friends[i].player.id == playerId)
                                    {
                                        screenManager.panel.friends.RemoveAt(i);
                                        break;
                                    }
                                }
                                for (int i = 0; i < screenManager.panel.friends.Count; i++)
                                {
                                    screenManager.panel.friends[i]._object = i;
                                }
                                screenManager.panel.SetType(TabPanel.tabFriend.type);
                                screenManager.panel.Show();
                            }
                            break;
                        }
                    case 28:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                screenManager.panel.enemys.Clear();
                                int count_enemy = message.ReadSByte();
                                for (int i = 0; i < count_enemy; i++)
                                {
                                    CmdFriend enemy = new CmdFriend(screenManager.panel, 34, i);
                                    enemy.player = new Player();
                                    enemy.player.id = message.ReadInt();
                                    enemy.player.name = message.ReadUTF();
                                    enemy.player.gender = message.ReadSByte();
                                    enemy.player.power = message.ReadLong();
                                    enemy.isOnline = message.ReadBool();
                                    screenManager.panel.enemys.Add(enemy);
                                }
                                screenManager.panel.SetType(TabPanel.tabEnemy.type);
                                screenManager.panel.Show();
                            }
                            else if (type == 2)
                            {
                                int playerId = message.ReadInt();
                                for (int i = 0; i < screenManager.panel.enemys.Count; i++)
                                {
                                    if (screenManager.panel.enemys[i].player.id == playerId)
                                    {
                                        screenManager.panel.enemys.RemoveAt(i);
                                        break;
                                    }
                                }
                                for (int i = 0; i < screenManager.panel.enemys.Count; i++)
                                {
                                    screenManager.panel.enemys[i]._object = i;
                                }
                                screenManager.panel.SetType(TabPanel.tabEnemy.type);
                                screenManager.panel.Show();
                            }
                            break;
                        }
                    case 29:
                        {
                            Player.me.effects.Add(new EffectTime(Player.me, message.ReadShort(), message.ReadLong()));
                            break;
                        }
                    case 30:
                        {
                            Player.me.LiveFromDead();
                            Player.isLockKey = false;
                            Player.me.isLockMove = false;
                            Player.me.isDie = false;
                            Player.me.diamond--;
                            break;
                        }
                    case 31:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.team = new Team();
                                Player.me.team.status = message.ReadSByte();
                                int count_member = message.ReadSByte();
                                for (int i = 0; i < count_member; i++)
                                {
                                    CmdTeamMember member = new CmdTeamMember(screenManager.panel, 45, i);
                                    member.player = new Player();
                                    member.player.id = message.ReadInt();
                                    member.player.name = message.ReadUTF();
                                    member.player.power = message.ReadLong();
                                    member.player.gender = message.ReadSByte();
                                    member.description = message.ReadUTF();
                                    Player.me.team.members.Add(member);
                                }
                            }
                            else if (type == 1)
                            {
                                if (Player.me.team != null)
                                {
                                    CmdTeamMember member = new CmdTeamMember(screenManager.panel, 45, Player.me.team.members.Count);
                                    member.player = new Player();
                                    member.player.id = message.ReadInt();
                                    member.player.name = message.ReadUTF();
                                    member.player.power = message.ReadLong();
                                    member.player.gender = message.ReadSByte();
                                    member.description = message.ReadUTF();
                                    Player.me.team.members.Add(member);
                                }
                            }
                            else if (type == 2)
                            {
                                int playerId = message.ReadInt();
                                if (Player.me.id == playerId)
                                {
                                    Player.me.team = null;
                                }
                                else if (Player.me.team != null)
                                {
                                    for (int i = 0; i < Player.me.team.members.Count; i++)
                                    {
                                        if (Player.me.team.members[i].player.id == playerId)
                                        {
                                            Player.me.team.members.RemoveAt(i);
                                            break;
                                        }
                                    }
                                }
                            }
                            else if (type == 3)
                            {
                                if (Player.me.team != null)
                                {
                                    Player.me.team.status = message.ReadSByte();
                                    InfoMe.addInfo("Tổ đội đã được đổi sang trạng thái: " + Player.me.team.GetStrStatus(), 1);
                                }
                            }
                            else if (type == 6)
                            {
                                screenManager.panel.teams.Clear();
                                int count_team = message.ReadSByte();
                                for (int i = 0; i < count_team; i++)
                                {
                                    CmdTeam team = new CmdTeam(screenManager.panel, 41, i);
                                    team.teamId = message.ReadInt();
                                    team.player = new Player();
                                    team.player.id = message.ReadInt();
                                    team.player.name = message.ReadUTF();
                                    team.player.gender = message.ReadSByte();
                                    team.player.power = message.ReadLong();
                                    team.name = "Tổ đội " + team.player.name;
                                    team.isOnline = message.ReadBool();
                                    team.description = "Thành viên: " + message.ReadSByte() + "/6";
                                    screenManager.panel.teams.Add(team);
                                }
                            }
                            if (screenManager.panel.isShow && screenManager.panel.type == TabPanel.tabTeam.type)
                            {
                                screenManager.panel.SetTabTeam();
                            }
                            break;
                        }
                    case 32:
                        {
                            Player.me.typePk = message.ReadSByte();
                            break;
                        }
                    case 33:
                        {
                            Player.me.typeFlag = message.ReadSByte();
                            break;
                        }
                    case 34:
                        {
                            Player.me.pointPk = message.ReadSByte();
                            break;
                        }
                    case 35:
                        {
                            screenManager.panel.mapsSpaceship.Clear();
                            int count_map = message.ReadSByte();
                            for (int i = 0; i < count_map; i++)
                            {
                                CmdMap map = new CmdMap(screenManager.panel, 39, i);
                                map.index = i;
                                map.name = message.ReadUTF();
                                map.info = message.ReadUTF();
                                screenManager.panel.mapsSpaceship.Add(map);
                            }
                            screenManager.panel.SetType(TabPanel.tabMapSpaceship.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case 36:
                        {
                            screenManager.gameScreen.isCanAutoPlay = message.ReadBool();
                            InfoMe.addInfo("Đã " + (screenManager.gameScreen.isCanAutoPlay ? "bật" : "tắt") + " Tự động luyện tập", 1);
                            break;
                        }
                    case 37:
                        {
                            int type = message.ReadSByte();
                            if (type == -1)
                            {
                                screenManager.panel.Close();
                            }
                            else
                            {
                                try
                                {
                                    if (type == 2)
                                    {
                                        TabPanel.tabUpgrade.name = message.ReadUTF();
                                        screenManager.panel.cmdUpgrade.caption = message.ReadUTF();
                                        screenManager.panel.typeUpgrade = message.ReadSByte();
                                        screenManager.panel.infosUpgrade.Clear();
                                        int size = message.ReadSByte();
                                        for (int i = 0; i < size; i++)
                                        {
                                            screenManager.panel.infosUpgrade.Add(message.ReadUTF());
                                        }
                                    }
                                }
                                catch
                                {
                                }
                                screenManager.panel.SetType(type);
                                screenManager.panel.Show();
                            }
                            break;
                        }
                    case 38:
                        {
                            int current_index = message.ReadSByte();
                            int move_index = message.ReadSByte();
                            Player.me.itemsBox[move_index] = Player.me.itemsBox[current_index].Clone();
                            Player.me.itemsBox[current_index] = null;
                            Player.me.itemsBox[move_index].indexUI = move_index;
                            break;
                        }
                    case 39:
                        {
                            Player.me.potential = message.ReadLong();
                            Player.me.baseDamage = message.ReadInt();
                            Player.me.baseHp = message.ReadInt();
                            Player.me.baseMp = message.ReadInt();
                            Player.me.baseConstitution = message.ReadInt();
                            Player.me.potentialUpDamage = message.ReadLong();
                            Player.me.potentialUpHp = message.ReadLong();
                            Player.me.potentialUpMp = message.ReadLong();
                            Player.me.potentialUpConstitution = message.ReadLong();
                            InfoMe.addInfo("Điểm tiềm năng đã được đặt lại.", 1);
                            break;
                        }

                    case 40:
                        {
                            Player.me.pointSkill = message.ReadShort();
                            for (int i = 0; i < Player.me.skills.Count; i++)
                            {
                                Player.me.skills[i].level = 0;
                            }
                            Player.me.skills[0].level = 1;
                            InfoMe.addInfo("Điểm kỹ năng đã được đặt lại.", 1);
                            break;
                        }
                    case 41:
                        {
                            long hp = message.ReadLong();
                            long mp = message.ReadLong();
                            int dy = -10;
                            if (hp > 0)
                            {
                                dy -= 10;
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "+" + Utils.GetMoneys(hp), Player.me.x, Player.me.y - Player.me.h + dy, 0, -2);
                            }
                            if (mp > 0)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_blue, "+" + Utils.GetMoneys(mp), Player.me.x, Player.me.y - Player.me.h + dy, 0, -2);
                            }
                            break;
                        }
                    case 42:
                        {
                            for (int i = 0; i < 8; i++)
                            {
                                Player.me.itemsBag.Add(null);
                            }
                            screenManager.panel.Close();
                            break;
                        }
                    case 43:
                        {
                            for (int i = 0; i < 8; i++)
                            {
                                Player.me.itemsBox.Add(null);
                            }
                            screenManager.panel.Close();
                            break;
                        }
                    case 44:
                        {
                            if (Player.me.taskDaily != null)
                            {
                                Player.me.taskDaily.param = message.ReadInt();
                            }
                            break;
                        }
                    case 45:
                        {
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.taskDaily = null;
                            }
                            else
                            {
                                Player.me.taskDaily = new TaskDaily();
                                Player.me.taskDaily.name = message.ReadUTF();
                                Player.me.taskDaily.description = message.ReadUTF();
                                Player.me.taskDaily.maxParam = message.ReadInt();
                                Player.me.taskDaily.param = message.ReadInt();
                            }
                            break;
                        }
                    case 46:
                        {
                            screenManager.panel.achievements.Clear();
                            int count = message.ReadSByte();
                            for (int i = 0; i < count; i++)
                            {
                                CmdAchievement achivement = new CmdAchievement(screenManager.panel, 40, i);
                                achivement.index = i + 1;
                                achivement.name = message.ReadUTF();
                                achivement.description = message.ReadUTF();
                                achivement.ruby = message.ReadInt();
                                achivement.maxParam = message.ReadInt();
                                achivement.param = message.ReadInt();
                                achivement.isReceive = message.ReadBool();
                                screenManager.panel.achievements.Add(achivement);
                            }
                            screenManager.panel.SetType(TabPanel.tabAchievement.type);
                            screenManager.panel.Show();
                            break;
                        }
                    case 47:
                        {
                            Player.me.pointActivity = message.ReadShort();
                            break;
                        }
                    case 48:
                        {
                            Player.me.countBarrack = message.ReadSByte();
                            break;
                        }
                    case 49:
                        {
                            screenManager.panel.pointShop = message.ReadInt();
                            break;
                        }
                    case 50:
                        {
                            for (int i = 0; i < Player.me.skills.Count; i++)
                            {
                                Skill skill = Player.me.skills[i];
                                if (skill.level > 0)
                                {
                                    skill.timeCanUse = 0;
                                }
                            }
                            break;
                        }
                    case 51:
                        {
                            long preHp = Player.me.hp;
                            Player.me.hp = message.ReadLong();
                            if (Player.me.hp > preHp)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "+" + Utils.GetMoneys(Player.me.hp - preHp), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            }
                            else if (Player.me.hp < preHp)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_red, "-" + Utils.GetMoneys(preHp - Player.me.hp), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            }
                            break;
                        }
                    case 52:
                        {
                            long preMp = Player.me.mp;
                            Player.me.mp = message.ReadLong();

                            if (Player.me.mp > preMp)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_blue, "+" + Utils.GetMoneys(Player.me.mp - preMp), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            }
                            else if (Player.me.mp < preMp)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_blue, "-" + Utils.GetMoneys(preMp - Player.me.mp), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            }
                            break;
                        }

                    case 53:
                        {
                            int effectId = message.ReadShort();
                            for (int i = 0; i < Player.me.effects.Count; i++)
                            {
                                Effect effect = Player.me.effects[i];
                                if (effect is EffectTime)
                                {
                                    EffectTime effectTime = (EffectTime)effect;
                                    if (effectTime.template.id == effectId)
                                    {
                                        effectTime.time = message.ReadLong();
                                        effectTime.endTime = DateTime.UtcNow.AddMilliseconds(effectTime.time);
                                        break;
                                    }
                                }
                            }
                            break;
                        }

                    case 54:
                        {
                            int size = message.ReadSByte();
                            screenManager.panel.isLightBag = new bool[size];
                            for (int i = 0; i < size; i++)
                            {
                                screenManager.panel.isLightBag[i] = message.ReadBool();
                            }
                            break;
                        }

                    case 55:
                        {
                            if (Player.me.skills != null)
                            {
                                int coolDownReduction = message.ReadSByte();
                                for (int i = 0; i < Player.me.skills.Count; i++)
                                {
                                    Player.me.skills[i].coolDownReduction = coolDownReduction;
                                }
                            }
                            break;
                        }

                    case 56:
                        {
                            int skillId = message.ReadSByte();
                            int coolDownReduction = message.ReadSByte();
                            for (int i = 0; i < Player.me.skills.Count; i++)
                            {
                                if (Player.me.skills[i].template.id == skillId)
                                {
                                    Player.me.skills[i].coolDownReduction = coolDownReduction;
                                }
                            }
                            break;
                        }

                    case 57:
                        {
                            int skillId = message.ReadSByte();
                            long time = message.ReadLong();
                            for (int i = 0; i < Player.me.skills.Count; i++)
                            {
                                if (Player.me.skills[i].template.id == skillId)
                                {
                                    Player.me.skills[i].timeCanUse = time;
                                }
                            }
                            break;
                        }

                    case 58:
                        {
                            int skillId = message.ReadSByte();
                            for (int i = 0; i < Player.me.skills.Count; i++)
                            {
                                if (Player.me.skills[i].template.id == skillId)
                                {
                                    Player.me.skills[i].coolDownIntrinsic = message.ReadShort();
                                    break;
                                }
                            }
                            break;
                        }

                    case 59:
                        {
                            InfoDlg.Hide();
                            int type = message.ReadSByte();
                            if (type == 0)
                            {
                                Player.me.itemsOther.Clear();
                                int count_i_body = message.ReadSByte();
                                for (int i = 0; i < count_i_body; i++)
                                {
                                    int itemId = message.ReadShort();
                                    if (itemId == -1)
                                    {
                                        Player.me.itemsOther.Add(null);
                                    }
                                    else
                                    {
                                        Item item = new Item(itemId, message);
                                        item.indexUI = i;
                                        Player.me.itemsOther.Add(item);
                                    }
                                }
                            }
                            else if (type == 1)
                            {
                                int index = message.ReadSByte();
                                int itemId = message.ReadShort();
                                if (itemId == -1)
                                {
                                    Player.me.itemsOther[index] = null;
                                }
                                else
                                {
                                    Item item = new Item(itemId, message);
                                    item.indexUI = index;
                                    Player.me.itemsOther[index] = item;
                                }
                            }
                            break;
                        }

                    case 60:
                        Player.me.coinLock = message.ReadLong();
                        break;

                    case 61:
                        Player.me.ruby = message.ReadInt();
                        break;

                    case 62:
                        {
                            long oldPotential = Player.me.potential;
                            long potentialAfter = message.ReadLong();

                            Player.me.potential = potentialAfter;

                            long gained = potentialAfter - oldPotential;
                            if (gained > 0)
                            {
                                screenManager.gameScreen.StartFlyText(MyFont.text_fly_green, "+" + Utils.GetMoneys(gained), Player.me.x, Player.me.y - Player.me.h, 0, -2);
                            }
                            break;
                        }
                }
            }
            catch (Exception e)
            {
                Debug.LogException(e);
            }
        }

        public static void MessageInfoClan(Message message)
        {
            try
            {
                sbyte subCmd = message.ReadSByte();
                switch (subCmd)
                {
                    case 0:
                        {
                            /*InfoDlg.Hide();
                            int clanId = message.ReadInt();
                            if (clanId == -1)
                            {
                                Player.me.clan = null;
                                InfoMe.AddInfo("Hiện tại bạn chưa có bang hội", 0);
                                break;
                            }
                            Clan clan = new Clan();
                            clan.SetId(clanId);
                            clan.SetName(message.ReadUTF());
                            clan.SetSlogan(message.ReadUTF());
                            clan.SetNotification(message.ReadUTF());
                            clan.SetImgId(message.ReadShort());
                            clan.SetLevel(message.ReadSByte());
                            clan.SetExp(message.ReadLong());
                            clan.SetCoin(message.ReadLong());
                            clan.SetDiamond(message.ReadInt());
                            clan.SetDiamondFees(message.ReadInt());
                            clan.SetCoinUpgrade(message.ReadInt());
                            clan.SetMaxMember(message.ReadShort());
                            clan.SetMaxExp(message.ReadLong());
                            clan.SetCreateTime(message.ReadUTF());
                            clan.roleId = message.ReadSByte();
                            int count_member = message.ReadSByte();
                            for (int i = 0; i < count_member; i++)
                            {
                                CmdClanMember member = new CmdClanMember(screenManager.panel, 68, i);
                                member.playerId = message.ReadInt();
                                member.roleId = message.ReadSByte();
                                member.name = message.ReadUTF();
                                member.gender = message.ReadSByte();
                                member.classId = message.ReadSByte();
                                member.sex = message.ReadSByte();
                                member.power = message.ReadLong();
                                member.point = message.ReadInt();
                                member.pointDay = message.ReadInt();
                                member.isOnline = message.ReadBool();
                                member.joinTime = message.ReadUTF();
                                clan.members.Add(member);
                            }
                            int count_history = message.ReadSByte();
                            for (int i = 0; i < count_history; i++)
                            {
                                clan.histories.Add("- " + message.ReadUTF());
                            }
                            Player.me.clan = clan;
                            screenManager.panel.SetType(TabPanel.tabClanInfo.type);
                            screenManager.panel.Show();*/
                            break;
                        }
                    case 1:
                        {
                            if (Player.me.clan != null)
                            {
                                //Player.me.clan.coin = message.ReadLong();
                            }
                            break;
                        }
                    case 2:
                        {
                            if (Player.me.clan != null)
                            {
                                //Player.me.clan.diamond = message.ReadInt();
                            }
                            break;
                        }
                    case 3:
                        {
                            if (Player.me.clan != null)
                            {
                                Player.me.clan.slogan = message.ReadUTF();
                            }
                            break;
                        }
                    case 4:
                        {
                            if (Player.me.clan != null)
                            {
                                Player.me.clan.notification = message.ReadUTF();
                            }
                            break;
                        }
                    case 8:
                        {
                            int clanId = message.ReadInt();
                            if (clanId == -1)
                            {
                                Player.me.clan = null;
                            }
                            else
                            {
                                Clan clan = new Clan();
                                clan.SetId(clanId);
                                clan.SetName(message.ReadUTF());
                                clan.SetRoleId(message.ReadSByte());
                                Player.me.clan = clan;
                            }
                            break;
                        }
                }
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }
    }
}
