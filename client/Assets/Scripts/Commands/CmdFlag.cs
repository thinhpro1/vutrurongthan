using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdFlag : Command
    {
        public string name;

        public string description;

        public CmdFlag(IActionListener actionListener, int action, object p)
        {
            this.image = Panel.imgItemShop;
            this.imageClick = Panel.imgItemShopClick;
            this.imageFocus = Panel.imgItemShopFocus;
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            w = image.GetWidth();
            h = image.GetHeight();
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public override void Paint(MyGraphics g)
        {
            int xBonus = 0;
            if (isClick)
            {
                xBonus += 10;
                g.DrawImage(Panel.imgItemShopClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? Panel.imgItemShop : Panel.imgItemShopFocus, x, y);
            }
            int dis = 5;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                myFont = MyFont.text_mini_white;
            }
            int hText = myFont.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            myFont.DrawString(g, name, x + xBonus + 15, yText, 0);
            myFont.DrawString(g, description, x + xBonus + 15, yText + hText + dis, 0);
        }
    }
}
