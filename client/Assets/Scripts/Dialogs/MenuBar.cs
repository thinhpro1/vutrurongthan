using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Displays;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using UnityEngine;

namespace Assets.Scripts.Dialogs
{
    public class MenuBar : IActionListener
    {
        public int x;

        public int y;

        public int w;

        public int h;

        public Command[] commands;

        public IActionListener actionListener;

        public Image imgBar;

        public int indexSelect = -1;

        public bool isShow;

        public bool isClickOut;

        public int yMax;

        public int yMin;

        public bool isClose;

        public MenuBar()
        {
            imgBar = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_menu_bar");
            w = imgBar.GetWidth();
            h = imgBar.GetHeight();
            x = (GameCanvas.w - w) / 2;
            y = GameCanvas.h - h - 50;
            yMax = GameCanvas.h;
            yMin = y;
            commands = new Command[6];
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i] = new Command();
                switch (i)
                {
                    case 0:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_player");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_player_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_player_click");
                            break;
                        }
                    case 1:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_general");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_general_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_general_click");
                            break;
                        }
                    case 2:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_setting");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_setting_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_setting_click");
                            break;
                        }
                    case 3:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_clan");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_clan_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_clan_click");
                            break;
                        }
                    case 4:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_message");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_message_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_message_click");
                            break;
                        }
                    case 5:
                        {
                            commands[i].image = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_exit");
                            commands[i].imageFocus = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_exit_focus");
                            commands[i].imageClick = GameCanvas.LoadImage("MainImages/GameScrs/Menus/img_exit_click");
                            break;
                        }
                }
                commands[i].actionListener = this;
                commands[i]._object = i;
                commands[i].actionId = i + 1;
                commands[i].w = commands[i].image.GetWidth();
                commands[i].h = commands[i].image.GetHeight();
            }
        }

        public void InitCmd()
        {
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i].x = x + 75 + 120 * i;
                commands[i].y = y + (h - 20 - commands[i].image.GetHeight());
            }
        }

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    {
                        indexSelect = (int)p;
                        Close();
                        ScreenManager.instance.panel.SetType(TabPanel.tabInventory.type);
                        ScreenManager.instance.panel.Show();
                        break;
                    }
                case 2:
                    {
                        indexSelect = (int)p;
                        Close();
                        ScreenManager.instance.gameScreen.ShowMenuOther();
                        break;
                    }
                case 3:
                    {
                        indexSelect = (int)p;
                        Close();
                        ScreenManager.instance.gameScreen.ShowMenuOption();
                        break;
                    }
                case 4:
                    {
                        indexSelect = (int)p;
                        Close();
                        InfoDlg.ShowWait();
                        Service.ClanAction(-1, -1, null);
                        break;
                    }
                case 5:
                    {
                        indexSelect = (int)p;
                        Close();
                        ScreenManager.instance.panel.SetType(TabPanel.tabChatGlobal.type);
                        ScreenManager.instance.panel.Show();
                        break;
                    }
                case 6:
                    {
                        indexSelect = (int)p;
                        Close();
                        DisplayManager.instance.dialog.SetInfo("Bạn có chắc chắn muốn thoát game không?", new Command("Có", this, 7, null), null, new Command("Không", DisplayManager.instance, 1, null));
                        break;
                    }
                case 7:
                    {
                        ScreenManager.instance.gameScreen.Logout();
                        break;
                    }
            }
        }

        public void Update()
        {
            if (!isClose)
            {
                if (y > yMin)
                {
                    int num = y - yMin >> 1;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    y -= num;
                }
            }
            else
            {
                if (y < yMax)
                {
                    int num = yMax - this.y >> 1;
                    if (num < 1)
                    {
                        num = 1;
                    }
                    y += num;
                }
                else
                {
                    isShow = false;
                }
            }
            InitCmd();
        }

        public void Paint(MyGraphics g)
        {
            if (!isShow)
            {
                return;
            }
            g.DrawImage(imgBar, x, y);
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i].Paint(g);
                if (i == 4 && ScreenManager.instance.panel.IsNewMessage() && GameCanvas.gameTick % 30 < 15)
                {
                    g.DrawImage(GameScreen.imgLight, commands[i].x + commands[i].w / 2, commands[i].y + commands[i].h / 2, StaticObj.VCENTER_HCENTER);
                }
                if (indexSelect == i)
                {
                    g.SetColor(Color.white);
                    g.drawRect(commands[i].x - 10, y + 15, commands[i].w + 20, 95);
                }
            }
        }

        public void PointerClicked(int x, int y)
        {
            if (x < this.x || x > this.x + w || y < this.y || y > this.y + h)
            {
                isClickOut = true;
                return;
            }
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i].PointerClicked(x, y);
            }
        }

        public void PointerReleased(int x, int y)
        {
            if (isClickOut && (x < this.x || x > this.x + w || y < this.y || y > this.y + h))
            {
                Close();
                return;
            }
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i].PointerReleased(x, y);
            }
        }

        public void PointerMove(int x, int y)
        {
            for (int i = 0; i < commands.Length; i++)
            {
                commands[i].PointerMove(x, y);
            }
        }

        public void Show()
        {
            isClose = false;
            indexSelect = -1;
            y = yMax;
            isShow = true;
        }

        public void Close()
        {
            isShow = false;
        }
    }
}
