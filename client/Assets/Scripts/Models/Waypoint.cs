using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;

namespace Assets.Scripts.Models
{
    public class Waypoint
    {
        public int minX;

        public int minY;

        public int maxX;

        public int maxY;

        public int x;

        public int y;

        public int w;

        public int h;

        public string name;

        public bool isShow;

        public int type;

        public Waypoint()
        {

        }

        public Waypoint(int x, int y, int type, string name)
        {
            this.type = type;
            this.name = name;

            this.maxY = y;
            this.minY = y - 150;

            if (type == 0)
            {
                maxX = x + 20;
                minX = x;
            }
            else if (type == 1)
            {
                maxX = x;
                minX = x - 20;
            }
            else if (type == 2)
            {
                maxX = x + 100;
                minX = x - 100;
            }

            w = 270;

            if (minX != 0)
            {
                this.x = minX - w - 20;
            }
            else
            {
                this.x = maxX + 20;
            }

            this.y = minY - 100;
            if (type == 2)
            {
                this.x = x - w / 2;
            }
            h = 60;
        }

        public void paint(MyGraphics g)
        {
            if (isShow)
            {
                g.SetColor(0, 0.5f);
                g.FillRect(x, y, w, h, 10);
                MyFont.text_white.DrawString(g, name, x + w / 2, y + 16, 2);
            }
        }

        public void update()
        {
            if (type == 1)
            {
                if (Player.me.x >= x - 200 && Player.me.y >= y - 200)
                {
                    isShow = true;
                }
                else
                {
                    isShow = false;
                }
            }
            else if (type == 0)
            {
                if (Player.me.x <= x + w + 200 && Player.me.y >= y - 200)
                {
                    isShow = true;
                }
                else
                {
                    isShow = false;
                }
            }
            else if (type == 2)
            {
                if (Player.me.x >= x - 200 && Player.me.x <= x + w + 200 && Player.me.y >= y - 200)
                {
                    isShow = true;
                }
                else
                {
                    isShow = false;
                }
            }
        }
    }
}
