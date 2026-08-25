using System;
using System.Collections.Generic;

namespace Assets.Scripts.Skills
{
    public class SkillPaint
    {
        public int id;

        public List<SkillPaintInfo> info = new List<SkillPaintInfo>();

        public int index;

        public bool isFly;

        public int dxFly;

        public int dyFly;

        public long updateTime;

        public SkillPaint Clone()
        {
            SkillPaint skillPaint = new SkillPaint();
            skillPaint.id = id;
            skillPaint.info = new List<SkillPaintInfo>();
            foreach (SkillPaintInfo skillPaintInfo in info)
            {
                SkillPaintInfo skillInfo = new SkillPaintInfo();
                skillInfo.sound = skillPaintInfo.sound;
                skillInfo.dart = skillPaintInfo.dart;
                skillInfo.action.AddRange(skillPaintInfo.action);
                foreach (SkillEffect skillEffect in skillPaintInfo.effects)
                {
                    SkillEffect effect = new SkillEffect();
                    effect.loop = skillEffect.loop;
                    effect.effectInfo = skillEffect.effectInfo;
                    skillInfo.effects.Add(effect);
                }
                skillInfo.timeOut = skillPaintInfo.timeOut;
                skillPaint.info.Add(skillInfo);
            }
            skillPaint.dxFly = dxFly;
            skillPaint.dyFly = dyFly;
            skillPaint.isFly = isFly;
            return skillPaint;
        }
    }
}
