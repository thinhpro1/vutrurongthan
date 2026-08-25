using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System;

namespace Assets.Scripts.Frames
{
    public class Mount
    {
        public MountTemplate template;

        public int x;

        public int y;

        public int dir;

        public int speed;

        public int disX;

        public int playerX;

        public bool isStart;

        public bool isEnd;

        public bool isShow;

        public void PaintFront(MyGraphics g)
        {
            if (template.layer == 1 && (isStart || isEnd))
            {
                template.Paint(g, x, y, dir);
            }
        }

        public void PaintBehind(MyGraphics g)
        {
            if (template.layer == 0 && (isStart || isEnd))
            {
                template.Paint(g, x, y, dir);
            }
        }

        public void Start(Player player)
        {
            if (player.ySd - player.y <= 60)
            {
                playerX = player.x;
            }
            if (disX < 300)
            {
                disX = Math.Abs(playerX - player.x);
            }
            if (disX >= 210 && player.ySd - player.y > 90 && !isStart && !isEnd)
            {
                dir = player.dir;
                speed = 50;
                if (dir < 0)
                {
                    x = ScreenManager.instance.gameScreen.cmx + ScreenManager.instance.w + 50;
                }
                else if (dir == 1)
                {
                    x = ScreenManager.instance.gameScreen.cmx - 100;
                }
                y = player.y;
                isEnd = false;
                isShow = false;
                isStart = true;
            }
        }

        public void End(Player player)
        {
            if (player.ySd - player.y < 72 && !isEnd)
            {
                isStart = false;
                isShow = false;
                disX = 0;
                isEnd = true;
            }
        }

        public void Update(Player player)
        {
            if (isStart && !isShow)
            {
                y = player.y;
                if (dir == -1 && x - player.x >= speed)
                {
                    x -= speed;
                    return;
                }
                if (dir == 1 && player.x - x >= speed)
                {
                    x += speed;
                    return;
                }
                x = player.x;
                isShow = true;
                isEnd = false;
            }
            else if (isShow)
            {
                if (player.IsDead() || player.ySd - player.y < 24 || player.IsWallBottom(24) || player.status == PlayerStatus.STAND)
                {
                    End(player);
                }
                dir = player.dir;
                y = player.y;
                x = player.x;
            }
            else if (isEnd)
            {
                if (dir == -1 && x > ScreenManager.instance.gameScreen.cmx - 100)
                {
                    x -= 60;
                    return;
                }
                if (dir == 1 && x < ScreenManager.instance.gameScreen.cmx + ScreenManager.instance.w + 50)
                {
                    x += 60;
                    return;
                }
                isStart = false;
                isShow = false;
                isEnd = false;
            }
            else if (!isStart || !isShow || !isEnd)
            {
                x = ScreenManager.instance.gameScreen.cmx - 100;
                y = ScreenManager.instance.gameScreen.cmy - 100;
            }
        }

    }
}
