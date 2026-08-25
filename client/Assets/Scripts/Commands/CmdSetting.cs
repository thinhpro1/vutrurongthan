using Assets.Scripts.Actions;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;

namespace Assets.Scripts.Commands
{
    public class CmdSetting : Command
    {
        public static Image imgOn;

        public static Image imgOnFocus;

        public static Image imgOnClick;

        public static Image imgOff;

        public static Image imgOffFocus;

        public static Image imgOffClick;

        static CmdSetting()
        {
            imgOn = GameCanvas.LoadImage("MainImages/Panels/btn_on");
            imgOnFocus = GameCanvas.LoadImage("MainImages/Panels/btn_on_focus");
            imgOnClick = GameCanvas.LoadImage("MainImages/Panels/btn_on_click");
            imgOff = GameCanvas.LoadImage("MainImages/Panels/btn_off");
            imgOffFocus = GameCanvas.LoadImage("MainImages/Panels/btn_off_focus");
            imgOffClick = GameCanvas.LoadImage("MainImages/Panels/btn_off_click");
        }

        public CmdSetting(IActionListener actionListener, int action, object p, string caption)
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
                image = imgOn;
                imageClick = imgOnClick;
                imageFocus = imgOnFocus;
            }
            else
            {
                image = imgOff;
                imageClick = imgOffClick;
                imageFocus = imgOffFocus;
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
