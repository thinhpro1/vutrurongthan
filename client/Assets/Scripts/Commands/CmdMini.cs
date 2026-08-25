using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;

namespace Assets.Scripts.Commands
{
    public class CmdMini : Command
    {
        public CmdMini()
        {

        }

        public CmdMini(string caption, IActionListener actionListener, int action, object p)
        {
            this.image = imgButtonMini;
            this.imageFocus = imgButtonMiniFocus;
            this.imageClick = imgButtonMiniClicked;
            this.caption = caption;
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
                w = 130;
                h = 40;
            }
            isShow = true;
        }

        public override void Paint(MyGraphics g)
        {
            if (!isShow)
            {
                return;
            }
            if (anchor == StaticObj.TOP_LEFT)
            {
                if (isClick)
                {
                    g.DrawImage(imageClick, x, y);
                }
                else
                {
                    g.DrawImage(!isFocus ? image : imageFocus, x, y);
                }
            }
            else if (anchor == StaticObj.VCENTER_HCENTER)
            {
                if (isClick)
                {
                    g.DrawImage(imageClick, x, y, StaticObj.VCENTER_HCENTER);
                }
                else
                {
                    g.DrawImage(!isFocus ? image : imageFocus, x, y, StaticObj.VCENTER_HCENTER);
                }
            }
            if (isClick)
            {
                MyFont.text_small_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_small_white.GetHeight()) / 2, 2);
            }
            else if (isFocus)
            {
                MyFont.text_mini_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_mini_white.GetHeight()) / 2, 2);
            }
            else
            {
                MyFont.text_mini_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_mini_blue.GetHeight()) / 2, 2);
            }
        }
    }
}
