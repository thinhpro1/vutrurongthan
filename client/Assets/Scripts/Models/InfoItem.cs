using Assets.Scripts.Entites.Players;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System;

namespace Assets.Scripts.Models
{
    public class InfoItem
    {
        public string info;

        public MyFont f;

        public int speed;

        public InfoItem(string s, MyFont f, int speed)
        {
            this.f = f;
            this.info = s;
            this.speed = speed;
        }
    }
}
