using Assets.Scripts.Effects;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Entites
{
    public abstract class Entity
    {
        public int id;

        public string name;

        public int gender = -1;

        public long hp;

        public long mp;

        public long maxHp;

        public long maxMp;

        public int speed;

        public int dir = 1;

        public string status;

        public int lastStatus;

        public int w;

        public int h;

        public int x;

        public int y;

        public int frame;

        public int frameIndex;

        public int frameIcon;

        public long timeFrame;

        public int xSd;

        public int ySd;

        public bool isDie;

        public bool isBlind;

        public bool isBindByAmulet;

        public List<Effect> effects = new List<Effect>();

        public bool isChocolate;

        public bool isIce;

        public long hpShow;

        public int frameTick;

        public bool isLockMove;

        /*public int id;

        public int level;

        public long hp;

        public long maxHp;

        public bool isLockMove;

        public bool isDie;

        public int x;

        public int y;

        public int dir;

        public int w;

        public int h;

        public long hpShow;


        public List<Effect> effects = new List<Effect>();

        public abstract void StartDie();*/

        public virtual bool IsDead()
        {
            return hp <= 0 || isDie;
        }

        public abstract void Paint(MyGraphics g);

        public virtual void PaintShadow(MyGraphics g)
        {
            if (!IsPaint())
            {
                return;
            }
            g.DrawImage(Map.bong, xSd, ySd - 3, StaticObj.VCENTER_HCENTER);
        }

        public bool IsPaint()
        {
            if (y < ScreenManager.instance.gameScreen.cmy - 100)
            {
                return false;
            }
            if (y > ScreenManager.instance.gameScreen.cmy + GameCanvas.h + 100)
            {
                return false;
            }
            if (x < ScreenManager.instance.gameScreen.cmx - 100)
            {
                return false;
            }
            if (x > ScreenManager.instance.gameScreen.cmx + GameCanvas.w + 100)
            {
                return false;
            }
            return true;
        }


        public virtual void Update()
        {
            UpdateEffect();
            if (hp < hpShow)
            {
                hpShow -= Math.Max((hpShow - hp) / 2, 1);
            }
            else if (hp > hpShow)
            {
                hpShow += Math.Max((hp - hpShow) / 2, 1);
            }
            UpdateShadow();
        }

        public void UpdateEffect()
        {
            bool isBlind = false;
            bool isChocolate = false;
            bool isStun = false;
            for (int i = 0; i < effects.Count; i++)
            {
                Effect effect = effects[i];
                try
                {
                    if (effect.IsClear())
                    {
                        effects.RemoveAt(i);
                        i--;
                    }
                    else
                    {
                        effect.Update();
                        if (effect is EffectTime)
                        {
                            EffectTime effectTime = (EffectTime)effect;
                            if (effectTime.template != null)
                            {
                                if (effectTime.template.isStun)
                                {
                                    isStun = true;
                                }
                                if (effectTime.template.id == 0)
                                {
                                    isBlind = true;
                                }
                                if (effectTime.template.id == 2)
                                {
                                    isChocolate = true;
                                }
                            }
                        }
                    }
                }
                catch
                {
                }
            }
            this.isChocolate = isChocolate;
            this.isLockMove = isStun;
            this.isBlind = isBlind;
        }

        public virtual void UpdateShadow()
        {
            int num = 0;
            xSd = x;
            if (Map.IsWall(x, y))
            {
                ySd = y;
                return;
            }
            ySd = y;
            while (num < 30)
            {
                num++;
                ySd += Map.size;
                if (Map.IsWall(xSd, ySd))
                {
                    if (ySd % Map.size != 0)
                    {
                        ySd -= ySd % Map.size;
                    }
                    break;
                }
            }
        }

    }
}
