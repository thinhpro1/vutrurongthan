using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Libs.Jsons;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using System;
using System.Collections.Generic;
using UnityEngine;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Displays;
using Assets.Scripts.Commands;
using Assets.Scripts.Items;
using Assets.Scripts.Entites.Npcs;
using Assets.Scripts.Effects;
using Assets.Scripts.Frames;
using System.Linq;
using Assets.Scripts.Networks;
using Assets.Scripts.Sounds;
using Assets.Scripts.Skills;

namespace Assets.Scripts.Games
{
    public class GameCanvas : IActionListener, IChatable
    {
        public static long lastPointerReleased;

        public static long lastPointerClicked;

        public static bool isLoading;

        public static int gameTick;

        public static int w;

        public static int h;

        public static ClientInput clientInput;

        public static Menu menu;

        public static MenuBar menuBar;

        public static Image imgLogoScr;

        public static GameCanvas instance;

        public static Dictionary<int, Level> levels;

        public static int versionLevel;

        public static int PointerClickX;

        public static int PointerClickY;

        public static int PointerReleaseX;

        public static int PointerReleaseY;

        public static Image[] imgLoading;

        public static Image imgWait;

        public static Image[] imgPopups;

        public GameCanvas()
        {
            versionLevel = -1;
            levels = new Dictionary<int, Level>();
        }

        static GameCanvas()
        {
            w = Screen.width;
            h = Screen.height;
            instance = new GameCanvas();
            menu = new MenuCommad();
            menuBar = new MenuBar();
            clientInput = new ClientInput();
            imgPopups = new Image[9];
            for (int i = 0; i < imgPopups.Length; i++)
            {
                imgPopups[i] = GameCanvas.LoadImage("MainImages/GameCanvas/Popups/" + i);
            }
        }

        public static void LoadData()
        {
            SkillManager.instance.Init();
            ServerManager.instance.Init();
            MonsterManager.instance.Init();
            NpcManager.instance.Init();
            ItemManager.instance.Init();
            EffectManager.instance.Init();
            FrameManager.instance.Init();
            GraphicManager.instance.Init();
            SoundManager.instance.Init();
            LoadLevel();
            Map.Init();

        }

        private static void LoadLevel()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("level"));
                versionLevel = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    Level level = new Level();
                    level.id = reader.ReadShort();
                    level.name = reader.ReadUTF();
                    level.power = reader.ReadLong();
                    levels.Add(level.id, level);
                }
            }
            catch
            {
                versionLevel = -1;
                levels.Clear();
            }
        }

        public static void SaveLevel()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionLevel);
            writer.WriteShort(levels.Count);
            for (int i = 0; i < levels.Count; i++)
            {
                Level level = levels.ElementAt(i).Value;
                writer.WriteShort(level.id);
                writer.WriteUTF(level.name);
                writer.WriteLong(level.power);
            }
            Rms.Save("level", writer.GetData());
        }

        public static void Update()
        {
            gameTick++;
            if (gameTick > 10000)
            {
                gameTick = 0;
            }
            try
            {
                if (ScreenManager.instance.currentScreen != null)
                {
                    if (DisplayManager.instance.dialog.isShow)
                    {
                        DisplayManager.instance.dialog.Update();
                    }
                    if (menu.isShow)
                    {
                        menu.Update();
                    }
                    if (menuBar.isShow)
                    {
                        menuBar.Update();
                    }
                    if (clientInput.isShow)
                    {
                        clientInput.Update();
                    }
                    if (ScreenManager.instance.panel.isShow)
                    {
                        ScreenManager.instance.panel.Update();
                    }
                    ScreenManager.instance.currentScreen.Update();
                    /* if (!DisplayManager.instance.dialog.isShow && ScreenManager.instance.currentScreen != null && ScreenManager.instance.currentScreen is SplashScreen && Utils.CurrentTimeMillis() - Session.timeConnect > 3000)
                     {
                         GameCanvas.StartDialogOk("Không thể kết nối đến máy chủ " + LoginScreen.strServers[LoginScreen.indexServer] + ", vui lòng khởi động lại", new Command("OK", GameCanvas.instance, 4, null));
                     }*/
                }
                InfoDlg.Update();
            }
            catch
            {
            }
        }

        public static void Paint(MyGraphics g)
        {
            try
            {
                if (ScreenManager.instance.currentScreen != null)
                {
                    ScreenManager.instance.currentScreen.Paint(g);
                }
                g.Translate(-g.getTranslateX(), -g.getTranslateY());
                g.SetClip(0, 0, w, h);
                if (ScreenManager.instance.panel.isShow)
                {
                    ScreenManager.instance.panel.Paint(g);
                }
                g.Translate(-g.getTranslateX(), -g.getTranslateY());
                g.SetClip(0, 0, w, h);
                InfoDlg.Paint(g);
                if (DisplayManager.instance.dialog.isShow)
                {
                    DisplayManager.instance.dialog.Paint(g);
                }
                else if (menu.isShow)
                {
                    menu.Paint(g);
                }
                else if (menuBar.isShow)
                {
                    menuBar.Paint(g);
                }
                else if (clientInput.isShow)
                {
                    clientInput.Paint(g);
                }
                InfoMe.Paint(g);
                if (Player.isLoadingMap || Player.isLogin)
                {
                    PaintChangeMap(g);
                }
                ResetTrans(g);
            }
            catch
            {
            }
        }

        public static void PaintChangeMap(MyGraphics g)
        {
            g.Reset();
            g.SetColor(0);
            g.FillRect(0, 0, w, h);
            ScreenManager.instance.PaintLoading(g);
        }



        public static bool IsPaint(int x, int y)
        {
            if (y < ScreenManager.instance.gameScreen.cmy - 100)
            {
                return false;
            }
            if (y > ScreenManager.instance.gameScreen.cmy + GameCanvas.h + 100)
            {
                return false;
            }
            if (x < ScreenManager.instance.gameScreen.cmx - 100)
            {
                return false;
            }
            if (x > ScreenManager.instance.gameScreen.cmx + GameCanvas.w + 100)
            {
                return false;
            }
            return true;
        }

        public static void PaintPopup(MyGraphics g, int x, int y, int w, int h)
        {
            int size = imgPopups[0].GetWidth();
            int row = h / size;
            int col = w / size;
            for (int i = 0; i < row; i++)
            {
                for (int j = 0; j < col; j++)
                {
                    Image image;
                    if (i == 0)
                    {
                        if (j == 0)
                        {
                            image = imgPopups[1];
                        }
                        else if (j == col - 1)
                        {
                            image = imgPopups[3];
                        }
                        else
                        {
                            image = imgPopups[2];
                        }
                    }
                    else if (i == row - 1)
                    {
                        if (j == 0)
                        {
                            image = imgPopups[6];
                        }
                        else if (j == col - 1)
                        {
                            image = imgPopups[8];
                        }
                        else
                        {
                            image = imgPopups[7];
                        }
                    }
                    else
                    {
                        if (j == 0)
                        {
                            image = imgPopups[5];
                        }
                        else if (j == col - 1)
                        {
                            image = imgPopups[4];
                        }
                        else
                        {
                            image = imgPopups[0];
                        }
                    }
                    g.DrawImage(image, x + size * j, y + size * i);
                }
            }
        }

        public static void ResetTrans(MyGraphics g)
        {
            g.Translate(-g.getTranslateX(), -g.getTranslateY());
            g.SetClip(0, 0, w, h);
        }

        public static void PaintBGGameScr(MyGraphics g)
        {
            if (Player.isLoadingMap)
            {
                return;
            }
            g.Reset();
            g.SetColor(999999999);
            g.FillRect(0, 0, w, h);
        }

        public static Image LoadImage(string path)
        {
            path = cutPng(path);
            Image result = null;
            try
            {
                result = Image.createImage(path);
                return result;
            }
            catch (Exception e)
            {
                return result;
            }
        }

        public static string cutPng(string str)
        {
            string result = str;
            if (str.Contains(".png"))
            {
                result = str.Replace(".png", string.Empty);
            }
            return result;
        }

        public static void KeyPress(KeyCode keyCode)
        {
            if (InfoDlg.IsLock())
            {
                return;
            }
            if (DisplayManager.instance.dialog.isShow)
            {
                DisplayManager.instance.dialog.KeyPress(keyCode);
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.KeyPress(keyCode);
                return;
            }
            if (menu.isShow)
            {
                menu.KeyPress(keyCode);
                return;
            }
            if (ScreenManager.instance.panel.isShow)
            {
                ScreenManager.instance.panel.KeyPress(keyCode);
                return;
            }
            if (ScreenManager.instance.currentScreen != null)
            {
                ScreenManager.instance.currentScreen.KeyPress(keyCode);
            }
        }

        public static void PointerReleased(int x, int y)
        {
            if (DisplayManager.instance.dialog.isShow)
            {
                DisplayManager.instance.dialog.PointerReleased(x, y);
                return;
            }
            if (InfoDlg.IsLock())
            {
                return;
            }
            if (Utils.CurrentTimeMillis() - lastPointerReleased < 50)
            {
                return;
            }
            lastPointerReleased = Utils.CurrentTimeMillis();
            PointerReleaseX = x;
            PointerReleaseY = y;
            if (clientInput.isShow)
            {
                clientInput.PointerReleased(x, y);
                return;
            }
            if (menu.isShow)
            {
                menu.PointerReleased(x, y);
                return;
            }
            if (menuBar.isShow)
            {
                menuBar.PointerReleased(x, y);
                return;
            }
            if (ScreenManager.instance.panel.isShow)
            {
                ScreenManager.instance.panel.PointerReleased(x, y);
                return;
            }
            if (ScreenManager.instance.currentScreen != null)
            {
                if (ScreenManager.instance.currentScreen == ScreenManager.instance.gameScreen && ScreenManager.instance.gameScreen.gamePad != null)
                {
                    ScreenManager.instance.gameScreen.gamePad.x = 125 + ScreenManager.instance.gameScreen.gamePad.width / 2;
                    ScreenManager.instance.gameScreen.gamePad.y = GameCanvas.h - 100 - ScreenManager.instance.gameScreen.gamePad.height / 2;
                    ScreenManager.instance.gameScreen.gamePad.PointerReleased(x, y);
                }
                ScreenManager.instance.currentScreen.PointerReleased(x, y);
            }
        }

        public static void PointerClicked(int x, int y)
        {
            if (DisplayManager.instance.dialog.isShow)
            {
                DisplayManager.instance.dialog.PointerClicked(x, y);
                return;
            }
            if (InfoDlg.IsLock())
            {
                return;
            }
            if (Utils.CurrentTimeMillis() - lastPointerClicked < 50)
            {
                return;
            }
            lastPointerClicked = Utils.CurrentTimeMillis();
            PointerClickX = x;
            PointerClickY = y;
            if (clientInput.isShow)
            {
                clientInput.PointerClicked(x, y);
                return;
            }
            if (menu.isShow)
            {
                menu.PointerClicked(x, y);
                return;
            }
            if (menuBar.isShow)
            {
                menuBar.PointerClicked(x, y);
                return;
            }
            if (ScreenManager.instance.panel.isShow)
            {
                ScreenManager.instance.panel.PointerClicked(x, y);
                return;
            }
            if (ScreenManager.instance.currentScreen != null)
            {
                if (ScreenManager.instance.currentScreen == ScreenManager.instance.gameScreen && ScreenManager.instance.gameScreen.gamePad != null)
                {
                    if ((x < 300 + ScreenManager.instance.gameScreen.gamePad.width / 2) && (y > GameCanvas.h - 275 - ScreenManager.instance.gameScreen.gamePad.height / 2))
                    {
                        ScreenManager.instance.gameScreen.gamePad.x = x;
                        ScreenManager.instance.gameScreen.gamePad.y = y;
                    }
                    ScreenManager.instance.gameScreen.gamePad.PointerClicked(x, y);
                }
                ScreenManager.instance.currentScreen.PointerClicked(x, y);
            }
        }

        public static void PointerMove(int x, int y)
        {
            if (DisplayManager.instance.dialog.isShow)
            {
                DisplayManager.instance.dialog.PointerMove(x, y);
                return;
            }
            if (InfoDlg.IsLock())
            {
                return;
            }
            if (clientInput.isShow)
            {
                clientInput.PointerMove(x, y);
                return;
            }
            if (menu.isShow)
            {
                menu.PointerMove(x, y);
                return;
            }
            if (menuBar.isShow)
            {
                menuBar.PointerMove(x, y);
                return;
            }
            if (ScreenManager.instance.panel.isShow)
            {
                ScreenManager.instance.panel.PointerMove(x, y);
                return;
            }
            if (ScreenManager.instance.currentScreen != null)
            {
                if (ScreenManager.instance.currentScreen == ScreenManager.instance.gameScreen && ScreenManager.instance.gameScreen.gamePad != null)
                {
                    ScreenManager.instance.gameScreen.gamePad.PointerMove(x, y);
                }
                ScreenManager.instance.currentScreen.PointerMove(x, y);
            }
        }

        public static void PointerScroll(int a)
        {
            if (InfoDlg.IsLock())
            {
                return;
            }
            if (ScreenManager.instance.panel != null && ScreenManager.instance.panel.isShow)
            {
                ScreenManager.instance.panel.PointerScroll(a);
            }
        }

        public void Perform(int idAction, object p)
        {
            switch (idAction)
            {
                case 1:
                    // startOKDlg
                    DisplayManager.instance.dialog.isShow = false;
                    DisplayManager.instance.dialog.Close();
                    break;
                case 2:
                    // startOKDlgOpenUrl
                    DisplayManager.instance.dialog.isShow = false;
                    DisplayManager.instance.dialog.Close();
                    Application.OpenURL(ServerManager.LINKWEB);
                    Main.main.Exit();
                    break;
                case 3:
                    // ok yes no
                    DisplayManager.instance.dialog.isShow = false;
                    DisplayManager.instance.dialog.Close();
                    Service.instance.ConfirmMenu(9, 0);
                    break;
                case 4:
                    // ok yes no
                    DisplayManager.instance.dialog.isShow = false;
                    DisplayManager.instance.dialog.Close();
                    //Rms.SaveInt("index_server", LoginScr.indexServer == 0 ? 1 : 0);
                    Application.Quit();
                    break;
                default:
                    break;
            }
        }

        public static void StartDialogOk(string info)
        {
            DisplayManager.instance.dialog.SetInfo(info, null, new Command(PlayerText.OK, GameCanvas.instance, 1, null), null);
        }

        public static void StartDialogOk(string info, Command command)
        {
            DisplayManager.instance.dialog.SetInfo(info, null, command, null);
        }

        public static void StartOKDlgOpenUrl(string info)
        {
            DisplayManager.instance.dialog.SetInfo(info, null, new Command(PlayerText.OK, GameCanvas.instance, 2, null), null);
        }

        public static void startWaitDlg()
        {
            Player.isLoadingMap = true;
        }

        public static void StartAt(List<CmdMenu> commands)
        {
            if (commands.Count <= 6)
            {
                if (menu == null || !(menu is MenuCommad))
                {
                    menu = new MenuCommad();
                }
            }
            else
            {
                if (menu == null || !(menu is MenuNpc))
                {
                    menu = new MenuNpc();
                }
            }
            menu.StartAt(commands);
        }

        public static void StartAt(List<CmdMenu> commands, string title, int iconId)
        {
            if (commands.Count <= 6)
            {
                if (menu == null || !(menu is MenuCommad))
                {
                    menu = new MenuCommad();
                }
            }
            else
            {
                if (menu == null || !(menu is MenuNpc))
                {
                    menu = new MenuNpc();
                }
            }
            menu.StartAt(commands, title, iconId);
        }

        public void OnChatFromMe(string text)
        {

        }

        public void OnChatFromMe(string name, List<TextField> textFields)
        {

        }


    }
}
