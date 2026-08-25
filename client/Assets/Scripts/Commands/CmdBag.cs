using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;

namespace Assets.Scripts.Commands
{
    public class CmdBag : Command
    {
        public CmdBag(string caption, Image image, Image imageFocus, Image imageClick, IActionListener actionListener, int action, object p)
        {
            this.caption = caption;
            this.image = image;
            this.imageFocus = imageFocus;
            this.imageClick = imageClick;
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

        public void Paint(MyGraphics g, bool isSelect)
        {
            if (isSelect)
            {
                g.DrawImage(imageClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x, y);
            }
            if (isSelect)
            {
                MyFont.text_mini_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_mini_white.GetHeight()) / 2, 2);
            }
            else
            {
                MyFont.text_mini_blue.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_mini_blue.GetHeight()) / 2, 2);
            }
        }
    }
}
