using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using Assets.Scripts.Tasks;
using System;
using UnityEngine;

namespace Assets.Scripts.Entites.Npcs
{
    public class Npc : Entity
    {
        public NpcTemplate template;

        public int tick;

        public ChatInfo chatInfo;

        public static Image imgQuesion;

        static Npc()
        {
            imgQuesion = GameCanvas.LoadImage("imgQuesion.png");
        }

        public Npc()
        {
            chatInfo = new ChatInfo();
        }

        public override void Paint(MyGraphics g)
        {
            if (Player.isLoadingMap || !IsPaint())
            {
                return;
            }
            if (template.id == 3)
            {
                return;
            }
            try
            {
                GraphicManager.instance.Draw(g, template.icons[frame], x + template.dx, y + template.dy, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
                if (template.id != 3)
                {
                    int num = h + MyFont.text_yellow.GetHeight() + 10;
                    if (Player.me.npcFocus != null && Player.me.npcFocus.Equals(this))
                    {
                        g.DrawImage(GameScreen.imgSelect, x, y + template.dy - h - 15, 3);
                        MyFont.text_yellow.DrawString(g, template.name, x, y + template.dy - (num += 15), 2, MyFont.text_grey);
                    }
                    else
                    {
                        MyFont.text_yellow.DrawString(g, template.name, x, y + template.dy - num, 2, MyFont.text_grey);
                    }
                    try
                    {
                        if (Player.me.task != null)
                        {
                            TaskSub taskSub = Player.me.task.subTasks[Player.me.task.index];
                            if (taskSub.npcId != -1 && taskSub.npcId == template.id)
                            {
                                g.DrawImage(imgQuesion, x, y + template.dy - num - (frame == 0 ? 0 : 2), StaticObj.BOTTOM_HCENTER);
                            }
                        }
                    }
                    catch
                    {
                    }
                }
                chatInfo.Paint(g, x, y - h, dir);
                foreach (Effect effect in effects)
                {
                    effect.Paint(g);
                }
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public override void Update()
        {
            base.UpdateEffect();
            chatInfo.Update();
            tick++;
            if (template.icons.Count == 2)
            {
                if (tick > 30)
                {
                    tick = 0;
                }
                if (tick % 15 < 5)
                {
                    frame = 0;
                }
                else
                {
                    frame = 1;
                }
            }
            else
            {
                if (tick > 5)
                {
                    tick = 0;
                    if (frame >= template.icons.Count - 1)
                    {
                        frame = 0;
                    }
                    else
                    {
                        frame++;
                    }
                }
            }
        }

        public void Init()
        {
            w = template.w;
            h = template.h;
        }

        public void AddChatInfo(string info)
        {
            chatInfo.AddChatInfo(info);
        }
    }
}
