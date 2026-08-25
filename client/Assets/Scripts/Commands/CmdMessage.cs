using Assets.Scripts.Actions;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Frames;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using UnityEngine;

namespace Assets.Scripts.Commands
{
    public class CmdMessage : Command
    {
        public Player player;

        public DateTime createTime;

        public string message;

        private string[] subMessage;

        private int wScroll;

        private int hScroll;

        private int xScroll;

        private int yScroll;

        public bool isServer;

        public CmdMessage(IActionListener actionListener, int action, Player player, string message)
        {
            actionId = action;
            this.actionListener = actionListener;
            wScroll = 400;
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
            createTime = DateTime.Now;
            this.message = message;
            this.player = player;
            subMessage = MyFont.text_white.SplitFontArray(message, wScroll - 20);
            int max = MyFont.text_white.GetWidth(subMessage[0]);
            for (int i = 0; i < subMessage.Length; i++)
            {
                int width = MyFont.text_white.GetWidth(subMessage[i]);
                if (max < width)
                {
                    max = width;
                }
            }
            wScroll = max + 20;
            if (player.id == Player.me.id)
            {
                w = wScroll;
                hScroll = 14 + subMessage.Length * MyFont.text_white.GetHeight() + (subMessage.Length - 1) * 7;
            }
            else
            {
                int wName = MyFont.text_green.GetWidth(player.name);
                if (max < wName)
                {
                    wScroll = wName + 20;
                }
                hScroll = MyFont.text_green.GetHeight() + 14 + subMessage.Length * MyFont.text_white.GetHeight() + (subMessage.Length - 1) * 7;
                w = Panel.imgGender[3].GetWidth() + 10 + wScroll;
            }
            h = hScroll;
            if (player.name == "Thông báo Server")
            {
                isServer = true;
            }
            this._object = player;
        }

        public override void Paint(MyGraphics g)
        {
            try
            {
                if (Player.me.id == player.id && !isServer)
                {
                    xScroll = x;
                    yScroll = y;
                    g.SetColor(84, 238, 255, 0.7f);
                    g.FillRect(xScroll, yScroll, wScroll, hScroll, 8);
                    g.SetColor(Color.black, 0.7f);
                    g.FillRect(xScroll + 2, yScroll + 2, wScroll - 4, hScroll - 4, 6);
                    int h_Text = MyFont.text_white.GetHeight();
                    for (int i = 0; i < subMessage.Length; i++)
                    {
                        MyFont.text_white.DrawString(g, subMessage[i], xScroll + 10, yScroll + 7 + (h_Text + 7) * i, 0);
                    }
                    MyFont.text_mini_white.DrawString(g, createTime.ToString("HH:mm"), xScroll - 5, y + 5, 1);
                    return;
                }
                if (player.id == 0)
                {
                    g.DrawImage(Panel.imgGender[3], x, y);
                }
                else if (player.head != null && player.head.template.chat != -1)
                {
                    GraphicManager.instance.Draw(g, player.head.template.chat, x, y, 0, StaticObj.TOP_LEFT);
                }

                xScroll = x + Panel.imgGender[3].GetWidth() + 10;
                yScroll = y;
                g.SetColor(84, 238, 255, 0.7f);
                g.FillRect(xScroll, yScroll, wScroll, hScroll, 8);
                g.SetColor(Color.black, 0.7f);
                g.FillRect(xScroll + 2, yScroll + 2, wScroll - 4, hScroll - 4, 6);

                MyFont.text_green.DrawString(g, player.name, xScroll + 10, yScroll + 7, 0);
                int hText = MyFont.text_white.GetHeight();
                int hTextName = MyFont.text_green.GetHeight();
                for (int i = 0; i < subMessage.Length; i++)
                {
                    MyFont.text_white.DrawString(g, subMessage[i], xScroll + 10, yScroll + 7 + hTextName + (hText + 7) * i, 0);
                }
                MyFont.text_mini_white.DrawString(g, createTime.ToString("HH:mm"), xScroll + wScroll + 5, y + 5, 0);
            }
            catch (Exception ex)
            {
                Debug.Log(ex);
            }

        }
    }
}
