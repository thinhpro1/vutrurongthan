using Assets.Scripts.Actions;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;

namespace Assets.Scripts.Commands
{
    public class CmdMenu : Command
    {
        public static Image imgMenu;

        public static Image imgMenuClick;

        public static Image imgMenuFocus;

        public static Image imgContent;

        public bool isActiveAction;

        public bool isMini;

        static CmdMenu()
        {
            imgMenu = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/img_menu");
            imgMenuClick = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/img_menu_click");
            imgMenuFocus = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/img_menu_focus");
            imgContent = GameCanvas.LoadImage("MainImages/GameCanvas/Menus/img_content");
        }

        public CmdMenu(string caption, IActionListener actionListener, int action, object p)
        {
            image = imgMenu;
            imageFocus = imgMenuFocus;
            imageClick = imgMenuClick;
            actionId = action;
            this.caption = caption.Replace("\n", " ");
            this.actionListener = actionListener;
            this._object = p;
            /* w = MenuNpc.imgBgr.w - 30;
             h = MyFont.text_big_white.GetHeight();*/
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
            if (isMini)
            {
                MyFont myFont = MyFont.text_white;
                int hText = myFont.GetHeight();
                int disY = isClick ? 3 : 0;
                int disX = isClick ? 3 : 0;
                string[] vs = myFont.SplitFontArray(caption, w - 40);
                for (int i = 0; i < vs.Length; i++)
                {
                    if (i == 0)
                    {
                        g.DrawImage(imgContent, x + disX, y + disY);
                    }
                    myFont.DrawString(g, vs[i], x + 40 + disX, y + disY + i * (hText + 5), 0);
                }
            }
            else
            {
                MyFont myFont = MyFont.text_white;
                if (isClick)
                {
                    myFont = MyFont.text_mini_white;
                    g.DrawImage(imageClick, x, y);
                }
                else
                {
                    g.DrawImage(!isSelect ? image : imageFocus, x, y);
                }
                int hText = myFont.GetHeight();
                string[] vs = MyFont.text_white.SplitFontArray(caption, w - 40);
                int yText = (h - hText * vs.Length - (vs.Length - 1) * 10) / 2;
                for (int i = 0; i < vs.Length; i++)
                {
                    myFont.DrawString(g, vs[i], x + w / 2, y + yText + i * (hText + 10), 2);
                }
            }
        }

        public override void PerformAction()
        {
            base.PerformAction();
            isActiveAction = true;
        }

    }
}
