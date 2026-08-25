using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using System.Collections.Generic;

namespace Assets.Scripts.Displays
{
    public class ChatInfo
    {
        private static Image imgGoc;

        public int limitTime = 100;

        public int tick;

        public string content;

        public List<string> contents = new List<string>();

        public int sayWidth = 400;

        public string[] says;

        public int w;

        public int h;

        public ChatInfo()
        {

        }

        static ChatInfo()
        {
            imgGoc = GameCanvas.LoadImage("imgGoc.png");
        }

        public void Paint(MyGraphics g, int x, int y, int dir)
        {
            if (says != null && says.Length > 0)
            {
                PaintPopUp(g, x - w / 2 - 1, y - h - 40, w, h);
                g.DrawImage(x - dir * 20, y - 42, imgGoc, (dir != 1) ? 2 : 0, MyGraphics.TOP | MyGraphics.HCENTER);
                int heightText = MyFont.text_black.GetHeight();
                int dis = (h - heightText * says.Length - 5 * (says.Length - 1)) / 2;
                for (int i = 0; i < says.Length; i++)
                {
                    MyFont.text_black.DrawString(g, says[i], x, y - h - 40 + dis + (heightText + 5) * i, 2);
                }
            }
        }

        private void PaintPopUp(MyGraphics g, int x, int y, int w, int h)
        {
            g.SetColor(0);
            g.FillRect(x, y, w, h, 16);
            g.SetColor(16777215);
            g.FillRect(x + 2, y + 2, w - 4, h - 4, 14);
        }

        public void Update()
        {
            tick++;
            if (tick >= limitTime)
            {
                tick = 0;
                contents.RemoveAt(0);
                if (contents.Count != 0)
                {
                    content = contents[0];
                    GetInfo();
                }
            }
            if (contents.Count == 0)
            {
                tick = 0;
                says = null;
            }
        }

        public void AddChatInfo(string text)
        {
            if (contents.Count > 10)
            {
                contents.RemoveAt(0);
            }
            if (contents.Count > 10 && contents.Contains(text))
            {
                return;
            }
            contents.Add(text);
            if (contents.Count == 1)
            {
                content = contents[0];
                GetInfo();
            }
        }

        public void GetInfo()
        {
            sayWidth = 350;
            says = MyFont.text_black.SplitFontArray(content, sayWidth);
            w = sayWidth + 20;
            if (says.Length == 1)
            {
                w = MyFont.text_black.getWidthExactOf(says[0]) + 40;
                if (w < 200)
                {
                    w = 200;
                }
            }
            h = says.Length * (MyFont.text_black.GetHeight() + 5) + 15;
            if (h < 80)
            {
                h = 80;
            }
            limitTime = content.Length;
            if (limitTime < 200)
            {
                limitTime = 200;
            }
        }

    }
}
