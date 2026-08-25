using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using UnityEngine;

namespace Assets.Scripts.InputCustoms
{
    public class ChatTextField : IActionListener
    {
        public IChatable parentScreen;

        private int x;

        private int y;

        public string name;

        private int w;

        private int h;

        public TextField textField;

        public bool isShow;

        private Command cmdOk;

        private Command cmdClose;

        private Image imgBgr;

        public object p;

        public ChatTextField()
        {
            imgBgr = GameCanvas.LoadImage("MainImages/GameScrs/img_bgr_popup_chat");
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
            x = GameCanvas.w / 2 - w / 2;
            y = GameCanvas.h - 240;

            textField = new TextField("MainImages/GameScrs/input_chat_popup");
            textField.name = "";
            cmdOk = new Command("Ok", this, 1, null);
            cmdClose = new Command("Đóng", this, 2, null);
            Init();
        }

        public virtual void Paint(MyGraphics g)
        {
            g.DrawImage(imgBgr, x, y);

            textField.Paint(g);


            Init();
            cmdClose.Paint(g);
            cmdOk.Paint(g);
        }

        public virtual void Init()
        {
            textField.x = x + 30;
            textField.y = y + 28;

            h = textField.h + 60;

            cmdOk.x = GameCanvas.w / 2 - cmdOk.w - 10;
            cmdOk.y = textField.y + textField.h + 40;

            cmdClose.x = GameCanvas.w / 2 + 10;
            cmdClose.y = textField.y + textField.h + 40;
        }

        public virtual void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    if (parentScreen != null)
                    {
                        parentScreen.OnChatFromMe(textField.GetText());
                        textField.SetText(string.Empty);
                        textField.ClearAllText();
                    }
                    break;
                case 2:
                    Close();
                    break;
            }
        }

        public virtual void PointerClicked(int x, int y)
        {
            if (cmdOk.PointerClicked(x, y))
            {
                return;
            }
            if (cmdClose.PointerClicked(x, y))
            {
                return;
            }
            textField.PointerClicked(x, y);
        }

        public virtual void PointerReleased(int x, int y)
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

        public virtual void PointerMove(int x, int y)
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

        public virtual void Update()
        {
            textField.Update();
        }

        public void Close()
        {
            isShow = false;
        }

        public virtual void KeyPress(KeyCode keyCode)
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
            textField.KeyPress(keyCode);
        }

        public void StartChat(string name, string info)
        {
            if (isShow)
            {
                return;
            }
            this.name = name;
            textField.name = info;
            textField.type = TextField.TYPE_NORMAL;
            textField.maxTextLength = 500;
            textField.isFocus = true;
            isShow = true;
        }

        public void StartChat(string name, string info, int length)
        {
            if (isShow)
            {
                return;
            }
            this.name = name;
            textField.name = info;
            textField.type = TextField.TYPE_NORMAL;
            textField.maxTextLength = length;
            textField.isFocus = true;
            p = null;
            isShow = true;
        }

        public void StartChatNumber(string name, string info, int length)
        {
            if (isShow)
            {
                return;
            }
            this.name = name;
            textField.name = info;
            textField.type = TextField.TYPE_NUMBER;
            textField.maxTextLength = length;
            textField.isFocus = true;
            isShow = true;
        }
    }
}
