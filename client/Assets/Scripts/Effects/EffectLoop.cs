using Assets.Scripts.Commons;
using Assets.Scripts.Entites;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;

namespace Assets.Scripts.Effects
{
    public class EffectLoop : Effect
    {
        public EffectImage effectImage;

        public int countLoop;

        public int x;

        public int y;

        public int anchor;

        public int dx;

        public int dy;

        public int transform;

        public EffectLoop(Entity entity, int effectImageId, int countLoop, int dx, int dy, int anchor)
        {
            this.entity = entity;
            effectImage = EffectManager.instance.effectImages[effectImageId];
            this.countLoop = countLoop;
            this.x = entity.x;
            this.y = entity.y;
            this.dx = dx;
            this.dy = dy;
            this.anchor = anchor;
            transform = -1;
        }

        public EffectLoop(Entity entity, int effectImageId, int countLoop, int dx, int dy, int anchor, int transform)
        {
            this.entity = entity;
            effectImage = EffectManager.instance.effectImages[effectImageId];
            this.countLoop = countLoop;
            this.x = entity.x;
            this.y = entity.y;
            this.dx = dx;
            this.dy = dy;
            this.anchor = anchor;
            this.transform = transform;
        }

        public EffectLoop(int effectImageId, int countLoop, int x, int y, int anchor, int transform)
        {
            effectImage = EffectManager.instance.effectImages[effectImageId];
            this.countLoop = countLoop;
            this.x = x;
            this.y = y;
            this.anchor = anchor;
            this.transform = transform;
        }

        public EffectLoop(Entity entity, EffectImage effectImage, int countLoop, int dx, int dy, int anchor, int transform)
        {
            this.entity = entity;
            this.effectImage = effectImage;
            this.countLoop = countLoop;
            this.x = entity.x;
            this.y = entity.y;
            this.dx = dx;
            this.dy = dy;
            this.anchor = anchor;
            this.transform = transform;
        }

        public override bool IsClear()
        {
            return countLoop <= 0;
        }

        public override void Paint(MyGraphics g)
        {
            if (entity == null && countLoop <= 0)
            {
                return;
            }
            if (effectImage == null || effectImage.icons.Count == 0)
            {
                return;
            }
            if (entity != null)
            {
                GraphicManager.instance.Draw(g, effectImage.icons[tick], x - dx * entity.dir, y - dy, transform == -1 ? (entity.dir == 1 ? 0 : 2) : transform, anchor == -1 ? ((entity.dir == -1) ? StaticObj.BOTTOM_LEFT : StaticObj.BOTTOM_RIGHT) : anchor);
            }
            else
            {
                GraphicManager.instance.Draw(g, effectImage.icons[tick], x, y, transform, anchor);
            }
        }

        public override void Update()
        {
            if (countLoop <= 0)
            {
                return;
            }
            if (effectImage != null && effectImage.icons.Count > 0)
            {
                long now = Utils.CurrentTimeMillis();
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
                        countLoop--;
                    }
                    currentTick = now;
                }
            }
        }
    }
}
