using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Dialogs
{
    public class MenuNpc : Menu
    {
        public static Image imgBgr;
        public static Image[] imgClose;

        private Command cmdClose;

        static MenuNpc()
        {
            imgBgr = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/img_bgr_menu");
            imgClose = new Image[3];
            imgClose[0] = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/btn_close");
            imgClose[1] = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/btn_close_focus");
            imgClose[2] = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/btn_close_click");
        }

        public MenuNpc()
        {
            cmdClose = new Command(imgClose[0], imgClose[1], imgClose[2], this, 1, null);
            w = imgBgr.w;
            h = imgBgr.h;
            x = 2;
            y = Screen.height - 2 - h;
            cmdClose.x = x + w + 5;
            cmdClose.y = y + h - cmdClose.h;
        }

        public override void Paint(MyGraphics g)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            g.Reset();
            g.DrawImage(imgBgr, x, y);
            cmdClose.Paint(g);
            g.SetClip(xScroll, yScroll, wScroll, hScroll);
            g.Translate(0, -cmy);
            for (int i = 0; i < commands.Count; i++)
            {
                commands[i].Paint(g, indexSelect == i);
            }
            g.Reset();
            if (title != string.Empty)
            {
                //GraphicManager.instance.Draw(g, iconId, xPopup + 20, yPopup, 0, StaticObj.BOTTOM_LEFT);
                GameCanvas.PaintPopup(g, xPopup, yPopup, wPopup, hPopup);
                for (int i = 0; i < subTitle.Length; i++)
                {
                    MyFont.text_white.DrawString(g, subTitle[i], xPopup + 20, yPopup + 25 + i * (MyFont.text_white.GetHeight() + 10), 0);
                }
            }
        }

        public override void Update()
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
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
            if (cmy > cmyTo)
            {
                int num = cmy - cmyTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmy -= num;
            }
            else if (cmy < cmyTo)
            {
                int num = cmyTo - cmy >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmy += num;
            }
        }

        public override void StartAt(List<CmdMenu> cmdMenus)
        {
            if (cmdMenus.Count == 0)
            {
                return;
            }
            StartAt(cmdMenus, string.Empty, 0);
        }

        public override void StartAt(List<CmdMenu> commands, string title, int iconId)
        {
            this.commands = commands;
            this.iconId = iconId;
            this.title = title;
            indexSelect = -1;
            xScroll = x + 15;
            yScroll = y + 15;
            wScroll = w - 30;
            hScroll = this.h - 30;
            int distance = 20;
            int yMax = yScroll;
            for (int i = 0; i < this.commands.Count; i++)
            {
                this.commands[i].x = xScroll;
                this.commands[i].y = yMax;
                this.commands[i].w = imgBgr.w - 30;
                this.commands[i].isMini = true;
                string[] vs = MyFont.text_white.SplitFontArray(this.commands[i].caption, this.commands[i].w - 40);
                if (vs.Length > 1)
                {
                    this.commands[i].h = vs.Length * MyFont.text_white.GetHeight() + (vs.Length - 1) * 5;
                }
                else
                {
                    this.commands[i].h = MyFont.text_white.GetHeight();
                }
                yMax += this.commands[i].h + distance;
            }
            cmyLim = yMax - 5 - hScroll - yScroll;
            if (cmyLim < 0)
            {
                cmyLim = 0;
            }
            cmy = cmyTo = 0;
            wPopup = 800;
            subTitle = MyFont.text_white.SplitFontArray(title, wPopup - 40);
            if (subTitle.Length == 1)
            {
                wPopup = MyFont.text_white.GetWidth(title) + 60;
                if (wPopup < this.w)
                {
                    wPopup = this.w;
                }
            }
            else if (subTitle.Length > 1)
            {
                int maxSize = MyFont.text_white.GetWidth(subTitle[0]) + 60;
                for (int i = 1; i < subTitle.Length; i++)
                {
                    int width = MyFont.text_white.GetWidth(subTitle[i]) + 60;
                    if (maxSize < width)
                    {
                        maxSize = width;
                    }
                }
                wPopup = maxSize;
                if (wPopup < this.w)
                {
                    wPopup = this.w;
                }
            }
            hPopup = subTitle.Length * MyFont.text_white.GetHeight() + (subTitle.Length - 1) * 10 + 50;
            int h = GameCanvas.imgPopups[0].GetWidth();
            if (hPopup % h != 0)
            {
                hPopup = (hPopup / h + 1) * h;
            }
            xPopup = x;
            yPopup = y - hPopup - 10;
            isPointerDownInScroll = false;
            isShow = true;
        }

        public override void KeyPress(KeyCode key)
        {

        }

        public override void PointerReleased(int x, int y)
        {
            if (cmdClose.PointerReleased(x, y))
            {
                return;
            }
            for (int i = 0; i < commands.Count; i++)
            {
                if (commands[i].PointerReleased(x, y + cmy) && commands[i].isActiveAction)
                {
                    Close();
                    return;
                }
            }
            isPointerDownInScroll = false;
        }

        public override void PointerClicked(int x, int y)
        {
            if (cmdClose.PointerClicked(x, y))
            {
                return;
            }
            if (x >= xScroll && x <= xScroll + wScroll && y >= yScroll && y <= yScroll + hScroll)
            {
                isPointerDownInScroll = true;
            }
            xPointerDown = x;
            yPointerDown = y;
            for (int i = 0; i < commands.Count; i++)
            {
                if (commands[i].PointerClicked(x, y + cmy))
                {
                    indexSelect = i;
                    return;
                }
            }
        }

        public override void PointerMove(int x, int y)
        {
            if (cmdClose.PointerMove(x, y))
            {
                return;
            }
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            pointerX = x;
            pointerY = y;
            for (int i = 0; i < commands.Count; i++)
            {
                if (commands[i].PointerMove(x, y + cmy))
                {
                    indexSelect = i;
                    return;
                }
            }
        }

        public override void Close()
        {
            InfoDlg.Hide();
            SoundMn.ButtonClose();
            isShow = false;
        }

    }
}
