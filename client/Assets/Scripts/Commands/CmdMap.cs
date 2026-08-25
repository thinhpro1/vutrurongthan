using Assets.Scripts.Actions;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdMap : Command
    {
        public int index;

        public string name;

        public string info;

        public CmdMap(IActionListener actionListener, int action, object p)
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
            g.DrawImage(Panel.imgGender[3], x + 15 + xBonus, y + 15, 0);
            int dis = 5;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                myFont = MyFont.text_mini_white;
            }
            int hText = myFont.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            myFont.DrawString(g, name, x + xBonus + 30 + Panel.imgBgrItem.GetWidth(), yText, 0);
            myFont.DrawString(g, info, x + xBonus + 30 + Panel.imgBgrItem.GetWidth(), yText + hText + dis, 0);
        }
    }
}
