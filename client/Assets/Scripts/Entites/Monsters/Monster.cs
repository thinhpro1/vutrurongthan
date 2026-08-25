using Assets.Scripts.Commons;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.Entites.Monsters
{
    public class Monster : Entity
    {
        public MonsterTemplate template;

        public bool isDontMove;

        public int xFirst;

        public int yFirst;

        public int dirV = 1;

        public int timeStatus;

        public int levelStatus;

        public long lastTimeFrame;

        public int iconPaint;

        public int percentHp;

        public int level;

        public Dictionary<Entity, long> targets;

        public long timeDie;

        public Monster(int templateId)
        {
            template = MonsterManager.instance.monsterTemplates[templateId];
            status = MonsterStatus.MOVE;
            targets = new Dictionary<Entity, long>();
            Init();
        }

        public void Init()
        {
            w = template.w;
            h = template.h;
        }

        public override void Paint(MyGraphics g)
        {
            try
            {
                if (!IsPaint())
                {
                    return;
                }
                if (status != MonsterStatus.DEAD)
                {
                    if (!isChocolate)
                    {
                        GraphicManager.instance.Draw(g, iconPaint, x + template.dx, y + template.dx, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
                    }
                    foreach (Effect effect in effects)
                    {
                        effect.Paint(g);
                    }
                    if (Player.me.monsterFocus != null && Player.me.monsterFocus.Equals(this))
                    {
                        try
                        {
                            PaintHp(g, x, y - h);
                        }
                        catch
                        {
                        }
                    }
                }
            }
            catch
            {
            }
        }

        public virtual void PaintHp(MyGraphics g, int x, int y)
        {
            if (levelStatus == 1)
            {
                MyFont.text_red.DrawString(g, "Tinh Ranh", x, y - 20, 2, MyFont.text_grey);
            }
            else if (levelStatus == 2)
            {
                MyFont.text_red.DrawString(g, "Thủ Lĩnh", x, y - 20, 2, MyFont.text_grey);
            }
            else if (levelStatus == 3)
            {
                MyFont.text_red.DrawString(g, "Tà Thần", x, y - 20, 2, MyFont.text_grey);
            }
            g.DrawImage(GameScreen.imgSelect, x, y - 10, StaticObj.BOTTOM_HCENTER);
        }

        public override void UpdateShadow()
        {
            if (status == MonsterStatus.DEAD)
            {
                xSd = ySd = -100;
                return;
            }
            base.UpdateShadow();
        }

        public override void Update()
        {
            base.Update();
            switch (status)
            {
                case MonsterStatus.START_DEAD:
                    UpdateDead();
                    break;

                case MonsterStatus.MOVE:
                    if (isBlind || isChocolate)
                    {
                        break;
                    }
                    timeStatus = 0;
                    UpdateMove();
                    break;
                case MonsterStatus.ATTACK:
                    if (isBlind || isChocolate)
                    {
                        break;
                    }
                    UpdateAttack();
                    break;
                case MonsterStatus.INJURE:
                    UpdateInjure();
                    break;
            }
        }

        public virtual void UpdateDead()
        {
            isDontMove = false;
            iconPaint = template.iconInjure;
            long now = Utils.CurrentTimeMillis();
            if (timeDie == 0)
            {
                timeDie = now;
                return;
            }
            long time = now - timeDie;
            if (time < 500)
            {
                if (template.type == MonsterMoveType.FLY)
                {
                    if (time < 100)
                    {
                        x -= (int)(time / 5) * dir;
                        y -= (int)(time / 5);
                    }
                    else if (!Map.IsWall(x, y))
                    {
                        x -= dir * 3;
                        y += (int)(time / 10);
                    }
                }
                else
                {
                    x -= dir * 3;
                    if (time % 100 < 50)
                    {
                        y -= (int)(time % 5) * 3;
                    }
                    else if (!Map.IsWall(x, y))
                    {
                        y += (int)(time % 5) * 3;
                    }
                }
                return;
            }
            ScreenManager.instance.gameScreen.effects.Add(new EffectLoop(17, 1, x, y - h / 2, StaticObj.VCENTER_HCENTER, 0));
            x = (y = -100);
            status = MonsterStatus.DEAD;
            timeStatus = 0;
            if (Player.me.monsterFocus == this)
            {
                Player.me.monsterFocus = null;
            }
        }

        public virtual void UpdateAttack()
        {
            iconPaint = template.iconAttack;
            if (targets.Count == 0)
            {
                status = MonsterStatus.MOVE;
                return;
            }
            dir = (targets.ElementAt(0).Key.x > x) ? 1 : (-1);
            for (int i = 0; i < targets.Count; i++)
            {
                MonsterManager.instance.darts.Add(new MonsterDart(this, targets.ElementAt(i).Key, targets.ElementAt(i).Value));
            }
            targets.Clear();
        }

        public void SetInjure()
        {
            if (status == MonsterStatus.ATTACK || IsDead())
            {
                return;
            }
            timeStatus = 5;
            status = MonsterStatus.INJURE;
            if (template.type != 0 && template.speed > 0 && Math.Abs(x - xFirst) < 30)
            {
                x -= 10 * dir;
            }
        }

        private void UpdateInjure()
        {
            iconPaint = template.iconInjure;
            timeStatus--;
            if (timeStatus <= 0)
            {
                if (isDie || hp <= 0)
                {
                    status = MonsterStatus.START_DEAD;
                }
                else
                {
                    timeStatus = 0;
                    status = MonsterStatus.MOVE;
                }
            }
        }

        public virtual void UpdateMove()
        {
            try
            {
                long now = Utils.CurrentTimeMillis();
                switch (template.type)
                {
                    case MonsterMoveType.STAND:
                        {
                            if (now - lastTimeFrame > 40)
                            {
                                lastTimeFrame = now;
                                frameTick++;
                            }
                            if (frameTick > 30)
                            {
                                frameTick = 0;
                            }
                            int index = 1;
                            if (frameTick % 15 < 5)
                            {
                                index = 0;
                            }
                            iconPaint = template.iconsMove[index];
                            return;
                        }

                    case MonsterMoveType.RUN:
                        {
                            if (now - lastTimeFrame > 40)
                            {
                                lastTimeFrame = now;
                                return;
                            }
                            x += template.speed * dir;
                            if (x > xFirst + template.rangeMove)
                            {
                                dir = -1;
                            }
                            else if (x < xFirst - template.rangeMove)
                            {
                                dir = 1;
                            }
                            break;
                        }

                    case MonsterMoveType.FLY:
                        {
                            x += template.speed * dir;
                            if (now - lastTimeFrame > 100)
                            {
                                lastTimeFrame = now;
                                y += template.speed * dirV;
                            }
                            if (x > xFirst + template.rangeMove)
                            {
                                dir = -1;
                            }
                            else if (x < xFirst - template.rangeMove)
                            {
                                dir = 1;
                            }
                            if (y > yFirst + template.rangeMove / 2)
                            {
                                dirV = -1;
                            }
                            else if (y < yFirst - template.rangeMove / 2)
                            {
                                dirV = 1;
                            }
                            break;
                        }
                }
                if (frame >= template.iconsMove.Count || frame < 0)
                {
                    frame = 0;
                }
                else
                {
                    if (now - timeFrame >= 100)
                    {
                        timeFrame = now;
                        if (frame < template.iconsMove.Count - 1)
                        {
                            frame++;
                        }
                        else
                        {
                            frame = 0;
                        }
                    }
                }
                iconPaint = template.iconsMove[frame];
            }
            catch
            {
            }
        }

        public void SetAttack(Entity player, long damage)
        {
            if (targets.ContainsKey(player))
            {
                targets[player] += damage;
            }
            else
            {
                targets.Add(player, damage);
            }
            status = MonsterStatus.ATTACK;
        }

        public void StartDie()
        {
            timeDie = Utils.CurrentTimeMillis();
            hp = 0;
            isDie = true;
            status = MonsterStatus.START_DEAD;
        }

        public void SetStatusServer(int status)
        {
            if (status == 0)
            {
                this.status = MonsterStatus.MOVE;
            }
            else
            {
                this.status = MonsterStatus.DEAD;
                x = -100;
                y = -100;
            }
        }

        public override bool IsDead()
        {
            return hp <= 0 || isDie || status == MonsterStatus.DEAD;
        }


    }
}
