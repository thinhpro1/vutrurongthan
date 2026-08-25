using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Dialogs
{
    public abstract class Menu : IActionListener
    {
        public bool isShow;

        public List<CmdMenu> commands;

        public int indexSelect;

        public int x;

        public int y;

        public int w;

        public int h;

        public int xPopup;

        public int yPopup;

        public int wPopup;

        public int hPopup;

        public string title;

        public string[] subTitle;

        public int iconId;

        public int cmxTo;

        public int cmx;

        public int cmxLim;

        public int cmyTo;

        public int cmy;

        public int cmyLim;

        public int xScroll;

        public int yScroll;

        public int wScroll;

        public int hScroll;

        public bool isPointerDownInScroll;

        public int xPointerDown;

        public int yPointerDown;

        public int pointerX;

        public int pointerY;

        public Menu()
        {
        }

        public abstract void StartAt(List<CmdMenu> commands);

        public abstract void StartAt(List<CmdMenu> commands, string title, int iconId);

        public abstract void Update();

        public abstract void Paint(MyGraphics g);

        public abstract void KeyPress(KeyCode keyCode);

        public abstract void PointerClicked(int x, int y);

        public abstract void PointerReleased(int x, int y);

        public abstract void PointerMove(int x, int y);

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    Close();
                    return;
            }
        }

        public abstract void Close();
    }
}
