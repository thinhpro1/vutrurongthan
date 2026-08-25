using Assets.Scripts.Games;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.GraphicCustoms
{
    public class MyFont
    {
        public static MyFont text_big_white;

        public static MyFont text_big_yellow;

        public static MyFont text_big_blue;

        public static MyFont text_big_red;

        public static MyFont text_big_green;

        public static MyFont text_big_black;

        public static MyFont text_big_grey;

        public static MyFont text_big_brown;

        public static MyFont text_black;

        public static MyFont text_red;

        public static MyFont text_green;

        public static MyFont text_green_2;

        public static MyFont text_blue;

        public static MyFont text_brown;

        public static MyFont text_white;

        public static MyFont text_yellow;

        public static MyFont text_orange;

        public static MyFont text_grey;

        public static MyFont text_fly_red;

        public static MyFont text_fly_blue;

        public static MyFont text_fly_yellow;

        public static MyFont text_fly_white;

        public static MyFont text_fly_green;

        public static MyFont text_fly_black;

        public static MyFont text_fly_orange;

        public static MyFont text_violet;

        public static MyFont text_mini_brown;

        public static MyFont text_mini_white;

        public static MyFont text_mini_yellow;

        public static MyFont text_mini_black;

        public static MyFont text_mini_blue;

        public static MyFont text_mini_orange;

        public static MyFont text_mini_red;

        public static MyFont text_mini_grey;

        public static MyFont text_mini_green;

        public static MyFont text_small_brown;

        public static MyFont text_small_white;

        public static MyFont text_small_yellow;

        public static MyFont text_small_black;

        public static MyFont text_small_blue;

        public static MyFont text_small_orange;

        public static MyFont text_small_red;

        public static MyFont text_small_grey;

        public static MyFont text_small_green;

        public static MyFont text_input;

        public static MyFont text_input_hint;

        public Font myFont;

        private int height;

        private int wO;

        public Color color = Color.white;

        public int type;

        private int id;

        public static int[] colorJava = new int[31]
        {
            0, 16711680, 6520319, 16777215, 16755200, 5449989, 21285, 52224, 7386228, 16771788,
            0, 65535, 21285, 16776960, 5592405, 16742263, 33023, 8701737, 15723503, 7999781,
            16768815, 14961237, 4124899, 4671303, 16096312, 16711680, 16755200, 52224, 16777215, 6520319,
            16096312
        };

        static MyFont()
        {
            text_mini_orange = new MyFont(new Color32(223, 116, 20, 255), 3);

            text_green_2 = new MyFont(SetColor(8701737), 1);

            text_orange = new MyFont(new Color32(223, 116, 20, 255), 1);
            text_input = new MyFont(new Color32(49, 140, 194, 255), 1);
            text_input_hint = new MyFont(new Color32(129, 159, 177, 255), 1);

            text_big_white = new MyFont(0, 0);
            text_big_yellow = new MyFont(1, 0);
            text_big_black = new MyFont(2, 0);
            text_big_red = new MyFont(3, 0);
            text_big_grey = new MyFont(4, 0);
            text_big_brown = new MyFont(5, 0);
            text_big_blue = new MyFont(6, 0);
            text_big_green = new MyFont(7, 0);

            text_white = new MyFont(0, 1);
            text_yellow = new MyFont(1, 1);
            text_black = new MyFont(2, 1);
            text_red = new MyFont(3, 1);
            text_grey = new MyFont(4, 1);
            text_brown = new MyFont(5, 1);
            text_blue = new MyFont(6, 1);
            text_green = new MyFont(7, 1);

            text_small_white = new MyFont(0, 2);
            text_small_yellow = new MyFont(1, 2);
            text_small_black = new MyFont(2, 2);
            text_small_red = new MyFont(3, 2);
            text_small_grey = new MyFont(4, 2);
            text_small_brown = new MyFont(5, 2);
            text_small_blue = new MyFont(6, 2);
            text_small_green = new MyFont(7, 2);

            text_mini_white = new MyFont(0, 3);
            text_mini_yellow = new MyFont(1, 3);
            text_mini_black = new MyFont(2, 3);
            text_mini_red = new MyFont(3, 3);
            text_mini_grey = new MyFont(4, 3);
            text_mini_brown = new MyFont(5, 3);
            text_mini_blue = new MyFont(6, 3);
            text_mini_green = new MyFont(7, 3);

            text_fly_red = new MyFont(0, 4);
            text_fly_yellow = new MyFont(1, 4);
            text_fly_green = new MyFont(2, 4);
            text_fly_white = new MyFont(3, 4);
            text_fly_blue = new MyFont(4, 4);
            text_fly_orange = new MyFont(5, 4);
            text_fly_black = new MyFont(6, 4);
        }

        public MyFont(int id, int type)
        {
            this.id = id;
            this.type = type;
            string path = "Fonts/font_fly_text";
            if (type == 0)
            {
                path = "Fonts/font_normal_big";
                switch (id)
                {
                    case 0:
                        //this.color = SetColor(15723503); // white
                        this.color = Color.white; // white
                        break;
                    case 1:
                        this.color = SetColor(16776960); // yellow
                        break;
                    case 2:
                        this.color = SetColor(0); // black
                        break;
                    case 3:
                        this.color = SetColor(16711680); // red
                        break;
                    case 4:
                        this.color = SetColor(4671303); // grey
                        break;
                    case 5:
                        this.color = new Color32(119, 69, 6, 255); // brown
                        break;
                    case 6:
                        this.color = new Color32(58, 210, 248, 255); // blue
                        break;
                    case 7:
                        this.color = new Color(0, 0.75f, 0); // green
                        break;
                }
            }
            else if (type == 1)
            {
                path = "Fonts/font_normal";
                switch (id)
                {
                    case 0:
                        //this.color = SetColor(15723503); // white
                        this.color = Color.white; // white
                        break;
                    case 1:
                        this.color = SetColor(16776960); // yellow
                        break;
                    case 2:
                        this.color = SetColor(0); // black
                        break;
                    case 3:
                        this.color = SetColor(16711680); // red
                        break;
                    case 4:
                        this.color = SetColor(4671303); // grey
                        break;
                    case 5:
                        this.color = new Color32(119, 69, 6, 255); // brown
                        break;
                    case 6:
                        this.color = new Color32(58, 210, 248, 255); // blue
                        break;
                    case 7:
                        this.color = new Color(0, 0.75f, 0); // green
                        break;
                }
            }
            else if (type == 2)
            {
                path = "Fonts/font_normal_small";
                switch (id)
                {
                    case 0:
                        //this.color = SetColor(15723503); // white
                        this.color = Color.white; // white
                        break;
                    case 1:
                        this.color = SetColor(16776960); // yellow
                        break;
                    case 2:
                        this.color = SetColor(0); // black
                        break;
                    case 3:
                        this.color = SetColor(16711680); // red
                        break;
                    case 4:
                        this.color = SetColor(4671303); // grey
                        break;
                    case 5:
                        this.color = new Color32(119, 69, 6, 255); // brown
                        break;
                    case 6:
                        this.color = new Color32(58, 210, 248, 255); // blue
                        break;
                    case 7:
                        this.color = new Color(0, 0.75f, 0); // green
                        break;
                }
            }
            else if (type == 3)
            {
                path = "Fonts/font_normal_mini";
                switch (id)
                {
                    case 0:
                        //this.color = SetColor(15723503); // white
                        this.color = Color.white; // white
                        break;
                    case 1:
                        this.color = SetColor(16776960); // yellow
                        break;
                    case 2:
                        this.color = SetColor(0); // black
                        break;
                    case 3:
                        this.color = SetColor(16711680); // red
                        break;
                    case 4:
                        this.color = SetColor(4671303); // grey
                        break;
                    case 5:
                        this.color = new Color32(119, 69, 6, 255); // brown
                        break;
                    case 6:
                        this.color = new Color32(58, 210, 248, 255); // blue
                        break;
                }
            }
            else if (type == 4)
            {
                path = "Fonts/font_fly_text";
                this.color = BorderTextColor(id);
            }
            myFont = (Font)Resources.Load(path);
            wO = getWidthExactOf("o");
        }

        public MyFont(Color color, string path)
        {
            myFont = (Font)Resources.Load(path);
            this.color = color;
            wO = getWidthExactOf("o");
            type = 4;
        }

        public MyFont(Color color, int type)
        {
            this.type = type;
            string file = "Fonts/normal"; // type 0
            if (type == 1)
            {
                file = "Fonts/font_normal";
            }
            else if (type == 2)
            {
                file = "Fonts/normal";
            }
            else if (type == 3)
            {
                file = "Fonts/normal";
            }
            myFont = (Font)Resources.Load(file);
            this.color = color;
            wO = getWidthExactOf("o");
        }

        public void setHeight(int height)
        {
            this.height = height;
        }

        public void DrawString(MyGraphics g, string st, int x, int y, int align)
        {
            SetTypePaint(g, st, x, y, align, null);
        }

        public void DrawString(MyGraphics g, string st, int x, int y, int align, MyFont font)
        {
            SetTypePaint(g, st, x, y + 2, align, font);
            SetTypePaint(g, st, x, y, align, null);
        }

        public void DrawStringBorder(MyGraphics g, string st, int x, int y, int align)
        {
            SetTypePaint(g, st, x - 1, y - 1, align, null);
            SetTypePaint(g, st, x - 1, y + 1, align, null);
            SetTypePaint(g, st, x + 1, y - 1, align, null);
            SetTypePaint(g, st, x + 1, y + 1, align, null);
            SetTypePaint(g, st, x, y - 1, align, null);
            SetTypePaint(g, st, x, y + 1, align, null);
            SetTypePaint(g, st, x + 1, y, align, null);
            SetTypePaint(g, st, x - 1, y, align, null);
            SetTypePaint(g, st, x, y, align, null);
        }

        private Color BorderTextColor(int id)
        {
            Color[] array = new Color[7]
            {
                Color.red,
                Color.yellow,
                Color.green,
                Color.white,
                SetColor(40404),
                Color.red,
                Color.black
            };
            return array[id];
        }

        public List<string> splitFontVector(string src, int lineWidth)
        {
            List<string> myVector = new List<string>();
            string text = string.Empty;
            for (int i = 0; i < src.Length; i++)
            {
                if (src[i] == '\n' || src[i] == '\b')
                {
                    myVector.Add(text);
                    text = string.Empty;
                    continue;
                }
                text += src[i];
                if (GetWidth(text) > lineWidth)
                {
                    int num = 0;
                    num = text.Length - 1;
                    while (num >= 0 && text[num] != ' ')
                    {
                        num--;
                    }
                    if (num < 0)
                    {
                        num = text.Length - 1;
                    }
                    myVector.Add(text.Substring(0, num));
                    i = i - (text.Length - num) + 1;
                    text = string.Empty;
                }
                if (i == src.Length - 1 && !text.Trim().Equals(string.Empty))
                {
                    myVector.Add(text);
                }
            }
            return myVector;
        }

        public static Color SetColor(int rgb)
        {
            int num = rgb & 0xFF;
            int num2 = (rgb >> 8) & 0xFF;
            int num3 = (rgb >> 16) & 0xFF;
            float b = (float)num / 256f;
            float g = (float)num2 / 256f;
            float r = (float)num3 / 256f;
            float a = 1f;
            return new Color(r, g, b, a);
        }

        public string[] SplitFontArray(string src, int lineWidth)
        {
            List<string> myVector = splitFontVector(src, lineWidth);
            string[] array = new string[myVector.Count];
            for (int i = 0; i < myVector.Count; i++)
            {
                array[i] = myVector[i];
            }
            return array;
        }

        public int GetWidth(string s)
        {
            return getWidthExactOf(s);
        }

        public int getWidthExactOf(string s)
        {
            try
            {
                GUIStyle gUIStyle = new GUIStyle();
                gUIStyle.font = myFont;
                return (int)gUIStyle.CalcSize(new GUIContent(s)).x;
            }
            catch
            {
                return getWidthNotExactOf(s);
            }
        }

        public int getWidthNotExactOf(string s)
        {
            return s.Length * wO;
        }

        public int GetHeight()
        {
            if (height > 0)
            {
                return height;
            }
            GUIStyle gUIStyle = new GUIStyle();
            gUIStyle.font = myFont;
            try
            {
                height = (int)gUIStyle.CalcSize(new GUIContent("Ag")).y;
            }
            catch
            {
                height = 24;
            }
            return height;
        }

        public void _drawString(MyGraphics g, string st, int x0, int y0, int align)
        {
            GUIStyle gUIStyle = new GUIStyle(GUI.skin.label);
            gUIStyle.font = myFont;
            if (type == 0)
            {
                y0 -= 5;
            }
            else if (type == 1)
            {
                y0 -= 5;
            }
            else if (type == 2)
            {
                y0 -= 5;
            }
            else if (type == 3)
            {
                y0 -= 5;
                gUIStyle.fontSize = 18;
            }
            float num = 0f;
            float num2 = 0f;
            switch (align)
            {
                case 0:
                    num = x0;
                    num2 = y0;
                    gUIStyle.alignment = TextAnchor.UpperLeft;
                    break;
                case 1:
                    num = x0 - GameCanvas.w;
                    num2 = y0;
                    gUIStyle.alignment = TextAnchor.UpperRight;
                    break;
                case 2:
                case 3:
                    num = x0 - GameCanvas.w / 2;
                    num2 = y0;
                    gUIStyle.alignment = TextAnchor.UpperCenter;
                    break;
            }
            gUIStyle.normal.textColor = color;
            g.drawString(st, (int)num, (int)num2, gUIStyle);
        }

        public void SetTypePaint(MyGraphics g, string st, int x, int y, int align, MyFont font)
        {
            x--;
            Color root = this.color;
            if (type == 4)
            {
                Color[] array = new Color[6]
                {
                    SetColor(6029312),
                    SetColor(7169025),
                    SetColor(7680),
                    SetColor(0),
                    SetColor(9264),
                    SetColor(6029312)
                };
                this.color = array[id];
                _drawString(g, st, x + 1, y, align);
                _drawString(g, st, x - 1, y, align);
                _drawString(g, st, x, y - 1, align);
                _drawString(g, st, x, y + 1, align);
                _drawString(g, st, x + 1, y + 1, align);
                _drawString(g, st, x + 1, y - 1, align);
                _drawString(g, st, x - 1, y - 1, align);
                _drawString(g, st, x - 1, y + 1, align);
                this.color = BorderTextColor(id);
            }
            else if (font != null)
            {
                this.color = font.color;
            }
            _drawString(g, st, x, y, align);
            this.color = root;
        }
    }
}
