using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Frames
{
    public class Aura
    {
        public int id;

        public List<int> icons = new List<int>();

        public int dx;

        public int dy;

        public int delay;

        public long updateTime;

        public int tick;

        public void Update(long now)
        {
            if (now - updateTime > delay)
            {
                if (tick < icons.Count - 1)
                {
                    tick++;
                }
                else
                {
                    tick = 0;
                }
                updateTime = now;
            }
        }

        public void Paint(MyGraphics g, int x, int y, int dir)
        {
            GraphicManager.instance.Draw(g, icons[tick], x + dx, y + dy, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
        }
    }
}
