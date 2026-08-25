using Assets.Scripts.Actions;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using UnityEngine;

namespace Assets.Scripts.Commands
{
    public class Command
    {
        public string caption;

        public string[] subCaption;

        public IActionListener actionListener;

        public int actionId;

        public int x;

        public int y;

        public int w;

        public int h;

        public bool isFocus;

        public object _object;

        public bool isShow;

        public Image image;

        public Image imageFocus;

        public Image imageClick;

        public bool isClick;

        public int anchor = StaticObj.TOP_LEFT;

        public bool isFollowWithCamera = false;

        public static Image imgButtonMini;

        public static Image imgButtonMiniClicked;

        public static Image imgButtonMiniFocus;

        private static Image imgButton;

        private static Image imgButtonClicked;

        private static Image imgButtonFocus;

        public bool isZoomText = true;

        static Command()
        {
            imgButtonMini = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd_mini");
            imgButtonMiniClicked = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd_mini_click");
            imgButtonMiniFocus = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd_mini_focus");
            imgButton = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd");
            imgButtonClicked = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd_click");
            imgButtonFocus = GameCanvas.LoadImage("MainImages/GameCanvas/btn_cmd_focus");
        }

        public Command()
        {
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public Command(Image image, Image imageFocus, Image imageClick, IActionListener actionListener, int action, object p)
        {
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

        public Command(string image, string imageFocus, string imageClick, string caption, IActionListener actionListener, int action, object p)
        {
            this.caption = caption;
            this.image = GameCanvas.LoadImage(image);
            this.imageFocus = GameCanvas.LoadImage(imageFocus);
            this.imageClick = GameCanvas.LoadImage(imageClick);
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
                w = 90;
                h = 90;
            }
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public Command(string image, string imageFocus, string imageClick, IActionListener actionListener, int action, object p)
        {
            this.image = GameCanvas.LoadImage(image);
            this.imageFocus = GameCanvas.LoadImage(imageFocus);
            this.imageClick = GameCanvas.LoadImage(imageClick);
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
                w = 90;
                h = 90;
            }
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public Command(string caption, IActionListener actionListener, int action, object p)
        {
            image = imgButton;
            imageFocus = imgButtonFocus;
            imageClick = imgButtonClicked;
            this.caption = caption;
            actionId = action;
            this.actionListener = actionListener;
            _object = p;
            w = 220;
            h = 65;
            isShow = true;
        }

        public Command(string caption, IActionListener actionListener, int action)
        {
            image = imgButton;
            imageFocus = imgButtonFocus;
            imageClick = imgButtonClicked;
            this.caption = caption;
            actionId = action;
            this.actionListener = actionListener;
            _object = null;
            w = 220;
            h = 65;
            isShow = true;
        }

        public virtual void Paint(MyGraphics g)
        {
            if (!isShow)
            {
                return;
            }
            if (image != null && imageFocus != null)
            {
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
                if (isClick && isZoomText)
                {
                    MyFont.text_mini_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_mini_white.GetHeight()) / 2 - 1, 2);
                }
                else if (isFocus)
                {
                    MyFont.text_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_white.GetHeight()) / 2 - 1, 2);
                }
                else
                {
                    MyFont.text_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_white.GetHeight()) / 2 - 1, 2);
                }
            }
        }

        public virtual bool PointerClicked(int x, int y)
        {
            if (!isShow)
            {
                isClick = false;
                isFocus = false;
                return false;
            }
            int xBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmx) : 0);
            int yBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmy) : 0);
            if (anchor == StaticObj.VCENTER_HCENTER)
            {
                xBonus -= w / 2;
                yBonus -= h / 2;
            }
            if (x >= this.x + xBonus && x <= this.x + w + xBonus
                && y >= this.y + yBonus && y <= this.y + h + yBonus)
            {
                isClick = true;
                return true;
            }
            else
            {
                isClick = false;
            }
            return false;
        }

        public virtual bool PointerReleased(int x, int y)
        {
            if (!isShow)
            {
                isClick = false;
                isFocus = false;
                return false;
            }
            int xBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmx) : 0);
            int yBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmy) : 0);
            if (anchor == StaticObj.VCENTER_HCENTER)
            {
                xBonus -= w / 2;
                yBonus -= h / 2;
            }
            if (!isClick)
            {
                isClick = false;
                return false;
            }
            isClick = false;
            if (x >= this.x + xBonus && x <= this.x + w + xBonus
                && y >= this.y + yBonus && y <= this.y + h + yBonus)
            {
                if (Math.Abs(GameCanvas.PointerReleaseX - GameCanvas.PointerClickX) < 40
                    && Math.Abs(GameCanvas.PointerReleaseY - GameCanvas.PointerClickY) < 40)
                {
                    PerformAction();
                }

                return true;
            }
            return false;
        }

        public virtual bool PointerMove(int x, int y)
        {
            if (!isShow)
            {
                isClick = false;
                isFocus = false;
                return false;
            }
            int xBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmx) : 0);
            int yBonus = (isFollowWithCamera ? (-ScreenManager.instance.gameScreen.cmy) : 0);
            if (anchor == StaticObj.VCENTER_HCENTER)
            {
                xBonus -= w / 2;
                yBonus -= h / 2;
            }
            if (x >= this.x + xBonus && x <= this.x + w + xBonus
                && y >= this.y + yBonus && y <= this.y + h + yBonus)
            {
                isFocus = true;
                return true;
            }
            else
            {
                isFocus = false;
            }
            return false;
        }

        public virtual void PerformAction()
        {
            if (actionId > 0 && actionListener != null)
            {
                SoundMn.buttonClick();
                isClick = false;
                isFocus = false;
                actionListener.Perform(actionId, _object);
            }
        }
    }
}
