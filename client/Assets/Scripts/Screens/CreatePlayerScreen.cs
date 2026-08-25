using Assets.Scripts.Dialogs;
using UnityEngine;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Actions;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.Services;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Commands;
using Assets.Scripts.Frames;
using Assets.Scripts.Skills;
using Assets.Scripts.Networks;

namespace Assets.Scripts.Screens
{
    public class CreatePlayerScreen : MyScreen, IActionListener
    {
        public static Image imgSoil;

        public Player player;

        public Player focus;

        public int indexSelect;

        public int indexPointerMove = -1;

        public int xScroll;

        public int yScroll;

        public int wScroll;

        public int hScroll;

        public TextField textField;

        public Command cmdOk;

        public Command cmdCancel;

        public int sex = 1;

        public static Image imgMay;

        public static Image imgMay1;

        public static Image imgStar;

        public static Image imgStar1;

        public static Image[] imgClass;

        public static Image imgSelect;

        private int cmx;

        private Command[] cmdNames;

        private string[] names = new string[]
        {
            "Songoku",
            "Picolo",
            "Vegeta"
        };

        public static int[,] parts = new int[,]
        {
            { 5, 21},
            { 3, 22},
            { 4, 23}
        };

        public static int[,] stars = new int[,]
        {
            { 5, 3 },
            { 4, 4 },
            { 3, 5 }
        };

        public static string[] infos = new string[]
        {
            "Hành tinh: ",
            "Tấn công: ",
            "Phòng thủ: ",
            "Đặc biệt: ",
            "Khắc chế: "
        };

        public static string[,] infoClasses = new string[,]
        {
            {
                "Songoku",
                "Saiyan",
                ".",
                ".",
                "Quả cầu Genki",
                "Trái đất"
            },
            {
                "Picolo",
                "Namek",
                ".",
                ".",
                "Tia hủy diệt",
                "Sayain"
            },
            {
                "Vegeta",
                "Saiyan",
                ".",
                ".",
                "Tự phát nổ",
                "Trái đất"
            }
        };

        public long timeSkill;

        public int yPlayer;

        static CreatePlayerScreen()
        {
            imgClass = new Image[3];
            for (int i = 0; i < imgClass.Length; i++)
            {
                imgClass[i] = GameCanvas.LoadImage("imgClassReg" + i);
            }
            imgSoil = GameCanvas.LoadImage("MainImages/CreatePlayers/img_soil");
            imgMay = GameCanvas.LoadImage("imgMay.png");
            imgMay1 = GameCanvas.LoadImage("imgMay1.png");
            imgStar = GameCanvas.LoadImage("imgStar.png");
            imgStar1 = GameCanvas.LoadImage("imgStar1.png");
            imgSelect = GameCanvas.LoadImage("imgSelectReg.png");
        }

        public CreatePlayerScreen(ScreenManager screenManager) : base(screenManager)
        {
            indexSelect = Utils.random(0, 2);
            player = new Player();
            player.x = GameCanvas.w / 2;
            yPlayer = player.y = GameCanvas.h / 2 + 100;
            player.head = FrameManager.instance.GetFrame(parts[indexSelect, 0]);
            player.body = FrameManager.instance.GetFrame(parts[indexSelect, 1]);
            player.level = 69;
            player.hp = 100000;
            focus = new Player();
            focus.x = GameCanvas.w - 100;
            focus.y = player.y;
            player.medal = 2;
            wScroll = 340;
            hScroll = 360;
            xScroll = GameCanvas.w / 2 - 280 - wScroll;
            yScroll = GameCanvas.h / 2 - hScroll / 2;

            textField = new TextField("MainImages/CreatePlayers/input_name_player");
            textField.name = "Tên nhân vật";

            textField.y = player.y + 150;
            textField.maxTextLength = 10;

            cmdOk = new Command("MainImages/CreatePlayers/btn_confirm_name", "MainImages/CreatePlayers/btn_confirm_name_focus", "MainImages/CreatePlayers/btn_confirm_name_click", "OK", this, 1, null);
            cmdOk.y = textField.y;
            cmdOk.w = 100;
            textField.command = cmdOk;

            textField.x = GameCanvas.w / 2 - (textField.w + 20 + cmdOk.w) / 2;
            cmdOk.x = textField.x + textField.w + 20;

            cmdNames = new Command[3];

            for (int i = 0; i < cmdNames.Length; i++)
            {
                cmdNames[i] = new Command("MainImages/CreatePlayers/btn_select_player", "MainImages/CreatePlayers/btn_select_player_focus", "MainImages/CreatePlayers/btn_select_player_click", this, 2, i);
                cmdNames[i].caption = names[i];
                cmdNames[i].isZoomText = false;
            }
            int yCmd = player.y - 330;
            int xCmd = (GameCanvas.w - 50 * (cmdNames.Length - 1) - cmdNames.Length * cmdNames[0].w) / 2;
            for (int i = 0; i < cmdNames.Length; i++)
            {
                cmdNames[i].x = xCmd + (cmdNames[0].w + 50) * i;
                cmdNames[i].y = yCmd;
            }

            cmdCancel = new Command("MainImages/CreatePlayers/btn_return", "MainImages/CreatePlayers/btn_return_focus", "MainImages/CreatePlayers/btn_return_click", this, 3, null);
            cmdCancel.x = 0;
            cmdCancel.y = GameCanvas.h - cmdCancel.h - 20;
        }

        public override void Paint(MyGraphics g)
        {
            ScreenManager.instance.PaintBackground(g);
            g.DrawImage(imgSoil, GameCanvas.w / 2, yPlayer - 35, StaticObj.TOP_CENTER);
            try
            {
                player.Paint(g);
                if (player.dart != null)
                {
                    player.dart.Paint(g);
                }
            }
            catch
            {
            }
            /* int xS = GameCanvas.w / 2 + 250;
             int yS = GameCanvas.h / 2 - 170;
             for (int i = 0; i < 3; i++)
             {
                 g.DrawImage(imgClass[i], xS + 120, yS + 120 * i);
                 if (indexSelect == i)
                 {
                     g.DrawImage(imgSelect, xS + 115, yS + 120 * i - 5);
                 }
             }*/
            for (int i = 0; i < cmdNames.Length; i++)
            {
                if (i == indexSelect)
                {
                    cmdNames[i].isClick = true;
                }
                cmdNames[i].Paint(g);
            }
            textField.Paint(g);
            cmdOk.Paint(g);
            cmdCancel.Paint(g);
            g.Reset();

        }

        public override void Update()
        {
            textField.Update();
            player.frameTick++;
            if (player.frameTick > 30)
            {
                player.frameTick = 0;
            }
            if (player.frameTick % 15 < 5)
            {
                player.frame = 0;
            }
            else
            {
                player.frame = 1;
            }
            try
            {
                player.Update();
                if (player.status != PlayerStatus.ACTION)
                {
                    player.status = PlayerStatus.STAND;
                }
                player.x = GameCanvas.w / 2;
                player.y = yPlayer;
                long now = Utils.CurrentTimeMillis();
                if (player.skillPaint == null)
                {
                    if (now - timeSkill > 2000)
                    {
                        timeSkill = now;
                        int paint = 35;
                        if (indexSelect == 1)
                        {
                            paint = 41;
                        }
                        else if (indexSelect == 2)
                        {
                            paint = 40;
                        }
                        player.playerFocus = focus;
                        player.SetSkillPaint(SkillManager.instance.paints[paint]);
                        player.skillPaint.isFly = false;
                    }
                }
                else
                {
                    timeSkill = now;
                }
            }
            catch
            {
            }

        }

        public override void PointerMove(int x, int y)
        {
            cmdOk.PointerMove(x, y);
            for (int i = 0; i < cmdNames.Length; i++)
            {
                cmdNames[i].PointerMove(x, y);
            }
            cmdCancel.PointerMove(x, y);
        }

        public override void PointerReleased(int x, int y)
        {
            for (int i = 0; i < cmdNames.Length; i++)
            {
                cmdNames[i].PointerReleased(x, y);
            }
            cmdOk.PointerReleased(x, y);
            cmdCancel.PointerReleased(x, y);
        }

        public override void PointerClicked(int x, int y)
        {
            textField.PointerClicked(x, y);
            for (int i = 0; i < cmdNames.Length; i++)
            {
                cmdNames[i].PointerClicked(x, y);
            }
            cmdOk.PointerClicked(x, y);
            cmdCancel.PointerClicked(x, y);
        }

        public override void KeyPress(KeyCode keyCode)
        {
            textField.KeyPress(keyCode);
        }

        public void Perform(int idAction, object p)
        {
            switch (idAction)
            {
                case 1:
                    {
                        if (textField.GetText().Equals(string.Empty))
                        {
                            GameCanvas.StartDialogOk(PlayerText.char_name_blank);
                            break;
                        }
                        if (textField.GetText().Length < 5)
                        {
                            GameCanvas.StartDialogOk(PlayerText.char_name_short);
                            break;
                        }
                        if (textField.GetText().Length > 10)
                        {
                            GameCanvas.StartDialogOk(PlayerText.char_name_long);
                            break;
                        }
                        InfoDlg.ShowWait();
                        Service.CreatePlayer(textField.GetText(), indexSelect);
                        break;
                    }
                case 2:
                    {
                        timeSkill = Utils.CurrentTimeMillis();
                        if (player.dart != null)
                        {
                            player.dart.Stop();
                        }
                        indexSelect = (int)p;
                        player.head = FrameManager.instance.GetFrame(parts[indexSelect, 0]);
                        player.body = FrameManager.instance.GetFrame(parts[indexSelect, 1]);
                        SoundMn.buttonClick();
                        break;
                    }
                case 3:
                    {
                        ServerManager.instance.session.Close();
                        ScreenManager.instance.loginScreen.SwitchToMe();
                        break;
                    }
            }
        }
    }
}
