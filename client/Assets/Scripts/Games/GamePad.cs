using System;
using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Games
{
    public class GamePad
    {
        public int x;

        public int y;

        public int width;

        public int height;

        public static Image imgBgr = GameCanvas.LoadImage("MainImages/GamePads/img_move");

        public static Image imgMove = GameCanvas.LoadImage("MainImages/GamePads/img_move_1");

        public int yMove;

        public int xMove;

        private int deltaX;

        private int deltaY;

        private int delta;

        private int angle;

        public static int max = 50;

        public bool isPointerClicked;

        public GamePad()
        {
            width = imgMove.GetWidth();
            height = imgMove.GetHeight();
            xMove = x = 125 + width / 2;
            yMove = y = GameCanvas.h - 100 - height / 2;
        }

        public void paint(MyGraphics g)
        {
            g.DrawImage(imgBgr, x, y, MyGraphics.HCENTER | MyGraphics.VCENTER);
            g.DrawImage(imgMove, xMove, yMove, MyGraphics.HCENTER | MyGraphics.VCENTER);
        }

        public void update()
        {

        }

        public void PointerClicked(int x, int y)
        {
            if (x >= this.x - this.width / 2 && x <= this.x + this.width / 2 && y >= this.y - this.height / 2 && y <= this.y + this.height / 2)
            {
                isPointerClicked = true;
            }
            else
            {
                isPointerClicked = false;
            }
        }

        public void PointerMove(int x, int y)
        {
            if (!isPointerClicked)
            {
                return;
            }
            ScreenManager.instance.gameScreen.auto = 0;
            ScreenManager.instance.gameScreen.isAutoPlay = false;
            deltaX = x - this.x;
            deltaY = y - this.y;

            if (Math.Abs(deltaX) <= 4 && Math.Abs(deltaY) <= 4)
            {
                return;
            }
            delta = (int)Math.Sqrt(deltaX * deltaX + deltaY * deltaY);

            if (delta > max)
            {
                xMove = this.x + deltaX * max / delta;
                yMove = this.y + deltaY * max / delta;
            }
            else
            {
                xMove = x;
                yMove = y;
            }

            angle = Utils.Angle(deltaX, deltaY);
            if ((angle <= 360 && angle >= 340) || (angle >= 0 && angle <= 20))
            {
                Player.isMoveUp = false;
                Player.isMoveDown = false;
                Player.isMoveLeft = false;
                Player.isMoveRight = true;
            }
            else if (angle > 20 && angle < 70)
            {
                Player.isMoveUp = false;
                Player.isMoveDown = true;
                Player.isMoveLeft = false;
                Player.isMoveRight = true;
            }
            else if (angle >= 70 && angle <= 110)
            {
                Player.isMoveUp = false;
                Player.isMoveDown = true;
                Player.isMoveLeft = false;
                Player.isMoveRight = false;
            }
            else if (angle > 110 && angle < 160)
            {
                Player.isMoveUp = false;
                Player.isMoveDown = true;
                Player.isMoveLeft = true;
                Player.isMoveRight = false;
            }
            else if (angle >= 160 && angle <= 200)
            {
                Player.isMoveUp = false;
                Player.isMoveDown = false;
                Player.isMoveLeft = true;
                Player.isMoveRight = false;
            }
            else if (angle > 200 && angle < 250)
            {
                Player.isMoveUp = true;
                Player.isMoveDown = false;
                Player.isMoveLeft = true;
                Player.isMoveRight = false;
            }
            else if (angle >= 250 && angle <= 290)
            {
                Player.isMoveUp = true;
                Player.isMoveDown = false;
                Player.isMoveLeft = false;
                Player.isMoveRight = false;
            }
            else if (angle > 290 && angle < 340)
            {
                Player.isMoveUp = true;
                Player.isMoveDown = false;
                Player.isMoveLeft = false;
                Player.isMoveRight = true;
            }
        }

        public void PointerReleased(int x, int y)
        {
            if (isPointerClicked)
            {
                Player.isMoveUp = false;
                Player.isMoveRight = false;
                Player.isMoveLeft = false;
                Player.isMoveDown = false;
            }
            isPointerClicked = false;
            xMove = this.x;
            yMove = this.y;
        }
    }
}
