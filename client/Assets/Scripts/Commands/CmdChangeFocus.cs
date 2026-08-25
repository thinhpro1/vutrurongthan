using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;

namespace Assets.Scripts.Commands
{
    public class CmdChangeFocus : Command
    {
        public long timeClick;

        public CmdChangeFocus(string image, string imageFocus, string imageClick, IActionListener actionListener, int action, object p)
        {
            this.image = GameCanvas.LoadImage(image);
            this.imageFocus = GameCanvas.LoadImage(imageFocus);
            this.imageClick = GameCanvas.LoadImage(imageClick);
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            if (this.image != null)
            {
                w = this.image.GetWidth();
                h = this.image.GetHeight();
            }
            else
            {
                w = 90;
                h = 90;
            }
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public override void Paint(MyGraphics g)
        {
            base.Paint(g);
            if (isShow && isClick)
            {
                long now = Utils.CurrentTimeMillis();
                if (now - timeClick > 1000)
                {
                    isClick = false;
                    ScreenManager.instance.panel.SetType(TabPanel.tabPlayerInMap.type);
                    ScreenManager.instance.panel.Show();
                }
            }
        }

        public override bool PointerClicked(int x, int y)
        {
            if (!isShow)
            {
                isClick = false;
                isFocus = false;
                return false;
            }
            if (x >= this.x && x <= this.x + w
                && y >= this.y && y <= this.y + h)
            {
                if (!isClick)
                {
                    timeClick = Utils.CurrentTimeMillis();
                }
                isClick = true;
                return true;
            }
            else
            {
                isClick = false;
            }
            return false;
        }
    }
}
