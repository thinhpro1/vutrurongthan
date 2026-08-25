using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdArea : Command
    {
        public int areaId;

        public int numPlayer;

        public int maxPlayer;

        public int numTeam;

        public bool isColorRed;

        public CmdArea(IActionListener actionListener, int action, object p)
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
            if (!isColorRed)
            {
                g.DrawImage(Panel.imgBgrItem, x + xBonus + 15, y + 15);
            }
            else
            {
                g.SetColor(246, 89, 68);
                g.FillRect(x + xBonus + 15, y + 15, 55, 55, 6);
            }
            MyFont.text_big_white.DrawString(g, areaId.ToString(), x + xBonus + 43, y + 25, 2);
            int dis = 5;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                myFont = MyFont.text_mini_white;
            }
            int hText = myFont.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            myFont.DrawString(g, "Dân số: " + numPlayer + "/" + maxPlayer, x + xBonus + 30 + Panel.imgBgrItem.GetWidth(), yText, 0);
            myFont.DrawString(g, "Tổ đội: " + numTeam, x + xBonus + 30 + Panel.imgBgrItem.GetWidth(), yText + hText + dis, 0);
        }
    }
}
