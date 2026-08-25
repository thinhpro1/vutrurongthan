using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public abstract class MyScreen
    {

        public ScreenManager screenManager;

        public MyScreen(ScreenManager screenManager)
        {
            this.screenManager = screenManager;
        }

        public abstract void Update();

        public abstract void Paint(MyGraphics g);

        public virtual void SwitchToMe()
        {
            ScreenManager.instance.currentScreen = this;
            SoundMn.stopAll();
        }

        public abstract void KeyPress(KeyCode keyCode);

        public abstract void PointerClicked(int x, int y);

        public abstract void PointerReleased(int x, int y);

        public abstract void PointerMove(int x, int y);
    }
}
