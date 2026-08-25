using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using System;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class ServerListScreen : MyScreen, IActionListener
    {
        public List<Command> servers;

        public ServerListScreen(ScreenManager screenManager) : base(screenManager)
        {
            servers = new List<Command>();
        }

        public void Init()
        {
            servers.Clear();
            int count = ServerManager.instance.servers.Count;
            for (int i = 0; i < count; i++)
            {
                Server server = ServerManager.instance.servers[i];
                servers.Add(new Command(server.name, this, 1, i));
            }
            int num_w = Math.Min(count, 3);
            int x = (ScreenManager.instance.w - num_w * servers[0].w - (count - 1) * 20) / 2;
            int y = (ScreenManager.instance.h - servers[0].h * (count / 3 + 1) - 10 * count / 3) / 2;
            for (int i = 0; i < count; i++)
            {
                servers[i].x = x + (servers[i].w + 20) * (i % 3);
                servers[i].y = y + (servers[i].h + 10) * (i / 3);
            }
        }

        public override void SwitchToMe()
        {
            Init();
            base.SwitchToMe();
            SoundMn.playSoundBgr();
        }

        public override void KeyPress(KeyCode keyCode)
        {
        }

        public override void Paint(MyGraphics g)
        {
            ScreenManager.instance.PaintBackground(g);
            if (!DisplayManager.instance.dialog.isShow && !GameCanvas.menu.isShow)
            {
                for (int i = 0; i < servers.Count; i++)
                {
                    servers[i].Paint(g);
                }
            }
        }

        public override void PointerClicked(int x, int y)
        {
            for (int i = 0; i < servers.Count; i++)
            {
                if (servers[i].PointerClicked(x, y))
                {
                    return;
                }
            }
        }

        public override void PointerMove(int x, int y)
        {
            for (int i = 0; i < servers.Count; i++)
            {
                if (servers[i].PointerMove(x, y))
                {
                    return;
                }
            }
        }

        public override void PointerReleased(int x, int y)
        {
            for (int i = 0; i < servers.Count; i++)
            {
                if (servers[i].PointerReleased(x, y))
                {
                    return;
                }
            }
        }

        public override void Update()
        {

        }

        public void Perform(int actionId, object p)
        {
            if (actionId == 1)
            {
                int index = (int)p;
                if (ServerManager.instance.indexServer == index && ServerManager.instance.session.IsConnected())
                {
                    if (ScreenManager.instance.currentScreen is ServerListScreen)
                    {
                        ScreenManager.instance.loginScreen.SwitchToMe();
                    }
                }
                else
                {
                    if (ServerManager.instance.session.IsConnected())
                    {
                        ServerManager.instance.session.Close();
                    }
                    Rms.SaveInt("index_server", index);
                    ServerManager.instance.indexServer = index;
                    ServerManager.instance.Connect();
                }
            }
        }

    }
}
