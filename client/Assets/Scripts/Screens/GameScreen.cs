using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Controllers;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites;
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
using Assets.Scripts.Services;
using Assets.Scripts.Skills;
using Assets.Scripts.Tasks;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class GameScreen : MyScreen, IChatable, IActionListener
    {
        public bool isShowEffectPower = true;

        public bool isSaveMapAutoPlay;

        public int cmx; // start X pixel

        public int cmy; // start Y pixel

        public int cmxTo; // start X pixel

        public int cmyTo; // start Y pixel

        public int cmdx;

        public int cmdy;

        public int cmvx;

        public int cmvy;

        public List<Monster> monsters;

        public List<Player> players;

        public List<Npc> npcs;

        public List<ItemMap> itemMaps;

        public List<FlyText> flyTexts;

        public List<Spaceship> spaceships;

        public List<Effect> effects = new List<Effect>();

        public ChatTextField chatTxtField;

        public ClientInput ClientInput;

        public static Image imgSelect;

        public Command cmdDie;

        public bool isPaintSkill;

        private Image imgBar;

        private Image imgBarHp;
        private Image imgBarFocus;
        private Image imgBarFocusName;

        private Image imgBarMp;

        public int tMenuDelay;

        public Command cmdChat;

        //public Command cmdMenu;

        public Command cmdGift;

        public CmdAuto cmdAuto;

        public Command cmdChangeFocus;

        public CmdBean cmdBean;

        public Command cmdAttack;

        public Command cmdSpecialSkill;

        public Command cmdQues;

        public Command cmdArea;

        public Command cmdCapsule;

        public Command cmdBag;

        public Command cmdClan;

        public Command cmdMess;

        public Command cmdOption;

        public Command cmdLogout;

        public Command cmdTask;

        public GamePad gamePad;

        public Command cmdMarket;

        public Command cmdTop;

        public Command cmdLucky;

        public Command cmdButtonTienIch;//hoang 

        public static Image[] imgGender;

        public static Image[] imgClassMini;

        public int lockTick;

        public static string[] strFlags = new string[] { "Tắt", "Đồ sát", "Cờ Đỏ", "Cờ Xanh", "Cờ Vàng", "Cờ Lục" };

        public static int hpPoint;

        public static long lastTimeUsePean;

        public int auto;

        public bool isAutoPlay;

        public bool isCanAutoPlay;

        public long lastTimeFire;

        public TextGlobal textGlobal;

        public bool isUseFreez;

        public int dem;

        public long hp;

        public long mp;

        public List<Item> itemSpins = new List<Item>();

        public bool isPaintItemSpin;

        public int xPaintItemSpin;

        public int yPaintItemSpin;

        public int wPaintItemSpin;

        public int hPaintItemSpin;

        public int cmxPaintItemSpin;

        public int cmxToPaintItemSpin;

        public long lastTimeDelaySpin;

        public bool isAutoFindMob;

        public bool isLockAction;

        public bool isHideMark;

        public long timeSolo;

        public DateTime timeEndSolo;

        public long TimeRemaining;

        public DateTime TimeEndRemaining;

        public bool isAutoPick;

        public CmdSkill[] keySkills;

        public static Image imgLineSkill;

        private Image imgTeamBoder;

        private int xTeam;

        private int yTeam;

        private Image imgBgrTask;

        private Image imgDoubleArrow;

        private Image imgDoubleArrowMini;

        private Image imgHeadMonster;

        private Image[] imgGenderTeam;

        private Image imgHpParty;

        private Image imgHpPartyBgr;

        public static Image imgLight;

        public int lastPointerReleasedX;

        public int lastPointerReleasedY;

        public long lastTimePointerReleased;

        public static string[] flyTextString;

        public static int[] flyTextX;

        public static int[] flyTextY;

        public static int[] flyTextYTo;

        public static int[] flyTextDx;

        public static int[] flyTextDy;

        public static int[] flyTextState;

        public static MyFont[] flyTextColor;

        public static int[] flyTime;

        private bool isShowTaskOrther;

        public bool isAttackTinhAnh;

        public bool isAttackThuLinh;

        public bool isShowSpin = true;

        public static bool isShowPaintTeam;

        public static int xTeamPaint;

        public static int yTeamPaint;

        public static int xTeamPaintShow;

        public static int yTeamPaintShow;

        public static int xMinTeamPaint;

        public static int yMinTeamPaint;

        public static int xMaxTeamPaint;

        public static int yMaxTeamPaint;

        public static int dirTeamPaint;

        public static List<BallDragon> ballDragons;

        public sbyte combineSuccess = -1;

        public int idNPC;

        public int xS;

        public int yS;

        private int rS;

        private int angleS;

        private int angleO;

        private int iAngleS;

        private int iDotS;

        private int speed;

        private int[] xArgS;

        private int[] yArgS;

        private int[] xDotS;

        private int[] yDotS;

        private int time;

        private int typeCombine;

        private int countUpdate;

        private int countR;

        private int countWait;

        private bool isSpeedCombine;

        private bool isCompleteEffCombine = true;

        public bool isPaintCombine;

        public bool isDoneCombine = true;

        public bool isShowDragon;

        public int indexShowDragon;

        public long lastTimeIndexShowDragon;

        public List<int> iconsDragon = new List<int>();

        public List<int> dragonballs = new List<int>();

        public int mapDragon = -1;

        public int areaDragon = -1;

        private int xChatVip;

        private bool startChat;

        private int currChatWidth;

        public static List<string> chatvips = new List<string>();

        public bool isAutoLogin;

        private int xTaskBgr = 32;//hoang task
        private int yTaskBgr = 180;
        private int wTaskBgr;
        private int hTaskBgr;

        public static List<MessageTime> messageTimes = new List<MessageTime>();
        public bool isCollapsedMenu = true;
        public Command cmdToggleMenu;
        public GameScreen(ScreenManager screenManager) : base(screenManager)
        {
            cmdDie = new Command(PlayerText.DIES[0], this, 1, null);
            cmdDie.isShow = false;
            cmdDie.y = GameCanvas.h - cmdDie.h - 10;
            cmdDie.x = GameCanvas.w / 2 - cmdDie.w / 2;
            cmdBag = new Command("MainImages/GameScrs/img_menu_player", "MainImages/GameScrs/img_menu_player_focus", "MainImages/GameScrs/img_menu_player_click", this, 999, null);// túi đồ
            cmdChat = new Command("MainImages/GameScrs/img_chat", "MainImages/GameScrs/img_chat_focus", "MainImages/GameScrs/img_chat_click", this, 18, null);//chat
            //cmdMenu = new Command("MainImages/GameScrs/img_menu", "MainImages/GameScrs/img_menu_focus", "MainImages/GameScrs/img_menu_click", this, 19, null);
            cmdArea = new Command("MainImages/GameScrs/img_area", "MainImages/GameScrs/img_area_focus", "MainImages/GameScrs/img_area_click", this, 38, null);//khu vực
            cmdGift = new Command("MainImages/GameScrs/img_gift", "MainImages/GameScrs/img_gift_focus", "MainImages/GameScrs/img_gift_click", this, 39, null);// hộp quà
            cmdAuto = new CmdAuto(this, 42, null);
            cmdCapsule = new Command("MainImages/GameScrs/img_capsule", "MainImages/GameScrs/img_capsule_focus", "MainImages/GameScrs/img_capsule_click", this, 41, null);

            cmdClan = new Command("MainImages/GameScrs/img_clan", "MainImages/GameScrs/img_clan_focus", "MainImages/GameScrs/img_clan_click", this, 45, null);// clan
            cmdMess = new Command("MainImages/GameScrs/img_mess", "MainImages/GameScrs/img_mess_focus", "MainImages/GameScrs/img_mess_click", this, 46, null);// tin nhắn
            cmdOption = new CmdChangeFocus("MainImages/GameScrs/img_option", "MainImages/GameScrs/img_option_focus", "MainImages/GameScrs/img_option_click", this, 47, null);// tính năng
            cmdLogout = new CmdChangeFocus("MainImages/GameScrs/img_logout", "MainImages/GameScrs/img_logout_focus", "MainImages/GameScrs/img_logout_click", this, 48, -1);// thoát
            cmdTask = new CmdChangeFocus("MainImages/GameScrs/img_task", "MainImages/GameScrs/img_task_focus", "MainImages/GameScrs/img_task_click", this, 49, -1);// nhiệm vụ
            cmdChangeFocus = new CmdChangeFocus("MainImages/GameScrs/img_change_focus", "MainImages/GameScrs/img_change_focus_focus", "MainImages/GameScrs/img_change_focus_click", this, 20, null);
            cmdBean = new CmdBean(this, 21, null);
            cmdAttack = new Command("MainImages/GameScrs/img_attack", "MainImages/GameScrs/img_attack_focus", "MainImages/GameScrs/img_attack_click", this, 23, null);
            cmdLucky = new Command("MainImages/GameScrs/img_lucky", "MainImages/GameScrs/img_lucky_focus", "MainImages/GameScrs/img_lucky_click", this, 51, null);// vòng quay
            cmdMarket = new Command("MainImages/GameScrs/img_market", "MainImages/GameScrs/img_market_focus", "MainImages/GameScrs/img_market_click", this, 52, null);// chợ
            cmdTop = new Command("MainImages/GameScrs/img_top", "MainImages/GameScrs/img_top_focus", "MainImages/GameScrs/img_top_click", this, 53, null);// bxh
            cmdQues = new Command("MainImages/GameScrs/img_menu_player", "MainImages/GameScrs/img_menu_player_focus", "MainImages/GameScrs/img_menu_player_click", this, 24, null);

            cmdButtonTienIch = new Command("MainImages/GameScrs/img_tienich", "MainImages/GameScrs/img_tienich_focus", "MainImages/GameScrs/img_tienich_click", this, 54, null);

            cmdQues.isShow = false;
            cmdQues.anchor = StaticObj.VCENTER_HCENTER;
            cmdQues.isFollowWithCamera = true;
            imgTeamBoder = GameCanvas.LoadImage("MainImages/GameScrs/img_team_boder");
            imgBgrTask = GameCanvas.LoadImage("MainImages/GameScrs/img_bgr_task");
            wTaskBgr = imgBgrTask.GetWidth();//hoang task
            hTaskBgr = imgBgrTask.GetHeight();//hoang task
            imgDoubleArrow = GameCanvas.LoadImage("MainImages/GameScrs/img_double_arrow");
            imgDoubleArrowMini = GameCanvas.LoadImage("MainImages/GameScrs/img_double_arrow_mini");
            imgHeadMonster = GameCanvas.LoadImage("MainImages/GameScrs/img_head_monster");
            imgHpParty = GameCanvas.LoadImage("MainImages/GameScrs/img_hp_party");
            imgHpPartyBgr = GameCanvas.LoadImage("MainImages/GameScrs/img_hp_party_bgr");
            imgLight = GameCanvas.LoadImage("MainImages/GameScrs/img_light");
            imgBarFocus = GameCanvas.LoadImage("MainImages/GameScrs/img_bar_focus");
            imgBarFocusName = GameCanvas.LoadImage("MainImages/GameScrs/img_bar_name_focus");
            gamePad = new GamePad();
            textGlobal = new TextGlobal();
            keySkills = new CmdSkill[10];
            for (int i = 0; i < keySkills.Length; i++)
            {
                keySkills[i] = new CmdSkill();
                keySkills[i].actionListener = this;
                keySkills[i].actionId = 37;
                keySkills[i]._object = i;
                //keySkills[i]
            }
            imgGenderTeam = new Image[4];
            for (int i = 0; i < imgGenderTeam.Length; i++)
            {
                imgGenderTeam[i] = GameCanvas.LoadImage("MainImages/GameScrs/img_gender_" + i);
            }
            imgBar = GameCanvas.LoadImage("MainImages/GameScrs/img_bar");
            imgBarHp = GameCanvas.LoadImage("MainImages/GameScrs/img_bar_hp");
            imgBarMp = GameCanvas.LoadImage("MainImages/GameScrs/img_bar_mp");
            flyTextX = new int[10];
            flyTextY = new int[10];
            flyTextDx = new int[10];
            flyTextDy = new int[10];
            flyTextState = new int[10];
            flyTextString = new string[10];
            flyTextYTo = new int[10];
            flyTime = new int[10];
            flyTextColor = new MyFont[10];
            for (int i = 0; i < flyTextState.Length; i++)
            {
                flyTextState[i] = -1;
            }
            InitButton();
            xTeamPaintShow = xTeamPaint = xMaxTeamPaint;
            yTeamPaintShow = yTeamPaint = yMaxTeamPaint;
            monsters = new List<Monster>();
            players = new List<Player>();
            npcs = new List<Npc>();
            itemMaps = new List<ItemMap>();
            spaceships = new List<Spaceship>();
            chatTxtField = new ChatTextField();
            chatTxtField.name = "Chat";
            chatTxtField.parentScreen = this;
            chatTxtField.textField.name = "nhập nội dung chat";
            imgLineSkill = GameCanvas.LoadImage("MainImages/GameScrs/img_line_skill");
            flyTexts = new List<FlyText>();
            isPaintSkill = true;
            isShowEffectPower = !(Rms.LoadInt("isShowEffectPower") == 0);
        }

        static GameScreen()
        {
            imgGender = new Image[4];
            for (int i = 0; i < imgGender.Length; i++)
            {
                imgGender[i] = GameCanvas.LoadImage("img_gender_" + i);
            }
            imgSelect = GameCanvas.LoadImage("imgFocus.png");
        }
        public void InitButton()
        {
            cmdAttack.x = GameCanvas.w - cmdAttack.w - 20;
            cmdAttack.y = GameCanvas.h - cmdAttack.h - 20;
            bool hide = isCollapsedMenu;
            cmdGift.isShow = !hide;
            cmdArea.isShow = !hide;
            cmdClan.isShow = !hide;
            cmdTask.isShow = !hide;
            cmdMarket.isShow = !hide;
            cmdBag.isShow = true;
            cmdLogout.isShow = !hide;
            cmdOption.isShow = !hide;
            cmdLucky.isShow = !hide;
            cmdMess.isShow = !hide;
            cmdChat.isShow = !hide;
            cmdTop.isShow = !hide;

            if (!hide)
            {
                // vị trí khi mở menu
                cmdBag.x = GameCanvas.w - cmdChat.w - 20;
                cmdBag.y = 20;

                cmdArea.x = cmdBag.x - cmdArea.w - 10;
                cmdArea.y = cmdBag.y;

                cmdClan.x = cmdArea.x - cmdClan.w - 10;
                cmdClan.y = cmdBag.y;

                cmdTask.x = cmdClan.x - cmdTask.w - 10;
                cmdTask.y = cmdBag.y;

                cmdMarket.x = cmdTask.x - cmdMarket.w - 10;
                cmdMarket.y = cmdBag.y;

                cmdGift.x = cmdMarket.x - cmdGift.w - 10;
                cmdGift.y = cmdBag.y;

                cmdLogout.x = cmdBag.x;
                cmdLogout.y = cmdBag.y + cmdBag.h + 10;

                cmdOption.x = cmdLogout.x - cmdOption.w - 10;
                cmdOption.y = cmdLogout.y;

                cmdLucky.x = cmdOption.x - cmdLucky.w - 10;
                cmdLucky.y = cmdOption.y;

                cmdMess.x = cmdLucky.x - cmdMess.w - 10;
                cmdMess.y = cmdLucky.y;

                cmdChat.x = cmdMess.x - cmdChat.w - 10;
                cmdChat.y = cmdLucky.y;

                cmdTop.x = cmdChat.x - cmdTop.w - 10;
                cmdTop.y = cmdLucky.y;
            }
            else
            {
                // vị trí khi thu gọn
                cmdBag.x = GameCanvas.w - cmdBag.w - 20;
                cmdBag.y = 20;
            }


            // ===== VỊ TRÍ CÁC NÚT KHÔNG ẨN =====
            cmdAuto.x = cmdBean.x - 7 - cmdAuto.w;
            cmdAuto.y = cmdBean.y + cmdBean.h - cmdAuto.h;

            int dis = 10;
            int wSkill = CmdSkill.imgBgr.w;

            int tamX = GameCanvas.w - 90 - 20;
            int tamY = GameCanvas.h - 90 - 20;
            int distance = 160;
            double goc = 33.0;

            keySkills[3].x = tamX - (int)(distance * Math.Cos(45.0 * Math.PI / 180.0));
            keySkills[3].y = tamY - (int)(distance * Math.Sin(45.0 * Math.PI / 180.0));

            keySkills[2].x = tamX - (int)(distance * Math.Cos((45.0 - goc) * Math.PI / 180.0));
            keySkills[2].y = tamY - (int)(distance * Math.Sin((45.0 - goc) * Math.PI / 180.0));

            keySkills[1].x = tamX - (int)(distance * Math.Cos((45.0 - 2 * goc) * Math.PI / 180.0));
            keySkills[1].y = tamY - (int)(distance * Math.Sin((45.0 - 2 * goc) * Math.PI / 180.0));

            keySkills[4].x = tamX - (int)(distance * Math.Cos((45.0 + goc) * Math.PI / 180.0));
            keySkills[4].y = tamY - (int)(distance * Math.Sin((45.0 + goc) * Math.PI / 180.0));

            keySkills[5].x = tamX - (int)(distance * Math.Cos((45.0 + 2 * goc) * Math.PI / 180.0));
            keySkills[5].y = tamY - (int)(distance * Math.Sin((45.0 + 2 * goc) * Math.PI / 180.0));

            distance += 100;
            goc = 20;

            keySkills[6].x = tamX - (int)(distance * Math.Cos((45.0 - 2 * goc) * Math.PI / 180.0));
            keySkills[6].y = tamY - (int)(distance * Math.Sin((45.0 - 2 * goc) * Math.PI / 180.0));

            keySkills[7].x = tamX - (int)(distance * Math.Cos((45.0 - goc) * Math.PI / 180.0));
            keySkills[7].y = tamY - (int)(distance * Math.Sin((45.0 - goc) * Math.PI / 180.0));

            keySkills[8].x = tamX - (int)(distance * Math.Cos(45.0 * Math.PI / 180.0));
            keySkills[8].y = tamY - (int)(distance * Math.Sin(45.0 * Math.PI / 180.0));

            keySkills[9].x = tamX - (int)(distance * Math.Cos((45.0 + goc) * Math.PI / 180.0));
            keySkills[9].y = tamY - (int)(distance * Math.Sin((45.0 + goc) * Math.PI / 180.0));

            cmdChangeFocus.x = GameCanvas.w - dis - cmdChangeFocus.w;
            cmdChangeFocus.y = keySkills[5].y - keySkills[5].h / 2 - 15 - cmdChangeFocus.h + 5;

            cmdCapsule.x = cmdChangeFocus.x - 10 - cmdCapsule.w;
            cmdCapsule.y = cmdChangeFocus.y;

            cmdBean.x = keySkills[1].x - keySkills[1].w / 2 - cmdBean.w - 7;
            cmdBean.y = keySkills[1].y + keySkills[1].h / 2 - cmdBean.h;

            xMinTeamPaint = GameCanvas.w - 20;
            yMinTeamPaint = yTeam;

            xMaxTeamPaint = xTeam;
            yMaxTeamPaint = yTeam;

            cmdButtonTienIch.x = GameCanvas.w - dis - cmdButtonTienIch.w; // Đặt bên trái capsule
            cmdButtonTienIch.y = cmdChangeFocus.y - cmdChangeFocus.h - 10; // Cùng hàng với capsule

            cmdButtonTienIch.isShow = true;
        }
        public override void Paint(MyGraphics g)
        {

            GameCanvas.PaintBGGameScr(g);
            if (Player.me.isBlind || isUseFreez)
            {
                dem++;
                if ((dem < 30 && dem >= 0 && GameCanvas.gameTick % 4 == 0) || (dem >= 30 && dem <= 50 && GameCanvas.gameTick % 3 == 0) || dem > 50)
                {
                    g.SetColor(16777215);
                    g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
                    if (dem <= 50)
                    {
                        return;
                    }
                    if (isUseFreez)
                    {
                        isUseFreez = false;
                        dem = 0;
                    }
                    g.Translate(-cmx, -cmy);
                    Player.me.Paint(g);
                    foreach (FlyText flyText in flyTexts)
                    {
                        flyText.Paint(g);
                    }
                    g.Reset();

                    PaintCommands(g);
                    PaintBar(g);
                    PaintTeam(g);
                    paintHintWay(g);
                    textGlobal.Paint(g);
                    return;
                }
            }
            Map.paint(g);
            // Paint shadow
            g.Translate(-cmx, -cmy);
            foreach (Waypoint waypoint in Map.waypoints)
            {
                waypoint.paint(g);
            }
            for (int i = 0; i < monsters.Count; i++)
            {
                monsters[i].PaintShadow(g);
            }
            foreach (Npc npc in npcs)
            {
                if (npc.IsPaint())
                {
                    g.DrawImage(Map.bong, npc.x, npc.y - 3, 3);
                }
            }
            BackgroudEffect.paintBackAll(g);
            foreach (Player player in players)
            {
                player.PaintShadow(g);
            }
            Player.me.PaintShadow(g);
            foreach (Spaceship spaceship in spaceships)
            {
                spaceship.PaintHole(g);
            }
            // end Paint shadow

            // Paint object body
            foreach (Npc npc in npcs)
            {
                npc.Paint(g);
            }
            foreach (Monster mob in monsters)
            {
                if (mob is MonsterPet)
                {
                    continue;
                }
                if (mob.status == MonsterStatus.MOVE)
                {
                    mob.Paint(g);
                }
            }
            if (isShowDragon && Map.mapId == mapDragon && Map.areaId == areaDragon)
            {
                long now = Utils.CurrentTimeMillis();
                if (now - lastTimeIndexShowDragon > 100)
                {
                    lastTimeIndexShowDragon = now;
                    indexShowDragon++;
                    if (indexShowDragon >= iconsDragon.Count)
                    {
                        indexShowDragon = 0;
                    }
                }
                GraphicManager.instance.Draw(g, iconsDragon[indexShowDragon], xS, Map.GetYSd(xS) + 50, 0, StaticObj.BOTTOM_HCENTER);
            }
            foreach (Spaceship spaceship in spaceships)
            {
                spaceship.Paint(g);
            }
            foreach (Player player in players)
            {
                if (player.status != PlayerStatus.ACTION)
                {
                    player.Paint(g);
                }
            }
            if (Player.me.status != PlayerStatus.ACTION)
            {
                Player.me.Paint(g);
            }
            foreach (Monster mob in monsters)
            {
                if (mob is MonsterPet && mob.status == MonsterStatus.MOVE)
                {
                    mob.Paint(g);
                }
            }
            foreach (Player player in players)
            {
                player.chatInfo.Paint(g, player.x, player.y - player.h - 40, player.dir);
            }
            Player.me.chatInfo.Paint(g, Player.me.x, Player.me.y - Player.me.h - 40, Player.me.dir);

            // end Paint object body

            // Paint attack
            foreach (Monster mob in monsters)
            {
                if (mob.status != MonsterStatus.MOVE)
                {
                    mob.Paint(g);
                }
            }
            foreach (Player player in players)
            {
                if (player.status == PlayerStatus.ACTION)
                {
                    player.Paint(g);
                }
            }
            if (Player.me.status == PlayerStatus.ACTION)
            {
                Player.me.Paint(g);
            }
            foreach (ItemMap itemMap in itemMaps)
            {
                itemMap.Paint(g);
            }

            // end Paint attack

            // Paint dart
            for (int i = 0; i < MonsterManager.instance.darts.Count; i++)
            {
                MonsterManager.instance.darts[i].Paint(g);
            }
            foreach (Player player in players)
            {
                if (player.dart != null)
                {
                    player.dart.Paint(g);
                }
            }
            if (Player.me.dart != null)
            {
                Player.me.dart.Paint(g);
            }
            // end Paint dart
            BackgroudEffect.paintFrontAll(g);
            foreach (FlyText flyText in flyTexts)
            {
                flyText.Paint(g);
            }
            paintFlyText(g);
            g.Reset();
            MyFont.text_white.DrawString(g, Player.me.x + " - " + Player.me.y, GameCanvas.w / 2, 30, 2);
            if (Player.me.isFusion)
            {
                Player.me.tFusion++;
                if (GameCanvas.gameTick % 3 == 0)
                {
                    g.SetColor(16777215);
                    g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
                }
                if (Player.me.tFusion >= 50)
                {
                    Player.me.FusionComplete();
                }
            }
            foreach (Player player in players)
            {
                if (player.isFusion && player.IsPaint())
                {
                    player.tFusion++;
                    if (GameCanvas.gameTick % 3 == 0)
                    {
                        g.SetColor(16777215);
                        g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
                    }
                    if (player.tFusion >= 50)
                    {
                        player.FusionComplete();
                    }
                }
            }
            PaintCommands(g);
            if (isPaintSkill)
            {
                gamePad.paint(g);
            }
            PaintBar(g);
            PaintEffectTime(g);
            if (chatTxtField.isShow)
            {
                chatTxtField.Paint(g);
            }
            if (cmdDie.isShow)
            {
                cmdDie.Paint(g);
            }
            PaintTask(g);
            int numT = 180;
            if (timeSolo > 0)
            {
                timeSolo = (timeEndSolo.Ticks - DateTime.UtcNow.Ticks) / 10000000;
                MyFont.text_yellow.DrawString(g, "Thời gian thi đấu: " + FormatTime(timeSolo), 32, numT -= 30, 0, MyFont.text_grey);
            }
            if (TimeRemaining > 0)
            {
                TimeRemaining = (TimeEndRemaining.Ticks - DateTime.UtcNow.Ticks) / 10000000;
                MyFont.text_yellow.DrawString(g, "Thời gian còn lại: " + FormatTime(TimeRemaining), 32, numT -= 30, 0, MyFont.text_grey);
            }

            PaintTeam(g);
            paintHintWay(g);
            textGlobal.Paint(g);
            PaintItemSpin(g);
            PaintCallDragon(g);
            PaintChatVip(g);
            //if (cmdToggleMenu.isShow)
            //{
            //    cmdToggleMenu.Paint(g);
            //}
            //if (cmdButtonTienIch.isShow)
            //{
            //    cmdButtonTienIch.Paint(g);
            //}

        }

        private void PaintEffectTime(MyGraphics g)
        {
            int num = 0;
            int h = cmdBean.y + cmdBean.h;
            foreach (Effect effect in Player.me.effects)
            {
                if (effect is EffectTime)
                {
                    EffectTime effectTime = (EffectTime)effect;
                    if (effectTime.template.iconId != -1)
                    {
                        int per = num / 7;
                        effectTime.PaintTime(g, cmdAuto.x - 7 - (effectTime.w + 7) * num + per * (effectTime.w + 7) * 7, h - (effectTime.h + 7) * per);
                        num++;
                    }
                }
            }
        }

        /*private void PaintTask(MyGraphics g)
        {
            if (Player.me.task == null)
            {
                return;
            }
            g.DrawImage(imgBgrTask, 32, 180);
            g.DrawImage(imgDoubleArrow, 60, 190);
            if (isShowTaskOrther)
            {
                if (Player.me.taskDaily == null)
                {
                    isShowTaskOrther = false;
                    return;
                }
                MyFont.text_yellow.DrawString(g, "NHIỆM VỤ HÀNG NGÀY:", 115, 185, 0, MyFont.text_grey);
                string[] array = MyFont.text_mini_white.SplitFontArray(Player.me.taskDaily.ToString(), imgBgrTask.GetWidth() - 90);
                for (int i = 0; i < array.Length; i++)
                {
                    MyFont.text_mini_white.DrawString(g, array[i].Trim(), 115, 210 + 18 * i, 0, MyFont.text_mini_grey);
                }
                return;
            }
            MyFont.text_yellow.DrawString(g, "NHIỆM VỤ CHÍNH:", 115, 185, 0, MyFont.text_grey);
            string text = Player.me.task.subTasks[Player.me.task.index].name;
            if (Player.me.task.subTasks[Player.me.task.index].param > 0)
            {
                text += " [" + Player.me.task.param + "/" + Player.me.task.subTasks[Player.me.task.index].param + "]";
            }
            string[] vs = MyFont.text_mini_white.SplitFontArray(text, imgBgrTask.GetWidth() - 90);
            for (int i = 0; i < vs.Length; i++)
            {
                MyFont.text_mini_white.DrawString(g, vs[i].Trim(), 115, 210 + 18 * i, 0, MyFont.text_mini_grey);
            }
        }
*/
        private void PaintTask(MyGraphics g)//hoang task
        {
            if (Player.me.task == null)
            {
                return;
            }
            g.DrawImage(imgBgrTask, xTaskBgr, yTaskBgr);
            g.DrawImage(imgDoubleArrow, 60, 190);
            if (isShowTaskOrther)
            {
                if (Player.me.taskDaily == null)
                {
                    isShowTaskOrther = false;
                    return;
                }
                MyFont.text_yellow.DrawString(g, "NHIỆM VỤ HÀNG NGÀY:", 115, 185, 0, MyFont.text_grey);
                string[] array = MyFont.text_mini_white.SplitFontArray(Player.me.taskDaily.ToString(), wTaskBgr - 90);
                for (int i = 0; i < array.Length; i++)
                {
                    MyFont.text_mini_white.DrawString(g, array[i].Trim(), 115, 210 + 18 * i, 0, MyFont.text_mini_grey);
                }
                return;
            }
            MyFont.text_yellow.DrawString(g, "NHIỆM VỤ CHÍNH:", 115, 185, 0, MyFont.text_grey);
            string text = Player.me.task.subTasks[Player.me.task.index].name;
            if (Player.me.task.subTasks[Player.me.task.index].param > 0)
            {
                text += " [" + Player.me.task.param + "/" + Player.me.task.subTasks[Player.me.task.index].param + "]";
            }
            string[] vs = MyFont.text_mini_white.SplitFontArray(text, wTaskBgr - 90);
            for (int i = 0; i < vs.Length; i++)
            {
                MyFont.text_mini_white.DrawString(g, vs[i].Trim(), 115, 210 + 18 * i, 0, MyFont.text_mini_grey);
            }
        }

        private bool IsClickOnTaskFrame(int x, int y)//hoang task
        {
            if (Player.me.task == null)
            {
                return false;
            }

            return x >= xTaskBgr && x <= xTaskBgr + wTaskBgr &&
                   y >= yTaskBgr && y <= yTaskBgr + hTaskBgr;
        }

        /*private void ShowTaskInfo()
        {
            if (Player.me.task == null)
            {
                return;
            }

            List<CmdMenu> menus = new List<CmdMenu>();

            // Thông tin nhiệm vụ chính
            if (!isShowTaskOrther)
            {
                TaskSub currentTask = Player.me.task.subTasks[Player.me.task.index];

                string taskInfo = "=== NHIỆM VỤ CHÍNH ===\n";
                taskInfo += "Tên: " + currentTask.name + "\n";

                if (currentTask.param > 0)
                {
                    taskInfo += "Tiến độ: " + Player.me.task.param + "/" + currentTask.param + "\n";
                }

                if (currentTask.npcId != -1)
                {
                    Npc npc = FindNpcInMap(currentTask.npcId);
                    if (npc != null)
                    {
                        taskInfo += "NPC: " + npc.template.name + "\n";
                        taskInfo += "Vị trí: (" + npc.x + ", " + npc.y + ")";
                    }
                }

                menus.Add(new CmdMenu("Xem chi tiết", null, -1, null));
                menus.Add(new CmdMenu("Đi đến mục tiêu", this, 60, null));

                // Hiển thị dialog với thông tin
                GameCanvas.StartDialogOk(taskInfo);
            }
            else
            {
                // Thông tin nhiệm vụ hàng ngày
                if (Player.me.taskDaily != null)
                {
                    string taskInfo = "=== NHIỆM VỤ HÀNG NGÀY ===\n";
                    taskInfo += Player.me.taskDaily.ToString();

                    GameCanvas.StartDialogOk(taskInfo);
                }
            }
        }*/

        private void ShowTaskInfo()
        {
            if (Player.me.task == null)
            {
                return;
            }

            List<CmdMenu> menus = new List<CmdMenu>();

            if (!isShowTaskOrther)
            {
                TaskSub currentTask = Player.me.task.subTasks[Player.me.task.index];

                string taskTitle = "NHIỆM VỤ CHÍNH";

                menus.Add(new CmdMenu("Xem chi tiết", this, 61, null));

                if (currentTask.npcId != -1)
                {
                    Npc npc = FindNpcInMap(currentTask.npcId);
                    if (npc != null)
                    {
                        menus.Add(new CmdMenu("Đi đến mục tiêu", this, 60, null));
                    }
                    else
                    {
                        menus.Add(new CmdMenu("Đi đến mục tiêu\n(Không ở khu này)", this, 60, null));
                    }
                } else
                {
                    menus.Add(new CmdMenu("Dịch chuyển đến map", this, 60, null));
                }

                GameCanvas.StartAt(menus, taskTitle, 0);
            }
            else
            {
                if (Player.me.taskDaily != null)
                {
                    string taskTitle = "NHIỆM VỤ HÀNG NGÀY";

                    menus.Add(new CmdMenu("Xem chi tiết", this, 62, null));

                    GameCanvas.StartAt(menus, taskTitle, 0);
                }
            }
        }
        public string FormatTime(long second)
        {
            if (second > 3600)
            {
                return (second / 3600) + " giờ";
            }
            else if (second > 60)
            {
                return (second / 60) + " phút";
            }
            return second + " giây";
        }

        private void PaintTeam(MyGraphics g)
        {
            /*if (Player.me.team == null)
            {
                return;
            }
            g.DrawImage(imgTeamBoder, xTeamPaintShow, yTeamPaintShow);
            if (xTeamPaintShow == xMinTeamPaint && dirTeamPaint == 0)
            {
                dirTeamPaint = 2;
            }
            if (xTeamPaintShow == xMaxTeamPaint && dirTeamPaint == 2)
            {
                dirTeamPaint = 0;
            }
            if (dirTeamPaint == 0)
            {
                g.DrawImage(imgDoubleArrowMini, xTeamPaintShow + 4, yTeamPaintShow + (imgTeamBoder.GetHeight() - imgDoubleArrowMini.GetHeight()) / 2);
            }
            else
            {
                g.DrawImage(xTeamPaintShow + 4 - imgDoubleArrowMini.GetWidth(), yTeamPaintShow + (imgTeamBoder.GetHeight() - imgDoubleArrowMini.GetHeight()) / 2, imgDoubleArrowMini, 2, 0);
            }
            int x = xTeamPaintShow;
            int y = yTeamPaintShow;
            int w = imgTeamBoder.GetWidth();
            int w_hp_max = imgHpPartyBgr.GetWidth();
            int h_hp_max = imgHpPartyBgr.GetHeight();
            for (int i = 0; i < Player.me.team.members.Count; i++)
            {
                int yPaint = y + 20 + 30 * i;
                CmdTeamMember member = Player.me.team.members[i];
                g.DrawImage(imgGenderTeam[member.player.gender], x + 15, yPaint, 0);
                g.DrawImage(imgHpPartyBgr, x + w - w_hp_max - 15, yPaint + 1);
                Player player = FindPlayerInMap(member.player.id);
                MyFont myfont = MyFont.text_mini_yellow;
                if (player == null && member.player.id == Player.me.id)
                {
                    player = Player.me;
                }
                if (player != null)
                {
                    try
                    {
                        int percent = (int)(player.hpShow * w_hp_max / player.maxHp);
                        g.drawRegion(imgHpParty, 0, 0, percent, h_hp_max, 0, x + w - w_hp_max - 15, yPaint + 1, 0);
                    }
                    catch
                    {
                    }
                }
                else
                {
                    myfont = MyFont.text_mini_grey;
                    g.DrawImage(imgHpParty, x + w - w_hp_max - 15, yPaint + 1);
                }
                MyFont.text_yellow.DrawString(g, "lv" + Player.GetLevel(member.player.power), x + 50, yPaint, 0);
                myfont.DrawString(g, member.player.name, x + w - w_hp_max + 5, yPaint + 1, 0);
            }*/
        }

        public void paintHintWay(MyGraphics g)
        {
            if (Player.me.task == null)
            {
                return;
            }
            try
            {
                TaskSub taskSub = Player.me.task.subTasks[Player.me.task.index];
                if (taskSub.npcId != -1)
                {
                    Npc npc = FindNpcInMap(taskSub.npcId);
                    if (npc != null && !npc.IsPaint())
                    {
                        if (npc.x < Player.me.x)
                        {
                            GraphicManager.instance.Draw(g, 280, GameCanvas.gameTick % 30 < 15 ? 5 : 8, GameCanvas.h / 2, 2, StaticObj.BOTTOM_LEFT);
                        }
                        else if (npc.x > Player.me.x)
                        {
                            GraphicManager.instance.Draw(g, 280, GameCanvas.w - (GameCanvas.gameTick % 30 < 15 ? 5 : 8), GameCanvas.h / 2, 0, StaticObj.BOTTOM_RIGHT);
                        }
                    }
                }
            }
            catch
            {
            }
        }

        // public void PaintExpBar(MyGraphics g)
        // {
        //     if (startChat)
        //     {
        //         return;
        //     }
        //     int per = (int)((Player.me.power - GameCanvas.levels[Player.me.level].power) * GameCanvas.w / (GameCanvas.levels[Player.me.level + 1].power - GameCanvas.levels[Player.me.level].power));
        //     g.SetColor(6191872);
        //     g.FillRect(0, GameCanvas.h - 7, GameCanvas.w, 7);
        //     g.SetColor(6350080);
        //     g.FillRect(0, GameCanvas.h - 5, per, 5);
        //     g.SetColor(7020544);
        //     g.FillRect(0, GameCanvas.h - 7, GameCanvas.w, 1);
        //     g.FillRect(0, GameCanvas.h - 1, GameCanvas.w, 1);
        //     for (int i = 0; i < 10; i++)
        //     {
        //         g.FillRect(i * GameCanvas.w / 10 - 1, GameCanvas.h - 7, 1, 7);
        //     }
        // }
        public void PaintExpBar(MyGraphics g)
        {
            if (startChat)
            {
                return;
            }
            int per = (int)((Player.me.power - GameCanvas.levels[Player.me.level].power) * GameCanvas.w / (GameCanvas.levels[Player.me.level + 1].power - GameCanvas.levels[Player.me.level].power));
            g.SetColor(6191872);
            g.FillRect(0, GameCanvas.h - 7, GameCanvas.w, 7);
            g.SetColor(6350080);
            g.FillRect(0, GameCanvas.h - 5, per, 5);
            g.SetColor(7020544);
            g.FillRect(0, GameCanvas.h - 7, GameCanvas.w, 1);
            g.FillRect(0, GameCanvas.h - 1, GameCanvas.w, 1);
            for (int i = 0; i < 10; i++)
            {
                g.FillRect(i * GameCanvas.w / 10 - 1, GameCanvas.h - 7, 1, 7);
            }
            try
            {
                long currentExp = Player.me.power - GameCanvas.levels[Player.me.level].power;
                long maxExp = GameCanvas.levels[Player.me.level + 1].power - GameCanvas.levels[Player.me.level].power;
                int expPercent = (int)((float)currentExp / maxExp * 100);

                string levelText = "Lv." + Player.me.level;
                string expText = "EXP: " + expPercent + "%";

                int textX = 10;
                int textY = GameCanvas.h - 30;
                //int bgWidth = 120;
                //int bgHeight = 20;

                // g.SetColor(0, 0.6f);
                // g.FillRect(textX - 5, textY - 15, bgWidth, bgHeight, 5);

                MyFont.text_yellow.DrawString(g, levelText, textX, textY, 0);

                MyFont.text_white.DrawString(g, expText, textX + 55, textY, 0);
            }
            catch (Exception ex)
            {
                Debug.LogException(ex);
            }
        }


        private void PaintBar(MyGraphics g)
        {
            g.DrawImage(imgBar, 0, 0);
            if (Player.me.head != null && Player.me.head.template.hpBar != -1)
            {
                GraphicManager.instance.Draw(g, Player.me.head.template.hpBar, 57, 63);
            }
            try
            {
                if (hp > 0 && Player.me.maxHp > 0)
                {
                    int percentHP = (int)(hp * imgBarHp.GetWidth() / Player.me.maxHp);
                    g.drawRegion(imgBarHp, 0, 0, percentHP, imgBarHp.GetHeight(), 0, 99, 29, StaticObj.TOP_LEFT);
                }
            }
            catch
            {
            }
            MyFont.text_white.DrawString(g, Utils.GetMoneys(Player.me.hp), 120, 35, 0, MyFont.text_grey);
            //g.DrawImage(imgHp, 157, 27, StaticObj.TOP_LEFT);
            try
            {
                if (mp > 0 && Player.me.maxMp > 0)
                {
                    int percentMP = (int)(mp * imgBarMp.GetWidth() / Player.me.maxMp);
                    g.drawRegion(imgBarMp, 0, 0, percentMP, imgBarMp.GetHeight(), 0, 87, 73, StaticObj.TOP_LEFT);
                }
            }
            catch
            {
            }
            MyFont.text_white.DrawString(g, Utils.GetMoneys(Player.me.mp), 115, 75, 0, MyFont.text_grey);
            //g.DrawImage(imgMp, 120, 65, StaticObj.TOP_LEFT);
            if (Player.me.monsterFocus != null)
            {
                PaintBarFocus(g, Player.me.monsterFocus, 400, 0);
            }
            else if (Player.me.playerFocus != null)
            {
                PaintBarFocus(g, Player.me.playerFocus, 400, 0);
            }
            int num = 260;
            for (int i = 0; i < messageTimes.Count; i++)
            {
                messageTimes[i].Paint(g, 32, num);
                num += 30;
            }
        }

        private void PaintBarFocus(MyGraphics g, Entity entity, int x, int y)
        {
            g.DrawImage(imgBarFocus, x, y);
            if (entity is Monster)
            {
                g.DrawImage(imgHeadMonster, x + 3, y + 10);
            }
            else if (entity is Player)
            {
                Player player = (Player)entity;
                if (player.head != null && player.head.template.hpBar != -1)
                {
                    GraphicManager.instance.Draw(g, player.head.template.hpBar, x + 57, y + 63);
                }
            }
            try
            {
                int perHP = (int)(entity.hpShow * imgBarHp.GetWidth() / entity.maxHp);
                if (perHP > 0)
                {
                    g.drawRegion(imgBarHp, 0, 0, perHP, imgBarHp.GetHeight(), 0, x + 99, y + 29, StaticObj.TOP_LEFT);
                }
            }
            catch
            {
            }
            MyFont.text_white.DrawString(g, Utils.GetMoneys(entity.hp), x + 120, y + 35, 0, MyFont.text_grey);
            string name = string.Empty;
            if (entity is Monster)
            {
                Monster monster = (Monster)entity;
                name = monster.template.name + (monster.level > 0 ? (" (lv" + monster.level + ")") : "");
            }
            else if (entity is Player)
            {

                Player player = (Player)entity;
                name = player.name + " (lv" + player.level + ")";
            }
            if (name != string.Empty)
            {
                g.DrawImage(imgBarFocusName, x + 99, y + 73, StaticObj.TOP_LEFT);
                MyFont.text_white.DrawString(g, name, x + 115, y + 72, 0, MyFont.text_grey);
            }
        }

        public void PaintCommands(MyGraphics g)
        {
            if (chatTxtField.isShow || GameCanvas.menu.isShow || GameCanvas.clientInput.isShow || GameCanvas.menuBar.isShow || screenManager.panel.isShow)
            {
                return;
            }
            g.Translate(-cmx, -cmy);
            if (cmdQues.isShow)
            {
                cmdQues.Paint(g);
            }
            g.Reset();
            if (cmdBean.isShow)
            {
                cmdBean.Paint(g);
            }
            if (cmdChat.isShow)
            {
                cmdChat.Paint(g);
            }
            if (cmdMarket.isShow)
            {
                cmdMarket.Paint(g);
            }
            if (cmdTop.isShow)
            {
                cmdTop.Paint(g);
            }
            if (cmdArea.isShow)
            {
                cmdArea.Paint(g);
                MyFont.text_mini_white.DrawString(g, Map.areaId.ToString(), cmdArea.x + cmdArea.w / 2 + 3, cmdArea.y + 20, 2);
            }
            /* if (cmdMenu.isShow)
             {
                 cmdMenu.Paint(g);
                 if (screenManager.panel.IsNewMessage() && GameCanvas.gameTick % 30 < 15)
                 {
                     g.DrawImage(imgLight, cmdMenu.x + cmdMenu.w / 2, cmdMenu.y + cmdMenu.h / 2, StaticObj.VCENTER_HCENTER);
                 }
             }*/
            if (cmdGift.isShow)
            {
                cmdGift.Paint(g);
            }
            if (cmdBag.isShow)
            {
                cmdBag.Paint(g);
            }
            if (cmdClan.isShow)
            {
                cmdClan.Paint(g);
            }
            if (cmdOption.isShow)
            {
                cmdOption.Paint(g);
            }
            if (cmdMess.isShow)
            {
                cmdMess.Paint(g);
                if (screenManager.panel.IsNewMessage() && GameCanvas.gameTick % 30 < 15)
                {
                    g.DrawImage(imgLight, cmdMess.x + cmdMess.w / 2, cmdMess.y + cmdMess.h / 2, StaticObj.VCENTER_HCENTER);
                }
            }
            if (cmdLucky.isShow)
            {
                cmdLucky.Paint(g);
            }
            if (cmdLogout.isShow)
            {
                cmdLogout.Paint(g);
            }
            if (cmdTask.isShow)
            {
                cmdTask.Paint(g);
            }
            if (!isPaintSkill)
            {
                return;
            }
            if (cmdAttack.isShow)
            {
                g.DrawImage(imgLineSkill, cmdAttack.x - 80, cmdAttack.y - 80);
                cmdAttack.Paint(g);
            }
            /* if (cmdSpecialSkill.isShow)
             {
                 cmdSpecialSkill.Paint(g);
             }*/
            if (cmdChangeFocus.isShow)
            {
                cmdChangeFocus.Paint(g);
            }
            if (cmdCapsule.isShow)
            {
                cmdCapsule.Paint(g);
            }
            if (cmdButtonTienIch.isShow)
            {
                cmdButtonTienIch.Paint(g);
            }
            if (cmdAuto.isShow)
            {
                cmdAuto.Paint(g, isAutoFindMob);
                /* if (isAutoFindMob)
                 {
                     g.DrawImage(imgSelect, cmdAuto.x + cmdAuto.w / 2, cmdAuto.y - 5, MyGraphics.HCENTER | MyGraphics.BOTTOM);
                 }*/
            }
            for (int i = 1; i < keySkills.Length; i++)
            {
                keySkills[i].Paint(g);
            }
            PaintExpBar(g);
        }

        public void StartCallDragon(int x, int y)
        {
            combineSuccess = 0;
            xS = x;
            yS = y - 200;
            rS = 120;
            iDotS = 7;
            angleS = (angleO = 90);
            time = 1;
            speed = 1;
            isSpeedCombine = true;
            isDoneCombine = false;
            isCompleteEffCombine = false;
            iAngleS = 360 / iDotS;
            xArgS = new int[iDotS];
            yArgS = new int[iDotS];
            xDotS = new int[iDotS];
            yDotS = new int[iDotS];
            SetDotStar();
            isPaintCombine = true;
            countUpdate = 10;
            countR = 30;
            countWait = 10;
        }

        public void CloseDragon()
        {
            isShowDragon = false;
            isPaintCombine = false;
            mapDragon = -1;
            areaDragon = -1;
        }

        public void PaintCallDragon(MyGraphics g)
        {
            if (Map.mapId != mapDragon || Map.areaId != areaDragon)
            {
                return;
            }
            if (isPaintCombine)
            {
                if (rS <= 0)
                {
                    GraphicManager.instance.Draw(g, 1114, xS, yS, 0, MyGraphics.VCENTER | MyGraphics.HCENTER);
                }
                for (int i = 0; i < yArgS.Length; i++)
                {
                    GraphicManager.instance.Draw(g, dragonballs[i], xDotS[i] - cmx, yDotS[i] - cmy, 0, MyGraphics.VCENTER | MyGraphics.HCENTER);
                }
            }
        }

        private void UpdateCallDragon()
        {
            countUpdate--;
            if (countUpdate < 0)
            {
                countUpdate = 0;
            }
            countR--;
            if (countR < 0)
            {
                countR = 0;
            }
            if (countUpdate != 0)
            {
                return;
            }
            if (!isCompleteEffCombine)
            {
                if (time > 0)
                {
                    if (combineSuccess != -1)
                    {
                        if (typeCombine == 3)
                        {
                            if (GameCanvas.gameTick % 10 == 0)
                            {
                                time--;
                            }
                        }
                        else
                        {
                            if (GameCanvas.gameTick % 2 == 0)
                            {
                                if (isSpeedCombine)
                                {
                                    if (speed < 40)
                                    {
                                        speed += 2;
                                    }
                                }
                                else if (speed > 10)
                                {
                                    speed -= 2;
                                }
                            }
                            if (countR == 0)
                            {
                                if (isSpeedCombine)
                                {
                                    if (rS > 0)
                                    {
                                        rS -= 5;
                                        if (rS < 120 && rS >= 115)
                                        {
                                            Effect effect = new EffectLoop(12, 1, xS + cmx, yS + cmy, StaticObj.VCENTER_HCENTER, 0);
                                            effects.Add(effect);
                                        }
                                    }
                                    else if (GameCanvas.gameTick % 10 == 0)
                                    {
                                        isSpeedCombine = false;
                                        time--;
                                        countR = 5;
                                        countWait = 10;
                                    }
                                }
                                else if (rS < 90)
                                {
                                    rS += 5;
                                }
                                else if (GameCanvas.gameTick % 10 == 0)
                                {
                                    isSpeedCombine = true;
                                    countR = 10;
                                }
                            }
                            angleS = angleO;
                            angleS -= speed;
                            if (angleS >= 360)
                            {
                                angleS -= 360;
                            }
                            if (angleS < 0)
                            {
                                angleS = 360 + angleS;
                            }
                            angleO = angleS;
                            SetDotStar();
                        }
                    }
                }
                else if (GameCanvas.gameTick % 20 == 0)
                {
                    isCompleteEffCombine = true;
                }
            }
            else
            {
                if (!isCompleteEffCombine)
                {
                    return;
                }
                if (combineSuccess == 1)
                {
                    countWait--;
                    if (countWait < 0)
                    {
                        countWait = 0;
                    }
                    if (rS < 300)
                    {
                        rS = Math.Abs(rS + 10);
                        if (rS == 20)
                        {
                            dem = 0;
                            isUseFreez = true;
                        }
                    }
                    else if (GameCanvas.gameTick % 20 == 0)
                    {
                        combineSuccess = -1;
                        isDoneCombine = true;
                    }
                    SetDotStar();
                }
                else
                {
                    if (combineSuccess != 0)
                    {
                        return;
                    }
                    if (countWait == 10)
                    {
                        isPaintCombine = false;
                        dem = 0;
                        isUseFreez = true;
                    }
                    if (isPaintCombine)
                    {
                        return;
                    }
                    countWait--;
                    if (!isShowDragon && countWait <= 0)
                    {
                        isShowDragon = true;
                    }
                    if (countWait < -50)
                    {
                        combineSuccess = -1;
                        isDoneCombine = true;
                    }
                }
            }
        }

        private void SetDotStar()
        {
            for (int i = 0; i < yArgS.Length; i++)
            {
                if (angleS >= 360)
                {
                    angleS -= 360;
                }
                if (angleS < 0)
                {
                    angleS = 360 + angleS;
                }
                yArgS[i] = Math.Abs(rS * Utils.sin(angleS) / 1024);
                xArgS[i] = Math.Abs(rS * Utils.cos(angleS) / 1024);
                if (angleS < 90)
                {
                    xDotS[i] = xS + xArgS[i];
                    yDotS[i] = yS - yArgS[i];
                }
                else if (angleS >= 90 && angleS < 180)
                {
                    xDotS[i] = xS - xArgS[i];
                    yDotS[i] = yS - yArgS[i];
                }
                else if (angleS >= 180 && angleS < 270)
                {
                    xDotS[i] = xS - xArgS[i];
                    yDotS[i] = yS + yArgS[i];
                }
                else
                {
                    xDotS[i] = xS + xArgS[i];
                    yDotS[i] = yS + yArgS[i];
                }
                angleS -= iAngleS;
            }
        }

        public void StartItemSpin()
        {
            if (!isShowSpin)
            {
                try
                {
                    if (itemSpins[6].template.type == Item.TYPE_YEN)
                    {
                        InfoMe.addInfo("Bạn nhận được " + itemSpins[6].quantity + " xu", 1);
                    }
                    else if (itemSpins[6].template.type == Item.TYPE_DIAMOND)
                    {
                        InfoMe.addInfo("Bạn nhận được " + itemSpins[6].quantity + " kim cương", 1);
                    }
                    else
                    {
                        InfoMe.addInfo("Bạn nhận được " + (itemSpins[6].quantity > 1 ? (itemSpins[6].quantity + " ") : "") + itemSpins[6].template.name, 1);
                    }
                }
                catch
                {
                }
                return;
            }
            screenManager.panel.Close();
            wPaintItemSpin = 75 * 5;
            hPaintItemSpin = 75;
            xPaintItemSpin = (GameCanvas.w - wPaintItemSpin) / 2;
            yPaintItemSpin = GameCanvas.h / 2 - hPaintItemSpin * 2;

            List<Item> itemSpinList = new List<Item>();
            itemSpinList.AddRange(itemSpins);

            int num = 0;
            while (num < 2)
            {
                for (int i = 0; i < itemSpinList.Count; i++)
                {
                    itemSpins.Add(itemSpinList[i]);
                }
                num++;
            }
            cmxPaintItemSpin = xPaintItemSpin + 37;
            cmxToPaintItemSpin = xPaintItemSpin - 75 * (itemSpinList.Count * 2 + 4) + 37;
            lastTimeDelaySpin = Utils.CurrentTimeMillis();
            isPaintItemSpin = true;
        }

        public void PaintItemSpin(MyGraphics g)
        {
            if (!isPaintItemSpin)
            {
                return;
            }
            g.Reset();

            g.SetColor(0, 0.5f);
            g.FillRect(xPaintItemSpin, yPaintItemSpin, wPaintItemSpin, hPaintItemSpin, 10);

            g.SetClip(xPaintItemSpin, yPaintItemSpin, wPaintItemSpin, hPaintItemSpin);

            for (int i = 0; i < itemSpins.Count; i++)
            {
                GraphicManager.instance.Draw(g, itemSpins[i].template.iconId, cmxPaintItemSpin + i * 75, yPaintItemSpin + 37, 0, StaticObj.VCENTER_HCENTER);
            }
            g.SetColor(Color.white);
            g.FillRect(xPaintItemSpin + wPaintItemSpin / 2, yPaintItemSpin, 1, hPaintItemSpin);
        }

        public void UpdateItemSpin()
        {
            if (cmxPaintItemSpin < cmxToPaintItemSpin)
            {
                lastTimeDelaySpin = Utils.CurrentTimeMillis();
                int num = cmxToPaintItemSpin - cmxPaintItemSpin >> 4;
                if (num < 1)
                {
                    num = 1;
                }
                cmxPaintItemSpin += num;
            }
            else if (cmxPaintItemSpin > cmxToPaintItemSpin)
            {
                lastTimeDelaySpin = Utils.CurrentTimeMillis();
                int num = cmxPaintItemSpin - cmxToPaintItemSpin >> 4;
                if (num < 1)
                {
                    num = 1;
                }
                cmxPaintItemSpin -= num;
            }
            else if (Utils.CurrentTimeMillis() - lastTimeDelaySpin > 1000)
            {
                lastTimeDelaySpin = Utils.CurrentTimeMillis();

                if (itemSpins[6].template.type == Item.TYPE_YEN)
                {
                    InfoMe.addInfo("Bạn nhận được " + itemSpins[6].quantity + " xu", 1);
                }
                else if (itemSpins[6].template.type == Item.TYPE_DIAMOND)
                {
                    InfoMe.addInfo("Bạn nhận được " + itemSpins[6].quantity + " kim cương", 1);
                }
                else
                {
                    InfoMe.addInfo("Bạn nhận được " + (itemSpins[6].quantity > 1 ? (itemSpins[6].quantity + " ") : "") + itemSpins[6].template.name, 1);
                }
                isPaintItemSpin = false;
            }
        }

        public static void paintFlyText(MyGraphics g)
        {
            for (int i = 0; i < flyTextState.Length; i++)
            {
                if (flyTextState[i] != -1 && GameCanvas.IsPaint(flyTextX[i], flyTextY[i]))
                {
                    flyTextColor[i].DrawStringBorder(g, flyTextString[i], flyTextX[i], flyTextY[i], 2);
                }
            }
        }

        public void UpdateFlyText()
        {
            for (int i = 0; i < flyTextState.Length; i++)
            {
                if (flyTextState[i] == -1)
                {
                    continue;
                }
                if (flyTextState[i] > flyTextYTo[i])
                {
                    flyTime[i]++;
                    if (flyTime[i] == 25)
                    {
                        flyTime[i] = 0;
                        flyTextState[i] = -1;
                        flyTextYTo[i] = 0;
                        flyTextDx[i] = 0;
                        flyTextX[i] = 0;
                    }
                }
                else
                {
                    flyTextState[i] += Math.Abs(flyTextDy[i]);
                    flyTextX[i] += flyTextDx[i];
                    flyTextY[i] += flyTextDy[i];
                }
            }
            for (int i = 0; i < flyTexts.Count; i++)
            {
                FlyText flyText = flyTexts[i];
                flyText.updateFirework();
                flyText.x += flyText.dx;
                flyText.y += flyText.dy;
                flyText.tick++;
                if (flyText.tick >= flyText.limitTime)
                {
                    flyTexts.Remove(flyText);
                }
            }
        }

        public void ChatVip(string chatVip)
        {
            if (!startChat)
            {
                currChatWidth = MyFont.text_yellow.GetWidth(chatVip);
                xChatVip = GameCanvas.w;
                startChat = true;
            }
            chatvips.Add(chatVip);
        }

        public void PaintChatVip(MyGraphics g)
        {
            if (chatvips.Count != 0)
            {
                xChatVip -= 5;
                g.SetClip(0, GameCanvas.h - 26, GameCanvas.w, 26);
                g.SetColor(0, 0.4f);
                g.FillRect(0, GameCanvas.h - 26, GameCanvas.w, 26);
                MyFont.text_yellow.DrawString(g, chatvips[0], xChatVip, GameCanvas.h - 26, 0, MyFont.text_grey);
            }
        }

        public void updateChatVip()
        {
            if (!startChat)
            {
                return;
            }
            //xChatVip -= 5;
            if (xChatVip < -currChatWidth)
            {
                xChatVip = GameCanvas.w;
                chatvips.RemoveAt(0);
                if (chatvips.Count == 0)
                {
                    startChat = false;
                }
                else
                {
                    currChatWidth = MyFont.text_yellow.GetWidth(chatvips[0]);
                }
            }
        }

        public static int GetHpPoint()
        {
            int num = 0;
            try
            {
                for (int i = 0; i < Player.me.itemsBag.Count; i++)
                {
                    if (Player.me.itemsBag[i] != null && Player.me.itemsBag[i].template.type == 19)
                    {
                        num += Player.me.itemsBag[i].quantity;
                    }
                }
            }
            catch
            {
            }
            return num;
        }

        public void SearchItem()
        {
            if (Player.me.itemFocus != null)
            {
                return;
            }
            int min = 99999;
            ItemMap itemMap = null;
            for (int i = 0; i < itemMaps.Count; i++)
            {
                if (itemMaps[i].playerId == -1 || itemMaps[i].playerId == Player.me.id)
                {
                    int dis = Math.Abs(itemMaps[i].x - Player.me.x);
                    if (dis < min && dis < 500)
                    {
                        min = dis;
                        itemMap = itemMaps[i];
                    }
                }
            }
            if (itemMap != null)
            {
                Player.me.FocusManualTo(itemMap);
            }
        }

        private int GetYSd(int xSd)
        {
            int num = 50;
            int num2 = 0;
            while (num2 < 90)
            {
                num2++;
                num += 24;
                if (Map.IsWall(xSd, num))
                {
                    if (num % 24 != 0)
                    {
                        num -= num % 24;
                    }
                    break;
                }
            }
            return num;
        }

        private Monster FindMonster()
        {
            if (isCanAutoPlay)
            {
                if (isAttackThuLinh)
                {
                    for (int i = 0; i < monsters.Count; i++)
                    {
                        Monster monster = monsters[i];
                        if (monster is MonsterPet)
                        {
                            continue;
                        }
                        if (!monster.IsDead() && monster.hp > 0 && monster.levelStatus == 2)
                        {
                            return monster;
                        }
                    }
                }
                if (isAttackTinhAnh)
                {
                    for (int i = 0; i < monsters.Count; i++)
                    {
                        Monster monster = monsters[i];
                        if (monster is MonsterPet)
                        {
                            continue;
                        }
                        if (!monster.IsDead() && monster.hp > 0 && monster.levelStatus == 1)
                        {
                            return monster;
                        }
                    }
                }
            }
            Monster rs = null;
            int dis = 9999;
            for (int i = 0; i < monsters.Count; i++)
            {
                Monster monster = monsters[i];
                if (monster is MonsterPet)
                {
                    continue;
                }
                if (!monster.IsDead() && monster.hp > 0 && (monster.levelStatus == 0 || (monster.levelStatus == 1 && isAttackTinhAnh) || (monster.levelStatus == 2 && isAttackThuLinh)))
                {
                    int num = Math.Abs(Player.me.x - monster.x);
                    if (dis > num)
                    {
                        rs = monster;
                        dis = num;
                    }
                }
            }
            return rs;
        }

        private void AutoPlay()
        {
            if ((!isCanAutoPlay && !isAutoFindMob) || Player.me.IsDead())
            {
                return;
            }
            bool flag = false;
            for (int i = 0; i < monsters.Count; i++)
            {
                if (!monsters[i].IsDead())
                {
                    flag = true;
                    break;
                }
            }
            if (!flag)
            {
                return;
            }
            if ((Player.me.hp <= Player.me.maxHp / 5 || Player.me.mp <= Player.me.maxMp / 5) && GameScreen.hpPoint > 0 && GameCanvas.gameTick % 20 == 0)
            {
                DoUseBean();
            }
            Player.me.playerFocus = null;
            Player.me.npcFocus = null;
            if (Player.me.monsterFocus == null)
            {
                if (!isCanAutoPlay && isAutoPick)
                {
                    SearchItem();
                    if (Player.me.itemFocus != null)
                    {
                        PickItem();
                        SearchItem();
                    }
                }
                else
                {
                    Player.me.itemFocus = null;
                }
                if (Player.me.itemFocus == null)
                {
                    Monster monster = FindMonster();
                    if (monster != null)
                    {
                        Player.me.FocusManualTo(monster);
                        if (isCanAutoPlay)
                        {
                            Player.me.x = monster.x;
                            Player.me.y = monster.y;
                            Service.instance.PlayerMove();
                        }
                    }
                }
            }
            else if (Player.me.monsterFocus.IsDead())
            {
                Player.me.monsterFocus = null;
            }
            if (Player.me.monsterFocus != null && Player.me.itemFocus == null && (Math.Abs(Player.me.x - Player.me.monsterFocus.x) > 100 || Math.Abs(Player.me.y - Player.me.monsterFocus.y) > 100))
            {
                Monster mon = FindMonster();
                if (mon.id != Player.me.monsterFocus.id && Math.Abs(Player.me.x - Player.me.monsterFocus.x) > Math.Abs(Player.me.x - mon.x))
                {
                    Player.me.FocusManualTo(mon);
                }
                Monster monster = Player.me.monsterFocus;
                if (isCanAutoPlay)
                {
                    Player.me.x = monster.x;
                    Player.me.y = monster.y;
                    Service.instance.PlayerMove();
                }
                else if (monster.x > Player.me.x && Player.me.dir == 1 && Map.IsWall(Player.me.x + Player.me.w, Player.me.y - 1))
                {
                    Player.me.currentMovePoint = new MovePoint(Player.me.x + Player.me.w + 20, GetYSd(Player.me.x + Player.me.w + 20));
                    return;
                }
                else if (monster.x < Player.me.x && Player.me.dir == -1 && Map.IsWall(Player.me.x - Player.me.w, Player.me.y - 1))
                {
                    Player.me.currentMovePoint = new MovePoint(Player.me.x - Player.me.w - 20, GetYSd(Player.me.x - Player.me.w - 20));
                    return;
                }
                else
                {
                    Player.me.currentMovePoint = new MovePoint(monster.x, monster.y);
                    return;
                }
            }
            if (Player.me.monsterFocus == null || Player.me.skillPaint != null || Player.me.dart != null)
            {
                return;
            }
            Skill skill = null;
            for (int m = 0; m < keySkills.Length; m++)
            {
                if (keySkills[m].skill == null || keySkills[m].skill.isPaintCanNotUse || !keySkills[m].skill.template.isProactive || keySkills[m].skill.template.id == 10 || keySkills[m].skill.template.id == 14)
                {
                    continue;
                }
                long mana = (long)(keySkills[m].skill.template.typeMana == 0 ? keySkills[m].skill.GetManaUse() : (keySkills[m].skill.GetManaUse() * Player.me.maxMp / 100));
                if (Player.me.mp >= mana)
                {
                    if (skill == null)
                    {
                        skill = keySkills[m].skill;
                    }
                    else if (skill.GetCoolDown() < keySkills[m].skill.GetCoolDown())
                    {
                        skill = keySkills[m].skill;
                    }
                }
            }
            if (skill != null)
            {
                DoSelectSkill(skill);
                DoFire();
            }
        }

        public void Logout()
        {
            try
            {
                DisplayManager.instance.dialog.Close();
                if (ServerManager.instance.session != null)
                {
                    ServerManager.instance.session.Close();
                }
                ServerManager.instance.session =
                    new Session(ServerManager.instance.controller);
                ServerManager.instance.Connect();
                ScreenManager.instance.loginScreen.SwitchToMe();
                if (GameCanvas.menu.isShow) GameCanvas.menu.isShow = false;
                if (GameCanvas.menuBar.isShow) GameCanvas.menuBar.isShow = false;
                if (ScreenManager.instance.panel.isShow) ScreenManager.instance.panel.isShow = false;
                InfoMe.infoWait.Clear();
                chatvips.Clear();
                messageTimes.Clear();
            }
            catch (Exception e)
            {
                Debug.Log("Logout error: " + e);
            }
        }


        public bool IsLostConnection()
        {
            Session session = ServerManager.instance.session;
            return (session.socket != null && !session.socket.Connected) || (session.socket == null && !session.isConnected);
        }

        public override void Update()
        {
            if (Player.isLoadingMap)
            {
                return;
            }
            if (isAutoFindMob)
            {
                auto = 10;
                isAutoPlay = true;
            }
            try
            {
                if (IsLostConnection())
                {
                    Logout();
                    if (!DisplayManager.instance.dialog.isShow)
                    {
                        GameCanvas.StartDialogOk(PlayerText.maychutathoacmatsong);
                    }
                    return;
                }
            }
            catch
            {
            }
            try
            {
                if (hp > Player.me.maxHp)
                {
                    hp = Player.me.maxHp;
                }
                if (mp > Player.me.maxMp)
                {
                    mp = Player.me.maxMp;
                }
                if (hp < Player.me.hp)
                {
                    long num = (Player.me.hp - hp) / 2;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    hp += num;
                }
                else if (hp > Player.me.hp)
                {
                    long num = (hp - Player.me.hp) / 2;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    hp -= num;
                }
                if (mp < Player.me.mp)
                {
                    long num = (Player.me.mp - mp) / 2;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    mp += num;
                }
                else if (mp > Player.me.mp)
                {
                    long num = (mp - Player.me.mp) / 2;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    mp -= num;
                }
            }
            catch (Exception ex)
            {
                Debug.LogException(ex);
            }
            FrameManager.instance.Update();
            if (lockTick > 0)
            {
                lockTick--;
                if (lockTick == 0)
                {
                    Controller.isStopReadMessage = false;
                }
            }
            if (Spaceship.tShock > 0)
            {
                Spaceship.tShock--;
            }
            if (Player.me.playerFocus == null)
            {
                cmdQues.isShow = false;
            }
            else if (cmdQues.isShow && !Player.me.IsCanAttack(Player.me.playerFocus))
            {
                cmdQues.x = Player.me.playerFocus.x;
                cmdQues.y = Player.me.playerFocus.y - Player.me.playerFocus.h - cmdQues.h - 50;
            }
            if (tMenuDelay > 0)
            {
                tMenuDelay--;
            }
            if (isAutoPlay && GameCanvas.gameTick % 10 == 0)
            {
                AutoPlay();
            }
            try
            {
                InitButton();
                UpdateCamera();
                Player.me.Update();
                for (int i = 0; i < players.Count; i++)
                {
                    players[i].Update();
                }
                InfoMe.update();
                for (int i = 0; i < spaceships.Count; i++)
                {
                    spaceships[i].Update();
                }
                for (int i = 0; i < monsters.Count; i++)
                {
                    monsters[i].Update();
                }
                for (int i = 0; i < npcs.Count; i++)
                {
                    npcs[i].Update();
                }
                for (int i = 0; i < MonsterManager.instance.darts.Count; i++)
                {
                    MonsterManager.instance.darts[i].Update();
                }
                for (int i = 0; i < itemMaps.Count; i++)
                {
                    itemMaps[i].Update();
                }
                for (int i = 0; i < Map.waypoints.Count; i++)
                {
                    Map.waypoints[i].update();
                }
                foreach (CmdSkill cmd in keySkills)
                {
                    if (cmd.skill != null)
                    {
                        cmd.skill.Update();
                    }
                }
                if (Player.me.isDie && !cmdDie.isShow && !GameCanvas.menu.isShow && !GameCanvas.menuBar.isShow)
                {
                    cmdDie.isShow = true;
                }
                if (chatTxtField.isShow)
                {
                    chatTxtField.Update();
                }
                BackgroudEffect.updateEff();
                for (int i = 0; i < effects.Count; i++)
                {
                    if (effects[i].IsClear())
                    {
                        effects.RemoveAt(i);
                        i--;
                    }
                    else
                    {
                        try
                        {
                            effects[i].Update();
                        }
                        catch
                        {
                            effects.RemoveAt(i);
                            i--;
                        }
                    }
                }
                UpdateFlyText();
                textGlobal.update();
                if (GameCanvas.gameTick % 5 == 0 && auto > 0 && Player.me.currentMovePoint == null)
                {
                    if (Player.me.mySkill != null && Player.me.mySkill.isPaintCanNotUse)
                    {
                        return;
                    }
                    if ((Player.me.monsterFocus != null && !Player.me.monsterFocus.IsDead() && Player.me.playerFocus == null)
                        || (Player.me.playerFocus != null && Player.me.IsCanAttack(Player.me.playerFocus))
                        || Player.me.itemFocus != null)
                    {
                        if (Player.me.mySkill.isPaintCanNotUse && Player.me.itemFocus == null)
                        {
                            return;
                        }
                        if (isAutoFindMob && isCanAutoPlay)
                        {
                            AutoPlay();
                        }
                        else
                        {
                            DoFire();
                        }
                    }
                }
                if (auto > 1)
                {
                    auto--;
                }
                if (isPaintItemSpin)
                {
                    UpdateItemSpin();
                }
            }
            catch
            {
            }
            if (xTeamPaintShow > xTeamPaint)
            {
                int num = xTeamPaintShow - xTeamPaint >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                xTeamPaintShow -= num;
            }
            else if (xTeamPaintShow < xTeamPaint)
            {
                int num = xTeamPaint - xTeamPaintShow >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                xTeamPaintShow += num;
            }
            UpdateCallDragon();
            for (int i = 0; i < messageTimes.Count; i++)
            {
                messageTimes[i].Update();
            }
            updateChatVip();
        }

        public void StartFlyText(MyFont myFont, string text, int x, int y, int dx, int dy)
        {
            int num = -1;
            for (int i = 0; i < flyTextState.Length; i++)
            {
                if (flyTextState[i] == -1)
                {
                    num = i;
                    break;
                }
            }
            if (num == -1)
            {
                return;
            }
            flyTextColor[num] = myFont;
            flyTextString[num] = text;
            flyTextX[num] = x;
            flyTextY[num] = y;
            flyTextDx[num] = dx;
            flyTextDy[num] = ((dy >= 0) ? 5 : (-5));
            flyTextState[num] = 0;
            flyTime[num] = 0;
            flyTextYTo[num] = 30;
            for (int j = 0; j < flyTextState.Length; j++)
            {
                if (flyTextState[j] != -1 && num != j && flyTextDy[num] < 0 && Math.Abs(flyTextX[num] - flyTextX[j]) <= 20 && flyTextYTo[num] == flyTextYTo[j])
                {
                    flyTextYTo[num] += 30;
                }
            }
        }

        public void levelup()
        {
            FlyText flyText = new FlyText();
            flyText.x = Player.me.x;
            flyText.y = Player.me.y - Player.me.h / 2;
            flyText.dx = 0;
            flyText.dy = -2;
            flyText.type = 1;
            flyText.tick = 0;

            flyTexts.Add(flyText);
        }

        public void completeTask()
        {
            FlyText flyText = new FlyText();
            flyText.x = Player.me.x;
            flyText.y = Player.me.y - Player.me.h / 2;
            flyText.dx = 0;
            flyText.dy = -2;
            flyText.type = 2;
            flyText.tick = 0;

            flyTexts.Add(flyText);
        }

        public override void PointerClicked(int x, int y)
        {
            if (cmdDie.isShow && cmdDie.PointerClicked(x, y))
            {
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerClicked(x, y);
                return;
            }
            if (cmdChat.isShow && cmdChat.PointerClicked(x, y))
            {
                return;
            }
            if (cmdTop.isShow && cmdTop.PointerClicked(x, y))
            {
                return;
            }
            if (cmdMarket.isShow && cmdMarket.PointerClicked(x, y))
            {
                return;
            }
            if (cmdAuto.isShow && cmdAuto.PointerClicked(x, y))
            {
                return;
            }
            if (cmdGift.isShow && cmdGift.PointerClicked(x, y))
            {
                return;
            }
            if (cmdBag.isShow && cmdBag.PointerClicked(x, y))
            {
                return;
            }
            if (cmdClan.isShow && cmdClan.PointerClicked(x, y))
            {
                return;
            }
            if (cmdTask.isShow && cmdTask.PointerClicked(x, y))
            {
                return;
            }
            if (cmdLucky.isShow && cmdLucky.PointerClicked(x, y))
            {
                return;
            }
            if (cmdLogout.isShow && cmdLogout.PointerClicked(x, y))
            {
                return;
            }
            if (cmdMess.isShow && cmdMess.PointerClicked(x, y))
            {
                return;
            }
            if (cmdOption.isShow && cmdOption.PointerClicked(x, y))
            {
                return;
            }
            if (cmdChangeFocus.isShow && cmdChangeFocus.PointerClicked(x, y))
            {
                return;
            }
            if (cmdCapsule.isShow && cmdCapsule.PointerClicked(x, y))
            {
                return;
            }
            /*if (cmdMenu.isShow && cmdMenu.PointerClicked(x, y))
            {
                return;
            }*/
            if (cmdArea.isShow && cmdArea.PointerClicked(x, y))
            {
                return;
            }
            if (Player.isLockKey || Player.me.isLockMove || InfoDlg.isLock || Controller.isStopReadMessage)
            {
                return;
            }
            if (cmdBean.isShow && cmdBean.PointerClicked(x, y))
            {
                return;
            }
            if (cmdAttack.isShow && cmdAttack.PointerClicked(x, y))
            {
                return;
            }
            for (int i = 0; i < keySkills.Length; i++)
            {
                if (keySkills[i].PointerClicked(x, y))
                {
                    return;
                }
            }
            if (cmdQues.isShow && cmdQues.PointerClicked(x, y))
            {
                return;
            }
            if (cmdButtonTienIch.isShow && cmdButtonTienIch.PointerClicked(x, y))
            {
                return;
            }
        }

        public override void PointerMove(int x, int y)
        {
            if (cmdButtonTienIch.isShow && cmdButtonTienIch.PointerMove(x, y))
            {
                return;
            }
            if (cmdDie.isShow && cmdDie.PointerMove(x, y))
            {
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerMove(x, y);
                return;
            }
            if (cmdChangeFocus.isShow && cmdChangeFocus.PointerMove(x, y))
            {
                return;
            }
            if (cmdTask.isShow && cmdTask.PointerMove(x, y))
            {
                return;
            }
            if (cmdLucky.isShow && cmdLucky.PointerMove(x, y))
            {
                return;
            }
            if (cmdLogout.isShow && cmdLogout.PointerMove(x, y))
            {
                return;
            }
            if (cmdMess.isShow && cmdMess.PointerMove(x, y))
            {
                return;
            }
            if (cmdOption.isShow && cmdOption.PointerMove(x, y))
            {
                return;
            }
            if (cmdAuto.isShow && cmdAuto.PointerMove(x, y))
            {
                return;
            }
            if (cmdBag.isShow && cmdBag.PointerMove(x, y))
            {
                return;
            }
            if (cmdClan.isShow && cmdClan.PointerMove(x, y))
            {
                return;
            }
            if (cmdCapsule.isShow && cmdCapsule.PointerMove(x, y))
            {
                return;
            }
            if (cmdGift.isShow && cmdGift.PointerMove(x, y))
            {
                return;
            }
            if (cmdChat.isShow && cmdChat.PointerMove(x, y))
            {
                return;
            }
            if (cmdTop.isShow && cmdTop.PointerMove(x, y))
            {
                return;
            }
            if (cmdMarket.isShow && cmdMarket.PointerMove(x, y))
            {
                return;
            }
            /* if (cmdMenu.isShow && cmdMenu.PointerMove(x, y))
             {
                 return;
             }*/
            if (cmdArea.isShow && cmdArea.PointerMove(x, y))
            {
                return;
            }
            if (Player.isLockKey || Player.me.isLockMove || InfoDlg.isLock || Controller.isStopReadMessage)
            {
                return;
            }
            if (cmdBean.isShow && cmdBean.PointerMove(x, y))
            {
                return;
            }
            if (cmdAttack.isShow && cmdAttack.PointerMove(x, y))
            {
                return;
            }
            for (int i = 0; i < keySkills.Length; i++)
            {
                if (keySkills[i].PointerMove(x, y))
                {
                    return;
                }
            }
            if (cmdQues.isShow && cmdQues.PointerMove(x, y))
            {
                return;
            }
        }

        public override void PointerReleased(int x, int y)
        {
            if (cmdButtonTienIch.isShow && cmdButtonTienIch.PointerReleased(x, y))
            {
                return;
            }
            if (cmdDie.isShow && cmdDie.PointerReleased(x, y))
            {
                return;
            }
            if (cmdAuto.isShow && cmdAuto.PointerReleased(x, y))
            {
                return;
            }
            if (cmdTask.isShow && cmdTask.PointerReleased(x, y))
            {
                return;
            }
            if (cmdBag.isShow && cmdBag.PointerReleased(x, y))
            {
                return;
            }
            if (cmdLucky.isShow && cmdLucky.PointerReleased(x, y))
            {
                return;
            }
            if (cmdLogout.isShow && cmdLogout.PointerReleased(x, y))
            {
                return;
            }
            if (cmdMess.isShow && cmdMess.PointerReleased(x, y))
            {
                return;
            }
            if (cmdOption.isShow && cmdOption.PointerReleased(x, y))
            {
                return;
            }
            if (cmdClan.isShow && cmdClan.PointerReleased(x, y))
            {
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerReleased(x, y);
                return;
            }
            if (cmdChat.isShow && cmdChat.PointerReleased(x, y))
            {
                return;
            }
            if (cmdTop.isShow && cmdTop.PointerReleased(x, y))
            {
                return;
            }
            if (cmdMarket.isShow && cmdMarket.PointerReleased(x, y))
            {
                return;
            }
            if (cmdGift.isShow && cmdGift.PointerReleased(x, y))
            {
                return;
            }
            if (cmdChangeFocus.isShow && cmdChangeFocus.PointerReleased(x, y))
            {
                return;
            }
            if (cmdCapsule.isShow && cmdCapsule.PointerReleased(x, y))
            {
                return;
            }
            /*if (cmdMenu.isShow && cmdMenu.PointerReleased(x, y))
            {
                return;
            }*/
            if (cmdArea.isShow && cmdArea.PointerReleased(x, y))
            {
                return;
            }
            if (Player.isLockKey || Player.me.isLockMove || InfoDlg.isLock || Controller.isStopReadMessage)
            {
                return;
            }
            if (cmdBean.isShow && cmdBean.PointerReleased(x, y))
            {
                return;
            }
            if (cmdAttack.isShow && cmdAttack.PointerReleased(x, y))
            {
                return;
            }
            for (int i = 1; i < keySkills.Length; i++)
            {
                if (keySkills[i].PointerReleased(x, y))
                {
                    return;
                }
            }
            if (cmdQues.isShow && cmdQues.PointerReleased(x, y))
            {
                return;
            }
            if (x >= 0 && x <= 120 && y >= 0 && y <= 120)
            {
                screenManager.panel.SetType(TabPanel.tabInventory.type);
                screenManager.panel.Show();
                return;
            }
            if (IsClickOnTaskFrame(x, y))
            {
                ShowTaskInfo();
                return;
            }
            if (x >= 60 && x <= 110 && y >= 190 && y <= 240)
            {
                isShowTaskOrther = !isShowTaskOrther;
                return;
            }
            if (xTeamPaint == xMaxTeamPaint)
            {
                if (x >= xMaxTeamPaint - 20 && x <= xMaxTeamPaint + 20 && y >= yTeamPaint && y <= yTeamPaint + 200)
                {
                    xTeamPaint = xMinTeamPaint;
                    return;
                }
            }
            else if (xTeamPaint == xMinTeamPaint)
            {
                if (x >= xMinTeamPaint - 20 && x <= xMinTeamPaint + 20 && y >= yTeamPaint && y <= yTeamPaint + 200)
                {
                    xTeamPaint = xMaxTeamPaint;
                    return;
                }
            }
            long now = Utils.CurrentTimeMillis();
            try
            {
                Entity entity = FindClickToItem(x, y);
                if (entity != null)
                {
                    Player.me.FocusManualTo(entity);
                    if (now - lastTimePointerReleased < 1000 && Math.Abs(x - lastPointerReleasedX) < 20 && Math.Abs(y - lastPointerReleasedY) < 20)
                    {
                        if (entity is Monster || (entity is Player && Player.me.IsCanAttack((Player)entity)))
                        {
                            if (Player.me.mySkill != keySkills[0].skill)
                            {
                                //Service.instance.SelectSkill(keySkills[0].skill.template.id);
                                Player.me.mySkill = keySkills[0].skill;
                            }
                            isAutoPlay = true;
                            auto = 10;
                            entity.effects.Add(new EffectLoop(entity, 11, 1, 0, entity.h / 2, StaticObj.VCENTER_HCENTER));
                        }
                        else if (entity is Npc)
                        {
                            isAutoPlay = false;
                            auto = 0;
                            entity.effects.Add(new EffectLoop(entity, 10, 1, 0, entity.h + 5, StaticObj.BOTTOM_HCENTER));
                            if (Math.Abs(entity.x - Player.me.x) < 150)
                            {
                                Service.OpenMenu(((Npc)entity).template.id);
                            }
                            else
                            {
                                Player.me.currentMovePoint = new MovePoint(entity.x, entity.y + 18);
                            }
                        }
                        else if (entity is ItemMap)
                        {
                            isAutoPlay = false;
                            auto = 0;
                            entity.effects.Add(new EffectLoop(entity, 10, 1, 0, entity.h + 5, StaticObj.BOTTOM_HCENTER));
                            if (Math.Abs(entity.x - Player.me.x) < 50)
                            {
                                Service.instance.PickItem(((ItemMap)entity).id);
                            }
                            else
                            {
                                Player.me.currentMovePoint = new MovePoint(entity.x, entity.y);
                            }
                        }
                    }
                }
            }
            catch
            {

            }

            lastTimePointerReleased = now;
            lastPointerReleasedX = x;
            lastPointerReleasedY = y;
        }

        public override void KeyPress(KeyCode keyCode)
        {
            if (chatTxtField.isShow)
            {
                chatTxtField.KeyPress(keyCode);
                return;
            }
            if (cmdDie.isShow)
            {
                if (keyCode == KeyCode.Return)
                {
                    cmdDie.PerformAction();
                }
                return;
            }
            if (Player.isLockKey || InfoDlg.isLock || Controller.isStopReadMessage)
            {
                return;
            }
            if (!DisplayManager.instance.dialog.isShow)
            {
                if (keyCode == KeyCode.R)
                {
                    chatTxtField.name = "Chat";
                    chatTxtField.textField.name = "chat";
                    chatTxtField.textField.isFocus = true;
                    chatTxtField.isShow = true;
                }
                else if (keyCode == KeyCode.Return)
                {
                    if (cmdDie.isShow)
                    {
                        cmdDie.PerformAction();
                    }
                    else if (!Player.me.IsDead())
                    {
                        ActionEnter();
                    }
                }
                else if (keyCode == KeyCode.F1)
                {
                    ShowMenu();
                }
                else if (keyCode == KeyCode.Space)
                {
                    DoUseBean();
                }
                else if (keyCode == KeyCode.A)
                {
                    Service.MiniGame(0, -1);
                }
                else if (keyCode == KeyCode.RightArrow)
                {
                    isAutoPlay = false;
                    auto = 0;
                    Player.me.currentMovePoint = null;
                    Player.isMoveLeft = false;
                    Player.isMoveRight = true;
                }
                else if (keyCode == KeyCode.LeftArrow)
                {
                    isAutoPlay = false;
                    auto = 0;
                    Player.me.currentMovePoint = null;
                    Player.isMoveRight = false;
                    Player.isMoveLeft = true;
                }
                else if (keyCode == KeyCode.UpArrow)
                {
                    isAutoPlay = false;
                    auto = 0;
                    Player.me.currentMovePoint = null;
                    Player.isMoveDown = false;
                    Player.isMoveUp = true;
                }
                else if (keyCode == KeyCode.DownArrow)
                {
                    isAutoPlay = false;
                    auto = 0;
                    Player.me.currentMovePoint = null;
                    Player.isMoveDown = true;
                    Player.isMoveUp = false;
                }
                else if (keyCode == KeyCode.M)
                {
                    Service.OpenMenu(3);
                }
                else if (keyCode == KeyCode.F2 || keyCode == KeyCode.Tab)
                {
                    Player.me.FindNextFocusByKey();
                }
                else if (keyCode == KeyCode.Alpha1)
                {
                    if (keySkills[0].skill != null)
                    {
                        DoSelectSkill(keySkills[0].skill);
                    }
                }
                else if (keyCode == KeyCode.Alpha2)
                {
                    if (keySkills[1].skill != null)
                    {
                        DoSelectSkill(keySkills[1].skill);
                    }
                }
                else if (keyCode == KeyCode.Alpha3)
                {
                    if (keySkills[2].skill != null)
                    {
                        DoSelectSkill(keySkills[2].skill);
                    }
                }
                else if (keyCode == KeyCode.Alpha4)
                {
                    if (keySkills[3].skill != null)
                    {
                        DoSelectSkill(keySkills[3].skill);
                    }
                }
                else if (keyCode == KeyCode.Alpha5)
                {
                    if (keySkills[4].skill != null)
                    {
                        DoSelectSkill(keySkills[4].skill);
                    }
                }
                else if (keyCode == KeyCode.Q)
                {
                    SearchItem();
                    if (Player.me.itemFocus != null)
                    {
                        PickItem();
                    }
                }
                else if (keyCode == KeyCode.S)
                {
                    bool flag = true;
                    for (int i = 0; i < players.Count; i++)
                    {
                        if (players[i].typePk == 3 || (players[i].bag != null && players[i].bag.id == 0))
                        {
                            flag = false;
                            Player.me.FocusManualTo(players[i]);
                            break;
                        }
                    }
                    if (flag)
                    {
                        for (int i = 0; i < monsters.Count; i++)
                        {
                            if (monsters[i] is BigMonster)
                            {
                                Player.me.FocusManualTo(monsters[i]);
                                break;
                            }
                        }
                    }
                }
                /* else if (ServerManager.instance.isLocal)
                 {
                     if (keyCode == KeyCode.T)
                     {
                         Player.me.SetSkillPaint(SkillManager.instance.paints[45]);
                     }
                     else if (keyCode == KeyCode.J)
                     {
                         Waypoint waypoint = findWayPoint(0);
                         if (waypoint != null)
                         {
                             Player.me.x = (waypoint.maxX + waypoint.minX) / 2;
                             Player.me.y = waypoint.maxY;
                         }
                     }
                     else if (keyCode == KeyCode.L)
                     {
                         Waypoint waypoint = findWayPoint(1);
                         if (waypoint != null)
                         {
                             Player.me.x = (waypoint.maxX + waypoint.minX) / 2;
                             Player.me.y = waypoint.maxY;
                         }
                     }
                     else if (keyCode == KeyCode.K)
                     {
                         Waypoint waypoint = findWayPoint(2);
                         if (waypoint != null)
                         {
                             Player.me.x = (waypoint.maxX + waypoint.minX) / 2;
                             Player.me.y = waypoint.maxY;
                         }
                     }
                 }*/
            }
        }

        public Waypoint findWayPoint(int type)
        {
            foreach (Waypoint waypoint in Map.waypoints)
            {
                if (waypoint.type == type)
                {
                    return waypoint;
                }
            }
            return null;
        }

        private Entity FindClickToItem(int px, int py)
        {
            Entity focus = null;
            int num = 0;

            List<Entity> entities = new List<Entity>();
            if (Player.isSelectMonster)
            {
                entities.AddRange(monsters);
            }
            if (Player.isSelectNpc)
            {
                entities.AddRange(npcs);
            }
            if (Player.isSelectItemMap)
            {
                entities.AddRange(itemMaps);
            }
            foreach (var player in players)
            {
                if ((Player.isSelectEnemy && Player.me.IsCanAttack(player)) || (Player.isSelectPlayer && !Player.isSelectEnemy))
                {
                    entities.Add(player);
                }
            }
            for (int i = 0; i < entities.Count; i++)
            {
                int x = entities[i].x;
                int y = entities[i].y;
                int w = entities[i].w;
                int h = entities[i].h;
                if (px >= x - cmx - w / 2 && px <= x - cmx + w / 2 && py <= y - cmy && py >= y - cmy - h)
                {
                    if (focus == null)
                    {
                        focus = entities[i];
                        num = Math.Abs(px - x) + Math.Abs(py - y);
                    }
                    else
                    {
                        int dis = Math.Abs(px - x) + Math.Abs(py - y);
                        if (dis < num)
                        {
                            focus = entities[i];
                            num = dis;
                        }
                    }
                    if (focus is Npc)
                    {
                        return focus;
                    }
                }
            }
            return focus;
        }

        private void UpdateCamera()
        {
            if (cmx != cmxTo || cmy != cmyTo)
            {
                cmvx = cmxTo - cmx << 2;
                cmvy = cmyTo - cmy << 2;
                cmdx += cmvx;
                cmx += cmdx >> 4;
                cmdx &= 15;
                cmdy += cmvy;
                cmy += cmdy >> 4;
                cmdy &= 15;
            }
            if (!Player.me.isSpaceship)
            {
                LoadCamera(Player.me.x, Player.me.y);
            }
        }

        public void LoadCamera(int x, int y)
        {
            cmxTo = x - screenManager.w / 2;
            if (cmxTo < 30)
            {
                cmxTo = 30;
            }
            if (cmxTo > Map.width - screenManager.w - 30)
            {
                cmxTo = Map.width - screenManager.w - 30;
            }
            cmyTo = y - screenManager.h * 5 / 7;
            if (cmyTo < 0)
            {
                cmyTo = 0;
            }
            if (cmyTo > Map.height - screenManager.h)
            {
                cmyTo = Map.height - screenManager.h;
            }
        }

        public override void SwitchToMe()
        {
            Player.isLoadingMap = false;
            Service.FinishLoadMap();
            base.SwitchToMe();
        }

        public Player FindPlayerInMap(int playerId)
        {
            foreach (Player player in players)
            {
                if (player.id == playerId)
                {
                    return player;
                }
            }
            return null;
        }

        public Npc FindNpcInMap(int npcId)
        {
            foreach (Npc npc in npcs)
            {
                if (npc.template.id == npcId)
                {
                    return npc;
                }
            }
            return null;
        }

        public Monster FindMonsterInMap(int mobId)
        {
            foreach (Monster mob in monsters)
            {
                if (mob.id == mobId)
                {
                    return mob;
                }
            }
            return null;
        }

        public BigMonster FindBigMonster()
        {
            foreach (Monster mob in monsters)
            {
                if (mob is BigMonster)
                {
                    return (BigMonster)mob;
                }
            }
            return null;
        }

        public void OnChatFromMe(string text)
        {
            if (chatTxtField.textField.GetText() == null || chatTxtField.textField.GetText().Equals(string.Empty) || text.Equals(string.Empty) || text == null)
            {
                chatTxtField.Close();
                return;
            }
            if (ServerManager.instance.isLocal)
            {
                if (text.StartsWith("y "))
                {
                    try
                    {
                        Player.me.y = int.Parse(text.Split(' ')[1]);
                    }
                    catch
                    {
                    }
                }
            }
            if (chatTxtField.name.Equals("Chat"))
            {
                Service.chat(text);
                chatTxtField.Close();
                return;
            }
        }

        public void CreateMenu(string title, List<string> menu, Npc npc)
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            for (int i = 0; i < menu.Count; i++)
            {
                menus.Add(new CmdMenu(menu[i], this, 5, npc == null ? 9 : npc.template.id));
            }
            GameCanvas.StartAt(menus, title, npc == null ? 0 : npc.template.avatar);
        }

        public void DoUseBean()
        {
            if (Utils.CurrentTimeMillis() - cmdBean.lastTimeUseBean < 10000)
            {
                return;
            }
            for (int i = 0; i < Player.me.itemsBag.Count; i++)
            {
                if (Player.me.itemsBag[i] != null && Player.me.itemsBag[i].template.type == 19)
                {
                    cmdBean.lastTimeUseBean = Utils.CurrentTimeMillis();
                    Service.ItemAction(Service.ACTION_USE_ITEM, i);
                    SoundMn.eatPeans();
                    return;
                }
            }
        }

        public void Perform(int idAction, object p)
        {

            switch (idAction)
            {
                case 1:
                    {
                        cmdDie.isShow = false;
                        ShowMenuDead();
                        break;
                    }
                case 2:
                    {
                        cmdDie.isShow = true;
                        break;
                    }
                case 3:
                    {
                        Service.returnFromDie();
                        break;
                    }
                case 4:
                    {
                        Service.liveFormDie();
                        break;
                    }
                case 5:
                    {
                        Service.instance.ConfirmMenu((int)p, GameCanvas.menu.indexSelect);
                        break;
                    }
                case 6:
                    {
                        screenManager.panel.SetType(TabPanel.tabInventory.type);
                        screenManager.panel.Show();
                        break;
                    }
                case 7:
                    {
                        ShowMenuOther();
                        break;
                    }
                case 8:
                    {
                        ShowMenuOption();
                        break;
                    }
                case 9:
                    {
                        // tin nhan
                        screenManager.panel.SetType(TabPanel.tabChatGlobal.type);
                        screenManager.panel.Show();
                        break;
                    }
                case 10:
                    {
                        Logout();
                        // thoat game
                        break;
                    }
                case 11:
                    {
                        // show task
                        screenManager.panel.SetType(4);
                        screenManager.panel.Show();
                        break;
                    }
                case 12:
                    {
                        // bang hoi
                        InfoDlg.ShowWait();
                        Service.ClanAction(-1, -1, null);
                        break;
                    }
                case 13:
                    {
                        screenManager.panel.SetType(TabPanel.tabTeam.type);
                        screenManager.panel.Show();
                        // nhom
                        break;
                    }
                case 14:
                    {
                        InfoDlg.ShowWait();
                        Service.instance.FriendActions(Service.FRIEND_ACTION_SHOW, -1, null);
                        // ban be
                        break;
                    }
                case 15:
                    {
                        InfoDlg.ShowWait();
                        Service.EnemyActions(Service.ENEMY_ACTION_SHOW, -1);
                        // ke thu
                        break;
                    }
                case 16:
                    {
                        // trang thai pk
                        //ShowMenuFlag();
                        screenManager.panel.SetType(TabPanel.tabFlag.type);
                        screenManager.panel.Show();
                        break;
                    }
                case 17:
                    {
                        // Khu vuc
                        Service.OpenMenu(3);


                        break;
                    }
                case 18:
                    {
                        // chat
                        chatTxtField.name = "Chat";
                        chatTxtField.textField.name = "chat";
                        chatTxtField.textField.isFocus = true;
                        chatTxtField.isShow = true;
                        break;
                    }
                case 19:
                    {
                        // menu
                        ShowMenu();
                        break;
                    }
                case 20:
                    {
                        // change focus
                        Player.me.FindNextFocusByKey();
                        break;
                    }
                case 21:
                    {
                        // use pean
                        DoUseBean();
                        break;
                    }
                case 22:
                    {
                        // skill special
                        break;
                    }
                case 23:
                    {
                        // do fire
                        if (!Player.me.IsDead())
                        {
                            ActionEnter();
                        }
                        break;
                    }
                case 24:
                    {
                        ShowMenuPlayer();
                        break;
                    }
                case 25:
                    {
                        // xem thong tin
                        Service.RequestInfoPlayer((int)p);
                        break;
                    }
                case 26:
                    {
                        // moi vao nhom
                        Service.TeamAction(0, (int)p);
                        break;
                    }
                case 27:
                    {
                        // chat rieng
                        Player player = (Player)p;
                        screenManager.panel.OpenChat(player);
                        break;
                    }
                case 28:
                    {
                        // giao dich
                        Service.TradeAction(0, (int)p, -1, -1);
                        break;
                    }
                case 29:
                    {
                        // ti thi
                        Service.soloActions((int)p);
                        break;
                    }
                case 30:
                    {
                        // cuu sat
                        break;
                    }
                case 31:
                    {
                        Service.instance.FriendActions(Service.FRIEND_ACTION_ADD, (int)p, null);
                        // ket ban
                        break;
                    }
                case 32:
                    {
                        // change flag
                        int type = (int)p;
                        if (type < 2)
                        {
                            Service.changePk(0, type - 1);
                        }
                        else
                        {
                            Service.changePk(1, type - 1);
                        }
                        break;
                    }
                case 33:
                    {

                        break;
                    }
                case 34:
                    {
                        // moi vao bang
                        Service.ClanAction(0, (int)p, null);
                        break;
                    }
                case 35:
                    {
                        // cài đặt
                        screenManager.panel.SetType(TabPanel.tabSetting.type);
                        screenManager.panel.Show();
                        break;
                    }
                case 36:
                    {
                        // nap tien
                        Application.OpenURL("https://rongthanmax.online");
                        break;
                    }
                case 37:
                    {
                        if (keySkills[(int)p].skill != null)
                        {
                            DoSelectSkill(keySkills[(int)p].skill);
                        }
                        break;
                    }
                case 38:
                    {
                        InfoDlg.ShowWait();
                        Service.OpenMenu(3);
                        break;
                    }
                case 39:
                    {
                        // gift
                        InfoDlg.ShowWait();
                        Service.MissionAction(-1, -1);
                        break;
                    }
                case 40:
                    {
                        // hoạt động trong game
                        Service.OpenMenu(-1);
                        break;
                    }
                case 41:
                    {
                        bool flag = false;
                        for (int i = 0; i < Player.me.itemsBag.Count; i++)
                        {
                            Item item = Player.me.itemsBag[i];
                            if (item != null && (item.template.id == 84 || item.template.id == 85))
                            {
                                flag = true;
                                Service.ItemAction(Service.ACTION_USE_ITEM, i);
                                break;
                            }
                        }
                        if (!flag)
                        {
                            InfoMe.addInfo("Không tìm thấy Capsule trong túi đồ", 0);
                        }
                        break;
                    }
                case 42:
                    {
                        if (isAutoFindMob)
                        {
                            auto = 0;
                            isAutoFindMob = false;
                            isAutoPlay = false;
                            InfoMe.addInfo("Đã tắt auto", 0);
                        }
                        else
                        {
                            foreach (Item item in Player.me.itemsBag)
                            {
                                if (item != null && item.template.id == 92)
                                {
                                    bool flag = false;
                                    foreach (Effect effect in Player.me.effects)
                                    {
                                        if (effect is EffectTime)
                                        {
                                            EffectTime effectTime = (EffectTime)effect;
                                            if (effectTime.template.id == 14)
                                            {
                                                flag = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!flag && item.GetParam(24) > 0)
                                    {
                                        DisplayManager.instance.StartDialogYesNo("Bạn có muốn sử dụng " + item.template.name + " không?", new Command("Có", this, 43, 0), new Command("Không", this, 43, 1));
                                        return;
                                    }
                                    break;
                                }
                            }
                            isAutoFindMob = true;
                            isAutoPlay = true;
                            InfoMe.addInfo("Đã bật auto", 1);
                        }
                        break;
                    }
                case 43:
                    {
                        DisplayManager.instance.dialog.Close();
                        int type = (int)p;
                        if (type == 0)
                        {
                            foreach (Item item in Player.me.itemsBag)
                            {
                                if (item != null && item.template.id == 92)
                                {
                                    bool flag = false;
                                    foreach (Effect effect in Player.me.effects)
                                    {
                                        if (effect is EffectTime)
                                        {
                                            EffectTime effectTime = (EffectTime)effect;
                                            if (effectTime.template.id == 14)
                                            {
                                                flag = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!flag && item.GetParam(24) > 0)
                                    {
                                        Service.ItemAction(Service.ACTION_USE_ITEM, item.indexUI);
                                    }
                                    break;
                                }
                            }
                        }
                        isAutoFindMob = true;
                        isAutoPlay = true;
                        InfoMe.addInfo("Đã bật auto", 1);
                        break;
                    }
                case 44:
                    {
                        ScreenManager.instance.panel.SetType(TabPanel.tabInventory.type);
                        ScreenManager.instance.panel.Show();
                        break;
                    }
                case 45:
                    {
                        InfoDlg.ShowWait();
                        Service.ClanAction(-1, -1, null);
                        break;
                    }
                case 46:
                    {
                        ScreenManager.instance.panel.SetType(TabPanel.tabChatGlobal.type);
                        ScreenManager.instance.panel.Show();
                        break;
                    }
                case 47:
                    {
                        ShowMenuOption();
                        break;
                    }
                case 48:
                    {
                        int action = (int)p;
                        if (action == -1)
                        {
                            DisplayManager.instance.dialog.SetInfo("Bạn có chắc chắn muốn thoát game không?", new Command("Có", this, 48, 0), null, new Command("Không", DisplayManager.instance, 1, null));
                        }
                        else
                        {
                            Logout();
                        }
                        break;
                    }
                case 49:
                    {
                        screenManager.panel.SetType(4);
                        screenManager.panel.Show();
                        break;
                    }
                case 50:
                    {
                        Service.OpenMenu(47);
                        break;
                    }
                case 51:
                    {
                        Service.instance.ViewLucky();
                        break;
                    }
                case 52:
                    {
                        Service.instance.ConsignmentAction(-1, -1, -1, -1, -1);
                        break;
                    }
                case 53:
                    {
                        Service.instance.ViewTop();
                        break;
                    }
                case 54:
                    {
                        bool flag = false;
                        for (int i = 0; i < Player.me.itemsBag.Count; i++)
                        {
                            Item item = Player.me.itemsBag[i];
                            if (item != null && item.template.id == 254)
                            {
                                flag = true;
                                Service.ItemAction(Service.ACTION_USE_ITEM, i);
                                break;
                            }
                        }
                        if (!flag)
                        {
                            InfoMe.addInfo("Không tìm thấy thẻ tiện ích trong túi đồ", 0);
                        }
                        break;
                    }
                case 60:
                    {
                        // Đi đến mục tiêu nhiệm vụ
                        if (Player.me.task != null && !isShowTaskOrther)
                        {
                            TaskSub currentTask = Player.me.task.subTasks[Player.me.task.index];
                            if (currentTask.npcId != -1)
                            {
                                Npc npc = FindNpcInMap(currentTask.npcId);
                                if (npc != null)
                                {
                                    Player.me.FocusManualTo(npc);
                                    Player.me.currentMovePoint = new MovePoint(npc.x, npc.y + 18);
                                    InfoMe.addInfo("Đang di chuyển đến " + npc.template.name, 1);
                                }
                                else
                                {
                                    InfoMe.addInfo("Không tìm thấy NPC trong khu vực này", 0);
                                }
                            } else
                            {
                                //gửi mess tele
                                InfoMe.addInfo("gửi mess tele " + currentTask.mapId + " xy: " + currentTask.xy, 0);
                                Service.instance.SendMessTaskTele(currentTask.mapId, currentTask.xy);
                            }
                        }
                        break;
                    }
                case 61:
                    {
                        // Xem chi tiết nhiệm vụ chính
                        if (Player.me.task != null && !isShowTaskOrther)
                        {
                            TaskSub currentTask = Player.me.task.subTasks[Player.me.task.index];

                            string taskInfo = "=== NHIỆM VỤ CHÍNH ===\n";
                            taskInfo += "Tên: " + currentTask.name + "\n";

                            if (currentTask.param > 0)
                            {
                                int percent = (int)((float)Player.me.task.param / currentTask.param * 100);
                                taskInfo += "Tiến độ: " + Player.me.task.param + "/" + currentTask.param + " (" + percent + "%),";
                            }

                            taskInfo += "Giai đoạn: " + (Player.me.task.index + 1) + "/" + Player.me.task.subTasks.Count + "\n";

                            if (currentTask.npcId != -1)
                            {
                                Npc npc = FindNpcInMap(currentTask.npcId);
                                if (npc != null)
                                {
                                    taskInfo += "NPC: " + npc.template.name + "\n";
                                    taskInfo += "Vị trí: (" + npc.x + ", " + npc.y + ")";
                                }
                                else
                                {
                                    taskInfo += "NPC: Không ở khu vực này";
                                }
                            }

                            GameCanvas.StartDialogOk(taskInfo);
                        }
                        break;
                    }
                case 999:
                    {
                        isCollapsedMenu = !isCollapsedMenu;
                        break;
                    }
            }
        }

        public string GetTaskInfoString()//hoang task
        {
            if (Player.me.task == null)
            {
                return "Không có nhiệm vụ";
            }

            string result = "";

            if (!isShowTaskOrther)
            {
                // Nhiệm vụ chính
                TaskSub currentTask = Player.me.task.subTasks[Player.me.task.index];
                result += "Nhiệm vụ: " + currentTask.name + "\n";

                if (currentTask.param > 0)
                {
                    int percent = (int)((float)Player.me.task.param / currentTask.param * 100);
                    result += "Tiến độ: " + Player.me.task.param + "/" + currentTask.param + " (" + percent + "%)\n";
                }

                result += "Giai đoạn: " + (Player.me.task.index + 1) + "/" + Player.me.task.subTasks.Count;
            }
            else
            {
                if (Player.me.taskDaily != null)
                {
                    result = "Nhiệm vụ hàng ngày: " + Player.me.taskDaily.ToString();
                }
                else
                {
                    result = "Chưa có nhiệm vụ hàng ngày";
                }
            }

            return result;
        }
        public void ShowMenu()
        {
            //GameCanvas.menuBar.Show();
            screenManager.panel.SetType(0);
            screenManager.panel.Show();
        }

        public void ShowMenuOther()
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            menus.Add(new CmdMenu("Nhiệm vụ", this, 11, null));
            menus.Add(new CmdMenu("Hoạt động trong game", this, 40, null));
            GameCanvas.StartAt(menus);
        }

        public void ShowMenuOption()
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            menus.Add(new CmdMenu("Mã bảo vệ", this, 50, null));
            menus.Add(new CmdMenu("Tổ đội", this, 13, null));
            menus.Add(new CmdMenu("Bạn bè", this, 14, null));
            menus.Add(new CmdMenu("Kẻ thù", this, 15, null));
            menus.Add(new CmdMenu("Trạng thái PK", this, 16, null));
            menus.Add(new CmdMenu("Cài đặt", this, 35, null));
            GameCanvas.StartAt(menus);
        }

        public void ShowMenuFlag()
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            for (int i = 0; i < strFlags.Length; i++)
            {
                menus.Add(new CmdMenu(strFlags[i], this, 32, i));
            }
            GameCanvas.StartAt(menus);
        }

        public void ShowMenuClan()
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            menus.Add(new CmdMenu("Thông tin", this, 36, null));
            menus.Add(new CmdMenu("Thành viên", this, 37, null));
            GameCanvas.StartAt(menus);
        }

        public void ShowMenuPlayer()
        {
            List<CmdMenu> menus = new List<CmdMenu>();
            menus.Add(new CmdMenu("Xem thông tin", this, 25, Player.me.playerFocus.id));
            menus.Add(new CmdMenu("Mời vào tổ đội", this, 26, Player.me.playerFocus.id));
            if (Player.me.clan != null && Player.me.clan.roleId < 2 && Player.me.playerFocus.clan == null)
            {
                menus.Add(new CmdMenu("Mời vào bang hội", this, 34, Player.me.playerFocus.id));
            }
            menus.Add(new CmdMenu("Nhắn tin\n" + Player.me.playerFocus.name, screenManager.panel, 35, Player.me.playerFocus));
            if (Player.me.level >= 10)
            {
                menus.Add(new CmdMenu("Giao dịch", this, 28, Player.me.playerFocus.id));
            }
            if (Player.me.level >= 5)
            {
                menus.Add(new CmdMenu("Thách đấu", this, 29, Player.me.playerFocus.id));
            }
            //menus.Add(new CmdMenu("Cừu sát", this, 30, Player.me.playerFocus.id));
            menus.Add(new CmdMenu("Kết bạn", this, 31, Player.me.playerFocus.id));
            GameCanvas.StartAt(menus);
        }

        private void ShowMenuDead()
        {
            List<CmdMenu> menu = new List<CmdMenu>();
            menu.Add(new CmdMenu(PlayerText.DIES[1], this, 2, null));
            menu.Add(new CmdMenu(PlayerText.DIES[2], this, 3, null));
            menu.Add(new CmdMenu(PlayerText.DIES[3], this, 4, null));
            GameCanvas.StartAt(menu);
        }

        public static MessageTime GetMessageTimeById(int id)
        {
            return messageTimes.FirstOrDefault(t => t.id == id);
        }

        public void PickItem()
        {
            if (Player.me.itemFocus == null)
            {
                return;
            }
            Player.me.dir = Player.me.x > Player.me.itemFocus.x ? (-1) : 1;
            if (Math.Abs(Player.me.x - Player.me.itemFocus.x) > 50 || Math.Abs(Player.me.y - Player.me.itemFocus.y) > 50)
            {
                Player.me.currentMovePoint = new MovePoint(Player.me.itemFocus.x, Player.me.itemFocus.y);
            }
            else
            {
                Service.instance.PickItem(Player.me.itemFocus.id);
            }
        }

        public bool IsMeCanAttackMonster(Monster monster)
        {
            if (monster == null)
            {
                return false;
            }
            return !monster.IsDead();
        }

        public void DoSelectSkill(Skill skill)
        {
            if (Player.me.isBlind || Player.me.isChocolate || Player.me.isBindByAmulet)
            {
                return;
            }
            if (skill == null)
            {
                return;
            }
            if (Player.me.skillPaint != null)
            {
                return;
            }
            if (Player.me.dart != null)
            {
                return;
            }
            if (Player.me.mySkill != skill)
            {
                Player.me.mySkill = skill;
            }
            if (Player.me.IsSkillUseAlone())
            {
                Player.me.vx = 0;
                Player.me.vy = 0;
                if (Player.me.IsCanUseMySkill())
                {
                    Player.me.UseMySkill();
                }
            }
            else if (Player.me.monsterFocus != null || Player.me.playerFocus != null)
            {
                if (Player.me.monsterFocus != null)
                {
                    Player.me.dir = (Player.me.x < Player.me.monsterFocus.x) ? 1 : -1;
                    if (Player.me.monsterFocus.IsDead())
                    {
                        return;
                    }
                }
                if (Player.me.playerFocus != null)
                {
                    Player.me.dir = (Player.me.x < Player.me.playerFocus.x) ? 1 : -1;
                }
                DoFire();
            }
        }

        public void OnChatFromMe(string name, List<TextField> textFields)
        {
        }

        public void ActionEnter()
        {
            Waypoint waypoint = Player.me.GetWaypoint();
            if (waypoint != null && waypoint.type == 2)
            {
                Service.instance.RequestChangeMap();
                Player.isLockKey = true;
                Player.isChangingMap = true;
                InfoDlg.ShowWait();
                return;
            }
            if (Player.me.mySkill != keySkills[0].skill)
            {
                //Service.instance.SelectSkill(keySkills[0].skill.template.id);
                Player.me.mySkill = keySkills[0].skill;
            }
            if (Player.me.npcFocus == null && Player.me.itemFocus == null)
            {
                long now = Utils.CurrentTimeMillis();
                if (Player.me.monsterFocus != null)
                {
                    isAutoPlay = true;
                }
                if (auto == 0)
                {
                    if (now - lastTimeFire < 1500 && (Player.me.monsterFocus != null || (Player.me.playerFocus != null && Player.me.IsCanAttack(Player.me.playerFocus))))
                    {
                        auto = 10;
                    }
                }
                else
                {
                    auto = 0;
                }
                lastTimeFire = now;
            }
            DoFire();
        }

        public void DoFire()
        {
            if (Player.me.IsDead())
            {
                return;
            }
            Player.me.vx = 0;
            Player.me.vy = 0;
            if (IsAttack() && Player.me.IsCanUseMySkill())
            {
                Player.me.UseMySkill();
            }
        }

        public bool IsAttack()
        {
            if (chatTxtField.isShow || GameCanvas.clientInput.isShow)
            {
                return false;
            }
            if (InfoDlg.isLock || Player.isLockKey)
            {
                return false;
            }
            if (Player.me.itemFocus != null)
            {
                PickItem();
                return false;
            }
            if (Player.me.isBlind || Player.me.isChocolate || Player.me.isBindByAmulet)
            {
                return false;
            }
            if (Player.me.mySkill != null && Player.me.mySkill.template.type == 1)
            {
                if (Player.me.mySkill.template.id == SkillName.THAI_DUONG_HA_SAN)
                {
                    if (Player.me.playerFocus != null)
                    {
                        return Player.me.IsCanAttack(Player.me.playerFocus);
                    }
                    return true;
                }
                return Player.me.IsCanUseMySkill();
            }
            if (Player.me.skillPaint != null || (Player.me.monsterFocus == null && Player.me.npcFocus == null && Player.me.playerFocus == null && Player.me.itemFocus == null))
            {
                return false;
            }
            if (Player.me.monsterFocus != null)
            {
                if (!IsMeCanAttackMonster(Player.me.monsterFocus))
                {
                    return false;
                }
                if (Player.me.mySkill == null)
                {
                    return false;
                }
                if (Player.me.mySkill.template.id == SkillName.TRI_THUONG)
                {
                    return false;
                }
                if (!Player.me.IsCanUseMySkill())
                {
                    return false;
                }
                if (Player.me.x < Player.me.monsterFocus.x)
                {
                    Player.me.dir = 1;
                }
                else
                {
                    Player.me.dir = -1;
                }
                int disX = Math.Abs(Player.me.x - Player.me.monsterFocus.x);
                int disY = Math.Abs(Player.me.y - Player.me.monsterFocus.y);
                Player.me.vx = 0;
                if (disX <= Player.me.mySkill.GetDx() && disY <= Player.me.mySkill.GetDy())
                {
                    if (Player.me.mySkill.template.id == 11)
                    {
                        return true;
                    }
                    if (Player.me.mySkill.template.id == 10)
                    {
                        return false;
                    }
                    if (disY > disX && Math.Abs(Player.me.y - Player.me.monsterFocus.y) > 30 && Player.me.monsterFocus.template.type == 2)
                    {
                        Player.me.currentMovePoint = new MovePoint(Player.me.x + Player.me.dir, Player.me.monsterFocus.y);
                        return false;
                    }
                    int num = 60;
                    bool flag = false;
                    if (Player.me.mySkill.GetDx() > 150)
                    {
                        num = 60;
                    }
                    bool flag2 = false;
                    if (Map.IsWall(Player.me.x, Player.me.y + 3))
                    {
                        int num4 = ((Player.me.x > Player.me.monsterFocus.x) ? 1 : (-1));
                        if (!Map.IsWall(Player.me.monsterFocus.x + num * num4, Player.me.y + 3))
                        {
                            flag2 = true;
                        }
                    }
                    if (disX <= num && !flag2)
                    {
                        if (disX >= 90)
                        {
                            int num5 = ((Player.me.x <= Player.me.monsterFocus.x) ? (-num) : num);
                            Player.me.currentMovePoint = new MovePoint(Player.me.x + num5, Player.me.y);
                            return false;
                        }
                        if (Player.me.x > Player.me.monsterFocus.x)
                        {
                            Player.me.x = Player.me.monsterFocus.x + num + (flag ? 90 : 0);
                            Player.me.dir = -1;
                        }
                        else
                        {
                            Player.me.x = Player.me.monsterFocus.x - num - (flag ? 90 : 0);
                            Player.me.dir = 1;
                        }
                        Service.instance.PlayerMove();
                    }
                    return true;
                }
                int num6 = (Player.me.mySkill.GetDx() - 60) * ((Player.me.x > Player.me.monsterFocus.x) ? 1 : (-1));
                if (disX <= Player.me.mySkill.GetDx())
                {
                    num6 = 0;
                }
                Player.me.currentMovePoint = new MovePoint(Player.me.monsterFocus.x + num6, Player.me.monsterFocus.y);
                return false;
            }
            if (Player.me.npcFocus != null)
            {
                if (Player.me.x < Player.me.npcFocus.x)
                {
                    Player.me.npcFocus.dir = -1;
                }
                else
                {
                    Player.me.npcFocus.dir = 1;
                }
                int disX = Math.Abs(Player.me.x - Player.me.npcFocus.x);
                int disY = Math.Abs(Player.me.y - Player.me.npcFocus.y);
                if (disX < 200)
                {
                    if (tMenuDelay == 0)
                    {
                        tMenuDelay = 50;
                        InfoDlg.ShowWait();
                        Service.instance.PlayerMove();
                        Service.OpenMenu(Player.me.npcFocus.template.id);
                    }
                }
                else
                {
                    int randomMove = (20 + Utils.r.Next(20)) * ((Player.me.x > Player.me.npcFocus.x) ? 1 : (-1));
                    Player.me.currentMovePoint = new MovePoint(Player.me.npcFocus.x + randomMove, Player.me.npcFocus.y + 18);
                }
                return false;
            }
            if (Player.me.playerFocus != null)
            {
                if (Player.me.x < Player.me.playerFocus.x)
                {
                    Player.me.dir = 1;
                }
                else
                {
                    Player.me.dir = -1;
                }
                int num10 = Math.Abs(Player.me.x - Player.me.playerFocus.x);
                int num11 = Math.Abs(Player.me.y - Player.me.playerFocus.y);
                if (Player.me.IsCanAttack(Player.me.playerFocus))
                {
                    if (Player.me.mySkill == null)
                    {
                        return false;
                    }
                    if (!Player.me.IsCanUseMySkill())
                    {
                        return false;
                    }
                    Player.me.vx = 0;
                    if (num10 <= Player.me.mySkill.GetDx() && num11 <= Player.me.mySkill.GetDy())
                    {
                        if (Player.me.mySkill.template.id == SkillName.THAI_DUONG_HA_SAN)
                        {
                            return true;
                        }
                        int num12 = 60;
                        if (Player.me.mySkill.GetDx() > 150)
                        {
                            num12 = 100;
                        }
                        bool flag4 = false;
                        if (Map.IsWall(Player.me.x, Player.me.y + 3))
                        {
                            int num13 = ((Player.me.x > Player.me.playerFocus.x) ? 1 : (-1));
                            if (!Map.IsWall(Player.me.playerFocus.x + num12 * num13, Player.me.y + 3))
                            {
                                flag4 = true;
                            }
                        }
                        if (num10 <= num12 && !flag4)
                        {
                            if (Player.me.x > Player.me.playerFocus.x)
                            {
                                Player.me.x = Player.me.playerFocus.x + num12;
                                Player.me.dir = -1;
                            }
                            else
                            {
                                Player.me.x = Player.me.playerFocus.x - num12;
                                Player.me.dir = 1;
                            }
                            Service.instance.PlayerMove();
                        }
                        return true;
                    }
                    int num14 = (Player.me.mySkill.GetDx() - 60) * ((Player.me.x > Player.me.playerFocus.x) ? 1 : (-1));
                    if (num10 <= Player.me.mySkill.GetDx())
                    {
                        num14 = 0;
                    }
                    Player.me.currentMovePoint = new MovePoint(Player.me.playerFocus.x + num14, Player.me.playerFocus.y);
                    return false;
                }
                if (num10 < 60 && num11 < 40)
                {
                    if (!Player.me.playerFocus.isDisciple && !Player.me.playerFocus.isBoss)
                    {
                        cmdQues.x = Player.me.playerFocus.x;
                        cmdQues.y = Player.me.playerFocus.y - Player.me.playerFocus.h - cmdQues.h - 50;
                        cmdQues.isShow = true;
                    }
                }
                else
                {
                    int num15 = (20 + Utils.r.Next(20)) * ((Player.me.x > Player.me.playerFocus.x) ? 1 : (-1));
                    Player.me.currentMovePoint = new MovePoint(Player.me.playerFocus.x + num15, Player.me.playerFocus.y);
                }
                return false;
            }
            return true;
        }

    }
}
