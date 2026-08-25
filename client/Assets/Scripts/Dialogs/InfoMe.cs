using Assets.Scripts.Displays;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System.Collections.Generic;

namespace Assets.Scripts.Dialogs
{
    public class InfoMe
    {
        public static List<InfoItem> infoWait = new List<InfoItem>();

        public static InfoItem info;

        public static int p1 = 5;

        public static int p2;

        public static int p3;

        public static int x;

        public static int strWidth;

        public static int limLeft = 2;

        public static int hI = 50;

        public static int TYPE_ERROR = 0;

        public static int TYPE_RECIVE_ITEM = 1;

        public static void Paint(MyGraphics g)
        {
            g.SetClip(0, -200, GameCanvas.w, 200 + GameCanvas.h);
            limLeft = 264;
            int num = 260;
            int num2 = GameCanvas.h - 180;
            int num3 = GameCanvas.w - 2 * num;
            if (info != null && (!DisplayManager.instance.dialog.isShow || DisplayManager.instance.dialog.cmdCenter == null))
            {
                g.SetClip(0, 0, GameCanvas.w, GameCanvas.h);
                g.SetColor(0, 0.4f);
                g.FillRect(num - 2, num2, num3 + 2, hI, 8);
                g.SetClip(num, num2, num3, hI);
                info.f.DrawString(g, info.info, x, num2 + 10, 0);
            }
        }

        public static void update()
        {
            if (p1 == 0)
            {
                x += (limLeft - x) / 3;
                if (x - limLeft < 3)
                {
                    x = limLeft + 2;
                    p1 = 2;
                    p2 = 0;
                }
            }
            else if (p1 == 2)
            {
                p2++;
                if (p2 > info.speed)
                {
                    p1 = 3;
                    p2 = 0;
                }
            }
            else if (p1 == 3)
            {
                if (x + strWidth < limLeft + GameCanvas.w - 20)
                {
                    x -= 6;
                }
                else
                {
                    x -= 2;
                }
                if (x + strWidth < limLeft)
                {
                    p1 = 4;
                    p2 = 0;
                }
            }
            else if (p1 == 4)
            {
                p2++;
                if (p2 > 10)
                {
                    p1 = 5;
                    p2 = 0;
                }
            }
            else
            {
                if (p1 != 5)
                {
                    return;
                }
                if (infoWait.Count > 0)
                {
                    InfoItem infoItem = infoWait[0];
                    infoWait.RemoveAt(0);
                    if (info == null || !infoItem.info.Equals(info.info))
                    {
                        info = infoItem;
                        strWidth = info.f.GetWidth(info.info);
                        p1 = (p2 = 0);
                        x = GameCanvas.w;
                    }
                }
                else
                {
                    info = null;
                }
            }
        }

        private static bool isCanMergeString(string s, int type)
        {
            if (info != null && info.info != null && s.Equals(info.info))
            {
                return true;
            }
            if (infoWait.Count > 0 && s.Equals(infoWait[infoWait.Count - 1].info))
            {
                return true;
            }
            if (s.Length < 8)
            {
                return false;
            }
            if (type == TYPE_RECIVE_ITEM)
            {
                if (info != null && info.info != null && p1 < 3 && info.info.Length >= 8)
                {
                    string text = s.Substring(0, 8);
                    string value = info.info.Substring(0, 8);
                    if (text.Equals(value))
                    {
                        int i;
                        for (i = 7; i < s.Length && i < info.info.Length && (s[i] < '0' || s[i] > '9') && s[i] == info.info[i]; i++)
                        {
                        }
                        string text2 = s.Substring(i);
                        InfoItem infoItem = info;
                        infoItem.info = infoItem.info.Replace(".", "") + ", " + text2;
                        p1 = 2;
                        p2 = 0;
                        return true;
                    }
                }
                if (infoWait.Count > 0)
                {
                    if (infoWait[infoWait.Count - 1].info.Length >= 8)
                    {
                        string text3 = s.Substring(0, 8);
                        string value2 = infoWait[infoWait.Count - 1].info.Substring(0, 8);
                        if (text3.Equals(value2))
                        {
                            int j;
                            for (j = 7; j < s.Length && j < infoWait[infoWait.Count - 1].info.Length && (s[j] < '0' || s[j] > '9') && s[j] == infoWait[infoWait.Count - 1].info[j]; j++)
                            {
                            }
                            string text4 = s.Substring(j);
                            infoWait[infoWait.Count - 1].info = infoWait[infoWait.Count - 1].info.Replace(".", "") + ", " + text4;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public static void addInfo(string s, int type)
        {
            if (!isCanMergeString(s, type))
            {
                if (infoWait.Count > 10)
                {
                    infoWait.RemoveAt(0);
                }
                MyFont font = MyFont.text_white;
                if (type == TYPE_ERROR)
                {
                    font = MyFont.text_red;
                }
                else if (type == TYPE_RECIVE_ITEM)
                {
                    font = MyFont.text_yellow;
                }
                infoWait.Add(new InfoItem(s, font, 20));
            }
        }

        public static bool isEmpty()
        {
            return p1 == 5 && infoWait.Count == 0;
        }
    }
}
