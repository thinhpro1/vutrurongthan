using System;
using System.Collections.Generic;

namespace Assets.Scripts.Skills
{
    public class SkillTemplate
    {
        public int id;

        public List<string> name = new List<string>();

        public List<string> description = new List<string>();

        public int type;

        public int maxLevel;

        public int maxUpgrade;

        public int typeMana;

        public int[][] dx;

        public int[][] dy;

        public int levelRequire;

        public int[] iconId;

        public bool isProactive;

        public int[][] coolDown;

        public int[][] manaUse;

        public int[] pointUpgrade;

        public List<SkillOption> options = new List<SkillOption>();
    }
}
