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

        public List<int> iconsInjure = new List<int>();

        public List<int> iconsAttack = new List<int>();

        public MonsterDartTemplate dart;

        public int w;

        public int h;

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
                foreach (int id in iconsInjure)
                {
                    GraphicManager.instance.CreateImage(id);
                }
                foreach (int id in iconsAttack)
                {
                    GraphicManager.instance.CreateImage(id);
                }
            }
            catch
            {
            }
        }
    }
}
