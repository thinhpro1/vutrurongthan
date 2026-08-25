using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using UnityEngine;

namespace Assets.Scripts.Displays
{
    public class Dialog
    {
        public bool isShow;

        private string[] info;

        private Command cmdLeft;

        public Command cmdCenter;

        private Command cmdRight;

        private int w;

        private int h;

        private int x;

        private int y;

        private Image imgBgr;

        private long timeStart;

        private long timeRemaining;

        public Dialog()
        {
            imgBgr = GameCanvas.LoadImage("MainImages/Displays/Dialogs/img_bgr_dialog");
        }

        public void Paint(MyGraphics g)
        {
            if (!isShow)
            {
                return;
            }
            g.Reset();
            g.DrawImage(imgBgr, x, y);
            int hText = MyFont.text_white.GetHeight();
            int num = y + (h - info.Length * hText) / 2 - 10;
            for (int i = 0; i < info.Length; i++)
            {
                string text = info[i];
                if (i == info.Length - 1)
                {
                    text += " (" + timeRemaining + ")";
                }
                MyFont.text_white.DrawString(g, text, ScreenManager.instance.w / 2, num, 2);
                num += hText;
            }
            if (cmdLeft != null)
            {
                cmdLeft.Paint(g);
            }
            if (cmdCenter != null)
            {
                cmdCenter.Paint(g);
            }
            if (cmdRight != null)
            {
                cmdRight.Paint(g);
            }
        }

        public void KeyPress(KeyCode keyCode)
        {
            switch (keyCode)
            {
                case KeyCode.F2:
                    if (cmdRight != null)
                    {
                        cmdRight.PerformAction();
                    }
                    break;
                case KeyCode.F1:
                    if (cmdLeft != null)
                    {
                        cmdLeft.PerformAction();
                    }
                    break;
                case KeyCode.KeypadEnter:
                case KeyCode.Return:
                    if (cmdCenter != null)
                    {
                        cmdCenter.PerformAction();
                    }
                    break;
            }
        }

        public void PointerClicked(int x, int y)
        {
            if (cmdCenter != null && cmdCenter.PointerClicked(x, y))
            {
                return;
            }
            if (cmdLeft != null && cmdLeft.PointerClicked(x, y))
            {
                return;
            }
            if (cmdRight != null && cmdRight.PointerClicked(x, y))
            {
                return;
            }
        }

        public void PointerReleased(int x, int y)
        {
            if (cmdCenter != null && cmdCenter.PointerReleased(x, y))
            {
                return;
            }
            if (cmdLeft != null && cmdLeft.PointerReleased(x, y))
            {
                return;
            }
            if (cmdRight != null && cmdRight.PointerReleased(x, y))
            {
                return;
            }
        }

        public void PointerMove(int x, int y)
        {
            if (cmdCenter != null && cmdCenter.PointerMove(x, y))
            {
                return;
            }
            if (cmdLeft != null && cmdLeft.PointerMove(x, y))
            {
                return;
            }
            if (cmdRight != null && cmdRight.PointerMove(x, y))
            {
                return;
            }
        }

        public void Update()
        {
            if (!isShow)
            {
                return;
            }
            long now = Utils.CurrentTimeMillis();
            timeRemaining = (timeStart + 10000 - now) / 1000;
            if (timeRemaining <= 0)
            {
                Close();
            }
        }

        public void Close()
        {
            isShow = false;
        }

        public void SetInfo(string text, Command left, Command center, Command right)
        {
            SoundMn.OpenDialog();
            w = 800;
            info = MyFont.text_white.SplitFontArray(text, w - 100);
            h = 190;
            cmdLeft = left;
            cmdCenter = center;
            cmdRight = right;
            h = info.Length * MyFont.text_white.GetHeight() + 60;
            if (info.Length < 4)
            {
                h = 4 * MyFont.text_white.GetHeight() + 60;
            }
            y = GameCanvas.h - 30 - h;
            x = GameCanvas.w / 2 - w / 2;
            if (left != null)
            {
                cmdLeft.x = GameCanvas.w / 2 - cmdLeft.w - 20;
                cmdLeft.y = y + h - cmdLeft.h / 2 - 24;
            }
            if (right != null)
            {
                cmdRight.x = GameCanvas.w / 2 + 20;
                cmdRight.y = y + h - cmdRight.h / 2 - 24;
            }
            if (center != null)
            {
                cmdCenter.x = GameCanvas.w / 2 - center.w / 2;
                cmdCenter.y = y + h - cmdCenter.h / 2 - 24;
            }
            timeStart = Utils.CurrentTimeMillis();
            isShow = true;
        }
    }
}
