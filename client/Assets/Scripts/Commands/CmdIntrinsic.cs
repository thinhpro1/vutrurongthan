using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdIntrinsic : Command
    {
        public int templateId;

        public int iconId;

        public string name;

        public string description;

        public int param;

        public CmdIntrinsic(IActionListener actionListener, int action, object p)
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
            g.DrawImage(CmdSkill.imgBgr, x + 7, y + 6);
            GraphicManager.instance.Draw(g, iconId, x + 12, y + 11, 0, StaticObj.TOP_LEFT);
            int dis = 5;
            int hText = MyFont.text_white.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            MyFont.text_white.DrawString(g, name.Replace("#", param.ToString()), x + 90, yText, 0);
            MyFont.text_white.DrawString(g, description, x + 90, yText + hText + dis, 0);
            int wBtn = Panel.imgBtnSmall.GetWidth();
            int xBtn = x + w - 15 - wBtn;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                myFont = MyFont.text_mini_white;
                g.DrawImage(Panel.imgBtnSmallClick, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            else
            {
                if (isFocus)
                {
                    myFont = MyFont.text_white;
                }
                g.DrawImage(!isFocus ? Panel.imgBtnSmallFocus : Panel.imgBtnSmallFocus, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            hText = myFont.GetHeight();
            if (param > 0)
            {
                myFont.DrawString(g, "Thay đổi", xBtn + wBtn / 2, y + (h - hText) / 2, 2);
            }
            else
            {
                myFont.DrawString(g, "Mở", xBtn + wBtn / 2, y + (h - hText) / 2, 2);
            }
        }
    }
}
