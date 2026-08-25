using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;

namespace Assets.Scripts.Skills
{
    public class Skill
    {
        public SkillTemplate template;

        public int level;

        public static Image imgCooldown;

        public bool isPaintCanNotUse;

        public int upgrade;

        public int point;

        public int coolDownReduction;

        public int coolDownIntrinsic;

        public long timeCanUse;

        public RandomCollection<int> paints = new RandomCollection<int>();

        static Skill()
        {
            imgCooldown = GameCanvas.LoadImage("imgCooldown.png");
        }

        public void Update()
        {
            long num = timeCanUse - Utils.CurrentTimeMillis();
            if (num > 0)
            {
                isPaintCanNotUse = true;
            }
            else
            {
                isPaintCanNotUse = false;
            }
        }

        public int GetIcon()
        {
            if (upgrade > 0)
            {
                return template.iconId[template.iconId.Length - 1];
            }
            return template.iconId[0];
        }

        public int GetManaUse()
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.manaUse[1].Length)
                {
                    return template.manaUse[1][template.manaUse[1].Length - 1];
                }
                return template.manaUse[1][index];
            }
            index = level - 1;
            if (index >= template.manaUse[0].Length)
            {
                return template.manaUse[0][template.manaUse[0].Length - 1];
            }
            return template.manaUse[0][index];
        }

        public string GetName()
        {
            if (upgrade > 0 && template.name.Count > 1)
            {
                return template.name[1];
            }
            return template.name[0];
        }

        public string GetDescription()
        {
            if (upgrade > 0 && template.description.Count > 1)
            {
                return template.description[1];
            }
            return template.description[0];
        }

        public int GetManaUse(int level)
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.manaUse[1].Length)
                {
                    return template.manaUse[1][template.manaUse[1].Length - 1];
                }
                return template.manaUse[1][index];
            }
            index = level - 1;
            if (index >= template.manaUse[0].Length)
            {
                return template.manaUse[0][template.manaUse[0].Length - 1];
            }
            return template.manaUse[0][index];
        }

        public long GetCoolDown()
        {
            long time = GetCoolDownTemplate();
            if (coolDownReduction > 0)
            {
                time -= time * coolDownReduction / 100;
            }
            if (coolDownIntrinsic > 0)
            {
                time -= time * coolDownIntrinsic / 100;
            }
            if (time < 100)
            {
                time = 100;
            }
            return time;
        }

        public long GetCoolDownTemplate()
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.coolDown[1].Length)
                {
                    return template.coolDown[1][template.coolDown[1].Length - 1];
                }
                return template.coolDown[1][index];
            }
            index = level - 1;
            if (index >= template.coolDown[0].Length)
            {
                return template.coolDown[0][template.coolDown[0].Length - 1];
            }
            return template.coolDown[0][index];
        }

        public long GetCoolDownTemplate(int level)
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.coolDown[1].Length)
                {
                    return template.coolDown[1][template.coolDown[1].Length - 1];
                }
                return template.coolDown[1][index];
            }
            index = level - 1;
            if (index >= template.coolDown[0].Length)
            {
                return template.coolDown[0][template.coolDown[0].Length - 1];
            }
            return template.coolDown[0][index];
        }

        public long GetCoolDown(int level)
        {
            long time = GetCoolDownTemplate(level);
            if (coolDownReduction > 0)
            {
                time -= time * coolDownReduction / 100;
            }
            return time;
        }

        public int GetDx()
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.dx[1].Length)
                {
                    return template.dx[1][template.dx[1].Length - 1];
                }
                return template.dx[1][index];
            }
            index = level - 1;
            if (index >= template.dx[0].Length)
            {
                return template.dx[0][template.dx[0].Length - 1];
            }
            return template.dx[0][index];
        }

        public int GetDy()
        {
            if (level < 0)
            {
                return 0;
            }
            int index;
            if (upgrade > 0)
            {
                index = upgrade - 1;
                if (index >= template.dy[1].Length)
                {
                    return template.dy[1][template.dy[1].Length - 1];
                }
                return template.dy[1][index];
            }
            index = level - 1;
            if (index >= template.dy[0].Length)
            {
                return template.dy[0][template.dy[0].Length - 1];
            }
            return template.dy[0][index];
        }

        public SkillPaint GetSkillPaint()
        {
            if (paints.IsEmpty())
            {
                return null;
            }
            int id = paints.Next();
            if (SkillManager.instance.paints.ContainsKey(id))
            {
                return SkillManager.instance.paints[id];
            }
            return null;
        }

    }
}
