using Assets.Scripts.Commons;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using Assets.Scripts.Skills;
using System;
using UnityEngine;

namespace Assets.Scripts.Entites.Monsters
{
    public class MonsterDart
    {
        public MonsterDartTemplate template;

        private Monster monster;

        private Entity target;

        private int status;

        private int tick;

        private long currentTick;

        private int vx;

        private int vy;

        private int va;

        private int xStart;

        private int yStart;

        private int xEnd;

        private int yEnd;

        private int x;

        private int y;

        private int dx;

        private int dy;

        private int angle;

        private int timeFly;

        private long damage;

        public MonsterDart(Monster monster, Entity target, long damage)
        {
            this.monster = monster;
            this.template = monster.template.dart;
            this.damage = damage;
            this.target = target;
            va = 24576;
            int dir = monster.x > target.x ? 1 : -1;
            xStart = x = monster.x - monster.w / 2 * dir;
            yStart = y = monster.y - monster.h * 2 / 3;
            xEnd = target.x;
            yEnd = target.y - target.h * 2 / 3;
            if (template.isMeteorite)
            {
                x = xStart = xEnd;
                y = yStart = ScreenManager.instance.gameScreen.cmy;
            }
            SetAngle(Utils.Angle(xEnd - xStart, yEnd - yStart));
        }

        public void Paint(MyGraphics g)
        {
            try
            {
                if (status == 0 && template.light.icon.Count > 0)
                {
                    GraphicManager.instance.Draw(g, template.light.icon[tick], xStart, yStart, angle);
                    return;
                }
                if (status == 1 && template.bullet.icon.Count > 0)
                {
                    GraphicManager.instance.Draw(g, template.bullet.icon[tick], x, y, angle);
                }
            }
            catch
            {
            }

        }

        public void Update()
        {
            if (status == 3)
            {
                // stop
                return;
            }
            if (target is Player && target.id != Player.me.id && ScreenManager.instance.gameScreen.FindPlayerInMap(target.id) == null)
            {
                Stop();
                return;
            }
            if (target is Monster && ScreenManager.instance.gameScreen.FindMonsterInMap(target.id) == null)
            {
                Stop();
                return;
            }
            if (status == 0)
            {
                if (template.light.icon.Count > 0)
                {
                    long now = Utils.CurrentTimeMillis();
                    if (currentTick == 0)
                    {
                        currentTick = now;
                    }
                    else if (now - currentTick > template.light.delay)
                    {
                        currentTick = now;
                        if (tick < template.light.icon.Count - 1)
                        {
                            tick++;
                        }
                        else
                        {
                            tick = 0;
                            currentTick = 0;
                            status = 1;
                        }
                    }
                }
                else
                {
                    status = 1;
                }
                return;
            }
            if (status == 1)
            {
                // update đạn bay
                bool isExplode = false;
                long now = Utils.CurrentTimeMillis();
                if (template.bullet.icon.Count > 0)
                {
                    if (currentTick == 0)
                    {
                        currentTick = now;
                    }
                    else if (now - currentTick > template.bullet.delay)
                    {
                        currentTick = now;
                        if (tick < template.bullet.icon.Count - 1)
                        {
                            tick++;
                        }
                        else
                        {
                            tick = 0;
                        }
                    }
                    for (int i = 0; i < 2; i++)
                    {
                        dx = target.x - x;
                        dy = target.y - target.h * 2 / 3 - y;
                        timeFly++;
                        if ((Math.Abs(dx) < target.w / 2 && Math.Abs(dy) < target.h) || timeFly > 180)
                        {
                            if (!isExplode)
                            {
                                isExplode = true;
                                break;
                            }
                        }
                        int angle = Utils.Angle(dx, dy);
                        if (Math.Abs(angle - this.angle) < 90 || dx * dx + dy * dy > 4096)
                        {
                            if (Math.Abs(angle - this.angle) < 15)
                            {
                                this.angle = angle;
                            }
                            else if ((angle - this.angle >= 0 && angle - this.angle < 180) || angle - this.angle < -180)
                            {
                                this.angle = Utils.fixangle(this.angle + 15);
                            }
                            else
                            {
                                this.angle = Utils.fixangle(this.angle - 15);
                            }
                        }
                        if (va < 16384)
                        {
                            va += 2048;
                        }
                        vx = va * Utils.cos(this.angle) >> 10;
                        vy = va * Utils.sin(this.angle) >> 10;
                        x += (dx + vx) >> 10;
                        y += (dy + vy) >> 10;
                    }
                }
                if (isExplode || template.bullet.icon.Count == 0)
                {
                    status = 2;
                }
                return;
            }
            if (status == 2)
            {
                // update nổ
                if (target is Player)
                {
                    Player player = (Player)target;
                    player.DoInjure(damage, false);

                }
                if (template.isMeteorite)
                {
                    Spaceship.tShock = 10;
                }
                EffectImage effectImage = new EffectImage(-1, 0, 0, template.explode.delay, template.explode.icon.ToArray());
                target.effects.Add(new EffectLoop(target, effectImage, 1, 0, target.h * 2 / 3, StaticObj.VCENTER_HCENTER, monster.x > target.x ? 2 : 0));
                Stop();
            }
        }

        public void Stop()
        {
            status = 3;
            MonsterManager.instance.darts.Remove(this);
        }

        public void SetAngle(int angle)
        {
            this.angle = angle;
            vx = va * Utils.cos(angle) >> 10;
            vy = va * Utils.sin(angle) >> 10;
        }
    }
}
