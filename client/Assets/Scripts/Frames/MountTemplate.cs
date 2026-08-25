using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System.Collections.Generic;

namespace Assets.Scripts.Frames
{
    public class MountTemplate
    {
        public int id;

        public List<int> icons = new List<int>();

        public int dx;

        public int dy;

        public int delay;

        public long updateTime;

        public int tick;

        public int layer;

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
            GraphicManager.instance.Draw(g, icons[tick], x + dx * dir, y + dy, (dir == 1) ? 0 : 2, StaticObj.TOP_CENTER);
        }
    }
}
