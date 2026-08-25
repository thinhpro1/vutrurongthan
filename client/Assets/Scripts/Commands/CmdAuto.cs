using Assets.Scripts.Actions;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System.Reflection;
using System.Xml.Linq;

namespace Assets.Scripts.Commands
{
    public class CmdAuto : Command
    {
        public static Image imgOn;

        public static Image imgOnClick;

        public static Image imgOnFocus;

        public static Image imgOff;

        public static Image imgOffClick;

        public static Image imgOffFocus;

        static CmdAuto()
        {
            imgOn = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_on");
            imgOnClick = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_on_click");
            imgOnFocus = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_on_focus");
            imgOff = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_off");
            imgOffClick = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_off_click");
            imgOffFocus = GameCanvas.LoadImage("MainImages/GameScrs/img_auto_off_focus");
        }

        public CmdAuto(IActionListener actionListener, int action, object p)
        {
            this.image = imgOn;
            this.imageClick = imgOnClick;
            this.imageFocus = imgOnFocus;
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            w = image.GetWidth();
            h = image.GetHeight();
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public void Paint(MyGraphics g, bool isOn)
        {
            if (isOn)
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
            else
            {
                if (isClick)
                {
                    g.DrawImage(imgOffClick, x, y);
                }
                else
                {
                    g.DrawImage(!isFocus ? imgOff : imgOffFocus, x, y);
                }
            }
        }
    }
}
