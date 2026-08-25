using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdPotential : Command
    {
        public string description;

        public Image icon;

        public CmdPotential(IActionListener actionListener, int action, object p)
        {
            this.image = Panel.imgBgrPotential;
            this.imageFocus = Panel.imgBgrPotentialFocus;
            this.imageClick = Panel.imgBgrPotentialFocus;
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            if (image != null)
            {
                w = image.GetWidth();
                h = image.GetHeight();
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
            if (isClick)
            {
                g.DrawImage(imageClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x, y);
            }
            int dis = 5;
            g.DrawImage(icon, x + 10, y + 10);
            int hText = MyFont.text_white.GetHeight();
            int yText = y + (h - hText * 2 - dis) / 2;
            MyFont.text_white.DrawString(g, caption, x + 80, yText, 0);
            MyFont.text_white.DrawString(g, description, x + 80, yText + dis + hText, 0);
        }
    }
}
