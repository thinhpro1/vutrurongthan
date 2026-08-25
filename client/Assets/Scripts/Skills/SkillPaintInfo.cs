using Assets.Scripts.Sounds;
using System.Collections.Generic;

namespace Assets.Scripts.Skills
{
    public class SkillPaintInfo
    {
        public Sound sound;

        public DartTemplate dart;

        public List<int> action = new List<int>();

        public List<SkillEffect> effects = new List<SkillEffect>();

        public int index;

        public long timeOut;

    }
}
