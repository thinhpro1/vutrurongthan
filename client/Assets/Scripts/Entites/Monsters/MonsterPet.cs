using Assets.Scripts.Commons;
using Assets.Scripts.Items;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Entites.Monsters
{
    public class MonsterPet : Monster
    {
        public int xTo;

        public int yTo;

        public List<Item> itemsBody = new List<Item>();

        public List<ItemOption> options = new List<ItemOption>();

        public int stamina;

        public int maxStamina;

        public long damage;

        public int maxExp;

        public int exp;

        public MonsterPet(int templateId) : base(templateId)
        {
        }

        public override void UpdateMove()
        {
            long now = Utils.CurrentTimeMillis();
            if (x == xTo && y == yTo)
            {
                /* if (now - lastTimeFrame > 40)
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
                 return;*/
            }
            else
            {
                dir = x < xTo ? 1 : -1;
                if (Math.Abs(x - xTo) > 500)
                {
                    x += (xTo - x) / 4;
                }
                else if (Math.Abs(x - xTo) < speed)
                {
                    x = xTo;
                }
                else
                {
                    x += speed * dir;
                }
                if (Math.Abs(y - yTo) > 500)
                {
                    y += (yTo - y) / 4;
                }
                else if (Math.Abs(y - yTo) < speed)
                {
                    y = yTo;
                }
                else
                {
                    y += speed * (y < yTo ? 1 : -1);
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

        public void Move(int xTo, int yTo, int speed)
        {
            this.xTo = xTo;
            this.yTo = yTo;
            this.speed = speed;
            if (speed < 4)
            {
                this.speed = 4;
            }
            this.status = MonsterStatus.MOVE;
        }
    }
}
