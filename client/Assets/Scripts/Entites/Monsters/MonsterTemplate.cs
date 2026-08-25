using Assets.Scripts.GraphicCustoms;
using System.Collections.Generic;

namespace Assets.Scripts.Entites.Monsters
{
    public class MonsterTemplate
    {
        public int id;

        public int rangeMove;

        public int speed;

        public int type;

        public string name;

        public List<int> iconsMove = new List<int>();
        public List<int> iconsStand = new List<int>();

        public int iconInjure;

        public int iconAttack;

        public MonsterDartTemplate dart;

        public int w;

        public int h;

        public int dx;

        public int dy;

        public void LoadIcons()
        {
            try
            {
                foreach (int id in iconsMove)
                {
                    GraphicManager.instance.CreateImage(id);
                }
                foreach (int id in iconsStand)
                {
                    GraphicManager.instance.CreateImage(id);
                }
                GraphicManager.instance.CreateImage(iconInjure);
                GraphicManager.instance.CreateImage(iconAttack);
            }
            catch
            {
            }
        }
    }
}
