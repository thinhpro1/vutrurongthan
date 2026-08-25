using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdAchievement : Command
    {
        public int index;

        public string name;

        public string description;

        public int ruby;

        public int param;

        public int maxParam;

        public bool isReceive;

        public CmdAchievement(IActionListener actionListener, int action, object p)
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
            g.DrawImage(Panel.imgBgrAchivement, x, y);
            int dis = 5;
            int hText = MyFont.text_white.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            MyFont.text_white.DrawString(g, index + ". " + name, x + 15, yText, 0);
            MyFont.text_white.DrawString(g, description + " (" + param + "/" + maxParam + ")", x + 15, yText + hText + dis, 0);
            int wBtn = Panel.imgBtnSmall.GetWidth();
            int xBtn = x + w - 15 - wBtn;
            int xBonus = 0;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                xBonus += 5;
                myFont = MyFont.text_mini_white;
                g.DrawImage(Panel.imgBtnSmallClick, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            else
            {
                if (isFocus)
                {
                    myFont = MyFont.text_white;
                }
                g.DrawImage(!isFocus ? Panel.imgBtnSmall : Panel.imgBtnSmallFocus, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            hText = myFont.GetHeight();
            if (isReceive)
            {
                myFont.DrawString(g, "Đã nhận", xBtn + wBtn / 2, y + (h - hText) / 2, 2);
            }
            else
            {
                g.DrawImage(Panel.imgRuby, xBtn + xBonus + 15, y + (h - Panel.imgRuby.GetHeight()) / 2);
                myFont.DrawString(g, ruby.ToString(), xBtn + xBonus + 15 + Panel.imgRuby.GetWidth() + 5, y + (h - hText) / 2, 0);
            }
        }
    }
}
