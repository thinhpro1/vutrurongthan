using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Services;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.InputCustoms
{
    public class ClientInput : IActionListener
    {
        public IChatable chatable;

        public List<TextField> textFields;

        private int x;

        private int y;

        private int yMin;

        private int yMax;

        private bool isClose;

        private string name;

        private int w;

        private int h;

        public bool isShow;

        private Command cmdOk;

        private Command cmdClose;

        private int wTitle;

        public ClientInput()
        {
            cmdOk = new Command("Ok", this, 1, null);
            cmdClose = new Command("Đóng", this, 2, null);
            textFields = new List<TextField>();
        }

        public void Paint(MyGraphics g)
        {
            g.Reset();
            GameCanvas.PaintPopup(g, x, y, w, h);
            foreach (var item in textFields)
            {
                item.Paint(g);
            }
            cmdClose.Paint(g);
            cmdOk.Paint(g);
        }

        public void Init()
        {
            w = 600;
            x = GameCanvas.w / 2 - w / 2;
            y = GameCanvas.h - 20 - cmdOk.h - 10 - 60 - (textFields.Count - 1) * 20 - textFields.Count * textFields[0].h;
            wTitle = MyFont.text_white.GetWidth(name) + 80;
            int h = -20;
            for (int i = 0; i < textFields.Count; i++)
            {
                textFields[i].x = x + (w - textFields[i].w) / 2;
                textFields[i].y = 30 + y + 20 * i + textFields[i].h * i;
                h += textFields[i].h + 20;
            }
            this.h = h + 60;
            cmdOk.x = GameCanvas.w / 2 - cmdOk.w - 10;
            cmdOk.y = y + this.h + 10;
            cmdClose.x = GameCanvas.w / 2 + 10;
            cmdClose.y = cmdOk.y;
        }

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    {
                        Close();
                        if (chatable == null)
                        {
                            Service.ClientInput(textFields);
                        }
                        else
                        {
                            chatable.OnChatFromMe(name, textFields);
                        }
                        break;
                    }
                case 2:
                    {
                        Close();
                        break;
                    }
            }
        }

        public void PointerClicked(int x, int y)
        {
            if (cmdOk.PointerClicked(x, y))
            {
                return;
            }
            if (cmdClose.PointerClicked(x, y))
            {
                return;
            }
            for (int i = 0; i < textFields.Count; i++)
            {
                textFields[i].PointerClicked(x, y);
            }
        }

        public void PointerReleased(int x, int y)
        {
            if (cmdOk.PointerReleased(x, y))
            {
                return;
            }
            if (cmdClose.PointerReleased(x, y))
            {
                return;
            }
        }

        public void PointerMove(int x, int y)
        {
            if (cmdOk.PointerMove(x, y))
            {
                return;
            }
            if (cmdClose.PointerMove(x, y))
            {
                return;
            }
        }

        public void Update()
        {
            foreach (TextField textField in textFields)
            {
                textField.Update();
            }
        }

        public void KeyPress(KeyCode keyCode)
        {
            if (keyCode == KeyCode.Return)
            {
                cmdOk.PerformAction();
                return;
            }
            if (keyCode == KeyCode.F2)
            {
                cmdClose.PerformAction();
                return;
            }
            if (keyCode == KeyCode.Tab)
            {
                for (int i = 0; i < textFields.Count; i++)
                {
                    TextField textField = textFields[i];
                    if (textField.isFocus)
                    {
                        textField.isFocus = false;
                        if (i < textFields.Count - 1)
                        {
                            textFields[i + 1].isFocus = true;
                        }
                        else
                        {
                            textFields[0].isFocus = true;
                        }
                        break;
                    }
                }
                return;
            }
            foreach (TextField textField in textFields)
            {
                textField.KeyPress(keyCode);
            }
        }

        private void Close()
        {
            isShow = false;
        }

        public void Show(string name, List<TextField> textFields)
        {
            this.name = name;
            this.textFields.Clear();
            this.textFields.AddRange(textFields);
            this.textFields[0].isFocus = true;
            Init();
            isShow = true;
        }

    }
}
