using Assets.Scripts.Actions;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace Assets.Scripts.Commands
{
    public class CmdPlayerMessage : Command
    {
        public Player player;

        public List<CmdMessage> messages;

        public bool isNew;

        public CmdPlayerMessage(IActionListener actionListener, int action, Player player)
        {
            this.image = Panel.imgBtnMini;
            this.imageClick = Panel.imgBtnMiniClick;
            this.imageFocus = Panel.imgBtnMiniFocus;
            actionId = action;
            this.actionListener = actionListener;
            w = image.GetWidth();
            h = image.GetHeight();
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
            this.player = player;
            messages = new List<CmdMessage>();
            isNew = true;
        }

        public override void Paint(MyGraphics g)
        {
            if (isClick)
            {
                g.DrawImage(imageClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x, y);
            }
            if (isNew && GameCanvas.gameTick % 30 < 15)
            {
                g.DrawImage(GameScreen.imgLight, x + w / 2, y + h / 2, StaticObj.VCENTER_HCENTER);
            }
            if (isClick)
            {
                MyFont.text_small_white.DrawString(g, player.name, x + w / 2, y + (h - MyFont.text_small_white.GetHeight()) / 2, 2);
            }
            else if (isFocus)
            {
                MyFont.text_mini_white.DrawString(g, player.name, x + w / 2, y + (h - MyFont.text_mini_white.GetHeight()) / 2, 2);
            }
            else
            {
                MyFont.text_mini_blue.DrawString(g, player.name, x + w / 2, y + (h - MyFont.text_mini_blue.GetHeight()) / 2, 2);
            }
        }

    }
}
