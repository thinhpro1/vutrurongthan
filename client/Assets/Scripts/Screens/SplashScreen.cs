using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class SplashScreen : MyScreen
    {
        public SplashScreen(ScreenManager screenManager) : base(screenManager) { }


        public override void Update()
        {
        }

        public override void Paint(MyGraphics g)
        {
            g.SetColor(0);
            g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
            ScreenManager.instance.PaintLoading(g);
        }

        public override void KeyPress(KeyCode keyCode)
        {
        }

        public override void PointerClicked(int x, int y)
        {
        }

        public override void PointerReleased(int x, int y)
        {
        }

        public override void PointerMove(int x, int y)
        {
        }
    }
}
