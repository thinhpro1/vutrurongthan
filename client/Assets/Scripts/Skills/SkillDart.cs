using Assets.Scripts.Commons;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Services;
using System;
using UnityEngine;

namespace Assets.Scripts.Skills
{
    public class SkillDart
    {
        public DartTemplate template;

        private Player player;

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

        private bool isLine;

        private long timeLine;

        public SkillDart(Player player, DartTemplate template, int x, int y)
        {
            this.player = player;
            this.template = template;
            isLine = template.isLine;
            va = 24576;
            xEnd = xStart = this.x = x;
            yEnd = yStart = this.y = y;
            if (player.monsterFocus == null)
            {
                target = player.playerFocus;
            }
            else
            {
                target = player.monsterFocus;
            }
            if (target != null)
            {
                xEnd = target.x;
                yEnd = target.y - target.h * 2 / 3;
                // SetAngle(Utils.Angle(xEnd - x, yEnd - y));
                if (isLine)
                {
                    int dx = player.x - target.x;
                    int dy = player.y - target.y;
                    if (Math.Abs(dx) < 200 && Math.Abs(dy) < 200)
                    {
                        if (Math.Abs(dx) >= Math.Abs(dy))
                        {
                            xEnd = xStart + 200 * (dx < 0 ? 1 : -1);
                        }
                        else
                        {
                            yEnd = yStart + 200 * (dy < 0 ? 1 : -1);
                        }
                    }
                    if (dy == 0 && Math.Abs(xStart - xEnd) > 200)
                    {
                        yEnd = y;
                    }
                }
                SetAngle(Utils.Angle(xEnd - xStart, yEnd - yStart));
                //SetAngle(Utils.Angle(xStart - xEnd, yStart - yEnd));
            }
            if (!template.isTarget)
            {
                target = player;
            }
        }

        public void Paint(MyGraphics g)
        {
            if (status == 0 && template.bullet.icon.Count > 0)
            {
                if (template.isLine)
                {
                    if (xStart != xEnd || yStart != yEnd)
                    {
                        if (Math.Abs(xStart - xEnd) >= Math.Abs(yStart - yEnd))
                        {
                            int dirX = xEnd > xStart ? 1 : -1;
                            for (int i = 10; i < (xEnd - xStart) * dirX - 10; i += 2)
                            {
                                int num = 0;
                                if (yStart != yEnd)
                                {
                                    num = i * (yEnd - yStart) / (xEnd - xStart);
                                }
                                if ((yEnd > yStart && num < 0) || (yEnd < yStart && num > 0))
                                {
                                    num *= (-1);
                                }
                                GraphicManager.instance.Draw(g, template.bullet.icon[1], xStart + i * dirX, yStart + num, angle);
                            }
                        }
                        else
                        {
                            int dirX = yEnd > yStart ? 1 : -1;
                            for (int i = 10; i < (yEnd - yStart) * dirX - 10; i += 2)
                            {
                                int num = 0;
                                if (xStart != xEnd)
                                {
                                    num = i * (xEnd - xStart) / (yEnd - yStart);
                                }
                                if ((xEnd > xStart && num < 0) || (xEnd < xStart && num > 0))
                                {
                                    num *= (-1);
                                }
                                GraphicManager.instance.Draw(g, template.bullet.icon[1], xStart + num, yStart + i * dirX, angle);
                            }
                        }
                        GraphicManager.instance.Draw(g, template.bullet.icon[0], xStart, yStart, angle);
                        GraphicManager.instance.Draw(g, template.bullet.icon[2], xEnd, yEnd, angle);
                    }
                }
                else
                {
                    GraphicManager.instance.Draw(g, template.bullet.icon[tick], x, y, angle);
                }
            }
        }

        public void Update()
        {
            if (status == 2)
            {
                // stop
                return;
            }
            if (template.isTarget && target == null)
            {
                Stop();
                return;
            }
            if (status == 0)
            {
                // update đạn bay
                if (!template.isTarget)
                {
                    status = 1;
                    if (player.Equals(Player.me))
                    {
                        Service.instance.Attack(null);
                    }
                    return;
                }
                bool isExplode = false;
                long now = Utils.CurrentTimeMillis();
                if (template.bullet.icon.Count > 0 && !template.isLine)
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
                if (isLine)
                {
                    if (timeLine == 0)
                    {
                        timeLine = now;
                    }
                    else if (now - timeLine > template.bullet.delay)
                    {
                        isLine = false;
                        isExplode = true;
                    }
                }
                if ((isExplode && !isLine) || template.bullet.icon.Count == 0)
                {
                    status = 1;
                    if (player.Equals(Player.me))
                    {
                        Service.instance.Attack(target);
                    }
                }
                return;
            }
            if (status == 1)
            {
                // update nổ
                if (target is Monster)
                {
                    ((Monster)target).SetInjure();
                }
                if (target != player)
                {
                    target.dir = player.x > target.x ? 1 : -1;
                    if (template.bullet.icon.Count == 0)
                    {
                        target.effects.Add(new EffectLoop(target, 13, 1, 0, target.h / 2, player.x > target.x ? StaticObj.BOTTOM_RIGHT : StaticObj.BOTTOM_LEFT, player.x > target.x ? 0 : 2));
                    }
                }
                EffectImage effectImage = new EffectImage(-1, 0, 0, template.explode.delay, template.explode.icon.ToArray());
                target.effects.Add(new EffectLoop(target, effectImage, 1, 0, target.h * 2 / 3, StaticObj.VCENTER_HCENTER, player.x > target.x ? 2 : 0));
                Stop();
            }
        }

        public void Stop()
        {
            status = 2;
            player.dart = null;
            player.skillPaint = null;
            if (!player.IsDead())
            {
                if (player.IsWallBottom())
                {
                    player.SetStatus(PlayerStatus.STAND);
                }
                else
                {
                    player.SetStatus(PlayerStatus.FALL);
                }
            }
        }

        public void SetAngle(int angle)
        {
            this.angle = angle;
            vx = va * Utils.cos(angle) >> 10;
            vy = va * Utils.sin(angle) >> 10;
        }
    }
}
