using Assets.Scripts.Commons;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Entites.Monsters
{
    public class BigMonster : Monster
    {
        public static Image shadowBig = GameCanvas.LoadImage("shadowBig.png");

        public BigMonster(int templateId) : base(templateId)
        {

        }

        public override void PaintHp(MyGraphics g, int x, int y)
        {
            int num = (int)(hp * 150L / maxHp);
            if (num != 0)
            {
                g.SetColor(0);
                g.FillRect(x - 72, y, 154, 16);
                g.SetColor(Color.red);
                g.FillRect(x - 70, y + 2, num, 12);
            }
        }

        public override void PaintShadow(MyGraphics g)
        {
            if (!IsPaint())
            {
                return;
            }
            g.DrawImage(shadowBig, xSd, ySd, 3);
        }

        public void SetAttack(Dictionary<Player, long> targets)
        {
            foreach (var item in targets)
            {
                if (this.targets.ContainsKey(item.Key))
                {
                    this.targets[item.Key] += item.Value;
                }
                else
                {
                    this.targets.Add(item.Key, item.Value);
                }
            }
            status = MonsterStatus.ATTACK;
            Spaceship.tShock = 10;
            effects.Add(new EffectLoop(this, 8, 1, -80, h * 2 / 3 - 30, StaticObj.VCENTER_HCENTER));
        }

        public override void UpdateDead()
        {
            iconPaint = template.iconInjure;
            if (GameCanvas.gameTick % 10 == 0)
            {
                effects.Add(new EffectLoop(this, 9, 1, Utils.random(0, w / 2) * (Utils.r.Next(2) == 0 ? 1 : (-1)), Utils.random(h / 4, h), StaticObj.VCENTER_HCENTER));
            }
        }
    }
}
