using System.Collections.Generic;

namespace Assets.Scripts.Frames
{
    public class FrameTemplate
    {
        public int id;

        public int type;

        public int hpBar;

        public int chat;

        public List<int> dead = new List<int>();

        public List<int> stand = new List<int>();

        public List<int> run = new List<int>();

        public int fly;

        public int jump;

        public int fall;

        public int injure;

        public Dictionary<int, int> action = new Dictionary<int, int>();

        public int dx;

        public int dy;

        public int width;

        public int height;
    }
}
