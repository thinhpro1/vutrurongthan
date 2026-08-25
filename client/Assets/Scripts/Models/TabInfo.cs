using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Models
{
    public class TabInfo : IActionListener
    {
        public bool isShow;

        private int x;

        private int y;

        private int w;

        private int h;

        private int xScroll;

        private int yScroll;

        private int wScroll;

        private int hScroll;

        private int cmyTo;

        private int cmy;

        private int cmdy;

        private int cmvy;

        private int cmyLim;

        private bool isPointerDownInScroll;

        private int xPointerDown;

        private int yPointerDown;

        private bool isPointerUp;

        private int pointerX;

        private int pointerY;

        private string[] says;

        private string title;

        private List<Command> commands;

        private int iconId;

        private Command cmdClose;

        private int star;

        private int star_use;

        private Image imgBgr;

        private Image imgStar;

        public static Image imgStarUse;

        public TabInfo()
        {
            commands = new List<Command>();
            cmdClose = new Command("MainImages/Panels/TabInfos/btn_close", "MainImages/Panels/TabInfos/btn_close_focus", "MainImages/Panels/TabInfos/btn_close_click", this, 1, null);
            imgBgr = GameCanvas.LoadImage("MainImages/Panels/TabInfos/img_tab_info_item");
            imgStar = GameCanvas.LoadImage("MainImages/Panels/TabInfos/img_star");
            imgStarUse = GameCanvas.LoadImage("MainImages/Panels/TabInfos/img_star_use");
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
            x = (GameCanvas.w - w) / 2;
            y = (GameCanvas.h - h) / 2 - 30;
            cmdClose.x = x + w - cmdClose.w - 15;
            cmdClose.y = y + 15;
        }

        public void Paint(MyGraphics g)
        {
            if (!isShow)
            {
                return;
            }
            g.DrawImage(imgBgr, x, y);
            cmdClose.Paint(g);
            int xIcon = 0;
            if (iconId != -1)
            {
                xIcon += 30;
                GraphicManager.instance.Draw(g, iconId, x + 40, y + 40, 0);
            }
            MyFont.text_big_green.DrawString(g, title, x + 40 + xIcon, y + 20, 0);
            for (int i = 0; i < this.commands.Count; i++)
            {
                commands[i].Paint(g);
            }
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);

            int yText = yScroll + 5 - MyFont.text_black.GetHeight();

            if (this.star > 0)
            {
                int w_star = imgStarUse.GetWidth();
                for (int i = 0; i < this.star; i++)
                {
                    if (i < this.star_use)
                    {
                        g.DrawImage(imgStarUse, xScroll + 20 + (w_star + 10) * i, yText + MyFont.text_black.GetHeight());
                    }
                    else
                    {
                        g.DrawImage(imgStar, xScroll + 20 + (w_star + 10) * i, yText + MyFont.text_black.GetHeight());
                    }
                }
                yText += imgStarUse.GetHeight() + 5;
            }
            for (int i = 0; i < says.Length; i++)
            {
                MyFont myFont = GetFont(says[i].Substring(0, 2));
                string[] vs = myFont.SplitFontArray(says[i].Substring(2), wScroll - 20);
                foreach (string info in vs)
                {
                    myFont.DrawString(g, info, xScroll + 20, yText += MyFont.text_white.GetHeight(), 0);
                }
            }
            cmyLim = yText - (yScroll + 5 - MyFont.text_white.GetHeight()) - hScroll + 5;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            g.Reset();
            if (cmyLim > 0)
            {
                Panel.PaintScroll(g, xScroll + wScroll + 5, yScroll + 20, 1, hScroll - 40, cmyLim + hScroll, hScroll, cmy);
            }
        }

        private MyFont GetFont(string id)
        {
            MyFont myfont = MyFont.text_white;

            if (id.Length > 2)
            {
                id = id.Substring(0, 2);
            }
            switch (id)
            {
                case "n0":
                    myfont = MyFont.text_white;
                    break;
                case "n1":
                    myfont = MyFont.text_yellow;
                    break;
                case "n2":
                    myfont = MyFont.text_black;
                    break;
                case "n3":
                    myfont = MyFont.text_red;
                    break;
                case "n4":
                    myfont = MyFont.text_grey;
                    break;
                case "n5":
                    myfont = MyFont.text_brown;
                    break;
                case "n6":
                    myfont = MyFont.text_blue;
                    break;
                case "n7":
                    myfont = MyFont.text_green;
                    break;
                case "m0":
                    myfont = MyFont.text_mini_white;
                    break;
                case "m1":
                    myfont = MyFont.text_mini_yellow;
                    break;
                case "m2":
                    myfont = MyFont.text_mini_black;
                    break;
                case "m3":
                    myfont = MyFont.text_mini_red;
                    break;
                case "m4":
                    myfont = MyFont.text_mini_grey;
                    break;
                case "m5":
                    myfont = MyFont.text_mini_brown;
                    break;
                case "m6":
                    myfont = MyFont.text_mini_blue;
                    break;
                case "m7":
                    myfont = MyFont.text_mini_green;
                    break;
                case "b0":
                    myfont = MyFont.text_big_white;
                    break;
                case "b1":
                    myfont = MyFont.text_big_yellow;
                    break;
                case "b2":
                    myfont = MyFont.text_big_black;
                    break;
                case "b3":
                    myfont = MyFont.text_big_red;
                    break;
                case "b4":
                    myfont = MyFont.text_big_grey;
                    break;
                case "b5":
                    myfont = MyFont.text_big_brown;
                    break;
                case "b6":
                    myfont = MyFont.text_big_blue;
                    break;
                case "b7":
                    myfont = MyFont.text_big_green;
                    break;
            }

            return myfont;
        }


        public void Update()
        {
            if (isPointerDownInScroll && pointerY != yPointerDown)
            {
                cmyTo -= pointerY - yPointerDown;
                yPointerDown = pointerY;
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
        }

        public void ShowInfo(string title, string info, int iconId, int star, int star_use, params Command[] commands)
        {
            this.title = title;
            this.iconId = iconId;
            this.star = star;
            this.star_use = star_use;
            this.commands.Clear();
            this.commands.AddRange(commands);
            for (int i = 0; i < this.commands.Count; i++)
            {
                this.commands[i].x = x + w - 15 - this.commands[i].w - (this.commands[i].w + 10) * i;
                this.commands[i].y = y + h - 15 - this.commands[i].h;
            }
            says = info.Split('|');

            xScroll = x + 20;
            wScroll = w - 40;
            yScroll = y + 60;
            hScroll = h - 120;

            cmyLim = says.Length * MyFont.text_white.GetHeight() - hScroll + (this.star == 0 ? 0 : 30);
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = (cmyTo = 0);

            isShow = true;
        }

        public void Close()
        {
            isShow = false;
        }

        public void PointerClicked(int x, int y)
        {
            if (x <= this.x || x >= this.x + w || y >= this.y + h || y <= this.y)
            {
                Close();
                return;
            }
            if (cmdClose.PointerClicked(x, y))
            {
                return;
            }
            foreach (var command in commands)
            {
                if (command.PointerClicked(x, y))
                {
                    return;
                }
            }
            if (x >= xScroll && x <= xScroll + wScroll && y >= yScroll && y <= yScroll + hScroll)
            {
                isPointerDownInScroll = true;
            }
            xPointerDown = x;
            yPointerDown = y;
        }

        public void PointerReleased(int x, int y)
        {
            isPointerDownInScroll = false;
            if (cmdClose.PointerReleased(x, y))
            {
                return;
            }
            foreach (var command in commands)
            {
                if (command.PointerReleased(x, y))
                {
                    return;
                }
            }
        }

        public void PointerMove(int x, int y)
        {
            pointerX = x;
            pointerY = y;
            if (cmdClose.PointerMove(x, y))
            {
                return;
            }
            foreach (var command in commands)
            {
                if (command.PointerMove(x, y))
                {
                    return;
                }
            }
        }

        public void KeyPress(KeyCode keyCode)
        {
            if (keyCode == KeyCode.F1)
            {
                commands[commands.Count - 1].PerformAction();
            }
            else if (keyCode == KeyCode.F2)
            {
                Close();
            }
        }

        public void PointerScroll(int a)
        {
            if (a == 0 || cmyLim == 0)
            {
                return;
            }
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

        public void Perform(int idAction, object p)
        {
            switch (idAction)
            {
                case 1:
                    Close();
                    break;
            }
        }
    }
}
