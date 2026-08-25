using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdSettingFocus : Command
    {
        public CmdSettingFocus(IActionListener actionListener, int action, object p, string caption)
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
            this.caption = caption;
        }

        public void Paint(MyGraphics g, bool isOn)
        {
            if (isClick)
            {
                g.DrawImage(Panel.imgItemShopClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? Panel.imgItemShop : Panel.imgItemShopFocus, x, y);
            }
            MyFont.text_white.DrawString(g, caption, x + 15, y + (h - MyFont.text_white.GetHeight()) / 2, 0);
            Image image;
            Image imageClick;
            Image imageFocus;
            if (isOn)
            {
                image = CmdSetting.imgOn;
                imageClick = CmdSetting.imgOnClick;
                imageFocus = CmdSetting.imgOnFocus;
            }
            else
            {
                image = CmdSetting.imgOff;
                imageClick = CmdSetting.imgOffClick;
                imageFocus = CmdSetting.imgOffFocus;
            }
            int wImage = image.GetWidth();
            int hImage = image.GetHeight();
            if (isClick)
            {
                g.DrawImage(imageClick, x + w - 15 - wImage, y + (h - hImage) / 2);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x + w - 15 - wImage, y + (h - hImage) / 2);
            }

        }
    }
}
