using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace Assets.Scripts.Effects
{
    public class EffectImage
    {
        public int id;

        public int dx;

        public int dy;

        public long delay;

        public List<int> icons;

        public EffectImage()
        {

        }

        public EffectImage(int id, int dx, int dy, int delay, params int[] icon)
        {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.delay = delay;
            this.icons = new List<int>();
            this.icons.AddRange(icon);
        }
    }
}
