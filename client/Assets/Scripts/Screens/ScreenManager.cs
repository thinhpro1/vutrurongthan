using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class ScreenManager
    {
        public static ScreenManager instance = new ScreenManager();

        public MyScreen currentScreen;

        public CreatePlayerScreen createPlayerScreen;

        public RegisterScreen registerScreen;

        public LoginScreen loginScreen;

        public ServerListScreen serverListScreen;

        public GameScreen gameScreen;

        public Panel panel;

        public int w;

        public int h;

        private Image imgWait;

        private Image[] imgLoading;

        private int tickLoading;

        private long timeLoading;

        private int cmxBgr;

        private Image imgMay;

        private Image imgMay1;

        private Image imgBgr;

        public Image imgLogo;

        public ScreenManager()
        {
            w = Screen.width;
            h = Screen.height;
            imgLoading = new Image[8];
            for (int i = 0; i < imgLoading.Length; i++)
            {
                imgLoading[i] = GameCanvas.LoadImage("MainImages/ScreenManagers/img_loading_" + i);
            }
            imgWait = GameCanvas.LoadImage("MainImages/ScreenManagers/img_wait");
            imgMay = GameCanvas.LoadImage("MainImages/ScreenManagers/img_may");
            imgMay1 = GameCanvas.LoadImage("MainImages/ScreenManagers/img_may1");
            imgBgr = GameCanvas.LoadImage("MainImages/ScreenManagers/img_bgr");
            imgLogo = GameCanvas.LoadImage("MainImages/ScreenManagers/img_logo");
        }

        public void Init()
        {
            w = Screen.width;
            h = Screen.height;
            currentScreen = new SplashScreen(instance);
            loginScreen = new LoginScreen(instance);
            gameScreen = new GameScreen(instance);
            panel = new Panel(instance);
        }

        public void PaintLoading(MyGraphics g)
        {
            g.Reset();
            int x = w / 2;
            int y = h / 2;
            int anchor = MyGraphics.HCENTER | MyGraphics.VCENTER;
            g.DrawImage(imgWait, x, y, anchor);
            long now = Utils.CurrentTimeMillis();
            if (now - timeLoading > 25)
            {
                if (tickLoading < imgLoading.Length - 1)
                {
                    tickLoading++;
                }
                else
                {
                    tickLoading = 0;
                }
                timeLoading = now;
            }
            g.DrawImage(imgLoading[tickLoading], x, y, anchor);
        }

        public void PaintBackground(MyGraphics g)
        {
            if (Player.isLoadingMap)
            {
                return;
            }
            g.Reset();
            bool oldSRGBWrite = GL.sRGBWrite;
            GL.sRGBWrite = true;
            Graphics.DrawTexture(new Rect(0, 0, w, h), imgBgr.texture);
            GL.sRGBWrite = oldSRGBWrite;
            cmxBgr += 10;
            int wB = -(cmxBgr >> 3);
            while (wB < w)
            {
                if (wB > -1440)
                {
                    g.DrawImage(imgMay, wB, h, StaticObj.BOTTOM_LEFT);
                }
                wB += 1440;
            }
            wB = -(cmxBgr >> 4);
            while (wB < w)
            {
                if (wB > -873)
                {
                    g.DrawImage(imgMay1, wB, 0, StaticObj.TOP_LEFT);
                }
                wB += 872;
            }
            MyFont.text_white.DrawString(g, ServerManager.LINKWEB, w - 2, 0, 1);
            MyFont.text_white.DrawString(g, "version " + ServerManager.VERSION, w - 2, 30, 1);
        }

    }
}
