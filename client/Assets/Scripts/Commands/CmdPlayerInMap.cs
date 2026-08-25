using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System.Drawing;

namespace Assets.Scripts.Commands
{
    public class CmdPlayerInMap : Command
    {
        public Player player;

        public int index;

        public CmdPlayerInMap(Player player, int index, IActionListener actionListener, int action, object p)
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
            this.player = player;
            this.index = index;
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
                myFontNameOnline.DrawString(g, (index + 1) + ". " + player.name + " (" + player.level + ")", x + 90 + xBonus, yText, 0);
                myFontDescription.DrawString(g, "Bang hội: " + (player.clan != null ? player.clan.name : "chưa có"), x + 90 + xBonus, yText + hText + dis, 0);
                if (player.typePk == 2)
                {
                    GraphicManager.instance.Draw(g, 77, x + w - 40, y + h / 2, 0, StaticObj.VCENTER_HCENTER);
                }
                else if (player.typeFlag > 0)
                {
                    GraphicManager.instance.Draw(g, Player.imgsFlag[player.typeFlag - 1], x + w - 40, y + h / 2, 0, StaticObj.VCENTER_HCENTER);
                }
            }
        }
    }
}
