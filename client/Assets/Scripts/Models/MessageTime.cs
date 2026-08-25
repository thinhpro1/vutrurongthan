using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System;
using UnityEngine;

namespace Assets.Scripts.Models
{
    public class MessageTime
    {
        public int id;

        public string text;

        public bool isNotClear;

        public long time;

        public DateTime endTime;

        public MessageTime(int id) { this.id = id; }

        public void setInfo(string text, long time)
        {
            if (time == -1)
            {
                isNotClear = true;
            }
            else
            {
                isNotClear = false;
            }
            this.time = time;
            endTime = DateTime.UtcNow.AddMilliseconds(time);
            this.text = text;
        }

        public string GetStrTime()
        {
            long num = time / 1000;
            if (num < 1000)
            {
                return num + "s";
            }
            else if (num < 3600)
            {
                return (num / 60) + "'";
            }
            else
            {
                return (num / 3600) + "h";
            }
        }

        public void Paint(MyGraphics g, int x, int y)
        {
            if (isNotClear)
            {
                MyFont.text_yellow.DrawString(g, text, x, y, 0, MyFont.text_grey);
            }
            else
            {
                MyFont.text_yellow.DrawString(g, text + " " + GetStrTime(), x, y, 0, MyFont.text_grey);
            }
        }

        public void Update()
        {
            time = (endTime.Ticks - DateTime.UtcNow.Ticks) / 10000;
            if (time <= 0 && !isNotClear)
            {
                GameScreen.messageTimes.Remove(this);
            }
        }
    }
}
