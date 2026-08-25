using System;
using System.Collections.Generic;
using Assets.Scripts.Games;
using Assets.Scripts.Models;
using Assets.Scripts.GraphicCustoms;
using UnityEngine;
using Assets.Scripts.Screens;
using Assets.Scripts.Commands;

namespace Assets.Scripts.Dialogs
{
    public class MenuCommad : Menu
    {

        public MenuCommad()
        {
        }

        public override void StartAt(List<CmdMenu> commands)
        {
            if (commands.Count == 0)
            {
                return;
            }
            StartAt(commands, string.Empty, 0);
        }

        public override void StartAt(List<CmdMenu> commands, string title, int iconId)
        {
            this.commands = commands;
            this.iconId = iconId;
            this.title = title;
            indexSelect = Main.isPC ? 0 : (-1);
            int distance = 10;
            int wMenu = commands.Count * CmdMenu.imgMenu.GetWidth() + (commands.Count - 1) * distance;
            x = (GameCanvas.w - wMenu) / 2;
            if (x < distance)
            {
                x = distance;
            }
            y = GameCanvas.h - CmdMenu.imgMenu.GetHeight() - 20;
            for (int i = 0; i < this.commands.Count; i++)
            {
                this.commands[i].x = x + (this.commands[i].w + distance) * i;
                this.commands[i].y = y;
            }
            xScroll = x;
            yScroll = y;
            wScroll = wMenu;
            if (wScroll > GameCanvas.w - distance)
            {
                wScroll = GameCanvas.w - distance;
            }
            hScroll = CmdMenu.imgMenu.GetHeight();
            cmxLim = wMenu - GameCanvas.w + distance * 2;
            if (cmxLim < 0)
            {
                cmxLim = 0;
            }
            cmx = cmxTo = 0;
            wPopup = 960;
            subTitle = MyFont.text_white.SplitFontArray(title, wPopup - 100);
            if (subTitle.Length == 1)
            {
                wPopup = MyFont.text_white.GetWidth(title) + 100;
                if (wPopup < 640)
                {
                    wPopup = 640;
                }
            }
            hPopup = subTitle.Length * MyFont.text_white.GetHeight() + (subTitle.Length - 1) * 10 + 50;
            int h = GameCanvas.imgPopups[0].GetWidth();
            if (hPopup % h != 0)
            {
                hPopup = (hPopup / h + 1) * h;
            }
            xPopup = (GameCanvas.w - wPopup) / 2;
            yPopup = y - 10 - hPopup;
            isPointerDownInScroll = false;
            isShow = true;
        }

        public override void Paint(MyGraphics g)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            g.Reset();
            if (title != string.Empty)
            {
                GraphicManager.instance.Draw(g, iconId, xPopup + 20, yPopup, 0, StaticObj.BOTTOM_LEFT);
                GameCanvas.PaintPopup(g, xPopup, yPopup, wPopup, hPopup);
                for (int i = 0; i < subTitle.Length; i++)
                {
                    MyFont.text_white.DrawString(g, subTitle[i], GameCanvas.w / 2, yPopup + 25 + i * (MyFont.text_white.GetHeight() + 10), 2);
                }
            }
            g.Translate(-cmx, 0);
            for (int i = 0; i < commands.Count; i++)
            {
                commands[i].Paint(g, indexSelect == i);
            }
            g.Reset();
        }

        public override void Update()
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            if (isPointerDownInScroll && pointerX != xPointerDown)
            {
                cmxTo -= pointerX - xPointerDown;
                xPointerDown = pointerX;
            }
            if (!isPointerDownInScroll)
            {
                if (cmxTo < 0)
                {
                    cmxTo = 0;
                }
                if (cmxTo > cmxLim)
                {
                    cmxTo = cmxLim;
                }
            }
            if (cmx > cmxTo)
            {
                int num = cmx - cmxTo >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmx -= num;
            }
            else if (cmx < cmxTo)
            {
                int num = cmxTo - cmx >> 1;
                if (num < 1)
                {
                    num = 1;
                }
                cmx += num;
            }
        }

        public override void KeyPress(KeyCode keyCode)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            bool flag = false;
            switch (keyCode)
            {
                case KeyCode.F2:
                    {
                        Close();
                        break;
                    }
                case KeyCode.Tab:
                case KeyCode.UpArrow:
                case KeyCode.RightArrow:
                    {
                        if (indexSelect < commands.Count - 1)
                        {
                            indexSelect++;
                        }
                        else
                        {
                            indexSelect = 0;
                        }
                        flag = true;
                        break;
                    }
                case KeyCode.DownArrow:
                case KeyCode.LeftArrow:
                    {
                        if (indexSelect > 0)
                        {
                            indexSelect--;
                        }
                        else
                        {
                            indexSelect = commands.Count - 1;
                        }
                        flag = true;
                        break;
                    }
                case KeyCode.F1:
                case KeyCode.KeypadEnter:
                case KeyCode.Return:
                    {
                        if (indexSelect >= 0 && indexSelect < commands.Count)
                        {
                            Close();
                            commands[indexSelect].PerformAction();
                        }
                        break;
                    }
            }
            if (flag && commands.Count > 0)
            {
                cmxTo = (indexSelect + 1) * (commands[0].w + 10) - GameCanvas.w / 2;
            }
        }

        public override void PointerClicked(int x, int y)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
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
                if (commands[i].PointerClicked(x + cmx, y))
                {
                    indexSelect = i;
                    return;
                }
            }
        }

        public override void PointerReleased(int x, int y)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            if ((x < xScroll || x > xScroll + wScroll || y < yScroll || y > yScroll + hScroll) && !isPointerDownInScroll)
            {
                Close();
                return;
            }
            for (int i = 0; i < commands.Count; i++)
            {
                if (commands[i].PointerReleased(x + cmx, y))
                {
                    Close();
                    return;
                }
            }
            isPointerDownInScroll = false;
        }

        public override void PointerMove(int x, int y)
        {
            if (ScreenManager.instance.gameScreen.isPaintCombine)
            {
                return;
            }
            pointerX = x;
            pointerY = y;
            for (int i = 0; i < commands.Count; i++)
            {
                if (commands[i].PointerMove(x + cmx, y))
                {
                    indexSelect = i;
                    return;
                }
            }
        }

        public override void Close()
        {
            InfoDlg.Hide();
            isPointerDownInScroll = false;
            SoundMn.ButtonClose();
            isShow = false;
        }
    }


}
