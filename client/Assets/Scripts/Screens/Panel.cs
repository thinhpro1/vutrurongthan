using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Npcs;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Services;
using Assets.Scripts.Skills;
using Assets.Scripts.Sounds;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class Panel : IActionListener, IChatable
    {
        public ScreenManager screenManager;
        public static Image imgBgrItem;
        private Image imgBgr;
        private Image imgBgrBig;
        private Image imgBgrSmall;
        private Image imgTitle;
        private Image imgTitleFocus;
        private Image imgTitleSelect;
        private Image imgTitleSelectFocus;
        private Image imgBorderCoin;
        private Image imgBorderDiamond;
        private Image imgCoin;
        public static Image imgDiamond;
        private Image imgWallBag;
        private Image imgBgrInfoPlayer;
        private Image imgBgrInfoUpgrade;
        private Image imgBgrInfoBag;
        private Image imgBgrInfoCsBag;
        private Image imgIconHp;
        private Image imgIconMp;
        private Image imgIconDmg;
        private Image imgCoinLock;
        public static Image imgRuby;
        public static Image[] effectItemUpgrades;
        public static Image[] effectRedItemUpgrades;
        public static Image[] effectYellowItemUpgrades;
        public static Image[] bgrItemUpgrades;
        public static Image imgSelectItem;
        private Image imgBgrSkill;
        public static Image imgBgrPotential;
        public static Image imgBgrPotentialFocus;
        public static Image imgItemShop;
        public static Image imgItemShopFocus;
        public static Image imgItemShopClick;
        public static Image imgItemTrade;
        public static Image imgItemTradeFocus;
        public static Image imgItemTradeClick;
        public static Image imgArea;
        public static Image imgBgrAchivement;
        public static Image imgBgrGift;
        public static Image imgBtnSmall;
        public static Image imgBtnSmallFocus;
        public static Image imgBtnSmallClick;
        public static Image imgBtnMini;
        public static Image imgBtnMiniFocus;
        public static Image imgBtnMiniClick;
        public static Image imgClanMember;
        public static Image imgClanMemberFocus;
        public static Image imgClanMemberClick;
        private Image imgHintWay;
        public static Image[] imgGender;
        private Command cmdClose;
        private CmdBag cmdBag;
        private CmdBag cmdBox;
        private CmdBag cmdOutFit;
        private CmdBag cmdOutFitOther;
        private CmdBag cmdInfoPlayer;
        private ItemPanel itemUpgrade;
        private ItemPanel itemNextUpgrade;
        private List<ItemPanel> itemsUpgrade;
        private List<ItemPanel> items;
        private ItemPanel[] itemsBody;
        private List<CmdSkill> skills;
        private List<TabPanel> tabs;
        public List<CmdArea> areas;
        public List<CmdFriend> friends;
        private List<CmdMini> cmdFriends;
        public List<CmdFriend> enemys;
        public List<CmdTop> tops;
        public List<CmdMap> mapsSpaceship;
        public List<CmdAchievement> achievements;
        public List<CmdGift> gifts;
        public List<CmdIntrinsic> intrinsics;
        public List<CmdTeam> teams;
        private List<CmdMini> cmdTeams;
        private List<CmdMini> cmdClans;
        public List<CmdMessage> cmdChatGlobals;
        public List<CmdMessage> cmdChatServers;
        public List<CmdMessage> cmdChatTeams;
        public List<CmdMessage> cmdChatClans;
        public List<CmdPlayerMessage> cmdChatPlayers;
        private List<CmdSetting> cmdSettings;
        public List<CmdPlayerMiniGame> cmdPlayerMiniGames;
        public List<CmdPlayerInMap> cmdPlayerInMaps;
        public List<CmdSettingFocus> cmdSettingFocus;
        private List<CmdFlag> cmdFlags;
        private CmdPotential[] cmdPotentials;
        public TabInfo tabInfo;
        private ChatTextField chatTxtField;
        public CmdMini cmdUpgrade;
        public Dictionary<string, List<Item>> itemsShop;
        public List<CmdReward> cmdRewards;
        private ClientInput clientInput;
        private Command cmdChat;
        private TextField textChat;
        public Player playerMenu;
        public MonsterPet petMenu;
        public Player[] viewers;
        public List<Item> itemsTrade;
        public List<Item> itemsPlayerTrade;
        public CmdMini cmdTrade;
        private Command cmdAddCoin;
        private bool isShowBag;
        private int typeShowOutFit;
        public bool isShow;
        private int x;
        private int y;
        private int w;
        private int h;
        private int tabIndex;
        public int indexSelect;
        private int xScroll;
        private int yScroll;
        private int wScroll;
        private int hScroll;
        private int cmy;
        private int cmyTo;
        private int cmyLim;
        private int cmx;
        private int cmxTo;
        private int cmxLim;
        private int xScrollInfo;
        private int yScrollInfo;
        private int wScrollInfo;
        private int hScrollInfo;
        private int cmyInfo;
        private int cmyInfoTo;
        private int cmyInfoLim;
        private int cmxInfo;
        private int cmxInfoTo;
        private int cmxInfoLim;
        public int type;
        private int xPlayerBag;
        private int yPlayerBag;
        private int xInfoPlayerBag;
        private int yInfoPlayerBag;
        public bool isNewMessageClan;
        public bool isNewMessageTeam;
        private bool isPointerDownInScroll;
        private bool isPointerDownInScrollInfo;
        private int xPointerDown;
        private int yPointerDown;
        private int pointerX;
        private int pointerY;
        private string upPointPotentialInfo;
        public List<string> infosUpgrade;
        public List<string> notifications;
        public List<int> indexsUpgrade;
        private int indexUpgrade = -1;
        public int typeUpgrade;
        public int typeShop;
        public int pointShop;
        public long coinTrade;
        public long coinPlayerTrade;
        public bool isLock;
        public bool isPlayerLock;
        public bool isAccept;
        public bool isPlayerAccept;
        private long[] STONE_UPGRADE = new long[] { 10, 30, 120, 480, 768, 1152, 2304, 9216, 16384, 40960, 163840, 327680, 655360, 1310720, 2621440, 5242880, 14141867, 23723806, 41516660 };
        private int[] XU_UPGRADE_STONE = new int[] { 500, 1000, 2000, 4000, 10000, 40000, 100000, 250000, 600000, 1000000, 2000000 };
        private long[] STONE = new long[] { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 3096576 };
        private string[] strFlags;
        private string[] infoFlags;
        public int[] iconFlags;
        private bool isAutoChat;
        private int delayChat;
        private string contentChat;
        private string strChat;
        private const int VIEW_ITEM_BAG = 0;
        private const int VIEW_ITEM_BODY = 1;
        private const int VIEW_ITEM_BOX = 2;
        private const int VIEW_ITEM_UPGRADE_BAG = 3;
        private const int VIEW_ITEM_UPGRADE_TAB = 4;
        private const int VIEW_ITEM_UPGRADE_LIST = 5;
        private const int VIEW_TIEM_SHOP = 6;
        private const int VIEW_ITEM_SHOP_BAG = 7;
        private const int VIEW_ITEM_TRADE_BAG = 8;
        private const int VIEW_ITEM_TRADE = 9;
        private const int VIEW_ITEM_TRADE_PLAYER = 10;
        private const int VIEW_ITEM_VIEW_PLAYER = 11;
        private const int VIEW_ITEM_DISCIPLE = 12;
        private const int VIEW_TIEM_REWARD = 13;
        private const int VIEW_ITEM_PET = 14;
        private const int VIEW_ITEM_GIFT = 15;
        private int indexTabDisciple;
        private List<CmdBag> cmdTabDisciples;
        public int giftType;
        public bool[] isLightBag;
        public Thread autoItem;
        public int statusPickMe;
        public string winnerNamePickMe = "";
        public string winnerCoinPickMe;
        public string winnerCoinJoinedPickMe;
        public long totalPickMe;
        public int countPlayerPickMe;
        public long coinJoinPickMe;
        public long endTimePickMe;
        public List<string> randomsPickMe = new List<string>();
        public string resultPickMe = "";
        public int cmyPickMe = 0;
        public int speedPickMe = 12;
        public int indexPickMe = 0;
        public int pickMeID = -1;
        public CmdMini cmdHuongDanPickMe;
        public CmdMini cmdJoinPickMe;
        public CmdMini cmdLucky;
        public List<Item> itemsLucky = new List<Item>();
        public int indexLucky = -1;
        public string requireLucky = "50 Kim cương";

        public Panel(ScreenManager screenManager)
        {
            this.screenManager = screenManager;
            imgBgrBig = GameCanvas.LoadImage("MainImages/Panels/img_bgr");
            imgBgrSmall = GameCanvas.LoadImage("MainImages/Panels/img_bgr_small");
            cmdClose = new Command("MainImages/Panels/btn_close", "MainImages/Panels/btn_close_focus", "MainImages/Panels/btn_close_click", this, 1, null);
            imgTitle = GameCanvas.LoadImage("MainImages/Panels/img_title");
            imgTitleFocus = GameCanvas.LoadImage("MainImages/Panels/img_title_focus");
            imgTitleSelect = GameCanvas.LoadImage("MainImages/Panels/img_title_select");
            imgTitleSelectFocus = GameCanvas.LoadImage("MainImages/Panels/img_title_select_focus");
            imgBgrItem = GameCanvas.LoadImage("MainImages/Panels/img_bgr_item");
            imgBorderCoin = GameCanvas.LoadImage("MainImages/Panels/img_border_coin");
            imgBorderDiamond = GameCanvas.LoadImage("MainImages/Panels/img_border_diamond");
            imgCoin = GameCanvas.LoadImage("MainImages/Panels/img_coin");
            imgDiamond = GameCanvas.LoadImage("MainImages/Panels/img_diamond");
            imgCoinLock = GameCanvas.LoadImage("MainImages/Panels/img_coin_lock");
            imgRuby = GameCanvas.LoadImage("MainImages/Panels/img_ruby");
            Image imgBag = GameCanvas.LoadImage("MainImages/Panels/btn_bag");
            Image imgBagClick = GameCanvas.LoadImage("MainImages/Panels/btn_bag_click");
            imgWallBag = GameCanvas.LoadImage("MainImages/Panels/img_wall_bag");
            imgBgrInfoPlayer = GameCanvas.LoadImage("MainImages/Panels/img_bgr_info_player");
            imgBgrInfoUpgrade = GameCanvas.LoadImage("MainImages/Panels/img_bgr_info_upgrade");
            imgBgrInfoBag = GameCanvas.LoadImage("MainImages/Panels/img_bgr_info_bag");
            imgBgrInfoCsBag = GameCanvas.LoadImage("MainImages/Panels/img_bgr_info_cs_bag");
            imgIconHp = GameCanvas.LoadImage("MainImages/Panels/img_icon_hp");
            imgIconMp = GameCanvas.LoadImage("MainImages/Panels/img_icon_mp");
            imgIconDmg = GameCanvas.LoadImage("MainImages/Panels/img_icon_dmg");
            imgSelectItem = GameCanvas.LoadImage("MainImages/Panels/img_click_item");
            imgBgrSkill = GameCanvas.LoadImage("MainImages/Panels/img_bgr_skill");
            imgBgrPotential = GameCanvas.LoadImage("MainImages/Panels/img_bgr_potential");
            imgBgrPotentialFocus = GameCanvas.LoadImage("MainImages/Panels/img_bgr_potential_focus");
            imgHintWay = GameCanvas.LoadImage("MainImages/Panels/img_hint_way");
            imgItemShop = GameCanvas.LoadImage("MainImages/Panels/btn_item_shop");
            imgItemShopClick = GameCanvas.LoadImage("MainImages/Panels/btn_item_shop_click");
            imgItemShopFocus = GameCanvas.LoadImage("MainImages/Panels/btn_item_shop_focus");
            imgItemTrade = GameCanvas.LoadImage("MainImages/Panels/btn_item_trade");
            imgItemTradeClick = GameCanvas.LoadImage("MainImages/Panels/btn_item_trade_click");
            imgItemTradeFocus = GameCanvas.LoadImage("MainImages/Panels/btn_item_trade_focus");
            imgArea = GameCanvas.LoadImage("MainImages/Panels/img_area");
            imgBgrAchivement = GameCanvas.LoadImage("MainImages/Panels/img_bgr_achivement");
            imgBgrGift = GameCanvas.LoadImage("MainImages/Panels/img_bgr_gift");
            imgBtnSmall = GameCanvas.LoadImage("MainImages/Panels/btn_small");
            imgBtnSmallFocus = GameCanvas.LoadImage("MainImages/Panels/btn_small_focus");
            imgBtnSmallClick = GameCanvas.LoadImage("MainImages/Panels/btn_small_click");
            imgBtnMini = GameCanvas.LoadImage("MainImages/Panels/btn_cmd_mini");
            imgBtnMiniFocus = GameCanvas.LoadImage("MainImages/Panels/btn_cmd_mini_focus");
            imgBtnMiniClick = GameCanvas.LoadImage("MainImages/Panels/btn_cmd_mini_click");
            imgClanMember = GameCanvas.LoadImage("MainImages/Panels/btn_clan_member");
            imgClanMemberClick = GameCanvas.LoadImage("MainImages/Panels/btn_clan_member_click");
            imgClanMemberFocus = GameCanvas.LoadImage("MainImages/Panels/btn_clan_member_focus");
            effectItemUpgrades = new Image[4];
            for (int i = 0; i < effectItemUpgrades.Length; i++)
            {
                effectItemUpgrades[i] = GameCanvas.LoadImage("MainImages/Panels/EffectItemUpgrades/eff" + i);
            }
            effectRedItemUpgrades = new Image[4];
            for (int i = 0; i < effectRedItemUpgrades.Length; i++)
            {
                effectRedItemUpgrades[i] = GameCanvas.LoadImage("MainImages/Panels/EffectItemUpgrades/effr" + i);
            }
            effectYellowItemUpgrades = new Image[4];
            for (int i = 0; i < effectYellowItemUpgrades.Length; i++)
            {
                effectYellowItemUpgrades[i] = GameCanvas.LoadImage("MainImages/Panels/EffectItemUpgrades/effy" + i);
            }
            bgrItemUpgrades = new Image[5];
            for (int i = 0; i < bgrItemUpgrades.Length; i++)
            {
                bgrItemUpgrades[i] = GameCanvas.LoadImage("MainImages/Panels/EffectItemUpgrades/bg" + i);
            }
            imgGender = new Image[4];
            for (int i = 0; i < imgGender.Length; i++)
            {
                imgGender[i] = GameCanvas.LoadImage("MainImages/Panels/img_gender_" + i);
            }
            cmdBag = new CmdBag("Túi đồ", imgBag, imgBag, imgBagClick, this, 4, 0);
            cmdBox = new CmdBag("Rương đồ", imgBag, imgBag, imgBagClick, this, 4, 1);
            cmdOutFit = new CmdBag("T.bị chính", imgBag, imgBag, imgBagClick, this, 4, 2);
            cmdOutFitOther = new CmdBag("T.bị phụ", imgBag, imgBag, imgBagClick, this, 4, 4);
            cmdInfoPlayer = new CmdBag("Thông tin", imgBag, imgBag, imgBagClick, this, 4, 3);
            cmdTabDisciples = new List<CmdBag>();
            cmdTabDisciples.Add(new CmdBag("Thông tin", imgBag, imgBag, imgBagClick, this, 77, 0));
            cmdTabDisciples.Add(new CmdBag("Kĩ năng", imgBag, imgBag, imgBagClick, this, 77, 1));
            cmdTabDisciples.Add(new CmdBag("Trạng thái", imgBag, imgBag, imgBagClick, this, 77, 2));
            cmdChat = new Command("MainImages/Panels/btn_small", "MainImages/Panels/btn_small_focus", "MainImages/Panels/btn_small_click", this, 51, null);
            cmdChat.caption = "Chat";
            textChat = new TextField("MainImages/Panels/input_chat");
            textChat.command = cmdChat;
            textChat.name = "Nhập nội dung chat";
            textChat.maxTextLength = 200;
            itemUpgrade = new ItemPanel(this, 19, null);
            itemNextUpgrade = new ItemPanel(this, 20, null);
            cmdUpgrade = new CmdMini("Cường hóa", this, 21, null);
            cmdTrade = new CmdMini("Khóa", this, 59, null);
            cmdHuongDanPickMe = new CmdMini("Hướng dẫn", this, 95, null);
            cmdJoinPickMe = new CmdMini("Tham gia", this, 96, null);
            cmdLucky = new CmdMini("Chọn", this, 97, null);
            cmdAddCoin = new Command("MainImages/Panels/btn_add", "MainImages/Panels/btn_add_focus", "MainImages/Panels/btn_add_click", this, 60, null);
            cmdPotentials = new CmdPotential[4];
            for (int i = 0; i < cmdPotentials.Length; i++)
            {
                cmdPotentials[i] = new CmdPotential(this, 16, i);
                cmdPotentials[i].icon = GameCanvas.LoadImage("MainImages/Panels/img_potential_" + i);
            }
            upPointPotentialInfo = "# tiềm năng tăng 1đ";
            isShowBag = true;
            typeShowOutFit = 0;
            tabs = new List<TabPanel>();
            tabInfo = new TabInfo();
            items = new List<ItemPanel>();
            itemsUpgrade = new List<ItemPanel>();
            areas = new List<CmdArea>();
            itemsBody = new ItemPanel[20];
            skills = new List<CmdSkill>();
            enemys = new List<CmdFriend>();
            friends = new List<CmdFriend>();
            cmdFriends = new List<CmdMini>();
            tops = new List<CmdTop>();
            mapsSpaceship = new List<CmdMap>();
            achievements = new List<CmdAchievement>();
            gifts = new List<CmdGift>();
            teams = new List<CmdTeam>();
            cmdTeams = new List<CmdMini>();
            cmdClans = new List<CmdMini>();
            cmdChatGlobals = new List<CmdMessage>();
            cmdChatServers = new List<CmdMessage>();
            cmdChatClans = new List<CmdMessage>();
            cmdChatTeams = new List<CmdMessage>();
            cmdChatPlayers = new List<CmdPlayerMessage>();
            infosUpgrade = new List<string>();
            notifications = new List<string>();
            indexsUpgrade = new List<int>();
            itemsShop = new Dictionary<string, List<Item>>();
            itemsTrade = new List<Item>();
            itemsPlayerTrade = new List<Item>();
            cmdSettings = new List<CmdSetting>();
            cmdFlags = new List<CmdFlag>();
            cmdPlayerMiniGames = new List<CmdPlayerMiniGame>();
            cmdPlayerInMaps = new List<CmdPlayerInMap>();
            cmdSettingFocus = new List<CmdSettingFocus>();
            cmdRewards = new List<CmdReward>();
            intrinsics = new List<CmdIntrinsic>();
            infosUpgrade.Add("- Vào túi đồ chọn trang bị cần cường hóa");
            infosUpgrade.Add("- Sau đó chọn đá cường hóa");
            infosUpgrade.Add("- Có thể chọn thêm Bùa bảo vệ để chống giảm cấp khi cường hóa thất bại (chỉ có thể sử dụng đối với trang bị từ cấp 8 trở lên)");
            infosUpgrade.Add("- Cường hóa thất bại có thể bị giảm cấp nhưng không bị giảm chỉ số gốc");
            strFlags = new string[] { "Tắt", "Đồ sát", "Cờ đen", "Cờ Đỏ", "Cờ Xanh", "Cờ Vàng", "Cờ Lục", "Cờ Cam", "Cờ Tím", "Cờ Lam" };
            infoFlags = new string[]
            {
                "Tăng 0% exp, xu khi đánh quái",
                "Tăng 20% exp, xu khi đánh quái",
                "Tăng 15% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái",
                "Tăng 10% exp, xu khi đánh quái"
            };
            strChat = "Nhập nội dung chat";
            chatTxtField = new ChatTextField();
            chatTxtField.parentScreen = this;
            clientInput = new ClientInput();
            clientInput.chatable = this;
        }

        public void Update()
        {
            Item.Update();
            if (tabInfo.isShow)
            {
                tabInfo.Update();
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.Update();
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.Update();
                return;
            }
            if (pointerY != yPointerDown)
            {
                if (isPointerDownInScroll)
                {
                    cmyTo -= pointerY - yPointerDown;
                }
                if (isPointerDownInScrollInfo)
                {
                    cmyInfoTo -= pointerY - yPointerDown;
                }
                yPointerDown = pointerY;
            }
            if (pointerX != xPointerDown)
            {
                if (isPointerDownInScroll)
                {
                    cmxTo -= pointerX - xPointerDown;
                }
                if (isPointerDownInScrollInfo)
                {
                    cmxInfoTo -= pointerX - xPointerDown;
                }
                xPointerDown = pointerX;
            }

            if (!isPointerDownInScroll)
            {
                if (cmyTo < 0)
                {
                    cmyTo = 0;
                }
                if (cmyTo > cmyLim)
                {
                    cmyTo = cmyLim;
                }
                if (cmxTo < 0)
                {
                    cmxTo = 0;
                }
                if (cmxTo > cmxLim)
                {
                    cmxTo = cmxLim;
                }
            }
            if (cmy < cmyTo)
            {
                int num = cmyTo - cmy >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmy += num;
            }
            else if (cmy > cmyTo)
            {
                int num = cmy - cmyTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmy -= num;
            }
            if (cmx < cmxTo)
            {
                int num = cmxTo - cmx >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmx += num;
            }
            else if (cmx > cmxTo)
            {
                int num = cmx - cmxTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmx -= num;
            }
            if (!isPointerDownInScrollInfo)
            {
                if (cmyInfoTo < 0)
                {
                    cmyInfoTo = 0;
                }
                if (cmyInfoTo > cmyInfoLim)
                {
                    cmyInfoTo = cmyInfoLim;
                }
                if (cmxInfoTo < 0)
                {
                    cmxInfoTo = 0;
                }
                if (cmxInfoTo > cmxInfoLim)
                {
                    cmxInfoTo = cmxInfoLim;
                }
            }
            if (cmyInfo < cmyInfoTo)
            {
                int num = cmyInfoTo - cmyInfo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmyInfo += num;
            }
            else if (cmyInfo > cmyInfoTo)
            {
                int num = cmyInfo - cmyInfoTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmyInfo -= num;
            }
            if (cmxInfo < cmxInfoTo)
            {
                int num = cmxInfoTo - cmxInfo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmxInfo += num;
            }
            else if (cmxInfo > cmxInfoTo)
            {
                int num = cmxInfo - cmxInfoTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmxInfo -= num;
            }
            textChat.Update();
        }

        public void Paint(MyGraphics g)
        {
            g.Reset();
            for (int i = 0; i < tabs.Count; i++)
            {
                tabs[i].Paint(g, tabIndex == i);
                if (((tabs[i].type == TabPanel.tabChatTeam.type && isNewMessageTeam)
                    || (tabs[i].type == TabPanel.tabChatClan.type && isNewMessageClan)
                    || (tabs[i].type == TabPanel.tabChatPlayer.type && IsNewPlayerMessage())) && GameCanvas.gameTick % 30 < 15)
                {
                    g.DrawImage(GameScreen.imgLight, tabs[i].x + tabs[i].w / 2, tabs[i].y + tabs[i].h / 2, StaticObj.VCENTER_HCENTER);
                }
            }
            g.DrawImage(imgBgr, x, y);
            cmdClose.Paint(g);

            switch (type)
            {
                case 0:
                    PaintBag(g);
                    break;
                case 1:
                    PaintSkill(g);
                    break;
                case 2:
                    PaintUpgrade(g);
                    break;
                case 3:
                    PaintShop(g);
                    break;
                case 4:
                    PaintTask(g);
                    break;
                case 5:
                    PaintTaskOther(g);
                    break;
                case 6:
                    PaintArea(g);
                    break;
                case 7:
                    PaintFriend(g);
                    break;
                case 8:
                    PaintEnemy(g);
                    break;
                case 9:
                    PaintTop(g);
                    break;
                case 10:
                    PaintMapSpaceship(g);
                    break;
                case 11:
                    PaintAchivement(g);
                    break;
                case 12:
                    PaintNotification(g);
                    break;
                case 13:
                    PaintTeam(g);
                    break;
                case 14:
                    PaintChatGlobal(g);
                    break;
                case 15:
                    PaintChatClan(g);
                    break;
                case 16:
                    PaintChatTeam(g);
                    break;
                case 17:
                    PaintChatPlayer(g);
                    break;
                case 18:
                    PaintTrade(g);
                    break;
                case 19:
                    PaintClanInfo(g);
                    break;
                case 20:
                    PaintClanMember(g);
                    break;
                case 21:
                    PaintViewPlayer(g);
                    break;
                case 22:
                    PaintSetting(g);
                    break;
                case 23:
                    PaintFlag(g);
                    break;
                case 24:
                    PaintChatServer(g);
                    break;
                case 25:
                    PaintMiniGame(g);
                    break;
                case 26:
                    PaintDisciple(g);
                    break;
                case 27:
                    PaintReward(g);
                    break;
                case 28:
                    PaintGift(g);
                    break;
                case 29:
                    PaintPlayerInMap(g);
                    break;
                case 30:
                    PaintSettingFocus(g);
                    break;
                case 31:
                    PaintPet(g);
                    break;
                case 32:
                    PaintIntrinsic(g);
                    break;
                case 33:
                    PaintPickMe(g);
                    break;
                case 34:
                    PaintLucky(g);
                    break;
            }
            g.Reset();
            if (tabInfo.isShow)
            {
                tabInfo.Paint(g);
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.Paint(g);
            }
            if (clientInput.isShow)
            {
                clientInput.Paint(g);
            }
        }

        public void Show()
        {
            isShow = true;
        }

        public void Close()
        {
            isShow = false;
            if (!isAccept && tabs.Contains(TabPanel.tabTrade))
            {
                Service.TradeAction(2, -1, -1, -1);
            }
            typeShop = 0;
        }

        public void SetType(int type)
        {
            this.type = type;
            tabIndex = 0;
            indexUpgrade = -1;
            itemsUpgrade.Clear();
            indexsUpgrade.Clear();
            tabs.Clear();
            teams.Clear();
            itemsTrade.Clear();
            itemsPlayerTrade.Clear();
            isAccept = false;
            isLock = false;
            isPlayerAccept = false;
            isPlayerLock = false;
            coinTrade = 0;
            coinPlayerTrade = 0;
            imgBgr = imgBgrBig;
            isShowBag = true;
            typeShowOutFit = 0;
            switch (type)
            {
                case 0:
                case 1:
                case 26:
                case 31:
                case 32:
                    tabs.Add(TabPanel.tabInventory);
                    tabs.Add(TabPanel.tabSkill);
                    tabs.Add(TabPanel.tabIntrinsic);
                    if (Player.disciple != null)
                    {
                        indexTabDisciple = 0;
                        tabs.Add(TabPanel.tabDisciple);
                    }
                    petMenu = Player.pet;
                    tabs.Add(TabPanel.tabPet);
                    break;
                case 2:
                    tabs.Add(TabPanel.tabUpgrade);
                    tabs.Add(TabPanel.tabInventory);
                    break;
                case 3:
                    for (int i = 0; i < itemsShop.Count; i++)
                    {
                        tabs.Add(new TabPanel(itemsShop.Keys.ElementAt(i), 3));
                    }
                    tabs.Add(TabPanel.tabInventory);
                    break;
                case 4:
                case 5:
                    tabs.Add(TabPanel.tabTask);
                    tabs.Add(TabPanel.tabTaskOrther);
                    break;
                case 6:
                    tabs.Add(TabPanel.tabArea);
                    break;
                case 7:
                    tabs.Add(TabPanel.tabFriend);
                    break;
                case 8:
                    tabs.Add(TabPanel.tabEnemy);
                    break;
                case 9:
                    tabs.Add(TabPanel.tabTop);
                    break;
                case 10:
                    tabs.Add(TabPanel.tabMapSpaceship);
                    break;
                case 11:
                    tabs.Add(TabPanel.tabAchievement);
                    break;
                case 12:
                    tabs.Add(TabPanel.tabNotification);
                    break;
                case 13:
                    tabs.Add(TabPanel.tabTeam);
                    tabs.Add(new TabPanel("Tin nhắn", 16));
                    break;
                case 14:
                case 15:
                case 16:
                case 17:
                case 24:
                    tabs.Add(TabPanel.tabChatGlobal);
                    tabs.Add(TabPanel.tabChatTeam);
                    tabs.Add(TabPanel.tabChatClan);
                    tabs.Add(TabPanel.tabChatPlayer);
                    tabs.Add(TabPanel.tabChatServer);
                    break;
                case 18:
                    tabs.Add(TabPanel.tabTrade);
                    tabs.Add(TabPanel.tabInventory);
                    break;
                case 19:
                case 20:
                    tabs.Add(TabPanel.tabClanInfo);
                    tabs.Add(TabPanel.tabClanMember);
                    tabs.Add(new TabPanel("Tin nhắn", 15));
                    break;
                case 21:
                    tabs.Add(TabPanel.tabViewPlayer);
                    if (viewers != null && viewers.Length > 1)
                    {
                        tabs.Add(new TabPanel("Đệ tử", TabPanel.tabViewPlayer.type));
                    }
                    tabs.Add(TabPanel.tabPet);
                    break;
                case 22:
                    tabs.Add(TabPanel.tabSetting);
                    break;
                case 23:
                    tabs.Add(TabPanel.tabFlag);
                    break;
                case 25:
                    tabs.Add(TabPanel.tabMiniGame);
                    break;
                case 27:
                    tabs.Add(TabPanel.tabReward);
                    break;
                case 28:
                    tabs.Add(TabPanel.tabGift);
                    break;
                case 29:
                    tabs.Add(TabPanel.tabPlayerInMap);
                    tabs.Add(TabPanel.tabSettingFocus);
                    break;
                case 33:
                    tabs.Add(TabPanel.tabPickMe);
                    break;
                case 34:
                    tabs.Add(TabPanel.tabLucky);
                    tabs.Add(TabPanel.tabInventory);
                    break;
            }
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
            x = (GameCanvas.w - w) / 2;
            y = (GameCanvas.h - h) / 2;
            cmdClose.x = x + w + 10;
            cmdClose.y = y + 10;
            SetTab(type);
        }

        private void SetTab(int type)
        {
            this.type = type;
            Service.instance.SetTab(true, type);
            indexSelect = -1;
            for (int i = 0; i < tabs.Count; i++)
            {
                tabs[i].isShow = true;
                if (tabIndex == i)
                {
                    tabs[i].image = imgTitleSelect;
                    tabs[i].imageClick = imgTitleSelectFocus;
                    tabs[i].imageFocus = imgTitleSelectFocus;
                }
                else
                {
                    tabs[i].image = imgTitle;
                    tabs[i].imageClick = imgTitleFocus;
                    tabs[i].imageFocus = imgTitleFocus;
                }
                tabs[i].w = tabs[i].image.GetWidth();
                tabs[i].h = tabs[i].image.GetHeight();
                tabs[i].x = x + 20 + tabs[i].w * i;
                tabs[i].y = y - tabs[i].h + 5;
                tabs[i].caption = tabs[i].name;
                tabs[i].anchor = StaticObj.TOP_LEFT;
                tabs[i].actionId = 2;
                tabs[i]._object = i;
                tabs[i].actionListener = this;
            }
            switch (type)
            {
                case 0:
                    SetTabBag();
                    break;
                case 1:
                    SetTabSkill();
                    break;
                case 2:
                    SetTabUpgrade();
                    break;
                case 3:
                    SetTabShop();
                    break;
                case 4:
                    SetTabTask();
                    break;
                case 5:
                    SetTabTaskOrther();
                    break;
                case 6:
                    SetTabArea();
                    break;
                case 7:
                    SetTabFriend();
                    break;
                case 8:
                    SetTabEnemy();
                    break;
                case 9:
                    SetTabTop();
                    break;
                case 10:
                    SetTabMapSpaceship();
                    break;
                case 11:
                    SetTabAchivement();
                    break;
                case 12:
                    SetTabNotification();
                    break;
                case 13:
                    SetTabTeam();
                    break;
                case 14:
                    SetTabChatGlobal();
                    break;
                case 15:
                    SetTabChatClan();
                    break;
                case 16:
                    SetTabChatTeam();
                    break;
                case 17:
                    SetTabChatPlayer();
                    break;
                case 18:
                    SetTabTrade();
                    break;
                case 19:
                    SetTabClanInfo();
                    break;
                case 20:
                    SetTabClanMember();
                    break;
                case 21:
                    SetTabViewPlayer();
                    break;
                case 22:
                    SetTabSetting();
                    break;
                case 23:
                    SetTabFlag();
                    break;
                case 24:
                    SetTabChatServer();
                    break;
                case 25:
                    SetTabMiniGame();
                    break;
                case 26:
                    SetTabDisciple();
                    break;
                case 27:
                    SetTabReward();
                    break;
                case 28:
                    SetTabGift();
                    break;
                case 29:
                    SetTabPlayerInMap();
                    break;
                case 30:
                    SetTabSettingFocus();
                    break;
                case 31:
                    SetTabPet();
                    break;
                case 32:
                    SetTabIntrinsic();
                    break;
                case 33:
                    SetTabPickMe();
                    break;
                case 34:
                    SetTabLucky();
                    break;
            }
        }

        public void KeyPress(KeyCode keyCode)
        {
            if (tabInfo.isShow)
            {
                tabInfo.KeyPress(keyCode);
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.KeyPress(keyCode);
            }
            if (clientInput.isShow)
            {
                clientInput.KeyPress(keyCode);
                return;
            }
            if (type == 14 || type == 15 || type == 16 || type == 17)
            {
                textChat.KeyPress(keyCode);
            }
            if (type == 0 && keyCode == KeyCode.F1 && indexSelect >= 0 && indexSelect <= items.Count && isShowBag)
            {
                try
                {
                    items[indexSelect].PerformAction();
                }
                catch
                {
                }
            }
            if (type == 2 && keyCode == KeyCode.Return)
            {
                cmdUpgrade.PerformAction();
            }
        }

        public void PointerMove(int x, int y)
        {
            if (tabInfo.isShow)
            {
                tabInfo.PointerMove(x, y);
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerMove(x, y);
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.PointerMove(x, y);
                return;
            }
            if (cmdClose.PointerMove(x, y))
            {
                return;
            }
            foreach (TabPanel tab in tabs)
            {
                if (tab.PointerMove(x, y))
                {
                    return;
                }
            }
            pointerX = x;
            pointerY = y;
            switch (type)
            {
                case 0:
                    {
                        if (cmdBag.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdBox.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdOutFit.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdInfoPlayer.PointerMove(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 1:
                    {
                        for (int i = 0; i < skills.Count; i++)
                        {
                            if (skills[i].PointerMove(x + cmx, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < cmdPotentials.Length; i++)
                        {
                            if (cmdPotentials[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 2:
                    {
                        if (itemUpgrade.PointerMove(x, y))
                        {
                            break;
                        }
                        if (itemNextUpgrade.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdUpgrade.PointerMove(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < itemsUpgrade.Count; i++)
                        {
                            if (itemsUpgrade[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 3:
                    {
                        if (tabIndex >= 0 && tabIndex < itemsShop.Count)
                        {
                            List<Item> items = itemsShop.ElementAt(tabIndex).Value;
                            for (int i = 0; i < items.Count; i++)
                            {
                                if (items[i].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 4:
                    {
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 6:
                    {
                        for (int i = 0; i < areas.Count; i++)
                        {
                            if (areas[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 7:
                    {
                        for (int i = 0; i < cmdFriends.Count; i++)
                        {
                            if (cmdFriends[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < friends.Count; i++)
                        {
                            if (friends[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 8:
                    {
                        for (int i = 0; i < enemys.Count; i++)
                        {
                            if (enemys[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 9:
                    {
                        for (int i = 0; i < tops.Count; i++)
                        {
                            if (tops[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 10:
                    {
                        for (int i = 0; i < mapsSpaceship.Count; i++)
                        {
                            if (mapsSpaceship[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 11:
                    {
                        for (int i = 0; i < achievements.Count; i++)
                        {
                            if (achievements[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 13:
                    {
                        for (int i = 0; i < cmdTeams.Count; i++)
                        {
                            if (cmdTeams[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < teams.Count; i++)
                        {
                            if (teams[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (Player.me.team != null)
                        {
                            for (int i = 0; i < Player.me.team.members.Count; i++)
                            {
                                if (Player.me.team.members[i].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 14:
                    {
                        if (cmdChat.PointerMove(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatGlobals.Count; i++)
                        {
                            if (cmdChatGlobals[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 15:
                    {
                        if (cmdChat.PointerMove(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatClans.Count; i++)
                        {
                            if (cmdChatClans[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 16:
                    {
                        if (cmdChat.PointerMove(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatTeams.Count; i++)
                        {
                            if (cmdChatTeams[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 17:
                    {
                        if (cmdChat.PointerMove(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatPlayers.Count; i++)
                        {
                            if (cmdChatPlayers[i].PointerMove(x + cmxInfo, y))
                            {
                                return;
                            }
                        }
                        if (indexSelect >= 0 && indexSelect < cmdChatPlayers.Count)
                        {
                            List<CmdMessage> messages = cmdChatPlayers[indexSelect].messages;
                            for (int i = 0; i < messages.Count; i++)
                            {
                                if (messages[i].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 18:
                    {
                        for (int i = 0; i < itemsTrade.Count; i++)
                        {
                            if (itemsTrade[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsPlayerTrade.Count; i++)
                        {
                            if (itemsPlayerTrade[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (cmdAddCoin.PointerMove(x, y))
                        {
                            return;
                        }
                        if (cmdTrade.PointerMove(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 19:
                    {
                        for (int i = 0; i < cmdClans.Count; i++)
                        {
                            if (cmdClans[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 20:
                    {
                        Clan clan = Player.me.clan;
                        if (clan != null)
                        {
                            for (int i = 0; i < clan.members.Count; i++)
                            {
                                if (clan.members[i].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 21:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        if (cmdOutFit.PointerMove(x, y))
                        {
                            return;
                        }
                        if (cmdOutFitOther.PointerMove(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 22:
                    {
                        for (int i = 0; i < cmdSettings.Count; i++)
                        {
                            if (cmdSettings[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 23:
                    {
                        for (int i = 0; i < cmdFlags.Count; i++)
                        {
                            if (cmdFlags[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 25:
                    {
                        for (int i = 0; i < cmdPlayerMiniGames.Count; i++)
                        {
                            if (cmdPlayerMiniGames[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 26:
                    {
                        if (cmdOutFit.PointerMove(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerMove(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < cmdTabDisciples.Count; i++)
                        {
                            if (cmdTabDisciples[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        if (indexTabDisciple == 0)
                        {
                            for (int i = 0; i < itemsBody.Length; i++)
                            {
                                if (itemsBody[i].PointerMove(x, y))
                                {
                                    return;
                                }
                            }
                        }
                        else if (indexTabDisciple == 1)
                        {
                            for (int i = 0; i < skills.Count; i++)
                            {
                                if (skills[i].PointerMove(x + cmx, y))
                                {
                                    return;
                                }
                            }
                            for (int i = 0; i < cmdPotentials.Length; i++)
                            {
                                if (cmdPotentials[i].PointerMove(x, y))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 27:
                    {
                        for (int i = 0; i < cmdRewards.Count; i++)
                        {
                            for (int j = 0; j < cmdRewards[i].items.Count; j++)
                            {
                                if (cmdRewards[i].items[j].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (cmdRewards[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 28:
                    {
                        for (int i = 0; i < gifts.Count; i++)
                        {
                            for (int j = 0; j < gifts[i].items.Count; j++)
                            {
                                if (gifts[i].items[j].PointerMove(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (gifts[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 29:
                    {
                        for (int i = 0; i < cmdPlayerInMaps.Count; i++)
                        {
                            if (cmdPlayerInMaps[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 30:
                    {
                        for (int i = 0; i < cmdSettingFocus.Count; i++)
                        {
                            if (cmdSettingFocus[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 31:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 32:
                    {
                        for (int i = 0; i < intrinsics.Count; i++)
                        {
                            if (intrinsics[i].PointerMove(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 33:
                    {
                        if (statusPickMe == 0)
                        {
                            if (cmdHuongDanPickMe.PointerMove(x, y))
                            {
                                return;
                            }
                            if (cmdJoinPickMe.PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 34:
                    {
                        if (cmdLucky.PointerMove(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerMove(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
            }
        }

        public void PointerClicked(int x, int y)
        {
            if (tabInfo.isShow)
            {
                tabInfo.PointerClicked(x, y);
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerClicked(x, y);
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.PointerClicked(x, y);
                return;
            }
            if (cmdClose.PointerClicked(x, y))
            {
                return;
            }
            foreach (TabPanel tab in tabs)
            {
                if (tab.PointerClicked(x, y))
                {
                    return;
                }
            }
            if (x >= xScroll && x <= xScroll + wScroll && y >= yScroll && y <= yScroll + hScroll)
            {
                isPointerDownInScroll = true;
            }
            if (x >= xScrollInfo && x <= xScrollInfo + wScrollInfo && y >= yScrollInfo && y <= yScrollInfo + hScrollInfo)
            {
                isPointerDownInScrollInfo = true;
            }
            xPointerDown = x;
            yPointerDown = y;
            if (x < this.x || x > this.x + this.w || y < this.y || y > this.y + this.h)
            {
                return;
            }
            switch (type)
            {
                case 0:
                    {
                        if (cmdBag.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdBox.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdOutFit.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdInfoPlayer.PointerClicked(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (isPointerDownInScroll && items[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 1:
                    {
                        for (int i = 0; i < skills.Count; i++)
                        {
                            if (skills[i].PointerClicked(x + cmx, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < cmdPotentials.Length; i++)
                        {
                            if (cmdPotentials[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 2:
                    {
                        if (itemUpgrade.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (itemNextUpgrade.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdUpgrade.PointerClicked(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < itemsUpgrade.Count; i++)
                        {
                            if (itemsUpgrade[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 3:
                    {
                        if (tabIndex >= 0 && tabIndex < itemsShop.Count)
                        {
                            List<Item> items = itemsShop.ElementAt(tabIndex).Value;
                            for (int i = 0; i < items.Count; i++)
                            {
                                if (items[i].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 4:
                    {
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 6:
                    {
                        for (int i = 0; i < areas.Count; i++)
                        {
                            if (areas[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 7:
                    {
                        for (int i = 0; i < cmdFriends.Count; i++)
                        {
                            if (cmdFriends[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < friends.Count; i++)
                        {
                            if (friends[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 8:
                    {
                        for (int i = 0; i < enemys.Count; i++)
                        {
                            if (enemys[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 9:
                    {
                        for (int i = 0; i < tops.Count; i++)
                        {
                            if (tops[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 10:
                    {
                        for (int i = 0; i < mapsSpaceship.Count; i++)
                        {
                            if (mapsSpaceship[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 11:
                    {
                        for (int i = 0; i < achievements.Count; i++)
                        {
                            if (achievements[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 13:
                    {
                        for (int i = 0; i < cmdTeams.Count; i++)
                        {
                            if (cmdTeams[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < teams.Count; i++)
                        {
                            if (teams[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (Player.me.team != null)
                        {
                            for (int i = 0; i < Player.me.team.members.Count; i++)
                            {
                                if (Player.me.team.members[i].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 14:
                    {
                        if (cmdChat.PointerClicked(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatGlobals.Count; i++)
                        {
                            if (cmdChatGlobals[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        textChat.PointerClicked(x, y);
                        break;
                    }
                case 15:
                    {
                        if (cmdChat.PointerClicked(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatClans.Count; i++)
                        {
                            if (cmdChatClans[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        textChat.PointerClicked(x, y);
                        break;
                    }
                case 16:
                    {
                        if (cmdChat.PointerClicked(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatTeams.Count; i++)
                        {
                            if (cmdChatTeams[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        textChat.PointerClicked(x, y);
                        break;
                    }
                case 17:
                    {
                        if (cmdChat.PointerClicked(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatPlayers.Count; i++)
                        {
                            if (cmdChatPlayers[i].PointerClicked(x + cmxInfo, y))
                            {
                                return;
                            }
                        }
                        if (indexSelect >= 0 && indexSelect < cmdChatPlayers.Count)
                        {
                            List<CmdMessage> messages = cmdChatPlayers[indexSelect].messages;
                            for (int i = 0; i < messages.Count; i++)
                            {
                                if (messages[i].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        textChat.PointerClicked(x, y);
                        break;
                    }
                case 18:
                    {
                        for (int i = 0; i < itemsTrade.Count; i++)
                        {
                            if (itemsTrade[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsPlayerTrade.Count; i++)
                        {
                            if (itemsPlayerTrade[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (cmdAddCoin.PointerClicked(x, y))
                        {
                            return;
                        }
                        if (cmdTrade.PointerClicked(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 19:
                    {
                        for (int i = 0; i < cmdClans.Count; i++)
                        {
                            if (cmdClans[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 20:
                    {
                        Clan clan = Player.me.clan;
                        if (clan != null)
                        {
                            for (int i = 0; i < clan.members.Count; i++)
                            {
                                if (clan.members[i].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 21:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        if (cmdOutFit.PointerClicked(x, y))
                        {
                            return;
                        }
                        if (cmdOutFitOther.PointerClicked(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 22:
                    {
                        for (int i = 0; i < cmdSettings.Count; i++)
                        {
                            if (cmdSettings[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 23:
                    {
                        for (int i = 0; i < cmdFlags.Count; i++)
                        {
                            if (cmdFlags[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 25:
                    {
                        for (int i = 0; i < cmdPlayerMiniGames.Count; i++)
                        {
                            if (cmdPlayerMiniGames[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 26:
                    {
                        if (cmdOutFit.PointerClicked(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerClicked(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < cmdTabDisciples.Count; i++)
                        {
                            if (cmdTabDisciples[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        if (indexTabDisciple == 0)
                        {
                            for (int i = 0; i < itemsBody.Length; i++)
                            {
                                if (itemsBody[i].PointerClicked(x, y))
                                {
                                    return;
                                }
                            }
                        }
                        else if (indexTabDisciple == 1)
                        {
                            for (int i = 0; i < skills.Count; i++)
                            {
                                if (skills[i].PointerClicked(x + cmx, y))
                                {
                                    return;
                                }
                            }
                            for (int i = 0; i < cmdPotentials.Length; i++)
                            {
                                if (cmdPotentials[i].PointerClicked(x, y))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 27:
                    {
                        for (int i = 0; i < cmdRewards.Count; i++)
                        {
                            for (int j = 0; j < cmdRewards[i].items.Count; j++)
                            {
                                if (cmdRewards[i].items[j].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (cmdRewards[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 28:
                    {
                        for (int i = 0; i < gifts.Count; i++)
                        {
                            for (int j = 0; j < gifts[i].items.Count; j++)
                            {
                                if (gifts[i].items[j].PointerClicked(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (gifts[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 29:
                    {
                        for (int i = 0; i < cmdPlayerInMaps.Count; i++)
                        {
                            if (cmdPlayerInMaps[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 30:
                    {
                        for (int i = 0; i < cmdSettingFocus.Count; i++)
                        {
                            if (cmdSettingFocus[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 31:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 32:
                    {
                        for (int i = 0; i < intrinsics.Count; i++)
                        {
                            if (intrinsics[i].PointerClicked(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 33:
                    {
                        if (statusPickMe == 0)
                        {
                            if (cmdHuongDanPickMe.PointerClicked(x, y))
                            {
                                return;
                            }
                            if (cmdJoinPickMe.PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 34:
                    {
                        if (cmdLucky.PointerClicked(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerClicked(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
            }
        }

        public void PointerReleased(int x, int y)
        {
            isPointerDownInScroll = false;
            isPointerDownInScrollInfo = false;
            if (tabInfo.isShow)
            {
                tabInfo.PointerReleased(x, y);
                return;
            }
            if (chatTxtField.isShow)
            {
                chatTxtField.PointerReleased(x, y);
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.PointerReleased(x, y);
                return;
            }
            if (cmdClose.PointerReleased(x, y))
            {
                return;
            }
            foreach (TabPanel tab in tabs)
            {
                if (tab.PointerReleased(x, y))
                {
                    return;
                }
            }
            switch (type)
            {
                case 0:
                    {
                        if (cmdBag.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdBox.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdOutFit.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdInfoPlayer.PointerReleased(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 1:
                    {
                        for (int i = 0; i < skills.Count; i++)
                        {
                            if (skills[i].PointerReleased(x + cmx, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < cmdPotentials.Length; i++)
                        {
                            if (cmdPotentials[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 2:
                    {
                        if (itemUpgrade.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (itemNextUpgrade.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdUpgrade.PointerReleased(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < itemsUpgrade.Count; i++)
                        {
                            if (itemsUpgrade[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 3:
                    {
                        if (tabIndex >= 0 && tabIndex < itemsShop.Count)
                        {
                            List<Item> items = itemsShop.ElementAt(tabIndex).Value;
                            for (int i = 0; i < items.Count; i++)
                            {
                                if (items[i].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 4:
                    {
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 6:
                    {
                        for (int i = 0; i < areas.Count; i++)
                        {
                            if (areas[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 7:
                    {
                        for (int i = 0; i < cmdFriends.Count; i++)
                        {
                            if (cmdFriends[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < friends.Count; i++)
                        {
                            if (friends[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 8:
                    {
                        for (int i = 0; i < enemys.Count; i++)
                        {
                            if (enemys[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 9:
                    {
                        for (int i = 0; i < tops.Count; i++)
                        {
                            if (tops[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 10:
                    {
                        for (int i = 0; i < mapsSpaceship.Count; i++)
                        {
                            if (mapsSpaceship[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 11:
                    {
                        for (int i = 0; i < achievements.Count; i++)
                        {
                            if (achievements[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 13:
                    {
                        for (int i = 0; i < cmdTeams.Count; i++)
                        {
                            if (cmdTeams[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < teams.Count; i++)
                        {
                            if (teams[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (Player.me.team != null)
                        {
                            for (int i = 0; i < Player.me.team.members.Count; i++)
                            {
                                if (Player.me.team.members[i].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 14:
                    {
                        if (cmdChat.PointerReleased(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatGlobals.Count; i++)
                        {
                            if (cmdChatGlobals[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 15:
                    {
                        if (cmdChat.PointerReleased(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatClans.Count; i++)
                        {
                            if (cmdChatClans[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 16:
                    {
                        if (cmdChat.PointerReleased(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatTeams.Count; i++)
                        {
                            if (cmdChatTeams[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 17:
                    {
                        if (cmdChat.PointerReleased(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < cmdChatPlayers.Count; i++)
                        {
                            if (cmdChatPlayers[i].PointerReleased(x + cmxInfo, y))
                            {
                                return;
                            }
                        }
                        if (indexSelect >= 0 && indexSelect < cmdChatPlayers.Count)
                        {
                            List<CmdMessage> messages = cmdChatPlayers[indexSelect].messages;
                            for (int i = 0; i < messages.Count; i++)
                            {
                                if (messages[i].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 18:
                    {
                        for (int i = 0; i < itemsTrade.Count; i++)
                        {
                            if (itemsTrade[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        for (int i = 0; i < itemsPlayerTrade.Count; i++)
                        {
                            if (itemsPlayerTrade[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        if (cmdAddCoin.PointerReleased(x, y))
                        {
                            return;
                        }
                        if (cmdTrade.PointerReleased(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 19:
                    {
                        for (int i = 0; i < cmdClans.Count; i++)
                        {
                            if (cmdClans[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 20:
                    {
                        Clan clan = Player.me.clan;
                        if (clan != null)
                        {
                            for (int i = 0; i < clan.members.Count; i++)
                            {
                                if (clan.members[i].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 21:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        if (cmdOutFit.PointerReleased(x, y))
                        {
                            return;
                        }
                        if (cmdOutFitOther.PointerReleased(x, y))
                        {
                            return;
                        }
                        break;
                    }
                case 22:
                    {
                        for (int i = 0; i < cmdSettings.Count; i++)
                        {
                            if (cmdSettings[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 23:
                    {
                        for (int i = 0; i < cmdFlags.Count; i++)
                        {
                            if (cmdFlags[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 25:
                    {
                        for (int i = 0; i < cmdPlayerMiniGames.Count; i++)
                        {
                            if (cmdPlayerMiniGames[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 26:
                    {
                        if (cmdOutFit.PointerReleased(x, y))
                        {
                            break;
                        }
                        if (cmdOutFitOther.PointerReleased(x, y))
                        {
                            break;
                        }
                        for (int i = 0; i < cmdTabDisciples.Count; i++)
                        {
                            if (cmdTabDisciples[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        if (indexTabDisciple == 0)
                        {
                            for (int i = 0; i < itemsBody.Length; i++)
                            {
                                if (itemsBody[i].PointerReleased(x, y))
                                {
                                    return;
                                }
                            }

                        }
                        else if (indexTabDisciple == 1)
                        {
                            for (int i = 0; i < skills.Count; i++)
                            {
                                if (skills[i].PointerReleased(x + cmx, y))
                                {
                                    return;
                                }
                            }
                            for (int i = 0; i < cmdPotentials.Length; i++)
                            {
                                if (cmdPotentials[i].PointerReleased(x, y))
                                {
                                    return;
                                }
                            }
                        }
                        break;
                    }
                case 27:
                    {
                        for (int i = 0; i < cmdRewards.Count; i++)
                        {
                            for (int j = 0; j < cmdRewards[i].items.Count; j++)
                            {
                                if (cmdRewards[i].items[j].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (cmdRewards[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 28:
                    {
                        for (int i = 0; i < gifts.Count; i++)
                        {
                            for (int j = 0; j < gifts[i].items.Count; j++)
                            {
                                if (gifts[i].items[j].PointerReleased(x, y + cmy))
                                {
                                    return;
                                }
                            }
                            if (gifts[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 29:
                    {
                        for (int i = 0; i < cmdPlayerInMaps.Count; i++)
                        {
                            if (cmdPlayerInMaps[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 30:
                    {
                        for (int i = 0; i < cmdSettingFocus.Count; i++)
                        {
                            if (cmdSettingFocus[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 31:
                    {
                        for (int i = 0; i < itemsBody.Length; i++)
                        {
                            if (itemsBody[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 32:
                    {
                        for (int i = 0; i < intrinsics.Count; i++)
                        {
                            if (intrinsics[i].PointerReleased(x, y + cmy))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 33:
                    {
                        if (statusPickMe == 0)
                        {
                            if (cmdHuongDanPickMe.PointerReleased(x, y))
                            {
                                return;
                            }
                            if (cmdJoinPickMe.PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
                case 34:
                    {
                        if (cmdLucky.PointerReleased(x, y))
                        {
                            return;
                        }
                        for (int i = 0; i < items.Count; i++)
                        {
                            if (items[i].PointerReleased(x, y))
                            {
                                return;
                            }
                        }
                        break;
                    }
            }
        }

        public void PointerScroll(int a)
        {
            if (tabInfo.isShow)
            {
                tabInfo.PointerScroll(a);
                return;
            }
            if (a == 0)
            {
                return;
            }
            if (cmyLim > 0)
            {
                cmyTo -= a * 20;
                if (cmyTo > cmyLim)
                {
                    cmyTo = cmyLim;
                }
                if (cmyTo < 0)
                {
                    cmyTo = 0;
                }
            }
            if (cmxLim > 0)
            {
                cmxTo -= a * 20;
                if (cmxTo > cmxLim)
                {
                    cmxTo = cmxLim;
                }
                if (cmxTo < 0)
                {
                    cmxTo = 0;
                }
            }
        }

        public void OnChatFromMe(string text)
        {
            if (chatTxtField.textField.GetText() == null || chatTxtField.textField.GetText().Equals(string.Empty) || text.Equals(string.Empty) || text == null)
            {
                chatTxtField.Close();
                return;
            }
            if (chatTxtField.name.Equals(PlayerText.input_point_potential[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Điểm phải lớn hơn 0");
                    }
                    else
                    {
                        if (type == TabPanel.tabSkill.type)
                        {
                            Service.UpPotential(indexSelect, num);
                        }
                        else
                        {
                            Service.UpPotentialDisciple(indexSelect, num);
                        }
                    }
                }
                catch
                {
                    GameCanvas.StartDialogOk("Điểm không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_quantity_trade[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Số lượng phải lớn hơn 0");
                        return;
                    }
                    Service.TradeAction(3, -1, indexSelect, num);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_coin_trade[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Xu phải lớn hơn 0");
                        return;
                    }
                    Service.TradeAction(5, -1, -1, num);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Xu không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_diamond_clan[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Kim cương phải lớn hơn 0");
                        return;
                    }
                    Service.ClanAction(2, num, null);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Kim cương không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_xu_clan[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Xu phải lớn hơn 0");
                        return;
                    }
                    Service.ClanAction(1, num, null);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Xu không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_mua_nhieu[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                        return;
                    }
                    Service.BuyItem(tabIndex, ((Item)chatTxtField.p).id, num);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_pick_me[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Xu không hợp lệ!");
                        return;
                    }
                    Service.instance.LuckyPickMe(1, num);
                }
                catch
                {
                    GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                }
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_slogan[0]))
            {
                Service.ClanAction(3, -1, text);
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_notification[0]))
            {
                Service.ClanAction(4, -1, text);
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_add_friend[0]))
            {
                Service.instance.FriendActions(3, -1, text);
                chatTxtField.Close();
            }
            else if (chatTxtField.name.Equals(PlayerText.input_quantity_use[0]))
            {
                try
                {
                    int num = int.Parse(text);
                    if (num <= 0)
                    {
                        GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                        return;
                    }
                    if (autoItem != null)
                    {
                        try
                        {
                            autoItem.Abort();
                        }
                        finally
                        {
                            autoItem = null;
                        }
                    }
                    Item item = Player.me.itemsBag[indexSelect];
                    if (item != null)
                    {
                        if (num > item.quantity)
                        {
                            GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                            return;
                        }
                        if (item.quantity == 1)
                        {
                            Service.ItemAction(Service.ACTION_USE_ITEM, indexSelect);
                        }
                        else if (item.quantity > 1)
                        {
                            int index = indexSelect;
                            int templateID = item.template.id;
                            autoItem = new Thread(() =>
                            {
                                try
                                {
                                    while (!Player.me.IsBagFull() && num > 0)
                                    {
                                        num--;
                                        Item item = Player.me.itemsBag[index];
                                        if (item != null && item.quantity > 0 && item.template.id == templateID)
                                        {
                                            Service.ItemAction(Service.ACTION_USE_ITEM, index);
                                        }
                                        Thread.Sleep(50);
                                    }
                                }
                                finally
                                {
                                    if (autoItem != null)
                                    {
                                        try
                                        {
                                            autoItem.Abort();
                                        }
                                        finally
                                        {
                                            autoItem = null;
                                        }
                                    }
                                }
                            });
                            autoItem.IsBackground = true;
                            autoItem.Start();
                        }
                    }
                    else
                    {
                        GameCanvas.StartDialogOk("Không tìm thấy vật phẩm");
                    }
                }
                catch
                {
                    GameCanvas.StartDialogOk("Số lượng không hợp lệ!");
                }
                chatTxtField.Close();
            }
        }

        public void OnChatFromMe(string name, List<TextField> textFields)
        {
            if (name.Equals(PlayerText.name_sell_market))
            {
                try
                {
                    int index = -1;
                    int price = int.Parse(textFields[index += 1].GetText());
                    if (price <= 0)
                    {
                        GameCanvas.StartDialogOk("Giá xu phải lớn hơn 0");
                        return;
                    }
                    int quantity = 1;
                    Item item = Player.me.itemsBag[indexSelect];
                    if (item.quantity > 1)
                    {
                        quantity = int.Parse(textFields[index += 1].GetText());
                    }
                    Service.instance.ConsignmentAction(0, item.indexUI, -1, price, quantity);
                    return;
                }
                catch
                {
                    GameCanvas.StartDialogOk("Giá trị nhập không hợp lệ!");
                }
            }
            if (name.Equals(strChat))
            {
                try
                {
                    contentChat = textFields[0].GetText();
                    delayChat = int.Parse(textFields[1].GetText());
                    if (delayChat < 5)
                    {
                        delayChat = 5;
                    }
                    isAutoChat = true;
                    Thread thread = new Thread(() =>
                    {
                        while (isAutoChat)
                        {
                            Service.ChatGlobal(contentChat);
                            Thread.Sleep(delayChat * 1000);
                        }
                    });
                    thread.IsBackground = true;
                    thread.Start();
                    return;
                }
                catch
                {
                    GameCanvas.StartDialogOk("Giá trị nhập không hợp lệ!");
                }
            }
        }

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    {
                        // Close panel
                        Close();
                        Service.instance.SetTab(false, -1);
                        break;
                    }
                case 2:
                    {
                        // select tab
                        tabIndex = (int)p;
                        SetTab(tabs[tabIndex].type);
                        break;
                    }
                case 3:
                    {   //hoang
                        // click item
                        indexSelect = (int)p;
                        if (items[indexSelect].item != null)
                        {
                            Item selectedItem = items[indexSelect].item;
                            TabPanel tab = tabs.FirstOrDefault(t => t.type == 3);
                            if (tab != null)
                            {
                                ViewItem(items[indexSelect].item, VIEW_ITEM_SHOP_BAG);
                            }
                            else if (tabs.Contains(TabPanel.tabUpgrade))
                            {
                                bool isValidItem = false;

                                if (typeUpgrade == 0) // Cường hóa trang bị
                                {
                                    // Chỉ cho phép trang bị (0-7) hoặc đá (18)
                                    isValidItem = (selectedItem.template.type < 8 || selectedItem.template.type == 18);
                                }
                                else if (typeUpgrade == 1) // Ghép đá
                                {
                                    // Chỉ cho phép đá cường hóa
                                    isValidItem = (selectedItem.template.type == 18);
                                }
                                else if (typeUpgrade == 2) // Loại khác
                                {
                                    isValidItem = true;
                                }

                                // Nếu item bị làm tối (không hợp lệ)
                                if (!isValidItem)
                                {
                                    InfoMe.addInfo("Không thể chọn vật phẩm này", 0);
                                    return; // DỪNG, không xử lý tiếp
                                }
                                ViewItem(items[indexSelect].item, VIEW_ITEM_UPGRADE_BAG);
                            }
                            else if (tabs.Contains(TabPanel.tabTrade))
                            {
                                ViewItem(items[indexSelect].item, VIEW_ITEM_TRADE_BAG);
                            }
                            else if (tabs.Contains(TabPanel.tabViewPlayer))
                            {
                                ViewItem(items[indexSelect].item, VIEW_ITEM_VIEW_PLAYER);
                            }
                            else
                            {
                                ViewItem(items[indexSelect].item, isShowBag ? VIEW_ITEM_BAG : VIEW_ITEM_BOX);
                            }
                        }
                        break;
                    }
                case 4:
                    {
                        // click btn bag
                        int select = (int)p;
                        bool isRefresh = false;
                        if (select == 0)
                        {
                            if (!isShowBag)
                            {
                                isShowBag = true;
                                isRefresh = true;
                            }
                        }
                        else if (select == 1)
                        {
                            if (tabs.Contains(TabPanel.tabUpgrade)) //hoang
                            {
                                InfoMe.addInfo("Không thể mở rương ở đây!", 0);
                                break;
                            }
                            if (isShowBag)
                            {
                                isShowBag = false;
                                isRefresh = true;
                            }
                        }
                        else if (select == 2)
                        {
                            if (typeShowOutFit != 0)
                            {
                                typeShowOutFit = 0;
                                isRefresh = true;
                            }
                        }
                        else if (select == 3)
                        {
                            if (typeShowOutFit != 2)
                            {
                                typeShowOutFit = 2;
                                isRefresh = true;
                            }
                        }
                        else if (select == 4)
                        {
                            if (typeShowOutFit != 1)
                            {
                                typeShowOutFit = 1;
                                isRefresh = true;
                            }
                        }
                        if (isRefresh && type != 21 && type != 26)
                        {
                            SetTab(0);
                        }
                        break;
                    }
                case 5:
                    {
                        //view item body
                        int indexSelect = (int)p;
                        if (itemsBody[indexSelect].item != null)
                        {
                            if (tabs.Contains(TabPanel.tabViewPlayer))
                            {
                                ViewItem(itemsBody[indexSelect].item, VIEW_ITEM_VIEW_PLAYER);
                            }
                            else if (type == TabPanel.tabDisciple.type)
                            {
                                ViewItem(itemsBody[indexSelect].item, VIEW_ITEM_DISCIPLE);
                            }
                            else if (type == TabPanel.tabPet.type)
                            {
                                ViewItem(itemsBody[indexSelect].item, VIEW_ITEM_PET);
                            }
                            else
                            {
                                ViewItem(itemsBody[indexSelect].item, VIEW_ITEM_BODY);
                            }
                        }
                        break;
                    }
                case 6:
                    {
                        // sử dụng item
                        Service.ItemAction(Service.ACTION_USE_ITEM, ((Item)p).indexUI);
                        tabInfo.Close();
                        break;
                    }
                case 7:
                    {
                        // bỏ item ra đất
                        Service.ItemAction(Service.ACTION_THROW_ITEM, ((Item)p).indexUI);
                        tabInfo.Close();
                        break;
                    }
                case 8:
                    {
                        // tháo item ra
                        if (typeShowOutFit == 0)
                        {
                            Service.ItemAction(Service.ACTION_UNDRESS_ITEM, ((Item)p).indexUI);
                        }
                        else
                        {
                            Service.ItemAction(Service.ACTION_UNDRESS_ITEM_OTHER, ((Item)p).indexUI);
                        }
                        tabInfo.Close();
                        break;
                    }
                case 9:
                    {
                        // cất item vào rương
                        Service.ItemAction(Service.ACTION_PUT_IN_ITEM, ((Item)p).indexUI);
                        tabInfo.Close();
                        break;
                    }
                case 10:
                    {
                        // lấy item ra hành trang
                        Service.ItemAction(Service.ACTION_TYPE_PUT_OUT_ITEM, ((Item)p).indexUI);
                        tabInfo.Close();
                        break;
                    }
                case 11:
                    {
                        // action click skill
                        indexSelect = (int)p;
                        if (skills[indexSelect].skill != null)
                        {
                            ViewSkill(skills[indexSelect].skill);
                        }
                        break;
                    }
                case 12:
                    {
                        // cộng skill
                        if (Player.me.pointSkill >= 1)
                        {
                            Skill skill = (Skill)p;
                            Service.upSkill(skill.template.id, 1);
                        }
                        else
                        {
                            InfoMe.addInfo("Bạn còn thiếu 1 điểm", 0);
                        }
                        break;
                    }
                case 13:
                    {
                        // gán skill
                        tabInfo.Close();
                        List<CmdMenu> menus = new List<CmdMenu>();
                        for (int i = 0; i < ScreenManager.instance.gameScreen.keySkills.Length; i++)
                        {
                            menus.Add(new CmdMenu("Ô đánh " + (i + 1), this, 14, i + 1));
                        }
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 14:
                    {
                        // confirm gán skill
                        if (indexSelect < skills.Count && indexSelect >= 0)
                        {
                            Skill skill = skills[indexSelect].skill;
                            if (skill != null && skill.template.isProactive && skill.level > 0)
                            {
                                Service.SaveKeySkill(skill.template.id, (int)p);
                            }
                        }
                        tabInfo.Close();
                        break;
                    }
                case 15:
                    {
                        // đóng tab expiryInfo
                        tabInfo.Close();
                        break;
                    }
                case 16:
                    {
                        // click view tiềm năng
                        indexSelect = (int)p;
                        ViewPotential(type == TabPanel.tabSkill.type ? Player.me : Player.disciple, indexSelect);
                        break;
                    }
                case 17:
                    {
                        // cộng điểm tiềm năng
                        Player player;
                        if (type == TabPanel.tabSkill.type)
                        {
                            player = Player.me;
                        }
                        else
                        {
                            player = Player.disciple;
                        }
                        int index = (int)p;
                        if (index > 3)
                        {
                            break;
                        }
                        long potential = 0L;
                        if (index == 0)
                        {
                            potential = player.potentialUpDamage;
                        }
                        else if (index == 1)
                        {
                            potential = player.potentialUpHp;
                        }
                        else if (index == 2)
                        {
                            potential = player.potentialUpMp;
                        }
                        else if (index == 3)
                        {
                            potential = player.potentialUpConstitution;
                        }
                        if (player.potential >= potential)
                        {
                            if (type == TabPanel.tabSkill.type)
                            {
                                Service.UpPotential(index, 1);
                            }
                            else
                            {
                                Service.UpPotentialDisciple(index, 1);
                            }
                        }
                        else
                        {
                            InfoMe.addInfo("Bạn còn thiếu " + Utils.GetMoneys(potential - player.potential) + " tiềm năng", 0);
                        }
                        break;
                    }
                case 18:
                    {
                        // cộng nhiều điểm tiềm năng
                        tabInfo.Close();
                        chatTxtField.StartChatNumber(PlayerText.input_point_potential[0], PlayerText.input_point_potential[1], 4);
                        break;
                    }
                case 19:
                    {
                        // view item upgrade
                        if (itemUpgrade.item != null)
                        {
                            ViewItem(itemUpgrade.item, VIEW_ITEM_UPGRADE_TAB);
                        }
                        break;
                    }
                case 20:
                    {
                        // view item next upgrade
                        if (itemNextUpgrade.item != null)
                        {
                            ViewItem(itemNextUpgrade.item, VIEW_ITEM_UPGRADE_TAB);
                        }
                        break;
                    }
                case 21:
                /*{
                    // cmd upgrade
                    if (typeUpgrade == 0)
                    {
                        if (itemUpgrade.item == null)
                        {
                            InfoMe.addInfo("Vui lòng chọn vật phẩm", 0);
                        }
                        else if (itemsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá nâng cấp", 0);
                        }
                        else
                        {
                            try
                            {
                                Item item = itemUpgrade.item;
                                if (item == null)
                                {
                                    InfoMe.addInfo("Vui lòng chọn vật phẩm", 0);
                                }
                                else if (item.template.type >= 8)
                                {
                                    InfoMe.addInfo("Vui lòng chọn vật phẩm", 0);
                                }
                                else
                                {
                                    InfoDlg.ShowWait();
                                    Service.Upgrade(item, indexsUpgrade);
                                }
                            }
                            catch { }
                        }
                    }
                    else if (typeUpgrade == 1)
                    {
                        if (indexsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá cường hóa", 0);
                        }
                        else
                        {
                            InfoDlg.ShowWait();
                            Service.StoneUpgrade(indexsUpgrade);
                        }
                    }
                    else if (typeUpgrade == 2)
                    {
                        if (indexsUpgrade.Count > 0)
                        {
                            Service.Upgrade(indexsUpgrade);
                            InfoDlg.ShowWait();
                        }
                    }
                    break;
                }*/

                //mxh
                {
                    if (typeUpgrade == 0)
                    {
                        if (itemUpgrade.item == null)
                        {
                            InfoMe.addInfo("Vui lòng chọn vật phẩm", 0);
                            break;
                        }
                        if (itemsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá nâng cấp", 0);
                            break;
                        }
                        if (indexsUpgrade == null || indexsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá nâng cấp", 0);
                            break;
                        }

                        Item item = itemUpgrade.item;
                        if (item.template.type >= 8)
                        {
                            InfoMe.addInfo("Vật phẩm không hợp lệ", 0);
                            break;
                        }
                        InfoDlg.ShowWait();
                        Service.Upgrade(item, indexsUpgrade);
                    }
                    else if (typeUpgrade == 1)
                    {
                        if (itemsUpgrade.Count == 0 || indexsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá cường hóa", 0);
                            break;
                        }

                        InfoDlg.ShowWait();
                        Service.StoneUpgrade(indexsUpgrade);
                    }
                    else if (typeUpgrade == 2)
                    {
                        if (itemsUpgrade.Count == 0 || indexsUpgrade.Count == 0)
                        {
                            InfoMe.addInfo("Vui lòng chọn đá nâng cấp", 0);
                            break;
                        }

                        InfoDlg.ShowWait();
                        Service.Upgrade(indexsUpgrade);
                    }

                    break;
                }
                case 22:
                    {
                        // tháo item upgrade
                        tabInfo.Close();
                        indexUpgrade = -1;
                        break;
                    }
                case 23:
                    {
                        // view item upgrade trong danh sách
                        indexSelect = (int)p;
                        if (itemsUpgrade[indexSelect].item != null)
                        {
                            ViewItem(itemsUpgrade[indexSelect].item, VIEW_ITEM_UPGRADE_LIST);
                        }
                        break;
                    }
                case 24:
                    {
                        // tháo item upgrade trong danh sách
                        tabInfo.Close();
                        Item item = (Item)p;
                        if (indexsUpgrade.Contains(item.indexUI))
                        {
                            indexsUpgrade.Remove(item.indexUI);
                        }
                        break;
                    }
                case 25:
                    {
                        // them item vào trong danh sách upgrade
                        tabInfo.Close();
                        Item item = (Item)p;
                        if (typeUpgrade == 0)
                        {
                            if (item.template.type < 8)
                            {
                                indexUpgrade = item.indexUI;
                            }
                            else
                            {
                                if (indexsUpgrade.Contains(item.indexUI))
                                {
                                    InfoMe.addInfo("Vật phẩm đã được chọn", 0);
                                }
                                else if (indexsUpgrade.Count >= 25)
                                {
                                    InfoMe.addInfo("Chỉ được chọn tối đa 25 vật phẩm", 0);
                                }
                                else
                                {
                                    indexsUpgrade.Add(item.indexUI);
                                }
                            }
                        }
                        else if (typeUpgrade == 1)
                        {
                            if (item.template.type == 18)
                            {
                                if (indexsUpgrade.Contains(item.indexUI))
                                {
                                    InfoMe.addInfo("Vật phẩm đã được chọn", 0);
                                }
                                else if (indexsUpgrade.Count >= 25)
                                {
                                    InfoMe.addInfo("Chỉ được chọn tối đa 25 vật phẩm", 0);
                                }
                                else
                                {
                                    indexsUpgrade.Add(item.indexUI);
                                }
                            }
                            else
                            {
                                InfoMe.addInfo("Chỉ được chọn Đá cường hóa", 0);
                            }
                        }
                        else if (typeUpgrade == 2)
                        {
                            if (indexsUpgrade.Contains(item.indexUI))
                            {
                                InfoMe.addInfo("Vật phẩm đã được chọn", 0);
                            }
                            else if (indexsUpgrade.Count >= 36)
                            {
                                InfoMe.addInfo("Chỉ được chọn tối đa 36 vật phẩm", 0);
                            }
                            else
                            {
                                indexsUpgrade.Add(item.indexUI);
                            }
                        }
                        break;
                    }
                case 26:
                    {
                        // view item shop
                        ViewItem(itemsShop.ElementAt(tabIndex).Value[(int)p], VIEW_TIEM_SHOP);
                        break;
                    }
                case 27:
                    {
                        // mua item market shop
                        tabInfo.Close();
                        Item item = (Item)p;
                        InfoDlg.ShowWait();
                        Service.instance.ConsignmentAction(1, -1, item.id, -1, -1);
                        break;
                    }
                case 28:
                    {
                        // lay item market shop
                        tabInfo.Close();
                        Item item = (Item)p;
                        InfoDlg.ShowWait();
                        Service.instance.ConsignmentAction(2, -1, item.id, -1, -1);
                        break;
                    }
                case 29:
                    {
                        // mua item shop
                        tabInfo.Close();
                        InfoDlg.ShowWait();
                        Service.BuyItem(tabIndex, ((Item)p).id, 1);
                        break;
                    }
                case 30:
                    {
                        // sell item
                        tabInfo.Close();
                        InfoDlg.ShowWait();
                        Service.ItemAction(Service.ACTION_TYPE_SELL_ITEM, ((Item)p).indexUI);
                        break;
                    }
                case 31:
                    {
                        // sell consignment
                        tabInfo.Close();
                        Item item = (Item)p;
                        if (item.quantity > 1)
                        {
                            List<TextField> textFields = new List<TextField>();
                            textFields.Add(new TextField("Giá bán (xu)", TextField.TYPE_NUMBER));
                            textFields.Add(new TextField("Số lượng", TextField.TYPE_NUMBER));
                            clientInput.Show(PlayerText.name_sell_market, textFields);
                        }
                        else
                        {
                            List<TextField> textFields = new List<TextField>();
                            textFields.Add(new TextField("Giá bán (xu)", TextField.TYPE_NUMBER));
                            clientInput.Show(PlayerText.name_sell_market, textFields);
                        }
                        break;
                    }
                case 32:
                    {
                        // change area
                        Service.RequestChangeArea((int)p);
                        break;
                    }
                case 33:
                    {
                        // cmd friend
                        indexSelect = (int)p;
                        CmdFriend friend = friends[indexSelect];
                        List<CmdMenu> menus = new List<CmdMenu>();
                        menus.Add(new CmdMenu("Xem thông tin", this, 72, friend.player.id));
                        menus.Add(new CmdMenu("Nhắn tin\n" + friend.player.name, this, 35, friend.player));
                        menus.Add(new CmdMenu("Dịch chuyển đến\n" + friend.player.name, this, 87, friend.player.id));
                        menus.Add(new CmdMenu("Xóa\n" + friend.player.name, this, 36, friend.player));
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 34:
                    {
                        // cmd enemy
                        indexSelect = (int)p;
                        CmdFriend enemy = enemys[indexSelect];
                        List<CmdMenu> menus = new List<CmdMenu>();
                        menus.Add(new CmdMenu("Xem thông tin", this, 72, enemy.player.id));
                        menus.Add(new CmdMenu("Trả thù", this, 82, enemy.player.id));
                        menus.Add(new CmdMenu("Xóa", this, 37, enemy.player));
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 35:
                    {
                        // chat player
                        Player player = (Player)p;
                        OpenChat(player);
                        break;
                    }
                case 36:
                    {
                        // xoa friend
                        InfoDlg.ShowWait();
                        Player player = (Player)p;
                        Service.instance.FriendActions(2, player.id, null);
                        break;
                    }
                case 37:
                    {
                        // xoa enemy
                        InfoDlg.ShowWait();
                        Player player = (Player)p;
                        Service.EnemyActions(2, player.id);
                        break;
                    }
                case 38:
                    {
                        // view top
                        indexSelect = (int)p;
                        CmdTop top = tops[indexSelect];
                        List<CmdMenu> menus = new List<CmdMenu>();
                        menus.Add(new CmdMenu("Xem thông tin", this, 72, top.player.id));
                        menus.Add(new CmdMenu("Nhắn tin\n" + top.player.name, this, 35, top.player));
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 39:
                    {
                        // select map spaceship
                        InfoDlg.ShowWait();
                        indexSelect = (int)p;
                        Service.RequestMapSpaceship(indexSelect);
                        Close();
                        break;
                    }
                case 40:
                    {
                        // receive achivement
                        indexSelect = (int)p;
                        CmdAchievement achievement = achievements[indexSelect];
                        if (!achievement.isReceive && achievement.param >= achievement.maxParam)
                        {
                            InfoDlg.ShowWait();
                            Service.RequestAchievement(indexSelect);
                            Close();
                        }
                        break;
                    }
                case 41:
                    {
                        // click team
                        indexSelect = (int)p;
                        CmdTeam team = teams[indexSelect];
                        List<CmdMenu> menus = new List<CmdMenu>();
                        menus.Add(new CmdMenu("Xem thông tin", this, 72, team.player.id));
                        menus.Add(new CmdMenu("Nhắn tin\n" + team.player.name, this, 35, team.player));
                        menus.Add(new CmdMenu("Xin vào\n" + team.player.name, this, 44, indexSelect));
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 42:
                    {
                        // find team
                        InfoDlg.ShowWait();
                        Service.TeamAction(6, -1);
                        break;
                    }
                case 43:
                    {
                        // create team
                        InfoDlg.ShowWait();
                        Service.TeamAction(0, -1);
                        break;
                    }
                case 44:
                    {
                        // xin vào team
                        indexSelect = (int)p;
                        CmdTeam team = teams[indexSelect];
                        Service.TeamAction(7, team.teamId);
                        break;
                    }
                case 45:
                    {
                        // click member team
                        indexSelect = (int)p;
                        List<CmdMenu> menus = new List<CmdMenu>();
                        if (Player.me.id == Player.me.team.members[0].player.id)
                        {
                            if (indexSelect != 0)
                            {
                                menus.Add(new CmdMenu("Xem thông tin", this, 72, Player.me.team.members[indexSelect].player.id));
                                menus.Add(new CmdMenu("Nhắn tin\n" + Player.me.team.members[indexSelect].player.name, this, 35, Player.me.team.members[indexSelect].player));
                                menus.Add(new CmdMenu("Nhường chức", this, 49, Player.me.team.members[indexSelect].player));
                                menus.Add(new CmdMenu("Khai trừ", this, 50, Player.me.team.members[indexSelect].player));
                            }
                        }
                        else if (Player.me.id != indexSelect)
                        {
                            menus.Add(new CmdMenu("Xem thông tin", this, 72, Player.me.team.members[indexSelect].player.id));
                            menus.Add(new CmdMenu("Nhắn tin\n" + Player.me.team.members[indexSelect].player.name, this, 35, Player.me.team.members[indexSelect].player));
                        }
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 46:
                    {
                        // out team
                        InfoDlg.ShowWait();
                        Service.TeamAction(2, -1);
                        break;
                    }
                case 47:
                    {
                        // set team
                        InfoDlg.ShowWait();
                        Service.TeamAction(3, -1);
                        break;
                    }
                case 48:
                    {
                        // refresh team
                        InfoDlg.ShowWait();
                        Service.TeamAction(8, -1);
                        break;
                    }
                case 49:
                    {
                        // nhường chức đội trưởng
                        InfoDlg.ShowWait();
                        Service.TeamAction(5, ((Player)p).id);
                        break;
                    }
                case 50:
                    {
                        // Khai trừ khỏi nhóm
                        InfoDlg.ShowWait();
                        Service.TeamAction(4, ((Player)p).id);
                        break;
                    }
                case 51:
                    {
                        // cmd chat
                        string content = textChat.GetText();
                        if (content == null || content.Length == 0)
                        {
                            break;
                        }
                        if (textChat.name.Equals(PlayerText.input_chat_global))
                        {
                            if (Player.me.diamond < 2)
                            {
                                InfoMe.addInfo("Cần ít nhất 2 kim cương để chat thế giới", 0);
                                textChat.ClearAllText();
                                break;
                            }
                            Service.ChatGlobal(content);
                        }
                        else if (textChat.name.Equals(PlayerText.input_chat_clan))
                        {
                            if (Player.me.clan != null)
                            {
                                Service.ChatClan(content);
                            }
                        }
                        else if (textChat.name.Equals(PlayerText.input_chat_team))
                        {
                            if (Player.me.team != null)
                            {
                                Service.ChatTeam(content);
                            }
                        }
                        else if (textChat.name.StartsWith(PlayerText.input_chat_player))
                        {
                            cmdChatPlayers[indexSelect].messages.Add(new CmdMessage(this, 52, Player.me, content));
                            Service.chatPlayer(content, cmdChatPlayers[indexSelect].player.id);
                            SetTabChatPlayer();
                        }
                        textChat.ClearAllText();
                        break;
                    }
                case 52:
                    {
                        // cmd message
                        Player player = (Player)p;
                        if (player.id != Player.me.id && player.name != "Thông báo Server")
                        {
                            List<CmdMenu> menus = new List<CmdMenu>();
                            menus.Add(new CmdMenu("Xem thông tin", this, 72, player.id));
                            menus.Add(new CmdMenu("Nhắn tin\n" + player.name, this, 35, player));
                            GameCanvas.StartAt(menus);
                        }
                        break;
                    }
                case 53:
                    {
                        // select chat
                        indexSelect = (int)p;
                        SetTabChatPlayer();
                        break;
                    }
                case 54:
                    {
                        // view item player trade
                        int index = (int)p;
                        ViewItem(itemsPlayerTrade[index], VIEW_ITEM_TRADE_PLAYER);
                        break;
                    }
                case 55:
                    {
                        // view item trade
                        int index = (int)p;
                        ViewItem(itemsTrade[index], VIEW_ITEM_TRADE);
                        break;
                    }
                case 56:
                    {
                        // add item trade
                        tabInfo.Close();
                        Item item = (Item)p;
                        if (itemsTrade.Count >= 12)
                        {
                            InfoMe.addInfo("Chỉ được giao dịch tối đa 12 vật phẩm", 0);
                            break;
                        }
                        bool flag = false;
                        for (int i = 0; i < itemsTrade.Count; i++)
                        {
                            if (itemsTrade[i].indexUI == item.indexUI)
                            {
                                flag = true;
                                break;
                            }
                        }
                        if (flag)
                        {
                            InfoMe.addInfo("Vật phẩm đã được chọn", 0);
                        }
                        else
                        {
                            indexSelect = item.indexUI;
                            if (item.quantity > 1)
                            {
                                // nhập số lượng
                                chatTxtField.StartChatNumber(PlayerText.input_quantity_trade[0], PlayerText.input_quantity_trade[1], 9);
                            }
                            else
                            {
                                Service.TradeAction(3, -1, item.indexUI, 1);
                            }
                        }
                        break;
                    }
                case 57:
                    {
                        // remove item trade
                        tabInfo.Close();
                        Item item = (Item)p;
                        Service.TradeAction(4, -1, item.indexUI, -1);
                        break;
                    }
                case 58:
                    {
                        // Close tab expiryInfo
                        tabInfo.Close();
                        break;
                    }
                case 59:
                    {
                        //cmd item trade
                        Service.TradeAction(6, -1, -1, -1);
                        if (isLock && isPlayerLock)
                        {
                            isAccept = true;
                        }
                        break;
                    }
                case 60:
                    {
                        //input coin trade
                        chatTxtField.StartChatNumber(PlayerText.input_coin_trade[0], PlayerText.input_coin_trade[1], 10);
                        break;
                    }
                case 61:
                    {
                        // out clan
                        DisplayManager.instance.dialog.SetInfo("Bạn có chắc chắn muốn rời bang hội không?", new Command("Có", this, 65, null), null, new Command("Không", this, 0, null));
                        break;
                    }
                case 62:
                    {
                        // đóng góp
                        List<CmdMenu> menus = new List<CmdMenu>();
                        menus.Add(new CmdMenu("Xu", this, 66, null));
                        //menus.Add(new CmdMenu("Kim cương", this, 67, null));
                        //menus.Add(new CmdMenu("Đóng", this, -1, null));
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 63:
                    {
                        // khẩu hiệu
                        chatTxtField.StartChat(PlayerText.input_slogan[0], PlayerText.input_slogan[1], 50);
                        break;
                    }
                case 64:
                    {
                        // thông báo clan
                        chatTxtField.StartChat(PlayerText.input_notification[0], PlayerText.input_notification[1], 200);
                        break;
                    }
                case 65:
                    {
                        // confirm out clan
                        Service.ClanAction(8, -1, null);
                        break;
                    }
                case 66:
                    {
                        // cống hiến xu
                        chatTxtField.StartChatNumber(PlayerText.input_xu_clan[0], PlayerText.input_xu_clan[1], 9);
                        break;
                    }
                case 67:
                    {
                        // cống hiến kim cuong
                        chatTxtField.StartChatNumber(PlayerText.input_diamond_clan[0], PlayerText.input_diamond_clan[1], 9);
                        break;
                    }
                case 68:
                    {
                        // clan member
                        indexSelect = (int)p;
                        List<CmdMenu> menus = new List<CmdMenu>();
                        if (Player.me.id == Player.me.clan.members[0].playerId)
                        {
                            if (indexSelect != 0)
                            {
                                menus.Add(new CmdMenu("Xem thông tin", this, 72, Player.me.clan.members[indexSelect].playerId));
                                Player player = new Player();
                                player.id = Player.me.clan.members[indexSelect].playerId;
                                player.name = Player.me.clan.members[indexSelect].name;
                                player.gender = Player.me.clan.members[indexSelect].gender;
                                menus.Add(new CmdMenu("Nhắn tin\n" + player.name, this, 35, player));

                                if (Player.me.clan.members[indexSelect].roleId == 1)
                                {
                                    menus.Add(new CmdMenu("Bãi nhiệm", this, 71, Player.me.clan.members[indexSelect].playerId));
                                }
                                else if (Player.me.clan.members[indexSelect].roleId == 2)
                                {
                                    menus.Add(new CmdMenu("Phong phó bang", this, 70, Player.me.clan.members[indexSelect].playerId));
                                }
                                menus.Add(new CmdMenu("Nhường chức", this, 76, Player.me.clan.members[indexSelect].playerId));
                                menus.Add(new CmdMenu("Khai trừ", this, 69, Player.me.clan.members[indexSelect].playerId));
                            }
                        }
                        else if (Player.me.id == Player.me.clan.members[1].playerId)
                        {
                            if (indexSelect != 1)
                            {
                                menus.Add(new CmdMenu("Xem thông tin", this, 72, Player.me.clan.members[indexSelect].playerId));
                                Player player = new Player();
                                player.id = Player.me.clan.members[indexSelect].playerId;
                                player.name = Player.me.clan.members[indexSelect].name;
                                player.gender = Player.me.clan.members[indexSelect].gender;
                                menus.Add(new CmdMenu("Nhắn tin\n" + player.name, this, 35, player));
                                menus.Add(new CmdMenu("Khai trừ", this, 69, Player.me.clan.members[indexSelect].playerId));
                            }
                        }
                        else
                        {
                            menus.Add(new CmdMenu("Xem thông tin", this, 72, Player.me.clan.members[indexSelect].playerId));
                            Player player = new Player();
                            player.id = Player.me.clan.members[indexSelect].playerId;
                            player.name = Player.me.clan.members[indexSelect].name;
                            player.gender = Player.me.clan.members[indexSelect].gender;
                            menus.Add(new CmdMenu("Nhắn tin\n" + player.name, this, 35, player));
                        }
                        GameCanvas.StartAt(menus);
                        break;
                    }
                case 69:
                    {
                        // kick out clan
                        Close();
                        Service.ClanAction(7, (int)p, null);
                        break;
                    }
                case 70:
                    {
                        // phong phó bang
                        Close();
                        Service.ClanAction(5, (int)p, null);
                        break;
                    }
                case 71:
                    {
                        // bãi nhiệm
                        Close();
                        Service.ClanAction(6, (int)p, null);
                        break;
                    }
                case 72:
                    {
                        // xem thong tin
                        Service.RequestInfoPlayer((int)p);
                        break;
                    }
                case 73:
                    {
                        // cmd setting
                        int index = (int)p;
                        switch (index)
                        {
                            case 0:
                                SoundManager.instance.isPlay = !SoundManager.instance.isPlay;
                                Rms.SaveInt("isPlaySound", SoundManager.instance.isPlay ? 1 : 0);
                                break;
                            case 1:
                                GraphicManager.instance.isLowGraphic = !GraphicManager.instance.isLowGraphic;
                                Rms.SaveInt("isLowGraphic", GraphicManager.instance.isLowGraphic ? 1 : 0);
                                break;
                            case 2:
                                screenManager.gameScreen.isShowSpin = !screenManager.gameScreen.isShowSpin;
                                break;
                            case 3:
                                InfoDlg.ShowWait();
                                Service.instance.SetShowMark();
                                break;
                            case 4:
                                screenManager.gameScreen.isShowEffectPower = !screenManager.gameScreen.isShowEffectPower;
                                Rms.SaveInt("isShowEffectPower", screenManager.gameScreen.isShowEffectPower ? 1 : 0);
                                break;
                            case 5:
                                Player.isPaintMedal = !Player.isPaintMedal;
                                Rms.SaveInt("isPaintMedal", Player.isPaintMedal ? 1 : 0);
                                break;
                            case 6:
                                InfoDlg.ShowWait();
                                Service.instance.LockAction();
                                break;
                            case 7:
                                screenManager.gameScreen.isAutoFindMob = !screenManager.gameScreen.isAutoFindMob;
                                break;
                            case 8:
                                InfoDlg.ShowWait();
                                Service.instance.SaveArea();
                                break;
                            case 9:
                                screenManager.gameScreen.isAutoPick = !screenManager.gameScreen.isAutoPick;
                                break;
                            case 10:
                                screenManager.gameScreen.isAttackTinhAnh = !screenManager.gameScreen.isAttackTinhAnh;
                                break;
                            case 11:
                                screenManager.gameScreen.isAttackThuLinh = !screenManager.gameScreen.isAttackThuLinh;
                                break;
                            case 12:
                                screenManager.gameScreen.isAutoLogin = !screenManager.gameScreen.isAutoLogin;
                                break;
                        }
                        break;
                    }
                case 74:
                    {
                        // change isOn
                        indexSelect = (int)p;
                        if (indexSelect < 2)
                        {
                            Service.changePk(0, indexSelect - 1);
                        }
                        else
                        {
                            Service.changePk(1, indexSelect - 1);
                        }
                        Close();
                        break;
                    }
                case 75:
                    {
                        // cmd player mini game
                        Service.MiniGame(1, (int)p);
                        break;
                    }
                case 76:
                    {
                        // nhường chức
                        Close();
                        Service.ClanAction(9, (int)p, null);
                        break;
                    }
                case 77:
                    {
                        // đổi tab đệ
                        int index = (int)p;
                        if (index == 2)
                        {
                            InfoDlg.ShowWait();
                            Service.DiscipleStatus();
                        }
                        else
                        {
                            indexTabDisciple = index;
                            SetTab(TabPanel.tabDisciple.type);
                        }
                        break;
                    }
                case 78:
                    {
                        // tháo đồ đệ tử ra
                        tabInfo.Close();
                        Service.ItemAction(typeShowOutFit == 0 ? Service.ACTION_TYPE_DISCIPLE_UNDRESS : Service.ACTION_TYPE_DISCIPLE_UNDRESS_OTHER, ((Item)p).indexUI);
                        break;
                    }
                case 79:
                    {
                        // mặc đồ cho đệ tử
                        tabInfo.Close();
                        Service.ItemAction(Service.ACTION_TYPE_DISCIPLE_WEAR, ((Item)p).indexUI);
                        break;
                    }
                case 80:
                    {
                        // nhan thuong reward
                        tabInfo.Close();
                        InfoDlg.ShowWait();
                        Service.Reward((int)p);
                        break;
                    }
                case 81:
                    {
                        int[] indexes = (int[])p;
                        Item item = cmdRewards[indexes[0]].gifts[indexes[1]];
                        ViewItem(item, VIEW_ITEM_GIFT);
                        break;
                    }
                case 82:
                    {
                        // trả thù
                        Service.EnemyActions(3, (int)p);
                        break;
                    }
                case 83:
                    {
                        // action gift
                        InfoDlg.ShowWait();
                        Service.MissionAction(giftType, (int)p);
                        break;
                    }
                case 84:
                    {
                        // nhập tên them bạn
                        chatTxtField.StartChat(PlayerText.input_add_friend[0], PlayerText.input_add_friend[1], 10);
                        break;
                    }
                case 85:
                    {
                        // player in map
                        int playerId = (int)p;
                        Player player = ScreenManager.instance.gameScreen.FindPlayerInMap(playerId);
                        if (player != null)
                        {
                            Close();
                            Player.me.FocusManualTo(player);
                            Player.me.currentMovePoint = new MovePoint(player.x, player.y);
                        }
                        break;
                    }
                case 86:
                    {
                        // setting focus
                        int index = (int)p;
                        switch (index)
                        {
                            case 0:
                                Player.isSelectItemMap = !Player.isSelectItemMap;
                                break;
                            case 1:
                                Player.isSelectNpc = !Player.isSelectNpc;
                                break;
                            case 2:
                                Player.isSelectMonster = !Player.isSelectMonster;
                                break;
                            case 3:
                                Player.isSelectPlayer = !Player.isSelectPlayer;
                                break;
                            case 4:
                                Player.isSelectEnemy = !Player.isSelectEnemy;
                                break;
                        }
                        break;
                    }
                case 87:
                    {
                        int playerId = (int)p;
                        Service.instance.TeleportToPlayer(playerId);
                        break;
                    }
                case 88:
                    {
                        // mặc đồ cho pet
                        tabInfo.Close();
                        Service.ItemAction(Service.ACTION_TYPE_PET_WEAR, ((Item)p).indexUI);
                        break;

                    }
                case 89:
                    {
                        // tháo đồ pet ra
                        tabInfo.Close();
                        Service.ItemAction(Service.ACTION_TYPE_PET_UNDRESS, ((Item)p).indexUI);
                        break;
                    }
                case 90:
                    {
                        // nhập số lượng sử dụng nhiều
                        tabInfo.Close();
                        chatTxtField.StartChat(PlayerText.input_quantity_use[0], PlayerText.input_quantity_use[1], 10);
                        break;
                    }
                case 91:
                    {
                        InfoDlg.ShowWait();
                        Service.instance.Intrinsic((int)p);
                        break;
                    }
                case 92:
                    {
                        int[] indexes = (int[])p;
                        Item item = gifts[indexes[0]].gifts[indexes[1]];
                        ViewItem(item, VIEW_ITEM_GIFT);
                        break;
                    }
                case 93:
                    {
                        tabInfo.Close();
                        chatTxtField.StartChat(PlayerText.input_mua_nhieu[0], PlayerText.input_mua_nhieu[1], 3);
                        chatTxtField.p = p;
                        break;
                    }
                case 94:
                    {
                        if (p != null)
                        {
                            ViewItem((Item)p, VIEW_ITEM_GIFT);
                        }
                        break;
                    }
                case 95:
                    {
                        Service.instance.LuckyPickMe(0, -1);
                        break;
                    }
                case 96:
                    {
                        chatTxtField.StartChat(PlayerText.input_pick_me[0], PlayerText.input_pick_me[1], 9);
                        break;
                    }
                case 97:
                    {
                        if (itemsLucky.Count == 0)
                        {
                            if (indexLucky == -1)
                            {
                                InfoMe.addInfo("Bạn chưa chọn ô nào", 1);
                                return;
                            }
                            Service.instance.Lucky(indexLucky);
                        }
                        else
                        {
                            itemsLucky.Clear();
                        }
                        break;
                    }
                case 98:
                    {
                        if (itemsLucky.Count == 0)
                        {
                            indexLucky = (int)p;
                        }
                        else
                        {
                            ViewItem(itemsLucky[(int)p], VIEW_ITEM_GIFT);
                        }
                        break;
                    }
            }
        }

        public static void PaintScroll(MyGraphics g, int x, int y, int w, int h, int hMax, int hView, int yView)
        {
            g.Reset();
            g.SetColor(Color.white);
            g.FillRect(x, y, w, h, 2);
            int hShow = hView * h / hMax;
            int yShow = yView * h / hMax;
            if (yShow < 0)
            {
                yShow = 0;
            }
            if (yShow + hShow > h)
            {
                yShow = h - hShow;
            }
            g.SetColor(new Color32(58, 210, 248, 255));
            g.FillRect(x - 1, y + yShow, w + 2, hShow, 2);
        }

        private void ViewItem(Item item, int action)
        {
            string info = "";
            int upgrade = item.GetUpgrade();
            int star = item.GetParam(67);
            int star_use = item.GetParam(68);
            if (action == VIEW_TIEM_SHOP)
            {
                if (tabIndex == tabs.Count - 2 && item.sellerName != null && item.sellerName != "")
                {
                    if (item.status == 0)
                    {
                        info += "|n0Đang bán";
                    }
                    else if (item.status == 1)
                    {
                        info += "|n0Đã bán";
                    }
                }
                string namePrice = " xu";
                if (item.typePrice == 1)
                {
                    namePrice = " Kim cương";
                }
                else if (item.typePrice == 2)
                {
                    namePrice = " Xu khóa";
                }
                else if (item.typePrice == 3)
                {
                    namePrice = " Ruby";
                }
                else if (item.typePrice > 3)
                {
                    namePrice = " Điểm";
                }
                info += "|n0Giá: " + Utils.GetMoneys(item.price) + namePrice;
                if (item.typePrice == 2)
                {
                    info += "|n0(Nếu không đủ Xu khóa sẽ trừ Xu)";
                }
                else if (item.typePrice == 3)
                {
                    info += "|n0(Nếu không đủ Ruby khóa sẽ trừ Kim cương)";
                }
            }
            if (item.template.description != "" && item.template.description != "Chưa có")
            {
                info += "|n0" + item.template.description;
            }
            if (item.isLock)
            {
                info += "|n0Đã khóa";
            }
            if (item.template.levelRequire > 1)
            {
                if (Player.me.level < item.template.levelRequire)
                {
                    info += "|n3Cấp độ yêu cầu: " + item.template.levelRequire;
                }
                else
                {
                    info += "|n0Cấp độ yêu cầu: " + item.template.levelRequire;
                }
            }

            if (item.template.gender != -1)
            {
                string planet = "Tộc: Trái đất";
                if (item.template.gender == 1)
                {
                    planet = "Tộc: Namek";
                }
                else if (item.template.gender == 2)
                {
                    planet = "Tộc: Saiyan";
                }
                if (Player.me.gender == item.template.gender)
                {
                    //expiryInfo += "|n0" + planet;
                }
                else
                {
                    info += "|n3" + planet;
                }
            }
            if (item.quantity > 1)
            {
                info += "|n0Số lượng: " + Utils.GetMoneys(item.quantity);
            }
            if (item.template.type < 8)
            {
                foreach (ItemOption itemOption in item.options)
                {
                    if (itemOption.template.id == 36)
                    {
                        info += "|n0" + itemOption.template.name;
                        break;
                    }
                }
                foreach (ItemOption itemOption in item.options)
                {
                    if (itemOption.template.type == 0)
                    {
                        info += "|n0" + itemOption.GetStrOption();
                    }
                }
                info += "|n7Kích ẩn (8 món)";
                bool flag = false;
                if (typeShowOutFit == 1)
                {
                    for (int i = 0; i < 8; i++)
                    {
                        if (Player.me.itemsOther[i] == null)
                        {
                            flag = true;
                            break;
                        }
                    }
                }
                else
                {
                    for (int i = 0; i < 8; i++)
                    {
                        if (Player.me.itemsBody[i] == null)
                        {
                            flag = true;
                            break;
                        }
                    }
                }
                foreach (ItemOption itemOption in item.options)
                {
                    if (itemOption.template.type == 2 || itemOption.template.type == 7)
                    {
                        info += (flag ? "|n5" : "|n0") + itemOption.GetStrOption();
                    }
                }
                info += "|n7Kích cấp độ";
                foreach (ItemOption itemOption in item.options)
                {
                    if (itemOption.template.type == 1)
                    {
                        string text_option = itemOption.GetStrOption();
                        if (upgrade >= 16)
                        {
                            info += "|n0" + text_option;
                        }
                        else if (upgrade >= 14)
                        {
                            info += ((text_option.Contains("(+4)") || text_option.Contains("(+8)") || text_option.Contains("(+12)") || text_option.Contains("(+14)")) ? "|n0" : "|n5") + text_option;
                        }
                        else if (upgrade >= 12)
                        {
                            info += ((text_option.Contains("(+4)") || text_option.Contains("(+8)") || text_option.Contains("(+12)")) ? "|n0" : "|n5") + text_option;
                        }
                        else if (upgrade >= 8)
                        {
                            info += ((text_option.Contains("(+4)") || text_option.Contains("(+8)")) ? "|n0" : "|n5") + text_option;
                        }
                        else if (upgrade >= 4)
                        {
                            info += (text_option.Contains("(+4)") ? "|n0" : "|n5") + text_option;
                        }
                        else
                        {
                            info += "|m5" + text_option;
                        }
                    }
                }
                //info += "|n7Kích cấp độ (8 món)";//mxh
                //int min_upgrade = 16;
                //for (int i = 0; i < 8; i++)
                //{
                //    if (Player.me.itemsBody[i] != null)
                //    {
                //        int upgrade_item = Player.me.itemsBody[i].GetUpgrade();
                //        if (min_upgrade > upgrade_item)
                //        {
                //            min_upgrade = upgrade_item;
                //        }
                //    }
                //    else
                //    {
                //        min_upgrade = 0;
                //        break;
                //    }
                //}
                //foreach (ItemOption itemOption in item.options)
                //{
                //    if (itemOption.template.type == 5)
                //    {
                //        string text_option = itemOption.GetStrOption();
                //        if (min_upgrade >= 16)
                //        {
                //            info += "|n0" + text_option;
                //        }
                //        else if (min_upgrade >= 14)
                //        {
                //            info += ((text_option.Contains("(8 món +4)") || text_option.Contains("(8 món +8)") || text_option.Contains("(8 món +12)") || text_option.Contains("(8 món +14)")) ? "|n0" : "|n5") + text_option;
                //        }
                //        else if (min_upgrade >= 12)
                //        {
                //            info += ((text_option.Contains("(8 món +4)") || text_option.Contains("(8 món +8)") || text_option.Contains("(8 món +12)")) ? "|n0" : "|n5") + text_option;
                //        }
                //        else if (min_upgrade >= 8)
                //        {
                //            info += ((text_option.Contains("(8 món +4)") || text_option.Contains("(8 món +8)")) ? "|n0" : "|n5") + text_option;
                //        }
                //        else if (min_upgrade >= 4)
                //        {
                //            info += (text_option.Contains("(8 món +4)") ? "|n0" : "|n5") + text_option;
                //        }
                //        else
                //        {
                //            info += "|m5" + text_option;
                //        }
                //    }
                //}

            }
            else
            {
                foreach (ItemOption itemOption in item.options)
                {
                    if (itemOption.template.id != 19 && itemOption.template.id != 67 && itemOption.template.id != 68 && itemOption.template.type != 4)
                    {
                        info += "|n0" + itemOption.GetStrOption();
                    }
                }
            }
            if (star_use > 0)
            {
                info += "|n7Pha lê hóa";
            }
            foreach (ItemOption itemOption in item.options)
            {
                if (itemOption.template.type == 4)
                {
                    info += "|n0" + itemOption.GetStrOption();
                }
            }
            if (item.sellerName != null && item.sellerName != "")
            {
                info += "|n7Người bán: " + item.sellerName;
                info += "|n7Còn: " + item.expiryInfo;
            }
            if (item.strFrom != null && item.strFrom != "")
            {
                info += "|n0Người gửi: " + item.strFrom;
                info += "|n0Ghi chú: " + item.strInfo;
                info += "|n0Hạn nhận: " + item.strExpiry;
            }
            string text_upgrade = upgrade > 0 ? (" [+" + upgrade + "]") : "";
            if (item.template.type >= 8)
            {
                text_upgrade = "";
            }
            if (info.StartsWith("|"))
            {
                info = info.Substring(1);
            }
            tabInfo.ShowInfo(item.template.name + text_upgrade, info, item.template.iconId, star, star_use, GetCmdActionItems(item, action).ToArray());
        }

        private List<Command> GetCmdActionItems(Item item, int action)
        {
            List<Command> commands = new List<Command>();
            if (action == VIEW_ITEM_BAG)
            {
                if (item.template.isMaster)
                {
                    commands.Add(new CmdMini("Sử dụng", this, 6, item));
                    if (item.quantity > 1)
                    {
                        commands.Add(new CmdMini("SD nhiều", this, 90, item));
                    }
                }
                if (item.template.isDisciple)
                {
                    commands.Add(new CmdMini("SD cho đệ", this, 79, item));
                }
                if (item.template.isPet)
                {
                    commands.Add(new CmdMini("SD cho pet", this, 88, item));
                }
                commands.Add(new CmdMini("Cất rương", this, 9, item));
                //commands.Add(new CmdMini("Bỏ ra", this, 7, item));
            }
            if (action == VIEW_ITEM_BOX)
            {
                commands.Add(new CmdMini("Lấy ra", this, 10, item));
            }
            if (action == VIEW_ITEM_BODY)
            {
                commands.Add(new CmdMini("Tháo ra", this, 8, item));
            }
            if (action == VIEW_ITEM_UPGRADE_TAB)
            {
                commands.Add(new CmdMini("Lấy ra", this, 22, item));
            }
            if (action == VIEW_ITEM_UPGRADE_LIST)
            {
                commands.Add(new CmdMini("Lấy ra", this, 24, item));
            }
            if (action == VIEW_ITEM_UPGRADE_BAG)
            {
                commands.Add(new CmdMini("Chọn", this, 25, item));
            }
            if (action == VIEW_ITEM_TRADE_BAG)
            {
                commands.Add(new CmdMini("Chọn", this, 56, item));
            }
            if (action == VIEW_ITEM_TRADE)
            {
                commands.Add(new CmdMini("Lấy ra", this, 57, item));
            }
            if (action == VIEW_ITEM_TRADE_PLAYER)
            {
                commands.Add(new CmdMini("Đóng", this, 58, item));
            }
            if (action == VIEW_ITEM_VIEW_PLAYER)
            {
                commands.Add(new CmdMini("Đóng", this, 58, item));
            }
            if (action == VIEW_TIEM_SHOP)
            {
                if (typeShop == 0 || typeShop > 1)
                {
                    commands.Add(new CmdMini("Mua", this, 29, item));
                    if (item.template.isUp)
                    {
                        commands.Add(new CmdMini("Mua nhiều", this, 93, item));
                    }
                }
                else
                {
                    if (tabIndex != tabs.Count - 2)
                    {
                        commands.Add(new CmdMini("Mua", this, 27, item));
                    }
                    else
                    {
                        commands.Add(new CmdMini(item.status == 0 ? "Lấy về" : "Nhận xu", this, 28, item));
                    }
                }
            }
            if (action == VIEW_ITEM_SHOP_BAG)
            {
                if (isShowBag)
                {
                    if (typeShop == 0 || typeShop > 1)
                    {
                        commands.Add(new CmdMini("Bán", this, 30, item));
                    }
                    else
                    {
                        commands.Add(new CmdMini("Bán", this, 31, item));
                    }
                }
                else
                {
                    commands.Add(new CmdMini("Lấy ra", this, 10, item));
                }
            }
            if (action == VIEW_ITEM_DISCIPLE)
            {
                commands.Add(new CmdMini("Tháo ra", this, 78, item));
            }
            if (action == VIEW_ITEM_PET)
            {
                commands.Add(new CmdMini("Tháo ra", this, 89, item));
            }
            if (action == VIEW_TIEM_REWARD)
            {
                commands.Add(new CmdMini("Nhận", this, 80, item));
            }
            if (action == VIEW_ITEM_GIFT)
            {
                commands.Add(new CmdMini("Đóng", this, -1, item));
            }
            commands.Reverse();
            return commands;
        }

        public void ViewSkill(Skill skill)
        {
            string info = "n0" + skill.GetDescription();

            if (skill.level > 0)
            {
                if (skill.level == skill.template.maxLevel)
                {
                    info += "|n7Đã đạt cấp tối đa";
                }
                else
                {
                    info += "|n7Cấp hiện tại: " + skill.level;
                }
                info += "|n0Cấp độ yêu cầu: " + skill.template.levelRequire;
                foreach (SkillOption skillOption in skill.template.options)
                {
                    int param = skillOption.GetParam(skill.level, skill.upgrade);
                    if (param <= 0)
                    {
                        continue;
                    }
                    info += "|n0" + skillOption.ToString(skill.level, skill.upgrade);
                }
                if (skill.template.isProactive)
                {
                    info += "|n0KI tiêu hao: " + Utils.GetMoneys(skill.GetManaUse()) + (skill.template.typeMana == 0 ? "" : "%");
                    info += "|n0Thời gian hồi chiêu: " + Utils.GetMoneys(skill.GetCoolDown()) + " mili giây";
                }
                if (skill.level == skill.template.maxLevel && skill.upgrade > 0)
                {
                    if (skill.upgrade >= skill.template.maxUpgrade)
                    {
                        info += "|n7Đã cường hóa tối đa";
                    }
                    else
                    {
                        info += "|n7Cường hóa: " + skill.upgrade;
                        info += "|n7Điểm thành thạo: " + Utils.GetMoneys(skill.point) + "/" + Utils.GetMoneys(skill.template.pointUpgrade[skill.upgrade]);
                    }
                }
            }
            if (skill.level < skill.template.maxLevel)
            {
                info += "|n7Cấp tiếp theo: " + (skill.level + 1);
                foreach (SkillOption skillOption in skill.template.options)
                {
                    int param = skillOption.GetParam(skill.level + 1, 0);
                    if (param <= 0)
                    {
                        continue;
                    }
                    info += "|n0" + skillOption.ToString(skill.level + 1, 0);
                }
                if (skill.template.isProactive)
                {
                    info += "|n0KI tiêu hao: " + Utils.GetMoneys(skill.GetManaUse(skill.level + 1)) + (skill.template.typeMana == 0 ? "" : "%");
                    info += "|n0Thời gian hồi chiêu: " + Utils.GetMoneys(skill.GetCoolDown(skill.level + 1)) + " mili giây";
                }
            }
            tabInfo.ShowInfo(skill.GetName(), info, -1, 0, 0, GetCmdActionSkills(skill).ToArray());
        }

        private List<Command> GetCmdActionSkills(Skill skill)
        {
            List<Command> commands = new List<Command>();
            if (type != TabPanel.tabDisciple.type)
            {
                if (skill.level < skill.template.maxLevel)
                {
                    commands.Add(new CmdMini("Cộng", this, 12, skill));
                }
                if (skill.template.isProactive)
                {
                    commands.Add(new CmdMini("Gán", this, 13, skill));
                }
            }
            if (commands.Count == 0)
            {
                commands.Add(new CmdMini("OK", this, 15, null));
            }
            return commands;
        }

        public void ViewPotential(Player player, int index)
        {
            if (index > 3)
            {
                return;
            }
            string name = "";
            string info = "";
            long potential = 0L;
            int point = 0;
            if (index == 0)
            {
                name = "Sức mạnh";
                info = "n0Tăng sức đánh khi tấn công";
                info += "|n0(1 điểm = 1 sức đánh)";
                //info += "|n0(từ cấp 30 trở lên tăng thêm 0.5 sức đánh với mỗi 10 cấp)";
                point = player.baseDamage;
                potential = player.potentialUpDamage;
            }
            else if (index == 1)
            {
                name = "Thể lực";
                info = "n0Tăng HP";
                long base_point = 20L + (player.level - 20) / 10 * 10;
                if (base_point < 20L)
                {
                    base_point = 20L;
                }
                base_point = 20L;
                info += "|n0(1 điểm = " + base_point + "HP)";
                //info += "|n0(Sẽ tăng thêm tại các mốc cấp 30, 40, 50,...)";
                point = player.baseHp;
                potential = player.potentialUpHp;
            }
            else if (index == 2)
            {
                name = "Trí lực";
                info = "n0Tăng KI";
                long base_point = 20L + (player.level - 20) / 10 * 10;
                if (base_point < 20L)
                {
                    base_point = 20L;
                }
                base_point = 20L;
                info += "|n0(1 điểm = " + base_point + "KI)";
                //info += "|n0(Sẽ tăng thêm tại các mốc cấp 30, 40, 50,...)";
                point = player.baseMp;
                potential = player.potentialUpMp;
            }
            else if (index == 3)
            {
                name = "Thân pháp";
                info = "n0Tăng né đòn, chí mạng";
                info += "|n0(1 điểm = 10 né đòn, 10 chí mạng)";
                point = player.baseConstitution;
                potential = player.potentialUpConstitution;
            }
            info += "|n0Điểm hiện tại: " + Utils.GetMoneys(point);
            int maxPoint;
            string info_point = string.Empty;
            if (index == 0)
            {
                maxPoint = player.baseDamage + 2000;
                if (maxPoint > player.baseHp + 1000)
                {
                    maxPoint = player.baseHp + 1000;
                }
                if (maxPoint > player.baseMp + 1000)
                {
                    maxPoint = player.baseMp + 1000;
                }
                info_point = "(!) Để tăng giới hạn Sức mạnh hãy nâng điểm Thể lực và Trí lực";
                info_point += "|n0(!) Điểm Sức mạnh không thể chênh lệch quá 1000 điểm so với điểm Thể lực và Trí lực";
            }
            else if (index == 1)
            {
                maxPoint = player.baseHp + 2000;
                if (maxPoint > player.baseDamage + 1000)
                {
                    maxPoint = player.baseDamage + 1000;
                }
                if (maxPoint > player.baseMp + 1000)
                {
                    maxPoint = player.baseMp + 1000;
                }
                info_point = "(!) Để tăng giới hạn Thể lực hãy nâng điểm Sức mạnh và Trí lực";
                info_point += "|n0(!) Điểm Thể lực không thể chênh lệch quá 1000 điểm so với điểm Sức mạnh và Trí lực";
            }
            else if (index == 2)
            {
                maxPoint = player.baseMp + 2000;
                if (maxPoint > player.baseHp + 1000)
                {
                    maxPoint = player.baseHp + 1000;
                }
                if (maxPoint > player.baseDamage + 1000)
                {
                    maxPoint = player.baseDamage + 1000;
                }
                info_point = "(!) Để tăng giới hạn Trí lực hãy nâng điểm Sức mạnh và Thể lực";
                info_point += "|n0(!) Điểm Trí lực không thể chênh lệch quá 1000 điểm so với điểm Sức mạnh và Thể lực";
            }
            else
            {
                maxPoint = 300;
            }
            info += "|n0Chỉ có thể nâng tối đa lên " + Utils.GetMoneys(maxPoint) + " điểm";
            if (info_point != "")
            {
                info += "|n0" + info_point;
            }

            if (player.potential > potential)
            {
                info += "|n0Cần " + upPointPotentialInfo.Replace("#", Utils.GetMoneys(potential));
            }
            else
            {
                info += "|n3Cần " + upPointPotentialInfo.Replace("#", Utils.GetMoneys(potential));
            }
            info += "|n7Điểm tiềm năng còn lại: " + Utils.GetMoneys(player.potential);
            tabInfo.ShowInfo(name, info, -1, 0, 0, GetCmdActionPotentials(index).ToArray());
        }

        private List<Command> GetCmdActionPotentials(int index)
        {
            List<Command> commands = new List<Command>();
            commands.Add(new CmdMini("Cộng", this, 17, index));
            commands.Add(new CmdMini("Cộng nhiều", this, 18, index));
            return commands;
        }

        private long GetSumStoneUpgrade()
        {
            long stone = 0;
            Item item = itemNextUpgrade.item;
            if (item != null)
            {
                int upgrade = item.GetUpgrade();
                if (upgrade > STONE_UPGRADE.Length)
                {
                    return 0;
                }
                foreach (int index in indexsUpgrade)
                {
                    Item i = Player.me.itemsBag[index];
                    if (i != null && i.template.type == 18)
                    {
                        if (i.template.id <= 57)
                        {
                            stone += STONE[i.template.id - 50] * (long)i.quantity;
                        }
                        else
                        {
                            stone += STONE[i.template.id - 150] * (long)i.quantity;
                        }
                    }
                }
            }
            return stone;
        }

        private int GetStoneIdUpgrade()
        {
            if (indexsUpgrade.Count == 0)
            {
                return 50;
            }
            if (itemsUpgrade.Count == 0 || itemsUpgrade[0].item.template.id == 161)
            {
                return 50;
            }
            if (itemsUpgrade[0].item.template.id == 57)
            {
                return 158;
            }
            return itemsUpgrade[0].item.template.id + 1;
        }

        private Item GetItemStoneUpgrade()
        {
            int id = GetStoneIdUpgrade();
            if (id == 50)
            {
                return null;
            }
            Item item = new Item();
            item.template = ItemManager.instance.itemTemplates[id];
            item.quantity = 1;
            return item;
        }

        private int GetXuStoneUpgrade()
        {
            int id = GetStoneIdUpgrade();
            if (id == 50)
            {
                return 0;
            }
            if (id <= 57)
            {
                return XU_UPGRADE_STONE[id - 51];
            }
            return XU_UPGRADE_STONE[id - 151];
        }

        private void InitCmdTeam()
        {
            cmdTeams.Clear();
            if (Player.me.team == null)
            {
                cmdTeams.Add(new CmdMini("Tìm", this, 42, null));
                cmdTeams.Add(new CmdMini("Tạo", this, 43, null));
                return;
            }
            cmdTeams.Add(new CmdMini("Rời", this, 46, null));
            if (Player.me.id == Player.me.team.members[0].player.id)
            {
                cmdTeams.Add(new CmdMini("Đổi trạng thái", this, 47, null));
            }
            cmdTeams.Add(new CmdMini("Làm mới", this, 48, null));
        }

        public void InitCmdClan()
        {
            cmdClans.Clear();
            if (Player.me.clan != null)
            {
                if (Player.me.clan.roleId > 0)
                {
                    cmdClans.Add(new CmdMini("Rời bang", this, 61, null));
                }
                cmdClans.Add(new CmdMini("Đóng góp", this, 62, null));
                if (Player.me.clan.roleId == 0)
                {
                    cmdClans.Add(new CmdMini("Khẩu hiệu", this, 63, null));
                }
                if (Player.me.clan.roleId < 2)
                {
                    cmdClans.Add(new CmdMini("Thông báo", this, 64, null));
                }
            }
        }

        public void InitCmdFlag()
        {
            cmdFlags.Clear();
            for (int i = 0; i < strFlags.Length; i++)
            {
                CmdFlag cmd = new CmdFlag(this, 74, i);
                cmd.name = strFlags[i];
                cmd.description = infoFlags[i];
                cmdFlags.Add(cmd);
            }
        }

        private void InitCmdSetting()
        {
            cmdSettings.Clear();
            cmdSettings.Add(new CmdSetting(this, 73, 0, "Âm thanh"));
            cmdSettings.Add(new CmdSetting(this, 73, 1, "Giảm đồ họa"));
            cmdSettings.Add(new CmdSetting(this, 73, 2, "Hiệu ứng vòng quay"));
            cmdSettings.Add(new CmdSetting(this, 73, 3, "Ẩn Avatar/Cải trang"));
            cmdSettings.Add(new CmdSetting(this, 73, 4, "Hào quang nhân vật"));
            cmdSettings.Add(new CmdSetting(this, 73, 5, "Huy hiệu nhân vật"));
            cmdSettings.Add(new CmdSetting(this, 73, 6, "Chặn người lạ"));
            cmdSettings.Add(new CmdSetting(this, 73, 7, "Tự đánh"));
            cmdSettings.Add(new CmdSetting(this, 73, 8, "Lưu khu vực"));
            cmdSettings.Add(new CmdSetting(this, 73, 9, "Tự nhặt"));
            cmdSettings.Add(new CmdSetting(this, 73, 10, "Tránh Tinh anh"));
            cmdSettings.Add(new CmdSetting(this, 73, 11, "Tránh Thủ lĩnh"));
            cmdSettings.Add(new CmdSetting(this, 73, 12, "Tự động đăng nhập lại"));
        }

        private void InitCmdSettingFocus()
        {
            cmdSettingFocus.Clear();
            cmdSettingFocus.Add(new CmdSettingFocus(this, 86, 0, "Chọn Vật phẩm"));
            cmdSettingFocus.Add(new CmdSettingFocus(this, 86, 1, "Chọn NPC"));
            cmdSettingFocus.Add(new CmdSettingFocus(this, 86, 2, "Chọn Quái"));
            cmdSettingFocus.Add(new CmdSettingFocus(this, 86, 3, "Chọn Người"));
            cmdSettingFocus.Add(new CmdSettingFocus(this, 86, 4, "Chọn Đối thủ"));
        }

        private void InitCmdFriend()
        {
            cmdFriends.Clear();
            cmdFriends.Add(new CmdMini("Thêm bạn", this, 84, null));
        }

        public void OpenChat(Player player)
        {
            CmdPlayerMessage chat = cmdChatPlayers.Find(x => x.player.id == player.id);
            if (chat == null)
            {
                cmdChatPlayers.Add(new CmdPlayerMessage(this, 53, player));
            }
            int index = 0;
            for (int i = 0; i < cmdChatPlayers.Count; i++)
            {
                if (cmdChatPlayers[i].player.id == player.id)
                {
                    index = i;
                    break;
                }
            }
            SetType(TabPanel.tabChatPlayer.type);
            indexSelect = index;
            SetTabChatPlayer();
            Show();
        }

        public void AddChat(Player player, string message)
        {
            CmdPlayerMessage chat = cmdChatPlayers.Find(x => x.player.id == player.id);
            if (chat == null)
            {
                chat = new CmdPlayerMessage(this, 53, player);
                cmdChatPlayers.Add(chat);
            }
            chat.isNew = true;
            chat.messages.Add(new CmdMessage(this, 52, player, message));
        }

        public bool IsNewPlayerMessage()
        {
            for (int i = 0; i < cmdChatPlayers.Count; i++)
            {
                if (cmdChatPlayers[i].isNew)
                {
                    return true;
                }
            }
            return false;
        }

        public bool IsNewMessage()
        {
            return isNewMessageClan || isNewMessageTeam || IsNewPlayerMessage();
        }

        private void SetTabBag()
        {
            items.Clear();
            if (isShowBag)
            {
                for (int i = 0; i < Player.me.itemsBag.Count; i++)
                {
                    items.Add(new ItemPanel(this, 3, i));
                }
            }
            else
            {
                for (int i = 0; i < Player.me.itemsBox.Count; i++)
                {
                    items.Add(new ItemPanel(this, 3, i));
                }
            }
            int count_item_w = 6;
            int count_item_h = 6;
            int width_item = imgBgrItem.GetWidth();
            int height_item = imgBgrItem.GetHeight();
            int dis_item = 7;
            wScroll = dis_item * (count_item_w + 1) + width_item * count_item_w;
            hScroll = dis_item * (count_item_h + 1) + height_item * count_item_h;
            xScroll = x + 35;
            yScroll = y + h - hScroll - imgBorderCoin.GetHeight() - 43;
            for (int i = 0; i < items.Count; i++)
            {
                items[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                items[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            cmdBag.x = xScroll + dis_item;
            cmdBag.y = yScroll - dis_item - cmdBag.h;
            cmdBox.x = cmdBag.x + cmdBag.w + dis_item;
            cmdBox.y = cmdBag.y;
            cmdInfoPlayer.x = x + w - 35 - cmdInfoPlayer.w - dis_item;
            cmdInfoPlayer.y = cmdBag.y;
            cmdOutFitOther.x = cmdInfoPlayer.x - dis_item - cmdInfoPlayer.w;
            cmdOutFitOther.y = cmdBag.y;
            cmdOutFit.x = cmdOutFitOther.x - dis_item - cmdOutFitOther.w;
            cmdOutFit.y = cmdBag.y;

            int max_height_item = items.Count / count_item_w + (items.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;

            count_item_w = 6;
            count_item_h = 6;
            int xBody = x + w - 35 - dis_item * count_item_w - width_item * count_item_w;
            for (int i = 0; i < itemsBody.Length; i++)
            {
                itemsBody[i] = new ItemPanel(this, 5, i);
                if (i < 4)
                {
                    itemsBody[i].x = xBody;
                    itemsBody[i].y = yScroll + dis_item + i * (width_item + dis_item);
                }
                else if (i < 8)
                {
                    itemsBody[i].x = xBody + (count_item_w - 1) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (i - 4) * (width_item + dis_item);
                }
                else if (i < 14)
                {
                    itemsBody[i].x = xBody + (i - 8) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item);
                }
                else
                {
                    itemsBody[i].x = xBody + (i - 14) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (count_item_h - 1) * (width_item + dis_item);
                }
            }
            xPlayerBag = xBody + (dis_item * count_item_w + width_item * count_item_w) / 2;
            yPlayerBag = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item) - 10;
            xInfoPlayerBag = xBody;
            yInfoPlayerBag = yScroll + dis_item;
            xScrollInfo = xInfoPlayerBag;
            yScrollInfo = yInfoPlayerBag;
            wScrollInfo = imgBgrInfoPlayer.GetWidth();
            hScrollInfo = imgBgrInfoPlayer.GetHeight();
            cmyInfoLim = 0;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            cmyInfo = cmyInfoTo = 0;
        }

        private void PaintBag(MyGraphics g)
        {
            // Paint bag or box
            cmdBag.Paint(g, isShowBag);
            cmdBox.Paint(g, !isShowBag);
            cmdInfoPlayer.Paint(g, typeShowOutFit == 2);
            cmdOutFit.Paint(g, typeShowOutFit == 0);
            cmdOutFitOther.Paint(g, typeShowOutFit == 1);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);


            //if (isShowBag)
            //{
            //    bool isPaintLight = false;
            //    foreach (TabPanel tab in tabs)
            //    {
            //        if ((tab.type == TabPanel.tabShop.type && typeShop == 1) || tab.type == TabPanel.tabUpgrade.type)
            //        {
            //            isPaintLight = true;
            //            break;
            //        }
            //    }
            //    for (int i = 0; i < items.Count; i++)
            //    {
            //        items[i].item = Player.me.itemsBag[i];
            //        items[i].Paint(g);
            //        if (indexSelect == i)
            //        {
            //            g.DrawImage(Panel.imgSelectItem, items[i].x - 5, items[i].y - 5);
            //        }
            //        if (isPaintLight && Player.me.itemsBag[i] != null && !isLightBag[i])
            //        {
            //            g.SetColor(Color.black, 0.9f);
            //            g.FillRect(items[i].x - 4, items[i].y - 4, items[i].w + 8, items[i].h + 8, 8);
            //        }
            //    }
            //}
            //else
            //{
            //    for (int i = 0; i < items.Count; i++)
            //    {
            //        items[i].item = Player.me.itemsBox[i];
            //        items[i].Paint(g);
            //    }
            //}

            if (isShowBag)
            {
                bool isPaintLight = false;
                bool isUpgradeTab = false;
                bool isShopTab = false;

                foreach (TabPanel tab in tabs)
                {
                    if (tab.type == TabPanel.tabUpgrade.type)
                    {
                        isPaintLight = true;
                        isUpgradeTab = true;
                        break;
                    }
                    if (tab.type == TabPanel.tabShop.type && typeShop == 1)
                    {
                        isPaintLight = true;
                        isShopTab = true;
                        break;
                    }
                }

                for (int i = 0; i < items.Count; i++)
                {
                    items[i].item = Player.me.itemsBag[i];
                    items[i].Paint(g);

                    if (indexSelect == i)
                    {
                        g.DrawImage(Panel.imgSelectItem, items[i].x - 5, items[i].y - 5);
                    }

                    // Logic làm TỐI các item KHÔNG hợp lệ
                    if (isPaintLight && Player.me.itemsBag[i] != null)
                    {
                        bool shouldBeDark = false;
                        Item item = Player.me.itemsBag[i];

                        if (isUpgradeTab) // TAB CƯỜNG HÓA
                        {
                            if (typeUpgrade == 0) // Cường hóa trang bị thường
                            {
                                shouldBeDark = !(item.template.type < 8 || item.template.type == 18);
                            }
                            else if (typeUpgrade == 1) // Nâng cấp đá
                            {
                                shouldBeDark = (item.template.type != 18);
                            }
                            else if (typeUpgrade == 2) // Khác
                            {
                                shouldBeDark = false;
                            }
                        }
                        else if (isShopTab) // TAB SHOP/CHỢ
                        {
                            shouldBeDark = item.isLock;
                        }
                        if (shouldBeDark)
                        {
                            g.SetColor(Color.black, 0.9f);
                            g.FillRect(items[i].x - 4, items[i].y - 4, items[i].w + 8, items[i].h + 8, 8);
                        }
                    }
                }
            }
            else // Rương đồ
            {
                for (int i = 0; i < items.Count; i++)
                {
                    items[i].item = Player.me.itemsBox[i];
                    items[i].Paint(g);
                }
            }

            g.Reset();
            g.DrawImage(imgBorderCoin, xScroll + 7, yScroll + hScroll + 7);
            g.DrawImage(imgCoin, xScroll + 14, yScroll + hScroll + 7 + (imgBorderCoin.h - imgCoin.h) / 2);
            string coin = Utils.GetMoneys(Player.me.coin);
            if (Player.me.coin >= 1_000_000_000_000)
            {
                coin = Utils.FormatNumber(Player.me.coin);
            }
            MyFont.text_mini_white.DrawString(g, coin, xScroll + imgBorderCoin.GetWidth(), yScroll + hScroll + 7 + (imgBorderCoin.h - MyFont.text_mini_white.GetHeight()) / 2, 1);
            int disCoin = imgBorderCoin.GetWidth() + 37;
            g.DrawImage(imgBorderCoin, xScroll + 7 + disCoin, yScroll + hScroll + 7);
            g.DrawImage(imgCoinLock, xScroll + 14 + disCoin, yScroll + hScroll + 7 + (imgBorderCoin.h - imgCoinLock.h) / 2);
            coin = Utils.GetMoneys(Player.me.coinLock);
            if (Player.me.coinLock >= 1_000_000_000_000)
            {
                coin = Utils.FormatNumber(Player.me.coinLock);
            }
            MyFont.text_mini_white.DrawString(g, coin, xScroll + imgBorderCoin.GetWidth() + disCoin, yScroll + hScroll + 7 + (imgBorderCoin.h - MyFont.text_mini_white.GetHeight()) / 2, 1);
            g.DrawImage(imgBorderDiamond, xScrollInfo + wScrollInfo, yScroll + hScroll + 7, StaticObj.TOP_RIGHT);
            g.DrawImage(imgDiamond, xScrollInfo + wScrollInfo - imgBorderDiamond.GetWidth() + 7, yScroll + hScroll + 7 + (imgBorderDiamond.h - imgDiamond.h) / 2);
            MyFont.text_mini_white.DrawString(g, Utils.GetMoneys(Player.me.diamond), xScrollInfo + wScrollInfo - 7, yScroll + hScroll + 7 + (imgBorderDiamond.h - MyFont.text_mini_white.GetHeight()) / 2, 1);
            disCoin = imgBorderDiamond.GetWidth() + 37;
            g.DrawImage(imgBorderDiamond, xScrollInfo + wScrollInfo - disCoin, yScroll + hScroll + 7, StaticObj.TOP_RIGHT);
            g.DrawImage(imgRuby, xScrollInfo + wScrollInfo - imgBorderDiamond.GetWidth() + 7 - disCoin, yScroll + hScroll + 7 + (imgBorderDiamond.h - imgDiamond.h) / 2);
            MyFont.text_mini_white.DrawString(g, Utils.GetMoneys(Player.me.ruby), xScrollInfo + wScrollInfo - 7 - disCoin, yScroll + hScroll + 7 + (imgBorderDiamond.h - MyFont.text_mini_white.GetHeight()) / 2, 1);
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
            // Paint body
            g.Reset();
            if (typeShowOutFit == 0 || typeShowOutFit == 1)
            {
                List<Item> items = typeShowOutFit == 0 ? Player.me.itemsBody : Player.me.itemsOther;
                for (int i = 0; i < itemsBody.Length; i++)
                {
                    switch (i)
                    {
                        case 0:
                            {
                                itemsBody[i].item = items[6];
                                itemsBody[i].Paint(g, "Áo");
                                break;
                            }
                        case 1:
                            {
                                itemsBody[i].item = items[0];
                                itemsBody[i].Paint(g, "Găng tay");
                                break;
                            }
                        case 2:
                            {
                                itemsBody[i].item = items[4];
                                itemsBody[i].Paint(g, "Quần");
                                break;
                            }
                        case 3:
                            {
                                itemsBody[i].item = items[2];
                                itemsBody[i].Paint(g, "Giày");
                                break;
                            }
                        case 4:
                            {
                                itemsBody[i].item = items[7];
                                itemsBody[i].Paint(g, "Radar");
                                break;
                            }
                        case 5:
                            {
                                itemsBody[i].item = items[5];
                                itemsBody[i].Paint(g, "Dây chuyền");
                                break;
                            }
                        case 6:
                            {
                                itemsBody[i].item = items[3];
                                itemsBody[i].Paint(g, "Nhẫn");
                                break;
                            }
                        case 7:
                            {
                                itemsBody[i].item = items[1];
                                itemsBody[i].Paint(g, "Ngọc bội");
                                break;
                            }
                        case 8:
                            {
                                itemsBody[i].item = items[8];
                                itemsBody[i].Paint(g, "Cải trang");
                                break;
                            }
                        case 9:
                            {
                                itemsBody[i].item = items[10];
                                itemsBody[i].Paint(g, "Thú cưỡi");
                                break;
                            }
                        case 10:
                            {
                                itemsBody[i].item = items[9];
                                itemsBody[i].Paint(g, "Bông tai");
                                break;
                            }
                        case 11:
                            {
                                itemsBody[i].item = items[11];
                                itemsBody[i].Paint(g, "Bang hội");
                                break;
                            }
                        case 12:
                            {
                                itemsBody[i].item = items[12];
                                itemsBody[i].Paint(g, "Vòng kim hãm");
                                break;
                            }
                        case 13:
                            {
                                itemsBody[i].item = items[13];
                                itemsBody[i].Paint(g, "Chưa mở 1");
                                break;
                            }
                        case 14:
                            {
                                itemsBody[i].item = items[14];
                                itemsBody[i].Paint(g, "Chưa mở 2");
                                break;
                            }
                        case 15:
                            {
                                itemsBody[i].item = items[15];
                                itemsBody[i].Paint(g, "Chưa mở 3");
                                break;
                            }
                        case 16:
                            {
                                itemsBody[i].item = items[16];
                                itemsBody[i].Paint(g, "Chưa mở 4");
                                break;
                            }
                        case 17:
                            {
                                itemsBody[i].item = items[17];
                                itemsBody[i].Paint(g, "Chưa mở 5");
                                break;
                            }
                        case 18:
                            {
                                itemsBody[i].item = items[18];
                                itemsBody[i].Paint(g, "Chưa mở 6");
                                break;
                            }
                        case 19:
                            {
                                itemsBody[i].item = items[19];
                                itemsBody[i].Paint(g, "Chưa mở 7");
                                break;
                            }
                    }

                }
                g.DrawImage(imgWallBag, xPlayerBag, yPlayerBag, StaticObj.BOTTOM_HCENTER);
                int frame = (Player.me.frameTick % 15 >= 5) ? 1 : 0;
                if (Player.me.head != null)
                {
                    int icon = Player.me.head.template.stand[frame];
                    if (icon != -1)
                    {
                        GraphicManager.instance.Draw(g, icon, xPlayerBag + Player.me.head.template.dx, yPlayerBag - 25 - Player.me.head.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                    }
                }
                if (Player.me.body != null)
                {
                    int icon = Player.me.body.template.stand[frame];
                    if (icon != -1)
                    {
                        GraphicManager.instance.Draw(g, icon, xPlayerBag + Player.me.body.template.dx, yPlayerBag - 25 - Player.me.body.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                    }
                }
            }
            else
            {
                g.DrawImage(imgBgrInfoPlayer, xInfoPlayerBag, yInfoPlayerBag);
                g.SetClip(xScrollInfo, yScrollInfo + 5, wScrollInfo, hScrollInfo - 40);//hoang
                g.Translate(0, -cmyInfo);
                int dis = MyFont.text_mini_white.GetHeight() + 2;
                int num = -dis + 10;
                MyFont.text_mini_white.DrawString(g, "Nhân vật: " + Player.me.name, xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                MyFont.text_mini_white.DrawString(g, "Bang hội: " + (Player.me.clan != null ? Player.me.clan.name : "Chưa có"), xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                if (Player.me.gender == 0)
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Earth", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                else if (Player.me.gender == 1)
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Namek", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                else
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Saiyan", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                MyFont.text_mini_white.DrawString(g, GameCanvas.levels[Player.me.level].name.Replace("#", Player.me.gender == 0 ? "Nhân" : (Player.me.gender == 1 ? "Namek" : "Sayain")) + ": " + Utils.FormatNumber(Player.me.power) + " sức mạnh", 
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                
                MyFont.text_mini_white.DrawString(g, "Cấp: " + Player.me.level + " + " + Player.me.GetStrPercentLevel() + "%",
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "HP: " + Utils.GetMoneys(Player.me.hp) + "/" + Utils.GetMoneys(Player.me.maxHp),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "KI: " + Utils.GetMoneys(Player.me.mp) + "/" + Utils.GetMoneys(Player.me.maxMp),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Sức đánh: " + Utils.GetMoneys(Player.me.maxDamage),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Chí mạng: " + Player.me.critical,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Giáp: " + Player.me.reduceDamage,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Phản sát thương: " + Player.me.strikeBack,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Né đòn: " + Player.me.dodge,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Hút HP: " + Player.me.bloodsucking,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Hút KI: " + Player.me.manaSucking,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                /* ===== CÁC CHỈ SỐ MỚI (ĐÚNG VỊ TRÍ SAU HÚT KI) ===== */

                MyFont.text_mini_white.DrawString(g, "Sức đánh chí mạng: " + Player.me.critDamage,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Giảm sát thương chí mạng: " + Player.me.ignoreCrit,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Chính xác: " + Player.me.accurate,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Xuyên giáp: " + Player.me.pierceArmor,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                /* ================================================ */

                MyFont.text_mini_white.DrawString(g, "Số lần đi bản doanh: " + Utils.GetMoneys(Player.me.countBarrack),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Điểm năng động: " + Utils.GetMoneys(Player.me.pointActivity),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Hiếu chiến: " + Utils.GetMoneys(Player.me.pointPk),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                cmyInfoLim = num - hScrollInfo + dis;
                if (cmyInfoLim < 0)
                {
                    cmyInfoLim = 0;
                }
                g.Reset();
                if (cmyInfoLim > 0)
                {
                    PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 20, 1, hScrollInfo - 40, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
                }
            }
        }

        private void SetTabSkill()
        {
            skills.Clear();
            for (int i = 0; i < Player.me.skills.Count; i++)
            {
                CmdSkill cmd = new CmdSkill();
                cmd.actionListener = this;
                cmd.actionId = 11;
                cmd._object = i;
                cmd.skill = Player.me.skills[i];
                skills.Add(cmd);
            }
            int wSkill = CmdSkill.imgBgr.GetWidth();
            xScroll = x + 40;
            yScroll = y + 80;
            wScroll = w - 80;
            hScroll = wSkill + 40;
            int dis = 10;
            int max_w_skill = wSkill * skills.Count + dis * (skills.Count - 1) + 40;
            int xSkill = xScroll + 20;
            if (max_w_skill < wScroll)
            {
                xSkill = xScroll + (wScroll - max_w_skill) / 2;
            }
            int ySkill = yScroll + hScroll / 2 + 10;
            for (int i = 0; i < skills.Count; i++)
            {
                skills[i].x = xSkill + skills[i].w / 2 + (wSkill + dis) * i;
                skills[i].y = ySkill;
            }
            cmxLim = max_w_skill - wScroll;
            if (cmxLim < 0)
            {
                cmxLim = 0;
            }
            cmx = cmxTo = 0;
            int yPotential = yScroll + hScroll + 100;
            cmdPotentials[0].x = xScroll;
            cmdPotentials[0].y = yPotential;
            cmdPotentials[1].x = xScroll + wScroll - cmdPotentials[1].w;
            cmdPotentials[1].y = cmdPotentials[0].y;
            cmdPotentials[2].x = xScroll;
            cmdPotentials[2].y = yPotential + cmdPotentials[0].h + 10;
            cmdPotentials[3].x = xScroll + wScroll - cmdPotentials[1].w;
            cmdPotentials[3].y = cmdPotentials[2].y;
        }

        private void PaintSkill(MyGraphics g)
        {
            // Paint skill
            MyFont.text_blue.DrawString(g, "Điểm kĩ năng: " + Utils.GetMoneys(Player.me.pointSkill), xScroll + 20, yScroll - 40, 0);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(-cmx, 0);
            for (int i = 0; i < skills.Count; i++)
            {
                skills[i].Paint(g);
            }
            g.Reset();
            g.DrawImage(imgBgrSkill, xScroll, yScroll);
            MyFont.text_blue.DrawString(g, "Điểm tiềm năng: " + Utils.GetMoneys(Player.me.potential), xScroll + 20, cmdPotentials[0].y - 40, 0);
            for (int i = 0; i < cmdPotentials.Length; i++)
            {
                if (i == 0)
                {
                    cmdPotentials[i].caption = "Sức mạnh: " + Utils.GetMoneys(Player.me.baseDamage);
                    cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.me.potentialUpDamage));
                }
                else if (i == 1)
                {
                    cmdPotentials[i].caption = "Thể lực: " + Utils.GetMoneys(Player.me.baseHp);
                    cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.me.potentialUpHp));
                }
                else if (i == 2)
                {
                    cmdPotentials[i].caption = "Trí lực: " + Utils.GetMoneys(Player.me.baseMp);
                    cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.me.potentialUpMp));
                }
                else if (i == 3)
                {
                    cmdPotentials[i].caption = "Thân pháp: " + Utils.GetMoneys(Player.me.baseConstitution);
                    cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.me.potentialUpConstitution));
                }
                cmdPotentials[i].Paint(g);
            }
        }

        private void SetTabUpgrade()
        {
            // khu vuc huong dan
            wScrollInfo = imgBgrInfoUpgrade.GetWidth();
            hScrollInfo = imgBgrInfoUpgrade.GetHeight();
            xScrollInfo = x + 35;
            yScrollInfo = y + h - hScrollInfo - 35;
            cmyInfoLim = 0;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            cmyInfo = cmyInfoTo = 0;
            // khu vuc nang cap
            cmdUpgrade.y = yScrollInfo + hScrollInfo - cmdUpgrade.h;
            itemsUpgrade.Clear();
            if (typeUpgrade == 0)
            {
                itemUpgrade.x = x + w / 2 + (w / 2 - itemUpgrade.w * 3) / 2 - itemUpgrade.w / 2;
                itemUpgrade.y = y + 50;
                itemNextUpgrade.x = itemUpgrade.x + itemUpgrade.w * 2;
                itemNextUpgrade.y = itemUpgrade.y;
                for (int i = 0; i < 24; i++)
                {
                    itemsUpgrade.Add(new ItemPanel(this, 23, i));
                }
                int count_item_w = 6;
                int count_item_h = 4;
                int width_item = imgBgrItem.GetWidth();
                int height_item = imgBgrItem.GetHeight();
                int dis_item = 7;
                wScroll = dis_item * (count_item_w + 1) + width_item * count_item_w;
                hScroll = dis_item * (count_item_h + 1) + height_item * count_item_h;
                xScroll = x + w - wScroll - 35;
                yScroll = cmdUpgrade.y - 20 - hScroll;
                for (int i = 0; i < itemsUpgrade.Count; i++)
                {
                    itemsUpgrade[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                    itemsUpgrade[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                }
            }
            else if (typeUpgrade == 1)
            {
                for (int i = 0; i < 24; i++)
                {
                    itemsUpgrade.Add(new ItemPanel(this, 23, i));
                }
                int count_item_w = 6;
                int count_item_h = 4;
                int width_item = imgBgrItem.GetWidth();
                int height_item = imgBgrItem.GetHeight();
                int dis_item = 7;
                wScroll = dis_item * (count_item_w + 1) + width_item * count_item_w;
                hScroll = dis_item * (count_item_h + 1) + height_item * count_item_h;
                xScroll = x + w - wScroll - 35;
                yScroll = cmdUpgrade.y - 20 - hScroll;
                for (int i = 0; i < itemsUpgrade.Count; i++)
                {
                    itemsUpgrade[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                    itemsUpgrade[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                }
                itemUpgrade.x = xScroll + (wScroll - itemUpgrade.w) / 2;
                itemUpgrade.y = y + 50;
            }
            else if (typeUpgrade == 2)
            {
                for (int i = 0; i < 36; i++)
                {
                    itemsUpgrade.Add(new ItemPanel(this, 23, i));
                }
                int count_item_w = 6;
                int count_item_h = 6;
                int width_item = imgBgrItem.GetWidth();
                int height_item = imgBgrItem.GetHeight();
                int dis_item = 7;
                wScroll = dis_item * (count_item_w + 1) + width_item * count_item_w;
                hScroll = dis_item * (count_item_h + 1) + height_item * count_item_h;
                xScroll = x + w - wScroll - 35;
                yScroll = cmdUpgrade.y - 20 - hScroll;
                for (int i = 0; i < itemsUpgrade.Count; i++)
                {
                    itemsUpgrade[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                    itemsUpgrade[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                }
                itemUpgrade.x = xScroll + (wScroll - itemUpgrade.w) / 2;
                itemUpgrade.y = y + 50;
            }
            cmdUpgrade.x = xScroll + (wScroll - cmdUpgrade.w) / 2;
            cmyLim = 0;
            cmy = cmyTo = 0;
        }

        private void PaintUpgrade(MyGraphics g)
        {
            MyFont.text_white.DrawString(g, "Hướng dẫn", xScrollInfo + wScrollInfo / 2, yScrollInfo - 35, 2);
            g.DrawImage(imgBgrInfoUpgrade, xScrollInfo, yScrollInfo);
            g.SetClip(xScrollInfo, yScrollInfo + 5, wScrollInfo, hScrollInfo - 10);
            g.Translate(0, -cmyInfo);
            int dis = MyFont.text_mini_white.GetHeight() + 2;
            int num = -dis + 10;
            foreach (string info in infosUpgrade)
            {
                string[] vs = MyFont.text_mini_white.SplitFontArray(info, wScrollInfo - 30);
                foreach (string text in vs)
                {
                    MyFont.text_mini_white.DrawString(g, text, xScrollInfo + 15, yScrollInfo + 5 + (num += dis), 0);
                }
            }
            cmyInfoLim = num - hScrollInfo;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            g.Reset();
            if (cmyInfoLim > 0)
            {
                PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 20, 1, hScrollInfo - 40, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
            }
            g.Reset();
            cmdUpgrade.Paint(g);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            for (int i = 0; i < itemsUpgrade.Count; i++)
            {
                if (i < indexsUpgrade.Count)
                {
                    Item item = Player.me.itemsBag[indexsUpgrade[i]];
                    if (item == null)
                    {
                        indexsUpgrade.RemoveAt(i);
                    }
                    itemsUpgrade[i].item = item;
                }
                else
                {
                    itemsUpgrade[i].item = null;
                }
                itemsUpgrade[i].Paint(g);
            }
            g.Reset();
            if (typeUpgrade == 0)
            {
                if (indexUpgrade < 0 || indexUpgrade >= Player.me.itemsBag.Count)
                {
                    itemUpgrade.item = null;
                    itemNextUpgrade.item = null;
                }
                else
                {
                    Item item = Player.me.itemsBag[indexUpgrade];
                    if (item == null
                        || (item.template.type >= 8 && typeUpgrade == 0)
                        || (item.template.type != 18 && typeUpgrade == 1))
                    {
                        itemUpgrade.item = null;
                        itemNextUpgrade.item = null;
                    }
                    else
                    {
                        itemUpgrade.item = item;
                        itemNextUpgrade.item = itemUpgrade.item.NextUpgrade();
                    }
                }
                itemUpgrade.Paint(g);
                itemNextUpgrade.Paint(g);
                g.DrawImage(imgHintWay, itemUpgrade.x + itemUpgrade.w + itemUpgrade.w / 2 + (GameCanvas.gameTick % 30 < 15 ? -3 : 3), itemUpgrade.y + itemUpgrade.h / 2, StaticObj.VCENTER_HCENTER);
                long max_stone = 0;
                if (itemUpgrade.item != null)
                {
                    int upgrade = itemUpgrade.item.GetUpgrade();
                    if (upgrade < STONE_UPGRADE.Length)
                    {
                        max_stone = STONE_UPGRADE[upgrade];
                    }
                }
                long sum_stone = GetSumStoneUpgrade();
                if (sum_stone > max_stone)
                {
                    sum_stone = max_stone;
                }
                int maxWidthPercent = wScroll - 20;
                int xPercent = xScroll + 10;
                int yPercent = itemUpgrade.y + itemUpgrade.h + 30;
                g.SetColor(Color.black, 0.8f);
                g.FillRect(xPercent, yPercent, maxWidthPercent, 40, 8);
                int widthPercent = 0;
                if (max_stone > 0)
                {
                    widthPercent = (int)(sum_stone * maxWidthPercent / max_stone);
                }
                g.SetColor(65280, 0.8f);
                g.FillRect(xPercent, yPercent, widthPercent, 40, 8);
                MyFont.text_white.DrawString(g, Utils.GetMoneys(sum_stone) + "/" + Utils.GetMoneys(max_stone), xPercent + maxWidthPercent / 2, yPercent + 7, 2);
            }
            else if (typeUpgrade == 1)
            {
                itemUpgrade.item = GetItemStoneUpgrade();
                itemUpgrade.Paint(g);

                MyFont.text_white.DrawString(g, "Tiêu hao: " + Utils.GetMoneys(GetXuStoneUpgrade()) + " xu", xScroll + 20, yScroll - 55, 0);
            }
        }

        private void SetTabShop()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            if (typeShop > 1)
            {
                hScroll -= 30;
            }
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            List<Item> items = itemsShop.ElementAt(tabIndex).Value;
            for (int i = 0; i < items.Count; i++)
            {
                items[i].w = width_item;
                items[i].h = height_item;
                items[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                items[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                items[i].actionId = 26;
                items[i].actionListener = this;
                items[i]._object = i;
                items[i].isShow = true;
                items[i].anchor = StaticObj.TOP_LEFT;
            }
            int max_height_item = items.Count / count_item_w + (items.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintShop(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            List<Item> items = itemsShop.ElementAt(tabIndex).Value;
            if (items.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Gian hàng trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < items.Count; i++)
            {
                items[i].PaintShop(g);
            }
            g.Reset();
            if (typeShop > 1)
            {
                MyFont.text_white.DrawString(g, "Điểm còn lại: " + Utils.GetMoneys(pointShop), xScroll + 20, yScroll + hScroll + 10, 0);
            }
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabTask()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            items.Clear();
            if (Player.me.task != null)
            {
                for (int i = 0; i < Player.me.task.items.Count; i++)
                {
                    items.Add(new ItemPanel(this, 94, Player.me.task.items[i]));
                }
                while (items.Count < 5)
                {
                    items.Add(new ItemPanel(this, 94, null));
                }
                if (cmyLim < 0)
                {
                    cmyLim = 0;
                }
            }
            else
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintTask(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            int range = MyFont.text_white.GetHeight();
            int num = -range + 10;
            if (Player.me.task == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại không có nhiệm vụ nào", xScroll + 20, yScroll + (num += range), 0);
                g.Reset();
                return;
            }
            MyFont.text_white.DrawString(g, Player.me.task.name, xScroll + wScroll / 2, yScroll + (num += range), 2);
            num += 5;
            for (int i = 0; i < Player.me.task.subTasks.Count; i++)
            {
                /*if (i > Player.me.task.index)
                {
                    break;
                }*/
                string text = "";
                if (Player.me.task.subTasks[i].param > 1)
                {
                    text = " [" + (i < Player.me.task.index ? Player.me.task.subTasks[i].param : Player.me.task.param) + "/" + Player.me.task.subTasks[i].param + "]";
                }
                if (i < Player.me.task.index)
                {
                    MyFont.text_white.DrawString(g, "- " + Player.me.task.subTasks[i].name + text, xScroll + 20, yScroll + (num += range), 0);
                }
                else if (i == Player.me.task.index)
                {
                    MyFont.text_white.DrawString(g, "- " + Player.me.task.subTasks[i].name + text, xScroll + 20 + (GameCanvas.gameTick % 30 > 15 ? 0 : 3), yScroll + (num += range), 0);
                }
                else
                {
                    MyFont.text_white.DrawString(g, "- ...", xScroll + 20, yScroll + (num += range), 0);
                }
            }
            if (Player.me.task.subTasks[Player.me.task.index].description != "")
            {
                num += 5;
                List<string> list = MyFont.text_white.splitFontVector("Ghi chú: " + Player.me.task.subTasks[Player.me.task.index].description, wScroll - 40);
                for (int i = 0; i < list.Count; i++)
                {
                    MyFont.text_white.DrawString(g, list[i], xScroll + 20, yScroll + (num += range), 0);
                }
            }
            if (Player.me.task.items.Count > 0)
            {
                num += 5;
                MyFont.text_white.DrawString(g, "Phần thưởng nhiệm vụ:", xScroll + 20, yScroll + (num += range), 0);
                int yItem = yScroll + num + range + 5;
                for (int i = 0; i < items.Count; i++)
                {
                    if (i < Player.me.task.items.Count)
                    {
                        items[i].item = Player.me.task.items[i];
                    }
                    items[i].y = yItem;
                    items[i].x = xScroll + 20 + (items[i].w + 6) * i;
                    items[i].Paint(g);
                }
            }
            cmyLim = num - hScroll + range + 8;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabTaskOrther()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            cmyLim = 0;
            cmy = cmyTo = 0;
        }

        private void PaintTaskOther(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            int range = MyFont.text_white.GetHeight();
            int num = -range + 10;
            if (Player.me.taskDaily == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại chưa có nhiệm vụ nào", xScroll + 20, yScroll + (num += range), 0);
                g.Reset();
                return;
            }
            MyFont.text_white.DrawString(g, "Nhiệm vụ hàng ngày", xScroll + wScroll / 2, yScroll + (num += range), 2);
            num += 5;
            MyFont.text_white.DrawString(g, "- " + Player.me.taskDaily.ToString(), xScroll + 20 + (GameCanvas.gameTick % 30 > 15 ? 0 : 3), yScroll + (num += range), 0);
            num += 5;
            List<string> list = MyFont.text_white.splitFontVector("Ghi chú: " + Player.me.taskDaily.description, wScroll - 40);
            for (int i = 0; i < list.Count; i++)
            {
                MyFont.text_white.DrawString(g, list[i], xScroll + 20, yScroll + (num += range), 0);
            }

            cmyLim = num - hScroll + range + 8;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabArea()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 115;
            hScroll = h - 140;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < areas.Count; i++)
            {
                areas[i].w = width_item;
                areas[i].h = height_item;
                areas[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                areas[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = areas.Count / count_item_w + (areas.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintArea(MyGraphics g)
        {
            g.DrawImage(imgArea, x + 30, y + 20);
            MyFont.text_big_white.DrawString(g, Map.areaId.ToString(), xScroll + 38, y + 36, 2);
            MyFont.text_white.DrawString(g, "Khu vực: " + Map.areaId, xScroll + 100, y + 40, 0);
            MyFont.text_white.DrawString(g, Map.name, xScroll + 100, y + 70, 0);
            MyFont.text_white.DrawString(g, "Dân số: " + areas[Map.areaId].numPlayer + "/" + areas[Map.areaId].maxPlayer, xScroll + wScroll, y + 40, 1);
            MyFont.text_white.DrawString(g, "Tổ đội: " + areas[Map.areaId].numTeam, xScroll + wScroll, y + 70, 1);

            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (areas.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có khu vực nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < areas.Count; i++)
            {
                areas[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabFriend()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            InitCmdFriend();
            if (cmdFriends.Count > 0)
            {
                hScroll -= cmdFriends[0].h + 10;
            }
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < friends.Count; i++)
            {
                friends[i].w = width_item;
                friends[i].h = height_item;
                friends[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                friends[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = friends.Count / count_item_w + (friends.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintFriend(MyGraphics g)
        {
            if (cmdFriends.Count > 0)
            {
                for (int i = 0; i < cmdFriends.Count; i++)
                {
                    cmdFriends[i].x = xScroll + wScroll - cmdFriends[i].w - (cmdFriends[i].w + 10) * i;
                    cmdFriends[i].y = yScroll + hScroll + 10;
                    cmdFriends[i].Paint(g);
                }
            }
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (friends.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có bạn bè nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < friends.Count; i++)
            {
                friends[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabEnemy()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < enemys.Count; i++)
            {
                enemys[i].w = width_item;
                enemys[i].h = height_item;
                enemys[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                enemys[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = enemys.Count / count_item_w + (enemys.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintEnemy(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (enemys.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có kẻ thù nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < enemys.Count; i++)
            {
                enemys[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabTop()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < tops.Count; i++)
            {
                tops[i].w = width_item;
                tops[i].h = height_item;
                tops[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                tops[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = tops.Count / count_item_w + (tops.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintTop(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (tops.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < tops.Count; i++)
            {
                tops[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabMapSpaceship()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < mapsSpaceship.Count; i++)
            {
                mapsSpaceship[i].w = width_item;
                mapsSpaceship[i].h = height_item;
                mapsSpaceship[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                mapsSpaceship[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = mapsSpaceship.Count / count_item_w + (mapsSpaceship.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintMapSpaceship(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (mapsSpaceship.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < mapsSpaceship.Count; i++)
            {
                mapsSpaceship[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabAchivement()
        {
            int count_item_w = 1;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgBgrAchivement.GetWidth();
            int height_item = imgBgrAchivement.GetHeight();
            for (int i = 0; i < achievements.Count; i++)
            {
                achievements[i].w = width_item;
                achievements[i].h = height_item;
                achievements[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                achievements[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = achievements.Count / count_item_w + (achievements.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintAchivement(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (achievements.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < achievements.Count; i++)
            {
                achievements[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabNotification()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            cmyLim = 0;
            cmy = cmyTo = 0;
        }

        private void PaintNotification(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            int range = MyFont.text_white.GetHeight();
            int num = -range + 10;
            for (int i = 0; i < notifications.Count; i++)
            {
                string[] vs = MyFont.text_white.SplitFontArray(notifications[i], wScroll - 20);
                for (int j = 0; j < vs.Length; j++)
                {
                    MyFont.text_white.DrawString(g, vs[j], xScroll + 10, yScroll + (num += range), 0);
                }
            }
            cmyLim = num - hScroll + range + 8;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabTeam()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            cmyLim = 0;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
            InitCmdTeam();
            if (cmdTeams.Count > 0)
            {
                hScroll -= cmdTeams[0].h + 10;
            }
        }

        private void PaintTeam(MyGraphics g)
        {
            if (cmdTeams.Count > 0)
            {
                for (int i = 0; i < cmdTeams.Count; i++)
                {
                    cmdTeams[i].x = xScroll + wScroll - cmdTeams[i].w - (cmdTeams[i].w + 10) * i;
                    cmdTeams[i].y = yScroll + hScroll + 10;
                    cmdTeams[i].Paint(g);
                }
            }
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (Player.me.team == null)
            {
                if (teams.Count == 0)
                {
                    MyFont.text_white.DrawString(g, "Hiện tại bạn chưa có tổ đội", xScroll + 20, yScroll + 20, 0);
                }
                else
                {
                    int count_item_w = 2;
                    int dis_item = 7;
                    int width_item = imgItemShop.GetWidth();
                    int height_item = imgItemShop.GetHeight();
                    for (int i = 0; i < teams.Count; i++)
                    {
                        teams[i].w = width_item;
                        teams[i].h = height_item;
                        teams[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                        teams[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                        teams[i].Paint(g);
                    }
                    int max_height_item = teams.Count / count_item_w + (teams.Count % count_item_w == 0 ? 0 : 1);
                    cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
                    if (cmyLim < 0)
                    {
                        cmyLim = 0;
                    }
                }
            }
            else
            {
                int count_item_w = 2;
                int dis_item = 7;
                int width_item = imgItemShop.GetWidth();
                int height_item = imgItemShop.GetHeight();
                for (int i = 0; i < Player.me.team.members.Count; i++)
                {
                    Player.me.team.members[i]._object = i;
                    Player.me.team.members[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                    Player.me.team.members[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                    Player.me.team.members[i].Paint(g);
                }
                int max_height_item = teams.Count / count_item_w + (teams.Count % count_item_w == 0 ? 0 : 1);
                cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
                if (cmyLim < 0)
                {
                    cmyLim = 0;
                }
                g.Reset();
                MyFont.text_white.DrawString(g, "Trạng thái: " + Player.me.team.GetStrStatus(), xScroll + 20, yScroll + hScroll - 2 - MyFont.text_white.GetHeight(), 0);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabChatGlobal()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70 - cmdChat.h - 10;
            cmdChat.x = xScroll + wScroll - cmdChat.w;
            cmdChat.y = yScroll + hScroll + 10;
            textChat.x = xScroll;
            textChat.y = cmdChat.y;
            textChat.name = PlayerText.input_chat_global;
            textChat.isFocus = true;
            int num = 7;
            while (cmdChatGlobals.Count > 50)
            {
                cmdChatGlobals.RemoveAt(0);
            }
            for (int i = 0; i < cmdChatGlobals.Count; i++)
            {
                num += cmdChatGlobals[i].h + 20;
            }
            int last_cmy = cmyLim;
            cmyLim = num - 20 + 7 - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            if (cmyTo == last_cmy)
            {
                cmyTo = cmyLim;
                cmy = 0;
            }
        }

        private void PaintChatGlobal(MyGraphics g)
        {
            textChat.Paint(g);
            cmdChat.Paint(g);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdChatGlobals.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có tin nhắn nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            int num = 7;
            if (cmyLim > 0)
            {
                for (int i = 0; i < cmdChatGlobals.Count; i++)
                {
                    if (Player.me.id == cmdChatGlobals[i].player.id && !cmdChatGlobals[i].isServer)
                    {
                        cmdChatGlobals[i].x = xScroll + wScroll - 7 - cmdChatGlobals[i].w;
                    }
                    else
                    {
                        cmdChatGlobals[i].x = xScroll + 7;
                    }
                    cmdChatGlobals[i].y = yScroll + num;
                    num += cmdChatGlobals[i].h + 20;
                    cmdChatGlobals[i].Paint(g);
                }
            }
            else
            {
                for (int i = cmdChatGlobals.Count - 1; i >= 0; i--)
                {
                    num += cmdChatGlobals[i].h;
                    if (Player.me.id == cmdChatGlobals[i].player.id && !cmdChatGlobals[i].isServer)
                    {
                        cmdChatGlobals[i].x = xScroll + wScroll - 7 - cmdChatGlobals[i].w;
                    }
                    else
                    {
                        cmdChatGlobals[i].x = xScroll + 7;
                    }
                    cmdChatGlobals[i].y = yScroll + hScroll - 7 - num;
                    num += 20;
                    cmdChatGlobals[i].Paint(g);
                }
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabChatTeam()
        {
            isNewMessageTeam = false;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70 - cmdChat.h - 10;
            cmdChat.x = xScroll + wScroll - cmdChat.w;
            cmdChat.y = yScroll + hScroll + 10;
            textChat.x = xScroll;
            textChat.y = cmdChat.y;
            textChat.name = PlayerText.input_chat_team;
            textChat.isFocus = true;
            int num = 7;
            if (cmdChatTeams.Count > 100)
            {
                cmdChatTeams.RemoveAt(0);
            }
            for (int i = 0; i < cmdChatTeams.Count; i++)
            {
                num += cmdChatTeams[i].h + 20;
            }
            int last_cmy = cmyLim;
            cmyLim = num - 20 + 7 - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            if (cmyTo == last_cmy)
            {
                cmyTo = cmyLim;
                cmy = 0;
            }
        }

        private void PaintChatTeam(MyGraphics g)
        {
            textChat.Paint(g);
            cmdChat.Paint(g);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (Player.me.team == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại bạn chưa có tổ đội", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            if (cmdChatTeams.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có tin nhắn nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            int num = 7;
            if (cmyLim > 0)
            {
                for (int i = 0; i < cmdChatTeams.Count; i++)
                {
                    if (Player.me.id == cmdChatTeams[i].player.id && !cmdChatTeams[i].isServer)
                    {
                        cmdChatTeams[i].x = xScroll + wScroll - 7 - cmdChatTeams[i].w;
                    }
                    else
                    {
                        cmdChatTeams[i].x = xScroll + 7;
                    }
                    cmdChatTeams[i].y = yScroll + num;
                    num += cmdChatTeams[i].h + 20;
                    cmdChatTeams[i].Paint(g);
                }
            }
            else
            {
                for (int i = cmdChatTeams.Count - 1; i >= 0; i--)
                {
                    num += cmdChatTeams[i].h;
                    if (Player.me.id == cmdChatTeams[i].player.id && !cmdChatTeams[i].isServer)
                    {
                        cmdChatTeams[i].x = xScroll + wScroll - 7 - cmdChatTeams[i].w;
                    }
                    else
                    {
                        cmdChatTeams[i].x = xScroll + 7;
                    }
                    cmdChatTeams[i].y = yScroll + hScroll - 7 - num;
                    num += 20;
                    cmdChatTeams[i].Paint(g);
                }
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabChatClan()
        {
            isNewMessageClan = false;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70 - cmdChat.h - 10;
            cmdChat.x = xScroll + wScroll - cmdChat.w;
            cmdChat.y = yScroll + hScroll + 10;
            textChat.x = xScroll;
            textChat.y = cmdChat.y;
            textChat.name = PlayerText.input_chat_clan;
            textChat.isFocus = true;
            int num = 7;
            if (cmdChatClans.Count > 100)
            {
                cmdChatClans.RemoveAt(0);
            }
            for (int i = 0; i < cmdChatClans.Count; i++)
            {
                num += cmdChatClans[i].h + 20;
            }
            int last_cmy = cmyLim;
            cmyLim = num - 20 + 7 - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            if (cmyTo == last_cmy)
            {
                cmyTo = cmyLim;
                cmy = 0;
            }
        }

        private void PaintChatClan(MyGraphics g)
        {
            textChat.Paint(g);
            cmdChat.Paint(g);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (Player.me.clan == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại bạn chưa có bang hội", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            if (cmdChatClans.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có tin nhắn nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            int num = 7;
            if (cmyLim > 0)
            {
                for (int i = 0; i < cmdChatClans.Count; i++)
                {
                    if (Player.me.id == cmdChatClans[i].player.id && !cmdChatClans[i].isServer)
                    {
                        cmdChatClans[i].x = xScroll + wScroll - 7 - cmdChatClans[i].w;
                    }
                    else
                    {
                        cmdChatClans[i].x = xScroll + 7;
                    }
                    cmdChatClans[i].y = yScroll + num;
                    num += cmdChatClans[i].h + 20;
                    cmdChatClans[i].Paint(g);
                }
            }
            else
            {
                for (int i = cmdChatClans.Count - 1; i >= 0; i--)
                {
                    num += cmdChatClans[i].h;
                    if (Player.me.id == cmdChatClans[i].player.id && !cmdChatClans[i].isServer)
                    {
                        cmdChatClans[i].x = xScroll + wScroll - 7 - cmdChatClans[i].w;
                    }
                    else
                    {
                        cmdChatClans[i].x = xScroll + 7;
                    }
                    cmdChatClans[i].y = yScroll + hScroll - 7 - num;
                    num += 20;
                    cmdChatClans[i].Paint(g);
                }
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabChatPlayer()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70 - cmdChat.h - 10;
            cmdChat.x = xScroll + wScroll - cmdChat.w;
            cmdChat.y = yScroll + hScroll + 10;
            textChat.x = xScroll;
            textChat.y = cmdChat.y;
            textChat.name = PlayerText.input_chat_player;
            textChat.isFocus = true;
            hScrollInfo = 0;
            if (cmdChatPlayers.Count > 0)
            {
                if (cmdChatPlayers.Count > 20)
                {
                    cmdChatPlayers.RemoveAt(0);
                }
                xScrollInfo = xScroll;
                yScrollInfo = yScroll;
                wScrollInfo = wScroll;
                hScrollInfo = imgBtnMini.GetHeight();
                yScroll += hScrollInfo + 10;
                hScroll -= hScrollInfo + 10;
                for (int i = 0; i < cmdChatPlayers.Count; i++)
                {
                    cmdChatPlayers[i]._object = i;
                    cmdChatPlayers[i].x = xScrollInfo + (10 + cmdChatPlayers[i].w) * i;
                    cmdChatPlayers[i].y = yScrollInfo;
                }
                cmxInfo = 0;
                cmxInfoTo = 0;
                cmxInfoLim = cmdChatPlayers.Count * cmdChatPlayers[0].w + (cmdChatPlayers.Count - 1) * 10 - wScrollInfo;
                if (cmxInfoLim < 0)
                {
                    cmxInfoLim = 0;
                }
            }
            int last_cmy = cmyLim;
            if (indexSelect >= 0 && indexSelect < cmdChatPlayers.Count)
            {
                int num = 7;
                List<CmdMessage> messages = cmdChatPlayers[indexSelect].messages;
                for (int i = 0; i < messages.Count; i++)
                {
                    num += messages[i].h + 20;
                }
                cmyLim = num - 20 + 7 - hScroll;
                if (cmyLim < 0)
                {
                    cmyLim = 0;
                }
                textChat.name = PlayerText.input_chat_player + " " + cmdChatPlayers[indexSelect].player.name;
            }
            else
            {
                cmyLim = 0;
            }
            if (cmyTo == last_cmy)
            {
                cmyTo = cmyLim;
                cmy = 0;
            }
        }

        private void PaintChatPlayer(MyGraphics g)
        {
            textChat.Paint(g);
            cmdChat.Paint(g);
            if (hScrollInfo > 0)
            {
                g.SetClip(xScrollInfo, yScrollInfo, wScrollInfo, hScrollInfo);
                g.Translate(-cmxInfo, 0);
                for (int i = 0; i < cmdChatPlayers.Count; i++)
                {
                    cmdChatPlayers[i].Paint(g);
                }
                g.Reset();
            }
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdChatPlayers.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có tin nhắn nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            if (indexSelect < 0 || indexSelect >= cmdChatPlayers.Count)
            {
                MyFont.text_white.DrawString(g, "Chọn để xem tin nhắn", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            List<CmdMessage> messages = cmdChatPlayers[indexSelect].messages;
            cmdChatPlayers[indexSelect].isNew = false;
            int num = 7;
            if (cmyLim > 0)
            {
                for (int i = 0; i < messages.Count; i++)
                {
                    if (Player.me.id == messages[i].player.id)
                    {
                        messages[i].x = xScroll + wScroll - 7 - messages[i].w;
                    }
                    else
                    {
                        messages[i].x = xScroll + 7;
                    }
                    messages[i].y = yScroll + num;
                    num += messages[i].h + 20;
                    messages[i].Paint(g);
                }
            }
            else
            {
                for (int i = messages.Count - 1; i >= 0; i--)
                {
                    num += messages[i].h;
                    if (Player.me.id == messages[i].player.id)
                    {
                        messages[i].x = xScroll + wScroll - 7 - messages[i].w;
                    }
                    else
                    {
                        messages[i].x = xScroll + 7;
                    }
                    messages[i].y = yScroll + hScroll - 7 - num;
                    num += 20;
                    messages[i].Paint(g);
                }
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabTrade()
        {
            xScroll = x + 35;
            wScroll = w / 2 - 70;
            yScroll = y + 90;
            hScroll = h - 170;
            wScrollInfo = wScroll;
            xScrollInfo = x + w - 35 - wScrollInfo;
            yScrollInfo = yScroll;
            hScrollInfo = hScroll;
            cmyLim = 0;
            cmyInfoLim = 0;
            cmy = cmyTo = 0;
            cmyInfo = cmyInfoTo = 0;
            cmdTrade.x = x + (w - cmdTrade.w) / 2;
            cmdTrade.y = yScroll + hScroll + 20;
        }

        private void PaintTrade(MyGraphics g)
        {
            g.DrawImage(imgGender[Player.me.gender], xScroll, yScroll - imgGender[Player.me.gender].GetHeight() - 10);
            MyFont.text_white.DrawString(g, Player.me.name, xScroll + imgGender[Player.me.gender].GetWidth() + 10, yScroll - 40, 0);
            g.SetColor(Color.black, 0.5f);
            g.FillRect(xScroll, yScroll, wScroll, hScroll, 8);
            g.DrawImage(imgBorderCoin, xScroll, yScroll + hScroll + 7);
            g.DrawImage(imgCoin, xScroll + 7, yScroll + hScroll + 7 + (imgBorderCoin.GetHeight() - imgCoin.GetHeight()) / 2);
            string coin = Utils.GetMoneys(coinTrade);
            if (coinTrade >= 1_000_000_000_000)
            {
                coin = Utils.FormatNumber(coinTrade);
            }
            MyFont.text_mini_white.DrawString(g, coin, xScroll + imgBorderCoin.GetWidth() - 7, yScroll + hScroll + 7 + (imgBorderCoin.GetHeight() - MyFont.text_mini_white.GetHeight()) / 2, 1);
            if (!isLock)
            {
                cmdAddCoin.x = xScroll + imgBorderCoin.GetWidth() + 7;
                cmdAddCoin.y = yScroll + hScroll + 7;
                cmdAddCoin.Paint(g);
            }
            g.DrawImage(xScrollInfo + wScrollInfo, yScrollInfo - imgGender[playerMenu.gender].GetHeight() - 10, imgGender[playerMenu.gender], 2, StaticObj.TOP_RIGHT);
            MyFont.text_white.DrawString(g, playerMenu.name, xScrollInfo + wScrollInfo - imgGender[playerMenu.gender].GetWidth() - 10, yScrollInfo - 40, 1);
            g.SetColor(Color.black, 0.5f);
            g.FillRect(xScrollInfo, yScrollInfo, wScrollInfo, hScrollInfo, 8);
            g.DrawImage(imgBorderCoin, xScrollInfo + wScrollInfo, yScrollInfo + hScrollInfo + 7, StaticObj.TOP_RIGHT);
            g.DrawImage(imgCoin, xScrollInfo + wScrollInfo - imgBorderCoin.GetWidth() + 7, yScrollInfo + hScrollInfo + 7 + (imgBorderCoin.GetHeight() - imgCoin.GetHeight()) / 2);
            coin = Utils.GetMoneys(coinPlayerTrade);
            if (coinPlayerTrade >= 1_000_000_000_000)
            {
                coin = Utils.FormatNumber(coinPlayerTrade);
            }
            MyFont.text_mini_white.DrawString(g, coin, xScrollInfo + wScrollInfo - 7, yScroll + hScroll + 7 + (imgBorderCoin.GetHeight() - MyFont.text_mini_white.GetHeight()) / 2, 1);
            g.DrawImage(imgHintWay, x + w / 2 + 15 + (GameCanvas.gameTick % 30 < 15 ? -2 : 2), y + h / 2, StaticObj.VCENTER_HCENTER);
            g.DrawImage(x + w / 2 - 15 + (GameCanvas.gameTick % 30 < 15 ? 2 : -2), y + h / 2, imgHintWay, 2, StaticObj.VCENTER_HCENTER);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (itemsTrade.Count > 0)
            {
                int count_item_w = 1;
                int dis_item = 7;
                int width_item = imgItemTrade.GetWidth();
                int height_item = imgItemTrade.GetHeight();
                List<Item> items = itemsTrade;
                for (int i = 0; i < items.Count; i++)
                {
                    items[i].w = width_item;
                    items[i].h = height_item;
                    items[i].x = xScroll + (i - i / count_item_w * count_item_w) * width_item;
                    items[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                    items[i].actionId = 55;
                    items[i].actionListener = this;
                    items[i]._object = i;
                    items[i].isShow = true;
                    items[i].anchor = StaticObj.TOP_LEFT;
                    items[i].PaintTrade(g);
                }
                int max_height_item = items.Count / count_item_w + (items.Count % count_item_w == 0 ? 0 : 1);
                cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
                if (cmyLim < 0)
                {
                    cmyLim = 0;
                }
            }
            else if (!isLock)
            {
                MyFont.text_mini_white.DrawString(g, "Bạn chưa chọn vật phẩm nào", xScroll + 15, yScroll + 15, 0);
            }
            else
            {
                MyFont.text_mini_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
            //Paint item player trade
            g.SetClip(xScrollInfo, yScrollInfo, wScrollInfo, hScrollInfo);
            g.Translate(0, -cmyInfo);
            if (!isPlayerLock)
            {
                MyFont.text_mini_white.DrawString(g, "Đối phương chưa khóa giao dịch", xScrollInfo + 15, yScrollInfo + 15, 0);
            }
            else
            {
                int count_item_w = 1;
                int dis_item = 0;
                int width_item = imgItemTrade.GetWidth();
                int height_item = imgItemTrade.GetHeight();
                List<Item> items = itemsPlayerTrade;
                for (int i = 0; i < items.Count; i++)
                {
                    items[i].w = width_item;
                    items[i].h = height_item;
                    items[i].x = xScrollInfo + (i - i / count_item_w * count_item_w) * width_item;
                    items[i].y = yScrollInfo + dis_item + i / count_item_w * (height_item + dis_item);
                    items[i].actionId = 54;
                    items[i].actionListener = this;
                    items[i]._object = i;
                    items[i].isShow = true;
                    items[i].anchor = StaticObj.TOP_LEFT;
                    items[i].PaintTrade(g);
                }
                int max_height_item = items.Count / count_item_w + (items.Count % count_item_w == 0 ? 0 : 1);
                cmyInfoLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScrollInfo;
                if (cmyInfoLim < 0)
                {
                    cmyInfoLim = 0;
                }
            }
            g.Reset();
            if (cmyInfoLim > 0)
            {
                PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 10, 1, hScrollInfo - 20, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
            }
            if (!isLock)
            {
                cmdTrade.isShow = true;
                cmdTrade.caption = "Khóa";
            }
            else
            {
                if (!isPlayerLock)
                {
                    cmdTrade.isShow = false;
                }
                else if (!isAccept)
                {
                    cmdTrade.isShow = true;
                    cmdTrade.caption = "Đồng ý";
                }
                else
                {
                    cmdTrade.isShow = false;
                }
            }
            if (cmdTrade.isShow)
            {
                cmdTrade.Paint(g);
            }
        }

        private void SetTabClanInfo()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            cmyLim = 0;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
            InitCmdClan();
            if (cmdClans.Count > 0)
            {
                hScroll -= cmdClans[0].h + 10;
            }
        }

        private void PaintClanInfo(MyGraphics g)
        {
            if (cmdClans.Count > 0)
            {
                for (int i = 0; i < cmdClans.Count; i++)
                {
                    cmdClans[i].x = xScroll + wScroll - cmdClans[i].w - (cmdClans[i].w + 10) * i;
                    cmdClans[i].y = yScroll + hScroll + 10;
                    cmdClans[i].Paint(g);
                }
            }
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);

            if (Player.me.clan == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại bạn chưa có bang hội", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            int dis = MyFont.text_white.GetHeight();
            int num = -dis + 10;
            Clan clan = Player.me.clan;
            MyFont.text_white.DrawString(g, "Bang hội: " + clan.GetName(), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Bang chủ: " + clan.members[0].name, xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Khẩu hiệu: " + clan.GetSlogan(), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Top: 0", xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Cấp: " + clan.GetLevel(), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Ngân sách: " + Utils.GetMoneys(clan.coin) + " xu", xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Kinh nghiệm: " + Utils.GetMoneys(clan.GetExp()) + "/" + Utils.GetMoneys(clan.GetMaxExp()), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Thành viên: " + clan.GetMembers().Count + "/" + clan.GetMaxPlayer(), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Bang chiến: 0/0", xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Huyết chiến: 0", xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Thành lập: " + clan.GetCreateTime(), xScroll + 20, yScroll + (num += dis), 0);
            MyFont.text_white.DrawString(g, "Ghi chú: " + clan.GetNotification(), xScroll + 20, yScroll + (num += dis), 0);
            if (num > hScroll - dis - 8)
            {
                cmyLim = num - hScroll + dis + 8;
            }
            else
            {
                cmyLim = 0;
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabClanMember()
        {
            int count_item_w = 1;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            if (Player.me.clan != null)
            {
                int width_item = imgClanMember.GetWidth();
                int height_item = imgClanMember.GetHeight();
                for (int i = 0; i < Player.me.clan.members.Count; i++)
                {
                    Player.me.clan.members[i].w = width_item;
                    Player.me.clan.members[i].h = height_item;
                    Player.me.clan.members[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                    Player.me.clan.members[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
                }
                int max_height_item = Player.me.clan.members.Count / count_item_w + (Player.me.clan.members.Count % count_item_w == 0 ? 0 : 1);
                cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
                if (cmyLim < 0)
                {
                    cmyLim = 0;
                }
            }
            else
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintClanMember(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (Player.me.clan == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại bạn chưa có bang hội", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < Player.me.clan.members.Count; i++)
            {
                Player.me.clan.members[i]._object = i;
                Player.me.clan.members[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabViewPlayer()
        {
            if (tabIndex == 0)
            {
                playerMenu = viewers[0];
            }
            else
            {
                playerMenu = viewers[1];
            }
            wScrollInfo = imgBgrInfoPlayer.GetWidth();
            hScrollInfo = imgBgrInfoPlayer.GetHeight();
            xScrollInfo = x + w - wScrollInfo - 35;
            yScrollInfo = y + (h - hScrollInfo) / 2;
            xInfoPlayerBag = xScrollInfo;
            yInfoPlayerBag = yScrollInfo;
            int count_item_w = 6;
            int count_item_h = 6;
            int width_item = imgBgrItem.GetWidth();
            int height_item = imgBgrItem.GetHeight();
            int dis_item = 7;
            int xBody = x + 35 + dis_item;
            yScroll = yScrollInfo + 17;
            for (int i = 0; i < itemsBody.Length; i++)
            {
                itemsBody[i] = new ItemPanel(this, 5, i);
                if (i < 4)
                {
                    itemsBody[i].x = xBody;
                    itemsBody[i].y = yScroll + dis_item + i * (width_item + dis_item);
                }
                else if (i < 8)
                {
                    itemsBody[i].x = xBody + (count_item_w - 1) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (i - 4) * (width_item + dis_item);
                }
                else if (i < 14)
                {
                    itemsBody[i].x = xBody + (i - 8) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item);
                }
                else
                {
                    itemsBody[i].x = xBody + (i - 14) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + dis_item + (count_item_h - 1) * (width_item + dis_item);
                }
            }
            xPlayerBag = xBody + (dis_item * count_item_w + width_item * count_item_w) / 2;
            yPlayerBag = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item) - 10;

            cmdOutFitOther.x = xPlayerBag + 5;
            cmdOutFitOther.y = yScroll - cmdOutFitOther.h;
            cmdOutFit.x = xPlayerBag - cmdOutFit.w - 5;
            cmdOutFit.y = yScroll - cmdOutFit.h;

            cmyInfoLim = 0;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            cmyInfo = cmyInfoTo = 0;
        }

        private void PaintViewPlayer(MyGraphics g)
        {
            g.DrawImage(imgBgrInfoPlayer, xInfoPlayerBag, yInfoPlayerBag);
            g.SetClip(xScrollInfo, yScrollInfo + 5, wScrollInfo, hScrollInfo - 10);
            g.Translate(0, -cmyInfo);
            int dis = MyFont.text_mini_white.GetHeight() + 2;
            int num = -dis + 10;
            MyFont.text_mini_white.DrawString(g, "Nhân vật: " + playerMenu.name, xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);
            MyFont.text_mini_white.DrawString(g, "Bang hội: " + (playerMenu.clan != null ? playerMenu.clan.name : "Chưa có"), xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);
            if (playerMenu.gender == 0)
            {
                MyFont.text_mini_white.DrawString(g, "Tộc: Earth", xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);
            }
            else if (playerMenu.gender == 1)
            {
                MyFont.text_mini_white.DrawString(g, "Tộc: Namek", xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);
            }
            else
            {
                MyFont.text_mini_white.DrawString(g, "Tộc: Saiyan", xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);
            }
            MyFont.text_mini_white.DrawString(g,
         GameCanvas.levels[playerMenu.level].name.Replace("#",
                 playerMenu.gender == 0 ? "Nhân" : (playerMenu.gender == 1 ? "Namek" : "Sayain"))
                 + ": " + Utils.FormatNumber(playerMenu.power) + " sức mạnh",
         xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Cấp: " + playerMenu.level + " + " + playerMenu.GetStrPercentLevel() + "%",
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "HP: " + Utils.GetMoneys(playerMenu.hp) + "/" + Utils.GetMoneys(playerMenu.maxHp),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "KI: " + Utils.GetMoneys(playerMenu.mp) + "/" + Utils.GetMoneys(playerMenu.maxMp),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Sức đánh: " + Utils.GetMoneys(playerMenu.maxDamage),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Chí mạng: " + playerMenu.critical,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Giáp: " + playerMenu.reduceDamage,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Phản sát thương: " + playerMenu.strikeBack,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Né đòn: " + playerMenu.dodge,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Hút HP: " + playerMenu.bloodsucking,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Hút KI: " + playerMenu.manaSucking,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            /* ===== CHỈ SỐ MỚI ===== */

            MyFont.text_mini_white.DrawString(g, "Sức đánh chí mạng: " + playerMenu.critDamage,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Giảm sát thương chí mạng: " + playerMenu.ignoreCrit,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Chính xác: " + playerMenu.accurate,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Xuyên giáp: " + playerMenu.pierceArmor,
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            /* ===================== */

            MyFont.text_mini_white.DrawString(g, "Số lần đi bản doanh: " + Utils.GetMoneys(playerMenu.countBarrack),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Điểm năng động: " + Utils.GetMoneys(playerMenu.pointActivity),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            MyFont.text_mini_white.DrawString(g, "Hiếu chiến: " + Utils.GetMoneys(playerMenu.pointPk),
                    xInfoPlayerBag + 20, yScrollInfo + (num += dis), 0);

            cmyInfoLim = num - hScrollInfo + dis;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            g.Reset();
            if (cmyInfoLim > 0)
            {
                PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 20, 1, hScrollInfo - 40, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
            }
            g.Reset();
            cmdOutFit.Paint(g, typeShowOutFit == 0);
            cmdOutFitOther.Paint(g, typeShowOutFit == 1);
            if (typeShowOutFit == 0 || typeShowOutFit == 1)
            {
                List<Item> items = typeShowOutFit == 0 ? playerMenu.itemsBody : playerMenu.itemsOther;
                for (int i = 0; i < itemsBody.Length; i++)
                {
                    switch (i)
                    {
                        case 0:
                            {
                                itemsBody[i].item = items[6];
                                itemsBody[i].Paint(g, "Áo");
                                break;
                            }
                        case 1:
                            {
                                itemsBody[i].item = items[0];
                                itemsBody[i].Paint(g, "Găng tay");
                                break;
                            }
                        case 2:
                            {
                                itemsBody[i].item = items[4];
                                itemsBody[i].Paint(g, "Quần");
                                break;
                            }
                        case 3:
                            {
                                itemsBody[i].item = items[2];
                                itemsBody[i].Paint(g, "Giày");
                                break;
                            }
                        case 4:
                            {
                                itemsBody[i].item = items[7];
                                itemsBody[i].Paint(g, "Radar");
                                break;
                            }
                        case 5:
                            {
                                itemsBody[i].item = items[5];
                                itemsBody[i].Paint(g, "Dây chuyền");
                                break;
                            }
                        case 6:
                            {
                                itemsBody[i].item = items[3];
                                itemsBody[i].Paint(g, "Nhẫn");
                                break;
                            }
                        case 7:
                            {
                                itemsBody[i].item = items[1];
                                itemsBody[i].Paint(g, "Ngọc bội");
                                break;
                            }
                        case 8:
                            {
                                itemsBody[i].item = items[8];
                                itemsBody[i].Paint(g, "Cải trang");
                                break;
                            }
                        case 9:
                            {
                                itemsBody[i].item = items[10];
                                itemsBody[i].Paint(g, "Thú cưỡi");
                                break;
                            }
                        case 10:
                            {
                                itemsBody[i].item = items[9];
                                itemsBody[i].Paint(g, "Bông tai");
                                break;
                            }
                        case 11:
                            {
                                itemsBody[i].item = items[11];
                                itemsBody[i].Paint(g, "Bang hội");
                                break;
                            }
                        case 12:
                            {
                                itemsBody[i].item = items[12];
                                itemsBody[i].Paint(g, "Vòng kim hãm");
                                break;
                            }
                        case 13:
                            {
                                itemsBody[i].item = items[13];
                                itemsBody[i].Paint(g, "Chưa mở 1");
                                break;
                            }
                        case 14:
                            {
                                itemsBody[i].item = items[14];
                                itemsBody[i].Paint(g, "Chưa mở 2");
                                break;
                            }
                        case 15:
                            {
                                itemsBody[i].item = items[15];
                                itemsBody[i].Paint(g, "Chưa mở 3");
                                break;
                            }
                        case 16:
                            {
                                itemsBody[i].item = items[16];
                                itemsBody[i].Paint(g, "Chưa mở 4");
                                break;
                            }
                        case 17:
                            {
                                itemsBody[i].item = items[17];
                                itemsBody[i].Paint(g, "Chưa mở 5");
                                break;
                            }
                        case 18:
                            {
                                itemsBody[i].item = items[18];
                                itemsBody[i].Paint(g, "Chưa mở 6");
                                break;
                            }
                        case 19:
                            {
                                itemsBody[i].item = items[19];
                                itemsBody[i].Paint(g, "Chưa mở 7");
                                break;
                            }
                    }

                }
            }

            /*for (int i = 0; i < itemsBody.Length; i++)
            {
                switch (i)
                {
                    case 0:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[6];
                            itemsBody[i].Paint(g, "Áo");
                            break;
                        }
                    case 1:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[0];
                            itemsBody[i].Paint(g, "Găng tay");
                            break;
                        }
                    case 2:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[4];
                            itemsBody[i].Paint(g, "Quần");
                            break;
                        }
                    case 3:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[2];
                            itemsBody[i].Paint(g, "Giày");
                            break;
                        }
                    case 4:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[7];
                            itemsBody[i].Paint(g, "Radar");
                            break;
                        }
                    case 5:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[5];
                            itemsBody[i].Paint(g, "Dây chuyền");
                            break;
                        }
                    case 6:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[3];
                            itemsBody[i].Paint(g, "Nhẫn");
                            break;
                        }
                    case 7:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[1];
                            itemsBody[i].Paint(g, "Ngọc bội");
                            break;
                        }
                    case 8:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[8];
                            itemsBody[i].Paint(g, "Cải trang");
                            break;
                        }
                    case 9:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[10];
                            itemsBody[i].Paint(g, "Thú cưỡi");
                            break;
                        }
                    case 10:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[9];
                            itemsBody[i].Paint(g, "Bông tai");
                            break;
                        }
                    case 11:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[11];
                            itemsBody[i].Paint(g, "Bang hội");
                            break;
                        }
                    case 12:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[12];
                            itemsBody[i].Paint(g, "Chưa mở 1");
                            break;
                        }
                    case 13:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[13];
                            itemsBody[i].Paint(g, "Chưa mở 1");
                            break;
                        }
                    case 14:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[14];
                            itemsBody[i].Paint(g, "Chưa mở 2");
                            break;
                        }
                    case 15:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[15];
                            itemsBody[i].Paint(g, "Chưa mở 3");
                            break;
                        }
                    case 16:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[16];
                            itemsBody[i].Paint(g, "Chưa mở 4");
                            break;
                        }
                    case 17:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[17];
                            itemsBody[i].Paint(g, "Chưa mở 5");
                            break;
                        }
                    case 18:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[18];
                            itemsBody[i].Paint(g, "Chưa mở 6");
                            break;
                        }
                    case 19:
                        {
                            itemsBody[i].item = playerMenu.itemsBody[19];
                            itemsBody[i].Paint(g, "Chưa mở 7");
                            break;
                        }
                }

            }*/
            g.DrawImage(imgWallBag, xPlayerBag, yPlayerBag, StaticObj.BOTTOM_HCENTER);
            int frame = (playerMenu.frameTick % 15 >= 5) ? 1 : 0;
            if (playerMenu.head != null)
            {
                int icon = playerMenu.head.template.stand[frame];
                if (icon != -1)
                {
                    GraphicManager.instance.Draw(g, icon, xPlayerBag + playerMenu.head.template.dx, yPlayerBag - 25 - playerMenu.head.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                }
            }
            if (playerMenu.body != null)
            {
                int icon = playerMenu.body.template.stand[frame];
                if (icon != -1)
                {
                    GraphicManager.instance.Draw(g, icon, xPlayerBag + playerMenu.body.template.dx, yPlayerBag - 25 - playerMenu.body.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                }
            }
        }

        private void SetTabSetting()
        {
            InitCmdSetting();
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < cmdSettings.Count; i++)
            {
                cmdSettings[i].w = width_item;
                cmdSettings[i].h = height_item;
                cmdSettings[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdSettings[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = cmdSettings.Count / count_item_w + (cmdSettings.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintSetting(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            for (int i = 0; i < cmdSettings.Count; i++)
            {
                bool isOn = false;
                switch (i)
                {
                    case 0:
                        isOn = SoundManager.instance.isPlay;
                        break;
                    case 1:
                        isOn = GraphicManager.instance.isLowGraphic;
                        break;
                    case 2:
                        isOn = screenManager.gameScreen.isShowSpin;
                        break;
                    case 3:
                        isOn = screenManager.gameScreen.isHideMark;
                        break;
                    case 4:
                        isOn = screenManager.gameScreen.isShowEffectPower;
                        break;
                    case 5:
                        isOn = Player.isPaintMedal;
                        break;
                    case 6:
                        isOn = screenManager.gameScreen.isLockAction;
                        break;
                    case 7:
                        isOn = screenManager.gameScreen.isAutoFindMob;
                        break;
                    case 8:
                        isOn = screenManager.gameScreen.isSaveMapAutoPlay;
                        break;
                    case 9:
                        isOn = screenManager.gameScreen.isAutoPick;
                        break;
                    case 10:
                        isOn = !screenManager.gameScreen.isAttackTinhAnh;
                        break;
                    case 11:
                        isOn = !screenManager.gameScreen.isAttackThuLinh;
                        break;
                    case 12:
                        isOn = screenManager.gameScreen.isAutoLogin;
                        break;
                }
                cmdSettings[i].Paint(g, isOn);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabFlag()
        {
            InitCmdFlag();
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < cmdFlags.Count; i++)
            {
                cmdFlags[i].w = width_item;
                cmdFlags[i].h = height_item;
                cmdFlags[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdFlags[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = cmdFlags.Count / count_item_w + (cmdFlags.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintFlag(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            for (int i = 0; i < cmdFlags.Count; i++)
            {
                cmdFlags[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabChatServer()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int num = 7;
            while (cmdChatServers.Count > 100)
            {
                cmdChatServers.RemoveAt(0);
            }
            for (int i = 0; i < cmdChatServers.Count; i++)
            {
                num += cmdChatServers[i].h + 20;
            }
            int last_cmy = cmyLim;
            cmyLim = num - 20 + 7 - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            if (cmyTo == last_cmy)
            {
                cmyTo = cmyLim;
                cmy = 0;
            }
        }

        private void PaintChatServer(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdChatServers.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có thông báo nào", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            int num = 7;
            if (cmyLim > 0)
            {
                for (int i = 0; i < cmdChatServers.Count; i++)
                {
                    if (Player.me.id == cmdChatServers[i].player.id && !cmdChatServers[i].isServer)
                    {
                        cmdChatServers[i].x = xScroll + wScroll - 7 - cmdChatServers[i].w;
                    }
                    else
                    {
                        cmdChatServers[i].x = xScroll + 7;
                    }
                    cmdChatServers[i].y = yScroll + num;
                    num += cmdChatServers[i].h + 20;
                    cmdChatServers[i].Paint(g);
                }
            }
            else
            {
                for (int i = cmdChatServers.Count - 1; i >= 0; i--)
                {
                    num += cmdChatServers[i].h;
                    if (Player.me.id == cmdChatServers[i].player.id && !cmdChatServers[i].isServer)
                    {
                        cmdChatServers[i].x = xScroll + wScroll - 7 - cmdChatServers[i].w;
                    }
                    else
                    {
                        cmdChatServers[i].x = xScroll + 7;
                    }
                    cmdChatServers[i].y = yScroll + hScroll - 7 - num;
                    num += 20;
                    cmdChatServers[i].Paint(g);
                }
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 10, yScroll + 10, 1, hScroll - 20, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabMiniGame()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < cmdPlayerMiniGames.Count; i++)
            {
                cmdPlayerMiniGames[i].w = width_item;
                cmdPlayerMiniGames[i].h = height_item;
                cmdPlayerMiniGames[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdPlayerMiniGames[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = cmdPlayerMiniGames.Count / count_item_w + (cmdPlayerMiniGames.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintMiniGame(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdPlayerMiniGames.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < cmdPlayerMiniGames.Count; i++)
            {
                cmdPlayerMiniGames[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabDisciple()
        {
            if (indexTabDisciple == 0)
            {
                items.Clear();
                int dis_item = 7;
                for (int i = 0; i < cmdTabDisciples.Count; i++)
                {
                    cmdTabDisciples[i].x = xScroll + dis_item + (cmdTabDisciples[i].w + dis_item) * i;
                    cmdTabDisciples[i].y = y + 38;
                }
                wScrollInfo = imgBgrInfoPlayer.GetWidth();
                hScrollInfo = imgBgrInfoPlayer.GetHeight();
                xScrollInfo = x + 35;
                yScrollInfo = cmdTabDisciples[0].y + cmdTabDisciples[0].h + dis_item + 10;
                xInfoPlayerBag = xScrollInfo;
                yInfoPlayerBag = yScrollInfo;
                int count_item_w = 6;
                int count_item_h = 6;
                int width_item = imgBgrItem.GetWidth();
                int height_item = imgBgrItem.GetHeight();
                wScroll = width_item * count_item_w + (dis_item + 1) * count_item_w;
                hScroll = height_item * count_item_h + (dis_item + 1) * count_item_h;
                int xBody = x + w - wScroll - 30;
                yScroll = yScrollInfo;
                xScroll = xBody;
                for (int i = 0; i < itemsBody.Length; i++)
                {
                    itemsBody[i] = new ItemPanel(this, 5, i);
                    if (i < 4)
                    {
                        itemsBody[i].x = xBody;
                        itemsBody[i].y = yScroll + i * (width_item + dis_item);
                    }
                    else if (i < 8)
                    {
                        itemsBody[i].x = xBody + (count_item_w - 1) * (width_item + dis_item);
                        itemsBody[i].y = yScroll + (i - 4) * (width_item + dis_item);
                    }
                    else if (i < 14)
                    {
                        itemsBody[i].x = xBody + (i - 8) * (width_item + dis_item);
                        itemsBody[i].y = yScroll + (count_item_h - 2) * (width_item + dis_item);
                    }
                    else
                    {
                        itemsBody[i].x = xBody + (i - 14) * (width_item + dis_item);
                        itemsBody[i].y = yScroll + (count_item_h - 1) * (width_item + dis_item);
                    }
                }
                xPlayerBag = xBody + (dis_item * count_item_w + width_item * count_item_w) / 2;
                yPlayerBag = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item) - 10;
                cmdOutFitOther.x = xPlayerBag + 5;
                cmdOutFitOther.y = cmdTabDisciples[0].y;
                cmdOutFit.x = xPlayerBag - cmdOutFit.w - 5;
                cmdOutFit.y = cmdTabDisciples[0].y;
                cmyInfoLim = 0;
                if (cmyInfoLim < 0)
                {
                    cmyInfoLim = 0;
                }
                cmyInfo = cmyInfoTo = 0;
                return;
            }
            if (indexTabDisciple == 1)
            {
                skills.Clear();
                for (int i = 0; i < Player.disciple.skills.Count; i++)
                {
                    CmdSkill cmd = new CmdSkill();
                    cmd.actionListener = this;
                    cmd.actionId = 11;
                    cmd._object = i;
                    cmd.skill = Player.disciple.skills[i];
                    skills.Add(cmd);
                }
                int wSkill = CmdSkill.imgBgr.GetWidth();
                xScroll = x + 40;
                yScroll = y + 90;
                wScroll = w - 80;
                hScroll = wSkill + 40;
                int dis = 10;
                int max_w_skill = wSkill * skills.Count + dis * (skills.Count - 1) + 40;
                int xSkill = xScroll + 20;
                if (max_w_skill < wScroll)
                {
                    xSkill = xScroll + (wScroll - max_w_skill) / 2;
                }
                int ySkill = yScroll + hScroll / 2 + 10;
                for (int i = 0; i < skills.Count; i++)
                {
                    skills[i].x = xSkill + skills[i].w / 2 + (wSkill + dis) * i;
                    skills[i].y = ySkill;
                }
                cmxLim = max_w_skill - wScroll;
                if (cmxLim < 0)
                {
                    cmxLim = 0;
                }
                cmx = cmxTo = 0;
                int yPotential = yScroll + hScroll + 100;
                cmdPotentials[0].x = xScroll;
                cmdPotentials[0].y = yPotential;
                cmdPotentials[1].x = xScroll + wScroll - cmdPotentials[1].w;
                cmdPotentials[1].y = cmdPotentials[0].y;
                cmdPotentials[2].x = xScroll;
                cmdPotentials[2].y = yPotential + cmdPotentials[0].h + 10;
                cmdPotentials[3].x = xScroll + wScroll - cmdPotentials[1].w;
                cmdPotentials[3].y = cmdPotentials[2].y;
                return;
            }
        }

        private void PaintDisciple(MyGraphics g)
        {
            g.Reset();

            if (Player.disciple == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại chưa có đệ tử", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < cmdTabDisciples.Count; i++)
            {
                cmdTabDisciples[i].Paint(g, indexTabDisciple == i);
            }
            if (indexTabDisciple == 0)
            {
                g.DrawImage(imgBgrInfoPlayer, xInfoPlayerBag, yInfoPlayerBag);
                g.SetClip(xScrollInfo, yScrollInfo + 5, wScrollInfo, hScrollInfo - 10);
                g.Translate(0, -cmyInfo);
                int dis = MyFont.text_mini_white.GetHeight() + 2;
                int num = -dis + 10;
                MyFont.text_mini_white.DrawString(g, "Đệ tử: " + Player.disciple.name, xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                if (Player.disciple.gender == 0)
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Earth", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                else if (Player.disciple.gender == 1)
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Namek", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                else
                {
                    MyFont.text_mini_white.DrawString(g, "Tộc: Saiyan", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
                }
                MyFont.text_mini_white.DrawString(g,
        GameCanvas.levels[Player.disciple.level].name.Replace("#",
                Player.disciple.gender == 0 ? "Nhân" : (Player.disciple.gender == 1 ? "Namek" : "Sayain"))
                + ": " + Utils.FormatNumber(Player.disciple.power) + " sức mạnh",
        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Cấp: " + Player.disciple.level + " + " + Player.disciple.GetStrPercentLevel() + "%",
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "HP: " + Utils.GetMoneys(Player.disciple.hp) + "/" + Utils.GetMoneys(Player.disciple.maxHp),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "KI: " + Utils.GetMoneys(Player.disciple.mp) + "/" + Utils.GetMoneys(Player.disciple.maxMp),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Sức đánh: " + Utils.GetMoneys(Player.disciple.maxDamage),
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Chí mạng: " + Player.disciple.critical,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Giáp: " + Player.disciple.reduceDamage,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Phản sát thương: " + Player.disciple.strikeBack,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Né đòn: " + Player.disciple.dodge,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Hút HP: " + Player.disciple.bloodsucking,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Hút KI: " + Player.disciple.manaSucking,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                /* ===== CHỈ SỐ MỚI ===== */

                MyFont.text_mini_white.DrawString(g, "Sức đánh chí mạng: " + Player.disciple.critDamage,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Giảm sát thương chí mạng: " + Player.disciple.ignoreCrit,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Chính xác: " + Player.disciple.accurate,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                MyFont.text_mini_white.DrawString(g, "Xuyên giáp: " + Player.disciple.pierceArmor,
                        xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);

                cmyInfoLim = num - hScrollInfo + dis;
                if (cmyInfoLim < 0)
                {
                    cmyInfoLim = 0;
                }
                g.Reset();
                if (cmyInfoLim > 0)
                {
                    PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 20, 1, hScrollInfo - 40, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
                }
                g.Reset();
                g.SetColor(Color.black, 0.8f);
                int maxWidthPercent = wScroll - MyFont.text_white.GetWidth("Thể lực:") - 10;
                int xPercent = xScroll + wScroll - maxWidthPercent;
                int yPercent = yScrollInfo + hScrollInfo - MyFont.text_white.GetHeight() - 1;
                MyFont.text_white.DrawString(g, "Thể lực:", xScroll, yPercent, 0);
                g.FillRect(xPercent, yPercent, maxWidthPercent, 32, 8);
                int widthPercent = 0;
                if (Player.disciple.maxStamina <= 0)
                {
                    Player.disciple.maxStamina = 1;
                }
                if (Player.disciple.stamina > 0)
                {
                    widthPercent = (int)(Player.disciple.stamina * maxWidthPercent / Player.disciple.maxStamina);
                }
                g.SetColor(65280, 0.8f);
                g.FillRect(xPercent, yPercent, widthPercent, 32, 8);
                MyFont.text_white.DrawString(g, Player.disciple.stamina * 100 / Player.disciple.maxStamina + "%", xPercent + maxWidthPercent / 2, yPercent + 3, 2);
                g.Reset();
                cmdOutFit.Paint(g, typeShowOutFit == 0);
                cmdOutFitOther.Paint(g, typeShowOutFit == 1);
                List<Item> items = typeShowOutFit == 0 ? Player.disciple.itemsBody : Player.disciple.itemsOther;
                for (int i = 0; i < itemsBody.Length; i++)
                {
                    switch (i)
                    {
                        case 0:
                            {
                                itemsBody[i].item = items[6];
                                itemsBody[i].Paint(g, "Áo");
                                break;
                            }
                        case 1:
                            {
                                itemsBody[i].item = items[0];
                                itemsBody[i].Paint(g, "Găng tay");
                                break;
                            }
                        case 2:
                            {
                                itemsBody[i].item = items[4];
                                itemsBody[i].Paint(g, "Quần");
                                break;
                            }
                        case 3:
                            {
                                itemsBody[i].item = items[2];
                                itemsBody[i].Paint(g, "Giày");
                                break;
                            }
                        case 4:
                            {
                                itemsBody[i].item = items[7];
                                itemsBody[i].Paint(g, "Radar");
                                break;
                            }
                        case 5:
                            {
                                itemsBody[i].item = items[5];
                                itemsBody[i].Paint(g, "Dây chuyền");
                                break;
                            }
                        case 6:
                            {
                                itemsBody[i].item = items[3];
                                itemsBody[i].Paint(g, "Nhẫn");
                                break;
                            }
                        case 7:
                            {
                                itemsBody[i].item = items[1];
                                itemsBody[i].Paint(g, "Ngọc bội");
                                break;
                            }
                        case 8:
                            {
                                itemsBody[i].item = items[8];
                                itemsBody[i].Paint(g, "Cải trang");
                                break;
                            }
                        case 9:
                            {
                                itemsBody[i].item = items[10];
                                itemsBody[i].Paint(g, "Thú cưỡi");
                                break;
                            }
                        case 10:
                            {
                                itemsBody[i].item = items[9];
                                itemsBody[i].Paint(g, "Bông tai");
                                break;
                            }
                        case 11:
                            {
                                itemsBody[i].item = items[11];
                                itemsBody[i].Paint(g, "Bang hội");
                                break;
                            }
                        case 12:
                            {
                                itemsBody[i].item = items[12];
                                itemsBody[i].Paint(g, "Vòng kìm hãm");
                                break;
                            }
                        case 13:
                            {
                                itemsBody[i].item = items[13];
                                itemsBody[i].Paint(g, "Chưa mở 1");
                                break;
                            }
                        case 14:
                            {
                                itemsBody[i].item = items[14];
                                itemsBody[i].Paint(g, "Chưa mở 2");
                                break;
                            }
                        case 15:
                            {
                                itemsBody[i].item = items[15];
                                itemsBody[i].Paint(g, "Chưa mở 3");
                                break;
                            }
                        case 16:
                            {
                                itemsBody[i].item = items[16];
                                itemsBody[i].Paint(g, "Chưa mở 4");
                                break;
                            }
                        case 17:
                            {
                                itemsBody[i].item = items[17];
                                itemsBody[i].Paint(g, "Chưa mở 5");
                                break;
                            }
                        case 18:
                            {
                                itemsBody[i].item = items[18];
                                itemsBody[i].Paint(g, "Chưa mở 6");
                                break;
                            }
                        case 19:
                            {
                                itemsBody[i].item = items[19];
                                itemsBody[i].Paint(g, "Chưa mở 7");
                                break;
                            }
                    }

                }
                g.DrawImage(imgWallBag, xPlayerBag, yPlayerBag, StaticObj.BOTTOM_HCENTER);
                int frame = (GameCanvas.gameTick % 15 >= 5) ? 1 : 0;
                if (Player.disciple.head != null)
                {
                    int icon = Player.disciple.head.template.stand[frame];
                    if (icon != -1)
                    {
                        GraphicManager.instance.Draw(g, icon, xPlayerBag + Player.disciple.head.template.dx, yPlayerBag - 25 - Player.disciple.head.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                    }
                }
                if (Player.disciple.body != null)
                {
                    int icon = Player.disciple.body.template.stand[frame];
                    if (icon != -1)
                    {
                        GraphicManager.instance.Draw(g, icon, xPlayerBag + Player.disciple.body.template.dx, yPlayerBag - 25 - Player.disciple.body.template.dy, 0, StaticObj.BOTTOM_HCENTER);
                    }
                }

                return;
            }
            if (indexTabDisciple == 1)
            {
                g.SetClip(xScroll, yScroll, wScroll, hScroll);
                g.Translate(-cmx, 0);
                for (int i = 0; i < skills.Count; i++)
                {
                    skills[i].Paint(g);
                }
                g.Reset();
                g.DrawImage(imgBgrSkill, xScroll, yScroll);
                MyFont.text_blue.DrawString(g, "Điểm tiềm năng: " + Utils.GetMoneys(Player.disciple.potential), xScroll + 20, cmdPotentials[0].y - 40, 0);
                MyFont.text_blue.DrawString(g, "Trạng thái: " + Player.disciple.statusDisciple, xScroll + wScroll - 20, cmdPotentials[0].y - 40, 1);
                for (int i = 0; i < cmdPotentials.Length; i++)
                {
                    if (i == 0)
                    {
                        cmdPotentials[i].caption = "Sức mạnh: " + Utils.GetMoneys(Player.disciple.baseDamage);
                        cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.disciple.potentialUpDamage));
                    }
                    else if (i == 1)
                    {
                        cmdPotentials[i].caption = "Thể lực: " + Utils.GetMoneys(Player.disciple.baseHp);
                        cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.disciple.potentialUpHp));
                    }
                    else if (i == 2)
                    {
                        cmdPotentials[i].caption = "Trí lực: " + Utils.GetMoneys(Player.disciple.baseMp);
                        cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.disciple.potentialUpMp));
                    }
                    else if (i == 3)
                    {
                        cmdPotentials[i].caption = "Thân pháp: " + Utils.GetMoneys(Player.disciple.baseConstitution);
                        cmdPotentials[i].description = upPointPotentialInfo.Replace("#", Utils.FormatNumber(Player.disciple.potentialUpConstitution));
                    }
                    cmdPotentials[i].Paint(g);
                }
                return;
            }
        }

        private void SetTabReward()
        {
            int count_item_w = 1;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgBgrGift.GetWidth();
            int height_item = imgBgrGift.GetHeight();
            for (int i = 0; i < cmdRewards.Count; i++)
            {
                cmdRewards[i].w = width_item;
                cmdRewards[i].h = height_item;
                cmdRewards[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdRewards[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = cmdRewards.Count / count_item_w + (cmdRewards.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintReward(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdRewards.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < cmdRewards.Count; i++)
            {
                cmdRewards[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabGift()
        {
            int count_item_w = 1;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgBgrGift.GetWidth();
            int height_item = imgBgrGift.GetHeight();
            for (int i = 0; i < gifts.Count; i++)
            {
                gifts[i].w = width_item;
                gifts[i].h = height_item;
                gifts[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                gifts[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = gifts.Count / count_item_w + (gifts.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintGift(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (gifts.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < gifts.Count; i++)
            {
                gifts[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabPlayerInMap()
        {
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            cmdPlayerInMaps.Clear();
            List<Player> players = ScreenManager.instance.gameScreen.players;
            for (int i = 0; i < players.Count; i++)
            {
                cmdPlayerInMaps.Add(new CmdPlayerInMap(players[i], i, this, 85, players[i].id));
            }
            for (int i = 0; i < cmdPlayerInMaps.Count; i++)
            {
                cmdPlayerInMaps[i].w = width_item;
                cmdPlayerInMaps[i].h = height_item;
                cmdPlayerInMaps[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdPlayerInMaps[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = cmdPlayerInMaps.Count / count_item_w + (cmdPlayerInMaps.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintPlayerInMap(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (cmdPlayerInMaps.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Hiện không có người chơi khác", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < cmdPlayerInMaps.Count; i++)
            {
                cmdPlayerInMaps[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabSettingFocus()
        {
            InitCmdSettingFocus();
            int count_item_w = 2;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgItemShop.GetWidth();
            int height_item = imgItemShop.GetHeight();
            for (int i = 0; i < cmdSettingFocus.Count; i++)
            {
                cmdSettingFocus[i].w = width_item;
                cmdSettingFocus[i].h = height_item;
                cmdSettingFocus[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                cmdSettingFocus[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = friends.Count / count_item_w + (friends.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintSettingFocus(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            for (int i = 0; i < cmdSettingFocus.Count; i++)
            {
                bool isOn = false;
                switch (i)
                {
                    case 0:
                        isOn = Player.isSelectItemMap;
                        break;
                    case 1:
                        isOn = Player.isSelectNpc;
                        break;
                    case 2:
                        isOn = Player.isSelectMonster;
                        break;
                    case 3:
                        isOn = Player.isSelectPlayer;
                        break;
                    case 4:
                        isOn = Player.isSelectEnemy;
                        break;
                }
                cmdSettingFocus[i].Paint(g, isOn);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private void SetTabPet()
        {
            items.Clear();
            int dis_item = 7;
            wScrollInfo = imgBgrInfoPlayer.GetWidth();
            hScrollInfo = imgBgrInfoPlayer.GetHeight();
            xScrollInfo = x + 35;
            yScrollInfo = y + (h - hScrollInfo) / 2;
            xInfoPlayerBag = xScrollInfo;
            yInfoPlayerBag = yScrollInfo;
            int count_item_w = 6;
            int count_item_h = 6;
            int width_item = imgBgrItem.GetWidth();
            int height_item = imgBgrItem.GetHeight();
            wScroll = width_item * count_item_w + (dis_item + 1) * count_item_w;
            hScroll = height_item * count_item_h + (dis_item + 1) * count_item_h;
            int xBody = x + w - wScroll - 30;
            yScroll = yScrollInfo;
            xScroll = xBody;
            for (int i = 0; i < itemsBody.Length; i++)
            {
                itemsBody[i] = new ItemPanel(this, 5, i);
                if (i < 4)
                {
                    itemsBody[i].x = xBody;
                    itemsBody[i].y = yScroll + i * (width_item + dis_item);
                }
                else if (i < 8)
                {
                    itemsBody[i].x = xBody + (count_item_w - 1) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + (i - 4) * (width_item + dis_item);
                }
                else if (i < 14)
                {
                    itemsBody[i].x = xBody + (i - 8) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + (count_item_h - 2) * (width_item + dis_item);
                }
                else
                {
                    itemsBody[i].x = xBody + (i - 14) * (width_item + dis_item);
                    itemsBody[i].y = yScroll + (count_item_h - 1) * (width_item + dis_item);
                }
            }
            xPlayerBag = xBody + (dis_item * count_item_w + width_item * count_item_w) / 2;
            yPlayerBag = yScroll + dis_item + (count_item_h - 2) * (width_item + dis_item) - 10;
            cmyInfoLim = 0;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
        }

        private void PaintPet(MyGraphics g)
        {
            if (petMenu == null)
            {
                MyFont.text_white.DrawString(g, "Hiện tại chưa có thú nuôi", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            g.DrawImage(imgBgrInfoPlayer, xInfoPlayerBag, yInfoPlayerBag);
            g.SetClip(xScrollInfo, yScrollInfo + 5, wScrollInfo, hScrollInfo - 10);
            g.Translate(0, -cmyInfo);
            int dis = MyFont.text_mini_white.GetHeight() + 2;
            int num = -dis + 10;
            MyFont.text_green.DrawString(g, petMenu.template.name, xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
            int star = 1;
            int upgrade = 0;
            for (int i = 0; i < petMenu.options.Count; i++)
            {
                ItemOption itemOption = petMenu.options[i];
                if (itemOption.template.id == 19)
                {
                    upgrade = itemOption.param;
                }
                if (itemOption.template.id == 67 || itemOption.template.id == 68)
                {
                    star = itemOption.param;
                }
            }
            num += dis;
            int w_star = TabInfo.imgStarUse.GetWidth();
            for (int i = 0; i < star; i++)
            {
                g.DrawImage(TabInfo.imgStarUse, xInfoPlayerBag + 20 + (w_star + 10) * i, yInfoPlayerBag + num + 10);
            }
            num += dis;
            MyFont.text_mini_white.DrawString(g, "Cấp: " + upgrade + " (" + petMenu.exp + "/" + petMenu.maxExp + ")", xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
            MyFont.text_mini_white.DrawString(g, "HP: " + Utils.GetMoneys(petMenu.hp) + "/" + Utils.GetMoneys(petMenu.maxHp), xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
            MyFont.text_mini_white.DrawString(g, "Tấn công: " + Utils.GetMoneys(petMenu.damage), xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
            for (int i = 0; i < petMenu.options.Count; i++)
            {
                ItemOption itemOption = petMenu.options[i];
                if (itemOption.template.id == 19 || itemOption.template.id == 67 || itemOption.template.id == 68 || itemOption.template.type == 6)
                {
                    continue;
                }
                MyFont.text_mini_white.DrawString(g, itemOption.GetStrOption(), xInfoPlayerBag + 20, yInfoPlayerBag + (num += dis), 0);
            }
            cmyInfoLim = num - hScrollInfo + dis;
            if (cmyInfoLim < 0)
            {
                cmyInfoLim = 0;
            }
            g.Reset();
            if (cmyInfoLim > 0)
            {
                PaintScroll(g, xScrollInfo + wScrollInfo + 5, yScrollInfo + 20, 1, hScrollInfo - 40, cmyInfoLim + hScrollInfo, hScrollInfo, cmyInfo);
            }
            g.Reset();
            g.SetColor(Color.black, 0.8f);
            int maxWidthPercent = wScroll - MyFont.text_white.GetWidth("Sinh lực:") - 10;
            int xPercent = xScroll + wScroll - maxWidthPercent;
            int yPercent = yScrollInfo + hScrollInfo - MyFont.text_white.GetHeight() - 1;
            MyFont.text_white.DrawString(g, "Sinh lực:", xScroll, yPercent, 0);
            g.FillRect(xPercent, yPercent, maxWidthPercent, 32, 8);
            int widthPercent = 0;
            if (petMenu.maxStamina <= 0)
            {
                petMenu.maxStamina = 1;
            }
            if (petMenu.stamina > 0)
            {
                widthPercent = (int)(petMenu.stamina * maxWidthPercent / petMenu.maxStamina);
            }
            g.SetColor(65280, 0.8f);
            g.FillRect(xPercent, yPercent, widthPercent, 32, 8);
            MyFont.text_white.DrawString(g, petMenu.stamina * 100 / petMenu.maxStamina + "%", xPercent + maxWidthPercent / 2, yPercent + 3, 2);
            g.Reset();
            for (int i = 0; i < itemsBody.Length; i++)
            {
                switch (i)
                {
                    case 0:
                        {
                            itemsBody[i].item = petMenu.itemsBody[6];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 1:
                        {
                            itemsBody[i].item = petMenu.itemsBody[0];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 2:
                        {
                            itemsBody[i].item = petMenu.itemsBody[4];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 3:
                        {
                            itemsBody[i].item = petMenu.itemsBody[2];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 4:
                        {
                            itemsBody[i].item = petMenu.itemsBody[7];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 5:
                        {
                            itemsBody[i].item = petMenu.itemsBody[5];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 6:
                        {
                            itemsBody[i].item = petMenu.itemsBody[3];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 7:
                        {
                            itemsBody[i].item = petMenu.itemsBody[1];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 8:
                        {
                            itemsBody[i].item = petMenu.itemsBody[8];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 9:
                        {
                            itemsBody[i].item = petMenu.itemsBody[10];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 10:
                        {
                            itemsBody[i].item = petMenu.itemsBody[9];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 11:
                        {
                            itemsBody[i].item = petMenu.itemsBody[11];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 12:
                        {
                            itemsBody[i].item = petMenu.itemsBody[12];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 13:
                        {
                            itemsBody[i].item = petMenu.itemsBody[13];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 14:
                        {
                            itemsBody[i].item = petMenu.itemsBody[14];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 15:
                        {
                            itemsBody[i].item = petMenu.itemsBody[15];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 16:
                        {
                            itemsBody[i].item = petMenu.itemsBody[16];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 17:
                        {
                            itemsBody[i].item = petMenu.itemsBody[17];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 18:
                        {
                            itemsBody[i].item = petMenu.itemsBody[18];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                    case 19:
                        {
                            itemsBody[i].item = petMenu.itemsBody[19];
                            itemsBody[i].Paint(g, "");
                            break;
                        }
                }

            }
            g.Reset();
            g.DrawImage(imgWallBag, xPlayerBag, yPlayerBag, StaticObj.BOTTOM_HCENTER);
            petMenu.Update();
            GraphicManager.instance.Draw(g, petMenu.iconPaint, xPlayerBag + petMenu.template.dx, yPlayerBag + petMenu.template.dx - 25, 0, StaticObj.BOTTOM_HCENTER);
        }

        public void SetTabIntrinsic()
        {
            int count_item_w = 1;
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            int width_item = imgBgrAchivement.GetWidth();
            int height_item = imgBgrAchivement.GetHeight();
            for (int i = 0; i < intrinsics.Count; i++)
            {
                intrinsics[i].w = width_item;
                intrinsics[i].h = height_item;
                intrinsics[i].x = xScroll + dis_item + (i - i / count_item_w * count_item_w) * (width_item + dis_item);
                intrinsics[i].y = yScroll + dis_item + i / count_item_w * (height_item + dis_item);
            }
            int max_height_item = intrinsics.Count / count_item_w + (intrinsics.Count % count_item_w == 0 ? 0 : 1);
            cmyLim = max_height_item * height_item + dis_item * (max_height_item + 1) - hScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }

        private void PaintIntrinsic(MyGraphics g)
        {
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            if (intrinsics.Count == 0)
            {
                MyFont.text_white.DrawString(g, "Danh sách trống", xScroll + 15, yScroll + 15, 0);
                g.Reset();
                return;
            }
            for (int i = 0; i < intrinsics.Count; i++)
            {
                intrinsics[i].Paint(g);
            }
            g.Reset();
            if (cmyLim > 0)
            {
                PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        public void SetTabPickMe()
        {
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            cmyLim = 0;
            cmy = cmyTo = 0;
            cmdJoinPickMe.x = xScroll + wScroll / 2 - 10 - cmdJoinPickMe.w;
            cmdHuongDanPickMe.x = xScroll + wScroll / 2 + 10;
            cmdJoinPickMe.y = cmdHuongDanPickMe.y = yScroll + hScroll - cmdJoinPickMe.h;
        }

        private void PaintPickMe(MyGraphics g)
        {
            g.Reset();
            int x = xScroll + wScroll / 2;
            if (statusPickMe == 0 || statusPickMe == 1)
            {
                cmdHuongDanPickMe.Paint(g);
                cmdJoinPickMe.Paint(g);
                int num = yScroll - 20;
                int dis = 40;
                MyFont.text_white.DrawString(g, "-------> Giải gần nhất <-------", x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Người chiến thắng: " + winnerNamePickMe, x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Số xu chiến thắng: " + winnerCoinPickMe, x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Số xu tham gia: " + winnerCoinJoinedPickMe, x, num += dis, 2);
                num += 5;
                MyFont.text_white.DrawString(g, "-------> Giải hiện tại <-------", x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Tổng giải thưởng: " + Utils.GetMoneys(totalPickMe) + " xu", x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Số người tham gia: " + countPlayerPickMe, x, num += dis, 2);
                MyFont.text_white.DrawString(g, "Bạn đã tham gia: " + Utils.GetMoneys(coinJoinPickMe) + " xu", x, num += dis, 2);
                if (totalPickMe == 0 || coinJoinPickMe == 0)
                {
                    MyFont.text_white.DrawString(g, "Tỉ lệ thắng: 0%", x, num += dis, 2);
                }
                else
                {
                    MyFont.text_white.DrawString(g, "Tỉ lệ thắng: " + ((double)coinJoinPickMe * 100 / (double)totalPickMe) + "%", x, num += dis, 2);
                }
                if (statusPickMe == 0)
                {
                    long time = endTimePickMe - Utils.CurrentTimeMillis();
                    if (time > 0)
                    {
                        MyFont.text_white.DrawString(g, "Thời gian: " + Math.Max(time / 1000, 0) + " giây", x, num += dis, 2);
                    }
                    else if (GameCanvas.gameTick % 14 > 7)
                    {
                        MyFont.text_yellow.DrawString(g, "Đang chuẩn bị quay thưởng, vui lòng chờ trong giây lát", x, num += dis, 2);
                    }
                }
                else if (GameCanvas.gameTick % 14 > 7)
                {
                    MyFont.text_yellow.DrawString(g, "Đang chuẩn bị quay thưởng, vui lòng chờ trong giây lát", x, num += dis, 2);
                }
            }
            else if (statusPickMe == 2)
            {
                MyFont.text_white.DrawString(g, "-------> Quay thưởng <-------", x, yScroll + 20, 2);
                MyFont.text_white.DrawString(g, "Kết quả:", xScroll + wScroll / 2 - 200, yScroll + hScroll / 2, 1);
                g.SetColor(0, 0.4f);
                g.FillRect(xScroll + wScroll / 2 - 180, yScroll + hScroll / 2 - 10, 360, 40);
                g.SetClip(xScroll + wScroll / 2 - 180, yScroll + hScroll / 2 - 100, 360, 200);
                for (int i = 0; i < 10; i++)
                {
                    int num = indexPickMe + i;
                    int yPaint = yScroll + hScroll / 2 + 100 + 40 * num + cmyPickMe;
                    if (yPaint < yScroll + hScroll / 2 - 140 && i == 0)
                    {
                        indexPickMe++;
                    }
                    num %= randomsPickMe.Count;
                    string text = randomsPickMe[num];
                    if (text.StartsWith("kq:"))
                    {
                        MyFont.text_white.DrawString(g, text.Replace("kq:", ""), x, yPaint, 2);
                        if (Math.Abs(yPaint - (yScroll + hScroll / 2)) <= speedPickMe + 5)
                        {
                            statusPickMe = 3;
                        }
                    }
                    else
                    {
                        MyFont.text_white.DrawString(g, text, x, yPaint, 2);
                    }
                }
                speedPickMe = GetSpeedPickMe(endTimePickMe);
                cmyPickMe -= speedPickMe;
                if (resultPickMe != "")
                {
                    bool flag = false;
                    foreach (string text in randomsPickMe)
                    {
                        if (text.StartsWith("kq:"))
                        {
                            flag = true;
                            break;
                        }
                    }
                    if (!flag)
                    {
                        randomsPickMe.Insert(((indexPickMe + 1) % randomsPickMe.Count + 1) % randomsPickMe.Count, "kq:" + resultPickMe);
                    }
                }
            }
            else if (statusPickMe == 3)
            {
                MyFont.text_white.DrawString(g, "-------> Quay thưởng <-------", x, yScroll + 20, 2);
                MyFont.text_white.DrawString(g, "Kết quả:", xScroll + wScroll / 2 - 200, yScroll + hScroll / 2, 1);
                g.SetColor(0, 0.4f);
                g.FillRect(xScroll + wScroll / 2 - 180, yScroll + hScroll / 2 - 10, 360, 40);
                g.SetClip(xScroll + wScroll / 2 - 180, yScroll + hScroll / 2 - 100, 360, 200);
                if (GameCanvas.gameTick % 14 > 7)
                {
                    MyFont.text_yellow.DrawString(g, resultPickMe, xScroll + wScroll / 2, yScroll + hScroll / 2, 2);
                }
            }

            g.Reset();
        }

        public int GetSpeedPickMe(long endTime)
        {
            if (resultPickMe != "")
            {
                return 3;
            }
            long now = Utils.CurrentTimeMillis();
            long time = endTime - now;
            if (time <= 2000)
            {
                return 3;
            }
            return (int)Math.Min((Math.Abs(time) - 1500) / 500, 36);
        }

        /*public void SetTabLucky()
        {
            int dis_item = 7;
            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;
            items.Clear();
            for (int i = 0; i < 9; i++)
            {
                items.Add(new ItemPanel(this, 98, i));
            }
            int xItem = xScroll + (wScroll - 3 * items[0].w - 2 * dis_item) / 2;
            int yItem = yScroll + 70;
            for (int i = 0; i < items.Count; i++)
            {
                items[i].x = xItem + (i % 3) * (items[0].w + dis_item);
                items[i].y = yItem + (i / 3) * (items[0].h + dis_item);
            }
            cmdLucky.x = xScroll + wScroll / 2 - cmdLucky.w / 2;
            cmdLucky.y = yScroll + hScroll - 20 - cmdLucky.h;
            cmyLim = 0;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
        }*/

        public void SetTabLucky()
        {
            int dis_item = 7;
            int totalSlot = 25;   // ⭐ 5x5
            int col = 5;          // ⭐ số cột

            xScroll = x + 35;
            wScroll = w - 70;
            yScroll = y + 35;
            hScroll = h - 70;

            items.Clear();
            for (int i = 0; i < totalSlot; i++)
            {
                items.Add(new ItemPanel(this, 98, i));
            }
            int xItem = xScroll
                + (wScroll - col * items[0].w - (col - 1) * dis_item) / 2;

            int yItem = yScroll + 40; 

            for (int i = 0; i < items.Count; i++)
            {
                items[i].x = xItem + (i % col) * (items[0].w + dis_item);
                items[i].y = yItem + (i / col) * (items[0].h + dis_item);
            }

            cmdLucky.x = xScroll + wScroll / 2 - cmdLucky.w / 2;
            cmdLucky.y = yScroll + hScroll - 20 - cmdLucky.h;

            cmyLim = (5 * items[0].h + 4 * dis_item) - (hScroll - 100);
            if (cmyLim < 0) cmyLim = 0;

            cmy = cmyTo = 0;
        }

        private void PaintLucky(MyGraphics g)
        {
            g.Reset();
            for (int i = 0; i < items.Count; i++)
            {
                if (i < itemsLucky.Count)
                {
                    items[i].item = itemsLucky[i];
                }
                else
                {
                    items[i].item = null;
                }
                items[i].Paint(g);
                if (i >= itemsLucky.Count)
                {
                    g.DrawImage(Npc.imgQuesion, items[i].x + items[i].w / 2, items[i].y + items[i].h / 2, StaticObj.VCENTER_HCENTER);
                }
                if (indexLucky == i)
                {
                    g.SetColor(Color.white);
                    g.drawRect(items[i].x - 1, items[i].y - 1, items[i].w + 2, items[i].h + 2);
                }
            }
            MyFont.text_white.DrawString(g, "Phí mỗi lượt: " + requireLucky, xScroll + 150, cmdLucky.y - 50, 0);
            cmdLucky.caption = itemsLucky.Count == 0 ? "Chọn" : "Đặt lại";
            cmdLucky.Paint(g);
            g.Reset();
        }

    }
}
