using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdFriend : Command
    {
        public Player player;

        public bool isOnline;

        public CmdFriend(IActionListener actionListener, int action, object p)
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
                xBonus += 5;
                g.DrawImage(Panel.imgItemShopClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? Panel.imgItemShop : Panel.imgItemShopFocus, x, y);
            }
            MyFont myFontNameOffline = MyFont.text_white;
            MyFont myFontNameOnline = MyFont.text_green;
            MyFont myFontDescription = MyFont.text_white;
            if (isClick)
            {
                myFontNameOnline = MyFont.text_mini_green;
                myFontNameOffline = MyFont.text_mini_white;
                myFontDescription = MyFont.text_mini_white;
            }
            if (player != null)
            {
                int dis = 5;
                int hText = myFontNameOffline.GetHeight();
                int yText = y + (h - hText * 2 - dis) / 2;
                g.DrawImage(Panel.imgGender[player.gender], x + 15 + xBonus, y + 15, 0);
                if (isOnline)
                {
                    myFontNameOnline.DrawString(g, player.name, x + 90 + xBonus, yText, 0);
                }
                else
                {
                    myFontNameOffline.DrawString(g, player.name, x + 90 + xBonus, yText, 0);
                }
                myFontDescription.DrawString(g, "Lv" + Player.GetLevel(player.power) + ": " + Utils.FormatNumber(player.power) + " sm", x + 90 + xBonus, yText + hText + dis, 0);
            }
        }
    }
}
