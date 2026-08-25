using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Frames;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System.Collections.Generic;

namespace Assets.Scripts.Models
{
    public class TextGlobal
    {
        private int x;

        private int y;

        private string text;

        private int w;

        private int h;

        public List<CmdMessage> infos = new List<CmdMessage>();

        private int xText;

        private long last;

        private static Image imgBgr = GameCanvas.LoadImage("MainImages/GameScrs/img_bgr_chat_global.png");

        public TextGlobal()
        {
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
            x = (ScreenManager.instance.w - w) / 2;
            y = 125;
            xText = x + 80;
        }

        public void Paint(MyGraphics g)
        {
            g.Reset();
            try
            {
                if (infos.Count > 0)
                {
                    g.DrawImage(imgBgr, x, y);
                    CmdMessage info = infos[0];
                    if (info.player.id == 0)
                    {
                        g.DrawImage(GameScreen.imgGender[4], x + 15, y + 9);
                    }
                    else if (info.player.head != null && info.player.head.template.chat != -1)
                    {
                        GraphicManager.instance.Draw(g, info.player.head.template.chat, x + 15, y + 62, 0, StaticObj.BOTTOM_LEFT);
                    }

                    MyFont.text_yellow.DrawString(g, info.player.name, x + 80, y + 8, 0, MyFont.text_grey);
                    g.SetClip(x + 80, y, w - 85, h);
                    MyFont.text_mini_white.DrawString(g, info.message, xText, y + 38, 0, MyFont.text_mini_grey);
                }
            }
            catch
            {
            }
            g.Reset();
        }



        public void update()
        {
            if (infos.Count > 0)
            {
                long now = Utils.CurrentTimeMillis();
                if (last == 0)
                {
                    last = now;
                }
                if (now - last > 5000)
                {
                    int wText = MyFont.text_white.GetWidth(infos[0].message);
                    if (wText > w - 85)
                    {
                        xText -= 4;
                        if (xText + wText < x + 150)
                        {
                            xText = x + 80;
                            infos.RemoveAt(0);
                            last = 0;
                        }
                    }
                    else
                    {
                        xText = x + 80;
                        infos.RemoveAt(0);
                        last = 0;
                    }
                }
            }

        }
    }
}
