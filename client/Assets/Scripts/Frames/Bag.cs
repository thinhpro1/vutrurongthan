using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using System;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Frames
{
    public class Bag
    {
        public int id;

        public List<int> icons = new List<int>();

        public int dxFly;

        public int dyFly;

        public int delay;

        public long updateTime;

        public int tick;

        public bool isFly;

        public long updateTimeFly;

        public int xFly;

        public int yFly;

        public bool isFlying;

        public int num;

        public void Update(long now)
        {
            try
            {
                if (now - updateTime > delay)
                {
                    if (tick < icons.Count - 1)
                    {
                        tick++;
                    }
                    else
                    {
                        tick = 0;
                    }
                    updateTime = now;
                }
                if (isFly)
                {
                    if (isFlying)
                    {
                        updateTimeFly = now;
                        num++;
                        int width = 100;
                        if (num < width)
                        {
                            yFly++;
                            xFly = 200 - GetX(yFly);
                        }
                        else if (num < width * 2)
                        {
                            yFly--;
                            xFly = GetX(yFly);
                        }
                        else if (num < width * 3)
                        {
                            yFly--;
                            xFly = GetX(yFly);
                        }
                        else if (num < width * 4)
                        {
                            yFly++;
                            xFly = 200 - GetX(yFly);
                        }
                        if (num >= width * 4)
                        {
                            isFlying = false;
                            xFly = 0;
                            yFly = 0;
                        }
                    }
                    else if (now - updateTimeFly > 3000)
                    {
                        isFlying = true;
                        xFly = 0;
                        yFly = 0;
                        num = 0;
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.LogException(ex);
            }

        }

        public void Paint(MyGraphics g, Player player)
        {
            int dx = 0;
            if (isFly)
            {
                dx -= 50;
            }
            if (player.body != null && player.body.icon == player.body.template.fly)
            {
                GraphicManager.instance.Draw(g, icons[tick], player.x + dxFly * player.dir + player.dx * player.dir + xFly * player.dir + dx * player.dir, player.y + dyFly + player.dy + yFly * player.dir, player.dir == 1 ? 0 : 2, StaticObj.BOTTOM_HCENTER);
            }
            else
            {
                GraphicManager.instance.Draw(g, icons[tick], player.x - player.dx * player.dir + xFly * player.dir + dx * player.dir, player.y + 2 * player.dy + yFly * player.dir, player.dir == 1 ? 0 : 2, StaticObj.BOTTOM_HCENTER);
            }
        }

        public int GetX(int x)
        {
            double height = 200;
            double width = 100;
            double num = (double)x;
            return (int)(Math.Sqrt(height * height * (1 - x * x / (width * width))));
        }
    }
}
