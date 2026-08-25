using Assets.Scripts.Commons;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Skills
{
    public class SkillOption
    {
        public SkillOptionTemplate template;

        public List<int> paramNormal = new List<int>();

        public List<int> paramUpgrade = new List<int>();

        public string ToString(int level, int levelUpgrade)
        {
            int param = GetParam(level, levelUpgrade);
            if (template.id == 40 || template.id == 41)
            {
                return template.name.Replace("#", ((float)param / 100) + "");
            }
            return template.name.Replace("#", Utils.GetMoneys(param));
        }

        public int GetParam(int level, int levelUpgrade)
        {
            if (level == 0)
            {
                return paramNormal[0];
            }
            if (levelUpgrade > 0)
            {
                if (levelUpgrade > paramUpgrade.Count)
                {
                    return paramUpgrade[paramUpgrade.Count - 1];
                }
                return paramUpgrade[levelUpgrade - 1];
            }
            if (level > paramNormal.Count)
            {
                return paramNormal[paramNormal.Count - 1];
            }
            return paramNormal[level - 1];
        }
    }
}
