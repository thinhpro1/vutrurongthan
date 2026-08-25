using Assets.Scripts.Actions;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Items;
using Assets.Scripts.Screens;
using System;
using UnityEngine;

namespace Assets.Scripts.Commands
{
    public class ItemPanel : Command
    {
        public Item item;

        public ItemPanel()
        {
            w = Panel.imgBgrItem.GetWidth();
            h = Panel.imgBgrItem.GetHeight();
        }

        public ItemPanel(IActionListener actionListener, int action, object p)
        {
            w = Panel.imgBgrItem.GetWidth();
            h = Panel.imgBgrItem.GetHeight();
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            isShow = true;
        }

        public override void Paint(MyGraphics g)
        {
            int upgrade = 0;
            if (item != null)
            {
                upgrade = item.GetUpgrade();
            }
            if (upgrade == 0)
            {
                g.SetColor(Color.black, 0.5f);
                g.FillRect(x, y, 54, 54, 8);
            }
            if (item != null)
            {
                item.Paint(g, x, y, w, h, upgrade);
            }
            if (isClick)
            {
                g.DrawImage(Panel.imgSelectItem, x - 5, y - 5);
            }
        }

        public void Paint(MyGraphics g, string name)
        {
            Paint(g);
            /*if (item == null)
            {
                string[] vs = MyFont.text_mini_white.SplitFontArray(name, w);
                if (vs.Length == 1)
                {
                    MyFont.text_mini_white.DrawString(g, name, x + w / 2, y + (h - MyFont.text_mini_white.GetHeight()) / 2, 2);
                }
                else if (vs.Length == 2)
                {
                    MyFont.text_mini_white.DrawString(g, vs[0], x + w / 2, y + 5, 2);
                    MyFont.text_mini_white.DrawString(g, vs[1], x + w / 2, y + 28, 2);
                    *//* int yStart = y + (h - MyFont.text_mini_white.GetHeight() * 2 - 8) / 2;
                     for (int i = 0; i < 2; i++)
                     {
                         MyFont.text_mini_white.DrawString(g, vs[i], x + w / 2, yStart + (MyFont.text_mini_white.GetHeight() + 8) * i, 2);
                     }
 *//*
                }
            }*/
        }
    }
}
