using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Dialogs
{
    public class InfoDlg
    {
        public static bool isShow;

        private static string title;

        private static string subtitle;

        public static int delay;

        public static bool isLock;

        public static int x;

        public static int y;

        public static int w;

        public static int h;

        static InfoDlg()
        {
            w = 300;
            h = 100;
            x = (GameCanvas.w - w) / 2;
            y = 15;
        }

        public static void Show(string title, string subtitle, int delay)
        {
            if (title != null)
            {
                isShow = true;
                InfoDlg.title = title;
                InfoDlg.subtitle = subtitle;
                InfoDlg.delay = delay;
            }
        }

        public static void ShowWait()
        {
            Show(PlayerText.PLEASEWAIT, null, 1000);
            isLock = true;
        }

        public static void Paint(MyGraphics g)
        {
            if (isShow && (!isLock || delay <= 4990))
            {

                if (isLock)
                {
                    ScreenManager.instance.PaintLoading(g);
                    return;
                }
                g.SetColor(0, 0.5f);
                g.FillRect(x, y, w, h, 16);

                int hText = MyFont.text_big_white.GetHeight();

                if (subtitle != null)
                {
                    int yText = y + (h - 2 * hText) / 2;
                    MyFont.text_big_white.DrawString(g, title, x + w / 2, yText + 2, 2);
                    MyFont.text_big_white.DrawString(g, subtitle, x + w / 2, yText + hText, 2);
                }
                else
                {
                    MyFont.text_big_white.DrawString(g, title, x + w / 2, y + (h - hText) / 2, 2);
                }
            }
        }

        public static bool IsLock()
        {
            return isShow && isLock;
        }

        public static void Update()
        {
            if (delay > 0)
            {
                delay--;
                if (delay == 0)
                {
                    Hide();
                }
            }
        }

        public static void Hide()
        {
            title = string.Empty;
            subtitle = null;
            isLock = false;
            delay = 0;
            isShow = false;
        }
    }
}
