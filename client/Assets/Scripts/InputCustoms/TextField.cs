using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using UnityEngine;

namespace Assets.Scripts.InputCustoms
{
    public class TextField
    {
        public bool isFocus;

        public int x;

        public int y;

        public int w;

        public int h;

        public int maxTextLength = 500;

        public int type;

        public string text = string.Empty;

        private string textPaint = string.Empty;

        public string name = string.Empty;

        public static int TYPE_NORMAL = 0;

        public static int TYPE_PASSWORD = 1;

        public static int TYPE_NUMBER = 2;

        public static int TYPE_USERNAME = 3;

        public static int TYPE_EMAIL = 4;

        private static string numbers = "0123456789";

        private static string passwords = "abcdefghijklmnopqrstuvwxyz0123456789";

        private static string usernames = "abcdefghijklmnopqrstuvwxyz0123456789";

        private static string emails = "abcdefghijklmnopqrstuvwxyz0123456789@.";

        private static Image imgClear = GameCanvas.LoadImage("MainImages/GameCanvas/img_clear_input.png");

        public static Image imgDefault = GameCanvas.LoadImage("MainImages/GameScrs/input_chat_popup");

        public Command command;

        public Image image;

        private TouchScreenKeyboard keyboard;

        public TextField()
        {
            image = imgDefault;
            text = string.Empty;
            type = TYPE_NORMAL;
            isFocus = false;
            w = image.GetWidth();
            h = image.GetHeight();
        }

        public TextField(string name, int tpye)
        {
            this.image = GameCanvas.LoadImage("MainImages/GameScrs/input_chat_popup");
            this.name = name;
            text = string.Empty;
            type = tpye;
            isFocus = false;
            w = this.image.GetWidth();
            h = this.image.GetHeight();
        }

        public TextField(string image)
        {
            this.image = GameCanvas.LoadImage(image);
            text = string.Empty;
            type = TYPE_NORMAL;
            isFocus = false;
            w = this.image.GetWidth();
            h = this.image.GetHeight();
        }

        public virtual void Paint(MyGraphics g)
        {
            g.Reset();
            if (image != null)
            {
                g.DrawImage(image, x, y);
            }
            else
            {
                g.SetColor(223, 116, 20);
                g.FillRect(x, y, w, h, 16);

                if (isFocus)
                {
                    g.SetColor(255, 255, 255);
                }
                else
                {
                    g.SetColor(207, 203, 204);
                }
                g.FillRect(x + 2, y + 2, w - 4, h - 4, 14);
            }


            g.SetClip(x + 2, y + 2, w - 4, h - 4);
            if (text != null && !text.Equals(string.Empty))
            {
                if (isFocus)
                {
                    MyFont.text_input.DrawString(g, textPaint, x + 20, y + (h - MyFont.text_input.GetHeight()) / 2 + (type == TYPE_PASSWORD ? 3 : 0) + 2, 0);
                    g.DrawImage(imgClear, x + w - 45, y + 16);

                }
                else
                {
                    MyFont.text_input.DrawString(g, textPaint, x + 20, y + (h - MyFont.text_input.GetHeight()) / 2 + (type == TYPE_PASSWORD ? 3 : 0) + 2, 0);
                }
            }
            else if (name != null)
            {
                MyFont.text_input_hint.DrawString(g, name, x + 20, y + (h - MyFont.text_input_hint.GetHeight()) / 2 + 2, 0);
            }
            if (isFocus)
            {
                if (GameCanvas.gameTick % 20 > 9)
                {
                    g.SetColor(MyFont.text_input.color);
                    int startX = x + 20;
                    if (text != null && text != string.Empty)
                    {
                        startX = x + MyFont.text_input.GetWidth(textPaint) + 20;
                    }
                    g.FillRect(startX, y + 12, 2, h - 20);
                }
            }

            g.Reset();
        }

        public void ClearAllText()
        {
            text = string.Empty;
            SetTextPaint();
        }

        public void Clear()
        {
            if (text.Length > 0)
            {
                text = text.Substring(0, text.Length - 1);
                SetTextPaint();
            }
        }

        public void SetTextPaint()
        {
            int w = MyFont.text_white.GetWidth(text);
            if (w > this.w - 90)
            {
                for (int i = text.Length - 2; i >= 0; i--)
                {
                    string content = text.Substring(i, text.Length - i);
                    if (MyFont.text_white.GetWidth(content) > this.w - 90)
                    {
                        textPaint = content;
                        break;
                    }
                }
            }
            else
            {
                textPaint = text;
            }
            if (type == TYPE_PASSWORD)
            {
                string pass = "";
                for (int i = 0; i < textPaint.Length; i++)
                {
                    pass += "*";
                }
                textPaint = pass;
            }
        }

        public void PointerClicked(int x, int y)
        {
            if (x >= this.x + w - 40 && x <= this.x + w && y >= this.y && y <= this.y + h)
            {
                ClearAllText();
                isFocus = true;
            }
            else if (x >= this.x && x <= this.x + w - 40 && y >= this.y && y <= this.y + h)
            {
                isFocus = true;
                if (!Main.isPC)
                {
                    //keyboard = TouchScreenKeyboard.Open("", TouchScreenKeyboardType.ASCIICapable);
                    keyboard = TouchScreenKeyboard.Open(text,
                        type == TYPE_NUMBER ? TouchScreenKeyboardType.NumberPad : TouchScreenKeyboardType.ASCIICapable,
                        false, false, type == TYPE_PASSWORD, false, this.name);
                }
            }
            else
            {
                isFocus = false;
                if (keyboard != null)
                {
                    keyboard = null;
                }
            }
        }

        public void KeyPress(KeyCode keyCode)
        {
            if (!isFocus)
            {
                return;
            }
            if (keyCode == KeyCode.Backspace)
            {
                Clear();
                return;
            }
            if ((keyCode == KeyCode.Return || keyCode == KeyCode.KeypadEnter) && command != null)
            {
                command.PerformAction();
            }
            if (Main.isPC)
            {
                try
                {
                    if ((Input.GetKey(KeyCode.LeftControl) || Input.GetKey(KeyCode.RightControl)) && keyCode == KeyCode.V)
                    {
                        TextEditor textEditor = new TextEditor();
                        textEditor.Paste();
                        if (text.Length + textEditor.text.Length <= maxTextLength)
                        {
                            text += textEditor.text;
                        }
                        else
                        {
                            text += textEditor.text.Substring(0, maxTextLength - text.Length);
                        }
                        SetTextPaint();
                        return;
                    }
                }
                catch
                {
                }
            }
            if (MyKeyMap.KeyInputs.Contains(keyCode) && text.Length < maxTextLength)
            {
                char charInput = (char)keyCode;
                if (type == TYPE_NORMAL)
                {
                    text += charInput;
                }
                if (type == TYPE_NUMBER && numbers.Contains(charInput.ToString()))
                {
                    text += charInput;
                }
                if (type == TYPE_USERNAME && usernames.Contains(charInput.ToString()))
                {
                    text += charInput;
                }
                if (type == TYPE_PASSWORD && passwords.Contains(charInput.ToString()))
                {
                    text += charInput;
                }
                if (Main.isPC && type != TYPE_PASSWORD && type != TYPE_USERNAME)
                {
                    text = Vietnamese.GetText(text);
                }
                SetTextPaint();
            }
        }

        public string GetText()
        {
            return text;
        }

        public void SetText(string text)
        {
            if (text != null)
            {
                this.text = text;
                SetTextPaint();
            }
        }

        public void Update()
        {
            if (isFocus && keyboard != null && !Main.isPC)
            {
                string text = keyboard.text;
                SetText(text);
                if (keyboard.status == TouchScreenKeyboard.Status.Done && command != null)
                {
                    command.PerformAction();
                    isFocus = false;
                    keyboard = null;
                }
                if (keyboard.status == TouchScreenKeyboard.Status.Canceled)
                {
                    isFocus = false;
                    keyboard = null;
                }
            }
        }
    }
}
