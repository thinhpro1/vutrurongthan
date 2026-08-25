using Assets.Scripts.Commons;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using UnityEngine;

namespace Assets.Scripts.Effects
{
    public class EffectFly : Effect
    {
        public EffectImage effectImage;

        public int x;

        public int y;

        public int speedX;

        public int speedY;

        public long timeUpdatePosistion;

        public EffectFly(int effectImageId, int x, int y, int speedX, int speedY)
        {
            effectImage = EffectManager.instance.effectImages[effectImageId];
            this.x = x;
            this.y = y;
            this.speedX = speedX;
            this.speedY = speedY;
        }

        public override bool IsClear()
        {
            return y < -200 || y > Map.height + 200 || x < -200 || x > Map.width + 200;
        }

        public override void Paint(MyGraphics g)
        {
            GraphicManager.instance.Draw(g, effectImage.icons[tick], x, y, 0, StaticObj.VCENTER_HCENTER);
        }

        public override void Update()
        {
            long now = Utils.CurrentTimeMillis();
            if (timeUpdatePosistion == 0)
            {
                timeUpdatePosistion = now;
            }
            else if (now - timeUpdatePosistion > 10)
            {
                timeUpdatePosistion = now;
                x += speedX;
                y += speedY;
            }
            if (currentTick == 0)
            {
                currentTick = now;
            }
            else if (now - currentTick > effectImage.delay)
            {
                if (tick < effectImage.icons.Count - 1)
                {
                    tick++;
                }
                else
                {
                    tick = 0;
                }
                currentTick = now;
            }
        }

    }
}
