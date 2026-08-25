using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdClanMember : Command
    {
        public int index;

        public string name;

        public string description;

        public int diamond;

        public int param;

        public int maxParam;

        public bool isReceive;

        public int playerId;

        public int roleId;

        public int gender;

        public int sex;

        public long power;

        public int point;

        public int pointDay;

        public string joinTime;

        public bool isOnline;

        public CmdClanMember(IActionListener actionListener, int action, object p)
        {
            this.image = Panel.imgBgrAchivement;
            this.imageClick = Panel.imgBgrAchivement;
            this.imageFocus = Panel.imgBgrAchivement;
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
                g.DrawImage(Panel.imgClanMemberClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? Panel.imgClanMember : Panel.imgClanMemberFocus, x, y);
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
            int dis = 5;
            int hText = myFontNameOffline.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            string role = "";
            if (roleId == 0)
            {
                role = "Bang chủ: ";
            }
            else if (roleId == 1)
            {
                role = "Bang phó: ";
            }
            g.DrawImage(Panel.imgGender[gender], x + 15 + xBonus, y + 15, 0);
            string name = role + this.name + " - " + "Lv" + Player.GetLevel(power) + ": " + Utils.FormatNumber(power) + " sm";
            if (isOnline)
            {
                myFontNameOnline.DrawString(g, name, x + 15 + Panel.imgGender[index].GetWidth() + 15 + xBonus, yText, 0);
            }
            else
            {
                myFontNameOffline.DrawString(g, name, x + 15 + Panel.imgGender[index].GetWidth() + 15 + xBonus, yText, 0);
            }
            myFontDescription.DrawString(g, "Thành tích: " + point + " (ngày: " + pointDay + ")", x + 15 + Panel.imgGender[index].GetWidth() + 15 + xBonus, yText + hText + dis, 0);
        }
    }
}
