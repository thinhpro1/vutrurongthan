using System;
using System.Collections;
using UnityEngine;
using Assets.Scripts.Games;

namespace Assets.Scripts.GraphicCustoms
{
    public class MyGraphics
    {
        public static int HCENTER = 1;

        public static int VCENTER = 2;

        public static int LEFT = 4;

        public static int RIGHT = 8;

        public static int TOP = 16;

        public static int BOTTOM = 32;

        public float r;

        public float g;

        public float b;

        public float a;

        public int clipX;

        public int clipY;

        public int clipW;

        public int clipH;

        private bool isClip;

        private bool isTranslate = true;

        private int translateX;

        private int translateY;

        private float translateXf;

        private float translateYf;

        public static Hashtable cachedTextures = new Hashtable();

        private int clipTX;

        private int clipTY;

        private Vector2 pos = new Vector2(0f, 0f);

        private Rect rect;

        private Matrix4x4 matrixBackup;

        private Vector2 pivot;

        public Vector2 size = new Vector2(128f, 128f);

        public Vector2 relativePosition = new Vector2(0f, 0f);

        private Material lineMaterial;

        // Helper method để vẽ texture với sRGB write đúng cách trong Linear Color Space
        private void DrawTextureWithSRGB(Rect screenRect, Texture texture, Rect sourceRect, int leftBorder, int rightBorder, int topBorder, int bottomBorder)
        {
            bool oldSRGBWrite = GL.sRGBWrite;
            GL.sRGBWrite = true;
            Graphics.DrawTexture(screenRect, texture, sourceRect, leftBorder, rightBorder, topBorder, bottomBorder);
            GL.sRGBWrite = oldSRGBWrite;
        }

        private void DrawTextureWithSRGB(Rect screenRect, Texture texture)
        {
            bool oldSRGBWrite = GL.sRGBWrite;
            GL.sRGBWrite = true;
            Graphics.DrawTexture(screenRect, texture);
            GL.sRGBWrite = oldSRGBWrite;
        }

        private void cache(string key, Texture value)
        {
            if (cachedTextures.Count > 400)
            {
                cachedTextures.Clear();
            }
            if (value.width * value.height < GameCanvas.w * GameCanvas.h)
            {
                cachedTextures.Add(key, value);
            }
        }

        public void Translate(int tx, int ty)
        {
            translateX += tx;
            translateY += ty;
            isTranslate = true;
            if (translateX == 0 && translateY == 0)
            {
                isTranslate = false;
            }
        }

        /*public void translate(float x, float y)
        {
            translateXf += x;
            translateYf += y;
            isTranslate = true;
            if (translateXf == 0f && translateYf == 0f)
            {
                isTranslate = false;
            }
        }*/

        public int getTranslateX()
        {
            return translateX;
        }

        public int getTranslateY()
        {
            return translateY;
        }

        public void SetClip(int x, int y, int w, int h)
        {
            clipTX = translateX;
            clipTY = translateY;
            clipX = x;
            clipY = y;
            clipW = w;
            clipH = h;
            isClip = true;
        }

        public void drawRect(int x, int y, int w, int h)
        {
            int num = 1;
            FillRect(x, y, w, num);
            FillRect(x, y, num, h);
            FillRect(x + w, y, num, h + 1);
            FillRect(x, y + h, w + 1, num);
        }

        public void FillRect(int x, int y, int w, int h)
        {
            if (w < 0 || h < 0)
            {
                return;
            }
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            int num = 1;
            int num2 = 1;
            string key = "fr" + num + num2 + r + g + b + a;
            Texture2D texture2D = (Texture2D)cachedTextures[key];
            if (texture2D == null)
            {
                texture2D = new Texture2D(num, num2);
                Color color = new Color(r, g, b, a);
                texture2D.SetPixel(0, 0, color);
                texture2D.Apply();
                cache(key, texture2D);
            }
            int num3 = 0;
            int num4 = 0;
            int num5 = 0;
            int num6 = 0;
            if (isClip)
            {
                num3 = clipX;
                num4 = clipY;
                num5 = clipW;
                num6 = clipH;
                if (isTranslate)
                {
                    num3 += clipTX;
                    num4 += clipTY;
                }
            }
            if (isClip)
            {
                GUI.BeginGroup(new Rect(num3, num4, num5, num6));
            }
            GUI.DrawTexture(new Rect(x - num3, y - num4, w, h), texture2D);
            if (isClip)
            {
                GUI.EndGroup();
            }
        }

        public void FillRect(int x, int y, int w, int h, int topLeftRadius, int topRightRadius, int bottomLeftRadius, int bottomRightRadius)
        {
            FillRect(x, y, w, h, 10, topLeftRadius, topRightRadius, bottomLeftRadius, bottomRightRadius);
        }

        public void FillRect(int x, int y, int w, int h, int Radius)
        {
            FillRect(x, y, w, h, 10, Radius, Radius, Radius, Radius);
        }

        public void FillRect(int x, int y, int w, int h, int wBorder, int topLeftRadius, int topRightRadius, int bottomLeftRadius, int bottomRightRadius)
        {
            if (w < 0 || h < 0)
            {
                return;
            }
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            int num = 1;
            int num2 = 1;
            string key = "fr" + num + num2 + r + g + b + a;
            Color color = new Color(r, g, b, a);
            Texture2D texture2D = (Texture2D)cachedTextures[key];
            if (texture2D == null)
            {
                texture2D = new Texture2D(num, num2);
                texture2D.SetPixel(0, 0, color);
                texture2D.Apply();
                cache(key, texture2D);
            }
            int num3 = 0;
            int num4 = 0;
            int num5 = 0;
            int num6 = 0;

            Vector4 vector4 = new Vector4(x, y, w - 2 * wBorder, h);
            Vector4 vector5 = new Vector4(topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
            if (isClip)
            {
                num3 = clipX;
                num4 = clipY;
                num5 = clipW;
                num6 = clipH;
                if (isTranslate)
                {
                    num3 += clipTX;
                    num4 += clipTY;
                }
            }
            if (isClip)
            {
                GUI.BeginGroup(new Rect(num3, num4, num5, num6));
            }
            GUI.DrawTexture(new Rect(x - num3, y - num4, w, h), texture2D, ScaleMode.StretchToFill, false, 0f, GUI.color, vector4, vector5);
            if (isClip)
            {
                GUI.EndGroup();
            }
        }

        public void SetColor(int rgb)
        {
            int num = rgb & 0xFF;
            int num2 = (rgb >> 8) & 0xFF;
            int num3 = (rgb >> 16) & 0xFF;
            b = (float)num / 256f;
            g = (float)num2 / 256f;
            r = (float)num3 / 256f;
            a = 1f;
        }

        public void SetColor(float r, float g, float b)
        {
            this.b = b / 256f;
            this.g = g / 256f;
            this.r = r / 256f;
            a = 1f;
        }

        public void SetColor(float r, float g, float b, float a)
        {
            this.b = b / 256f;
            this.g = g / 256f;
            this.r = r / 256f;
            this.a = a;
        }

        public void SetColor(Color color)
        {
            b = color.b;
            g = color.g;
            r = color.r;
            a = color.a;
        }

        public void SetColor(string rgb)
        {
            try
            {
                string[] vs = rgb.Split(',');
                this.r = float.Parse(vs[0]) / 256f;
                this.g = float.Parse(vs[1]) / 256f;
                this.b = float.Parse(vs[2]) / 256f;
                a = 1f;
            }
            catch
            {
                SetColor(0);
            }
        }

        public void SetColor(Color color, float a)
        {
            b = color.b;
            g = color.g;
            r = color.r;
            this.a = a;
        }

        public void drawString(string s, int x, int y, GUIStyle style)
        {
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            int num = 0;
            int num2 = 0;
            int num3 = 0;
            int num4 = 0;
            if (isClip)
            {
                num = clipX;
                num2 = clipY;
                num3 = clipW;
                num4 = clipH;
                if (isTranslate)
                {
                    num += clipTX;
                    num2 += clipTY;
                }
            }
            if (isClip)
            {
                GUI.BeginGroup(new Rect(num, num2, num3, num4));
            }
            GUI.Label(new Rect(x - num, y - num2, Screen.width, 100f), s, style);
            if (isClip)
            {
                GUI.EndGroup();
            }
        }

        public void SetColor(int rgb, float alpha)
        {
            int num = rgb & 0xFF;
            int num2 = (rgb >> 8) & 0xFF;
            int num3 = (rgb >> 16) & 0xFF;
            b = (float)num / 256f;
            g = (float)num2 / 256f;
            r = (float)num3 / 256f;
            a = alpha;
        }

        private void UpdatePos(int anchor)
        {
            Vector2 vector = new Vector2(0f, 0f);
            switch (anchor)
            {
                case 3:
                    vector = new Vector2(size.x / 2f, size.y / 2f);
                    break;
                case 20:
                    vector = new Vector2(0f, 0f);
                    break;
                case 17:
                    vector = new Vector2(Screen.width / 2, 0f);
                    break;
                case 24:
                    vector = new Vector2(Screen.width, 0f);
                    break;
                case 6:
                    vector = new Vector2(0f, Screen.height / 2);
                    break;
                case 10:
                    vector = new Vector2(Screen.width, Screen.height / 2);
                    break;
                case 36:
                    vector = new Vector2(0f, Screen.height);
                    break;
                case 33:
                    vector = new Vector2(Screen.width / 2, Screen.height);
                    break;
                case 40:
                    vector = new Vector2(Screen.width, Screen.height);
                    break;
            }
            pos = vector + relativePosition;
            rect = new Rect(pos.x - size.x * 0.5f, pos.y - size.y * 0.5f, size.x, size.y);
            pivot = new Vector2(rect.xMin + rect.width * 0.5f, rect.yMin + rect.height * 0.5f);
        }

        public void drawRegion(Image arg0, int x0, int y0, int w0, int h0, int arg5, int x, int y, int arg8)
        {
            _drawRegion(arg0, x0, y0, w0, h0, arg5, x, y, arg8);
        }

        public void DrawRegionSmall(Image arg0, int x0, int y0, int w0, int h0, int arg5, int x, int y, int arg8)
        {
            _drawRegion(arg0, x0, y0, w0, h0, arg5, x, y, arg8);
        }

        public void DrawRegionWithTransform(Image arg0, int x0, int y0, int w0, int h0, float arg5, int x, int y, int arg8)
        {
            _drawRegionWithTransform(arg0, x0, y0, w0, h0, arg5, x, y, arg8);
        }

        public void drawRegion(Image arg0, int x0, int y0, int w0, int h0, int arg5, int x, int y, int arg8, bool isClip)
        {
            drawRegion(arg0, x0, y0, w0, h0, arg5, x, y, arg8);
        }

        public void __drawRegion(Image image, int x0, int y0, int w, int h, int transform, float x, float y, int anchor)
        {
            if (image == null)
            {
                return;
            }
            if (isTranslate)
            {
                x += (float)translateX;
                y += (float)translateY;
            }
            float num = w;
            float num2 = h;
            float num3 = 0f;
            float num4 = 0f;
            float num5 = 0f;
            float num6 = 0f;
            float num7 = 1f;
            float num8 = 0f;
            int num9 = 1;
            if ((anchor & HCENTER) == HCENTER)
            {
                num5 -= num / 2f;
            }
            if ((anchor & VCENTER) == VCENTER)
            {
                num6 -= num2 / 2f;
            }
            if ((anchor & RIGHT) == RIGHT)
            {
                num5 -= num;
            }
            if ((anchor & BOTTOM) == BOTTOM)
            {
                num6 -= num2;
            }
            x += num5;
            y += num6;
            int num10 = 0;
            int num11 = 0;
            int num12 = 0;
            int num13 = 0;
            if (isClip)
            {
                num10 = clipX;
                num11 = clipY;
                num12 = clipW;
                num13 = clipH;
                if (isTranslate)
                {
                    num10 += clipTX;
                    num11 += clipTY;
                }
                Rect r = new Rect(x, y, w, h);
                Rect rect = intersectRect(r2: new Rect(num10, num11, num12, num13), r1: r);
                if (rect.width <= 0f || rect.height <= 0f)
                {
                    return;
                }
                num = rect.width;
                num2 = rect.height;
                num3 = rect.x - r.x;
                num4 = rect.y - r.y;
            }
            float num14 = 0f;
            float num15 = 0f;
            switch (transform)
            {
                case 2:
                    num14 += num;
                    num7 = -1f;
                    if (isClip)
                    {
                        if ((float)num10 > x)
                        {
                            num8 = 0f - num3;
                        }
                        else if ((float)(num10 + num12) < x + (float)w)
                        {
                            num8 = 0f - ((float)(num10 + num12) - x - (float)w);
                        }
                    }
                    break;
                case 1:
                    num9 = -1;
                    num15 += num2;
                    break;
                case 3:
                    num9 = -1;
                    num15 += num2;
                    num7 = -1f;
                    num14 += num;
                    break;
            }
            int num16 = 0;
            int num17 = 0;
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                matrixBackup = GUI.matrix;
                size = new Vector2(w, h);
                relativePosition = new Vector2(x, y);
                UpdatePos(3);
                switch (transform)
                {
                    case 6:
                        UpdatePos(3);
                        break;
                    case 5:
                        size = new Vector2(w, h);
                        UpdatePos(3);
                        break;
                }
                switch (transform)
                {
                    case 5:
                        GUIUtility.RotateAroundPivot(90f, pivot);
                        break;
                    case 6:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        break;
                    case 4:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num14 += num;
                        num7 = -1f;
                        if (isClip)
                        {
                            if ((float)num10 > x)
                            {
                                num8 = 0f - num3;
                            }
                            else if ((float)(num10 + num12) < x + (float)w)
                            {
                                num8 = 0f - ((float)(num10 + num12) - x - (float)w);
                            }
                        }
                        break;
                    case 7:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num9 = -1;
                        num15 += num2;
                        break;
                }
            }
            DrawTextureWithSRGB(new Rect(x + num3 + num14 + (float)num16, y + num4 + (float)num17 + num15, num * num7, num2 * (float)num9), image.texture, new Rect(((float)x0 + num3 + num8) / (float)image.texture.width, ((float)image.texture.height - num2 - ((float)y0 + num4)) / (float)image.texture.height, num / (float)image.texture.width, num2 / (float)image.texture.height), 0, 0, 0, 0);
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                GUI.matrix = matrixBackup;
            }
        }

        public void _drawRegion(Image image, float x0, float y0, int w, int h, int transform, int x, int y, int anchor)
        {
            if (image == null)
            {
                return;
            }
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            float num = w;
            float num2 = h;
            float num3 = 0f;
            float num4 = 0f;
            float num5 = 0f;
            float num6 = 0f;
            float num7 = 1f;
            float num8 = 0f;
            int num9 = 1;
            if ((anchor & HCENTER) == HCENTER)
            {
                num5 -= num / 2f;
            }
            if ((anchor & VCENTER) == VCENTER)
            {
                num6 -= num2 / 2f;
            }
            if ((anchor & RIGHT) == RIGHT)
            {
                num5 -= num;
            }
            if ((anchor & BOTTOM) == BOTTOM)
            {
                num6 -= num2;
            }
            x += (int)num5;
            y += (int)num6;
            int num10 = 0;
            int num11 = 0;
            int num12 = 0;
            int num13 = 0;
            if (isClip)
            {
                num10 = clipX;
                num11 = clipY;
                num12 = clipW;
                num13 = clipH;
                if (isTranslate)
                {
                    num10 += clipTX;
                    num11 += clipTY;
                }
                Rect r = new Rect(x, y, w, h);
                Rect rect = intersectRect(r2: new Rect(num10, num11, num12, num13), r1: r);
                if (rect.width <= 0f || rect.height <= 0f)
                {
                    return;
                }
                num = rect.width;
                num2 = rect.height;
                num3 = rect.x - r.x;
                num4 = rect.y - r.y;
            }
            float num14 = 0f;
            float num15 = 0f;
            switch (transform)
            {
                case 2:
                    num14 += num;
                    num7 = -1f;
                    if (isClip)
                    {
                        if (num10 > x)
                        {
                            num8 = 0f - num3;
                        }
                        else if (num10 + num12 < x + w)
                        {
                            num8 = -(num10 + num12 - x - w);
                        }
                    }
                    break;
                case 1:
                    num9 = -1;
                    num15 += num2;
                    break;
                case 3:
                    num9 = -1;
                    num15 += num2;
                    num7 = -1f;
                    num14 += num;
                    break;
            }
            int num16 = 0;
            int num17 = 0;
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                matrixBackup = GUI.matrix;
                size = new Vector2(w, h);
                relativePosition = new Vector2(x, y);
                UpdatePos(3);
                switch (transform)
                {
                    case 6:
                        UpdatePos(3);
                        break;
                    case 5:
                        size = new Vector2(w, h);
                        UpdatePos(3);
                        break;
                }
                switch (transform)
                {
                    case 5:
                        GUIUtility.RotateAroundPivot(90f, pivot);
                        break;
                    case 6:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        break;
                    case 4:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num14 += num;
                        num7 = -1f;
                        if (isClip)
                        {
                            if (num10 > x)
                            {
                                num8 = 0f - num3;
                            }
                            else if (num10 + num12 < x + w)
                            {
                                num8 = -(num10 + num12 - x - w);
                            }
                        }
                        break;
                    case 7:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num9 = -1;
                        num15 += num2;
                        break;
                }
            }
            DrawTextureWithSRGB(new Rect((float)x + num3 + num14 + (float)num16, (float)y + num4 + (float)num17 + num15, num * num7, num2 * (float)num9), image.texture, new Rect((x0 + num3 + num8) / (float)image.texture.width, ((float)image.texture.height - num2 - (y0 + num4)) / (float)image.texture.height, num / (float)image.texture.width, num2 / (float)image.texture.height), 0, 0, 0, 0);

            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                GUI.matrix = matrixBackup;
            }
        }

        public void _drawRegion(Image image, float x0, float y0, int w, int h, int transform, int x, int y, int anchor, int test)
        {
            if (image == null)
            {
                return;
            }
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            float num = w / 6;
            float num2 = h / 6;
            float num3 = 0f;
            float num4 = 0f;
            float num5 = 0f;
            float num6 = 0f;
            float num7 = 1f;
            float num8 = 0f;
            int num9 = 1;
            if ((anchor & HCENTER) == HCENTER)
            {
                num5 -= num / 2f;
            }
            if ((anchor & VCENTER) == VCENTER)
            {
                num6 -= num2 / 2f;
            }
            if ((anchor & RIGHT) == RIGHT)
            {
                num5 -= num;
            }
            if ((anchor & BOTTOM) == BOTTOM)
            {
                num6 -= num2;
            }
            x += (int)num5;
            y += (int)num6;
            int num10 = 0;
            int num11 = 0;
            int num12 = 0;
            int num13 = 0;
            if (isClip)
            {
                num10 = clipX;
                num11 = clipY;
                num12 = clipW;
                num13 = clipH;
                if (isTranslate)
                {
                    num10 += clipTX;
                    num11 += clipTY;
                }
                Rect r = new Rect(x, y, w, h);
                Rect rect = intersectRect(r2: new Rect(num10, num11, num12, num13), r1: r);
                if (rect.width <= 0f || rect.height <= 0f)
                {
                    return;
                }
                num = rect.width;
                num2 = rect.height;
                num3 = rect.x - r.x;
                num4 = rect.y - r.y;
            }
            float num14 = 0f;
            float num15 = 0f;
            switch (transform)
            {
                case 2:
                    num14 += num;
                    num7 = -1f;
                    if (isClip)
                    {
                        if (num10 > x)
                        {
                            num8 = 0f - num3;
                        }
                        else if (num10 + num12 < x + w)
                        {
                            num8 = -(num10 + num12 - x - w);
                        }
                    }
                    break;
                case 1:
                    num9 = -1;
                    num15 += num2;
                    break;
                case 3:
                    num9 = -1;
                    num15 += num2;
                    num7 = -1f;
                    num14 += num;
                    break;
            }
            int num16 = 0;
            int num17 = 0;
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                matrixBackup = GUI.matrix;
                size = new Vector2(w, h);
                relativePosition = new Vector2(x, y);
                UpdatePos(3);
                switch (transform)
                {
                    case 6:
                        UpdatePos(3);
                        break;
                    case 5:
                        size = new Vector2(w, h);
                        UpdatePos(3);
                        break;
                }
                switch (transform)
                {
                    case 5:
                        GUIUtility.RotateAroundPivot(90f, pivot);
                        break;
                    case 6:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        break;
                    case 4:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num14 += num;
                        num7 = -1f;
                        if (isClip)
                        {
                            if (num10 > x)
                            {
                                num8 = 0f - num3;
                            }
                            else if (num10 + num12 < x + w)
                            {
                                num8 = -(num10 + num12 - x - w);
                            }
                        }
                        break;
                    case 7:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num9 = -1;
                        num15 += num2;
                        break;
                }
            }
            DrawTextureWithSRGB(new Rect((float)x + num3 + num14 + (float)num16, (float)y + num4 + (float)num17 + num15, num * num7, num2 * (float)num9), image.texture, new Rect((x0 + num3 + num8) / (float)image.texture.width, ((float)image.texture.height - num2 - (y0 + num4)) / (float)image.texture.height, num / (float)image.texture.width, num2 / (float)image.texture.height), 0, 0, 0, 0);
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                GUI.matrix = matrixBackup;
            }
        }

        public void _drawRegionWithTransform(Image image, float x0, float y0, int w, int h, float angle, int x, int y, int anchor)
        {
            int transform = 5;
            if (image == null)
            {
                return;
            }
            if (isTranslate)
            {
                x += translateX;
                y += translateY;
            }
            float num = w;
            float num2 = h;
            float num3 = 0f;
            float num4 = 0f;
            float num5 = 0f;
            float num6 = 0f;
            float num7 = 1f;
            float num8 = 0f;
            int num9 = 1;
            if ((anchor & HCENTER) == HCENTER)
            {
                num5 -= num / 2f;
            }
            if ((anchor & VCENTER) == VCENTER)
            {
                num6 -= num2 / 2f;
            }
            if ((anchor & RIGHT) == RIGHT)
            {
                num5 -= num;
            }
            if ((anchor & BOTTOM) == BOTTOM)
            {
                num6 -= num2;
            }
            x += (int)num5;
            y += (int)num6;
            int num10 = 0;
            int num11 = 0;
            int num12 = 0;
            int num13 = 0;
            if (isClip)
            {
                num10 = clipX;
                num11 = clipY;
                num12 = clipW;
                num13 = clipH;
                if (isTranslate)
                {
                    num10 += clipTX;
                    num11 += clipTY;
                }
                Rect r = new Rect(x, y, w, h);
                Rect rect = intersectRect(r2: new Rect(num10, num11, num12, num13), r1: r);
                if (rect.width <= 0f || rect.height <= 0f)
                {
                    return;
                }
                num = rect.width;
                num2 = rect.height;
                num3 = rect.x - r.x;
                num4 = rect.y - r.y;
            }
            float num14 = 0f;
            float num15 = 0f;
            switch (transform)
            {
                case 2:
                    num14 += num;
                    num7 = -1f;
                    if (isClip)
                    {
                        if (num10 > x)
                        {
                            num8 = 0f - num3;
                        }
                        else if (num10 + num12 < x + w)
                        {
                            num8 = -(num10 + num12 - x - w);
                        }
                    }
                    break;
                case 1:
                    num9 = -1;
                    num15 += num2;
                    break;
                case 3:
                    num9 = -1;
                    num15 += num2;
                    num7 = -1f;
                    num14 += num;
                    break;
            }
            int num16 = 0;
            int num17 = 0;
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                matrixBackup = GUI.matrix;
                size = new Vector2(w, h);
                relativePosition = new Vector2(x, y);
                UpdatePos(3);
                switch (transform)
                {
                    case 6:
                        UpdatePos(3);
                        break;
                    case 5:
                        size = new Vector2(w, h);
                        UpdatePos(3);
                        break;
                }
                switch (transform)
                {
                    case 5:
                        GUIUtility.RotateAroundPivot(angle, pivot);
                        break;
                    case 6:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        break;
                    case 4:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num14 += num;
                        num7 = -1f;
                        if (isClip)
                        {
                            if (num10 > x)
                            {
                                num8 = 0f - num3;
                            }
                            else if (num10 + num12 < x + w)
                            {
                                num8 = -(num10 + num12 - x - w);
                            }
                        }
                        break;
                    case 7:
                        GUIUtility.RotateAroundPivot(270f, pivot);
                        num9 = -1;
                        num15 += num2;
                        break;
                }
            }
            DrawTextureWithSRGB(new Rect((float)x + num3 + num14 + (float)num16, (float)y + num4 + (float)num17 + num15, num * num7, num2 * (float)num9), image.texture, new Rect((x0 + num3 + num8) / (float)image.texture.width, ((float)image.texture.height - num2 - (y0 + num4)) / (float)image.texture.height, num / (float)image.texture.width, num2 / (float)image.texture.height), 0, 0, 0, 0);
            if (transform == 5 || transform == 6 || transform == 4 || transform == 7)
            {
                GUI.matrix = matrixBackup;
            }
        }

        public void DrawImage(Image image, int x, int y, int anchor)
        {
            if (image != null)
            {
                drawRegion(image, 0, 0, getImageWidth(image), getImageHeight(image), 0, x, y, anchor);
            }
        }

        public void DrawImage(int x, int y, Image image, int transform, int anchor)
        {
            if (image != null)
            {
                drawRegion(image, 0, 0, getImageWidth(image), getImageHeight(image), transform, x, y, anchor);
            }
        }

        public void DrawImage(Image image, int x, int y)
        {
            if (image != null)
            {
                drawRegion(image, 0, 0, getImageWidth(image), getImageHeight(image), 0, x, y, TOP | LEFT);
            }
        }

        public void Reset()
        {
            isClip = false;
            isTranslate = false;
            translateX = 0;
            translateY = 0;
        }

        public Rect intersectRect(Rect r1, Rect r2)
        {
            float num = r1.x;
            float num2 = r1.y;
            float x = r2.x;
            float y = r2.y;
            float num3 = num;
            num3 += r1.width;
            float num4 = num2;
            num4 += r1.height;
            float num5 = x;
            num5 += r2.width;
            float num6 = y;
            num6 += r2.height;
            if (num < x)
            {
                num = x;
            }
            if (num2 < y)
            {
                num2 = y;
            }
            if (num3 > num5)
            {
                num3 = num5;
            }
            if (num4 > num6)
            {
                num4 = num6;
            }
            num3 -= num;
            num4 -= num2;
            if (num3 < -30000f)
            {
                num3 = -30000f;
            }
            if (num4 < -30000f)
            {
                num4 = -30000f;
            }
            return new Rect(num, num2, (int)num3, (int)num4);
        }

        public void drawImageScale(Image image, int x, int y, int w, int h, int tranform)
        {
            GUI.color = Color.red;
            if (image != null)
            {
                DrawTextureWithSRGB(new Rect(x + translateX, y + translateY, (tranform != 0) ? (-w) : w, h), image.texture);
            }
        }

        public void drawImageSimple(Image image, int x, int y)
        {
            if (image != null)
            {
                DrawTextureWithSRGB(new Rect(x, y, image.w, image.h), image.texture);
            }
        }

        public static int getImageWidth(Image image)
        {
            return image.GetWidth();
        }

        public static int getImageHeight(Image image)
        {
            return image.GetHeight();
        }

        public void CreateLineMaterial()
        {
            if (!lineMaterial)
            {
                lineMaterial = new Material(Shader.Find("Specular"));
                lineMaterial.hideFlags = HideFlags.HideAndDontSave;
                lineMaterial.shader.hideFlags = HideFlags.HideAndDontSave;
            }
        }
    }
}
