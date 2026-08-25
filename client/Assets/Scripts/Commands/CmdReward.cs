using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Commands
{
    public class CmdReward : Command
    {
        public int index;

        public string name;

        public string description;

        public int type;

        public List<ItemPanel> items = new List<ItemPanel>();

        public List<Item> gifts = new List<Item>();

        public CmdReward(IActionListener actionListener, int action, object p)
        {
            this.image = Panel.imgBgrGift;
            this.imageClick = Panel.imgBgrGift;
            this.imageFocus = Panel.imgBgrGift;
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            w = image.GetWidth();
            h = image.GetHeight();
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public override void Paint(MyGraphics g)
        {
            g.DrawImage(Panel.imgBgrGift, x, y);
            int dis = 5;
            int hText = MyFont.text_white.GetHeight();
            int yText = y + 20;
            MyFont.text_white.DrawString(g, index + ". " + name, x + 23, yText, 0);
            MyFont.text_white.DrawString(g, "(Hạn nhận: " + description + ")", x + 23, yText + hText + dis, 0);
            int wBtn = Panel.imgBtnSmall.GetWidth();
            int xBtn = x + w - 20 - wBtn;
            int xBonus = 0;
            MyFont myFont = MyFont.text_white;
            if (isClick)
            {
                xBonus += 5;
                myFont = MyFont.text_mini_white;
                g.DrawImage(Panel.imgBtnSmallClick, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            else
            {
                if (isFocus)
                {
                    myFont = MyFont.text_white;
                }
                g.DrawImage(!isFocus ? Panel.imgBtnSmallFocus : Panel.imgBtnSmallFocus, xBtn, y + (h - Panel.imgBtnSmall.GetHeight()) / 2);
            }
            hText = myFont.GetHeight();
            myFont.DrawString(g, "Nhận", xBtn + wBtn / 2, y + (h - hText) / 2, 2);
            int yItem = y + MyFont.text_white.GetHeight() * 2 + dis * 2 + 23;
            for (int i = 0; i < items.Count; i++)
            {
                items[i].item = gifts[i];
                items[i].y = yItem;
                items[i].x = x + 23 + (items[i].w + 6) * i;
                items[i].Paint(g);
            }
        }
    }
}
