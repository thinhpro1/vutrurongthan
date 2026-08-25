using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Entites;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System;

namespace Assets.Scripts.Effects
{
    public class EffectTime : Effect
    {
        public EffectTimeTemplate template;

        public long time;

        public DateTime endTime;

        public static Image imgBgr;

        public int w;

        public int h;

        static EffectTime()
        {
            imgBgr = GameCanvas.LoadImage("MainImages/GameScrs/img_effect_bgr");
        }

        public EffectTime(Entity entity, int templateId, long time)
        {
            this.entity = entity;
            template = EffectManager.instance.effectTemplates[templateId];
            this.time = time;
            endTime = DateTime.UtcNow.AddMilliseconds(time);
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
        }

        public override void Paint(MyGraphics g)
        {
            if (entity == null || time <= 0)
            {
                return;
            }
            EffectImage effectImage = template.effectImage;
            if (effectImage == null || effectImage.icons.Count == 0)
            {
                return;
            }
            if (entity is Player)
            {
                Player player = (Player)entity;
                if (player.body != null && player.body.icon == player.body.template.fly)
                {
                    float angle = player.dir == 1 ? 270 : 90;
                    GraphicManager.instance.Draw(g, effectImage.icons[tick], entity.x - effectImage.dx * entity.dir + entity.w * entity.dir, entity.y - entity.h * 2 / 3, angle, entity.dir == 1 ? MyGraphics.VCENTER | MyGraphics.RIGHT : MyGraphics.VCENTER | MyGraphics.LEFT);
                    return;
                }
            }
            GraphicManager.instance.Draw(g, effectImage.icons[tick], entity.x - effectImage.dx * entity.dir, entity.y + effectImage.dy, entity.dir == 1 ? 0 : 2, StaticObj.BOTTOM_HCENTER);
        }

        public void PaintTime(MyGraphics g, int x, int y)
        {
            if (template.iconId == -1)
            {
                return;
            }
            g.DrawImage(imgBgr, x, y, StaticObj.BOTTOM_RIGHT);
            GraphicManager.instance.Draw(g, template.iconId, x - w / 2, y - h / 2, 0, StaticObj.VCENTER_HCENTER);
            MyFont.text_mini_yellow.DrawString(g, GetStrTime(), x - w / 2 + 2, y - h / 2, 2, MyFont.text_mini_grey);
        }

        public string GetStrTime()
        {
            long num = time / 1000;
            if (num < 1000)
            {
                return num + "s";
            }
            else if (num < 60000)
            {
                return (num / 60) + "'";
            }
            else
            {
                return (num / 3600) + "h";
            }
        }

        public override void Update()
        {
            if (template.isClearWhenDie && entity.IsDead())
            {
                time = 0;
            }
            else
            {
                time = (endTime.Ticks - DateTime.UtcNow.Ticks) / 10000;
            }
            EffectImage effectImage = template.effectImage;
            if (effectImage != null && effectImage.icons.Count > 0)
            {
                long now = Utils.CurrentTimeMillis();
                if (now - currentTick > effectImage.delay)
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

        public override bool IsClear()
        {
            return time <= 0;
        }
    }
}
