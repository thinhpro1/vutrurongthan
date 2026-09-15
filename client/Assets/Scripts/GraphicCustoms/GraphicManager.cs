using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.IOs;
using Assets.Scripts.Services;
using System.Collections;
using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;
using UnityEngine;
using Assets.Scripts.Networks;
using Assets.Scripts.Entites.Players;

namespace Assets.Scripts.GraphicCustoms
{
    public class GraphicManager
    {
        public static GraphicManager instance = new GraphicManager();

        public Dictionary<int, Image> images;

        private readonly object iconDataLock = new object();

        private readonly Dictionary<int, sbyte[]> datas;

        public Dictionary<int, long> timeRequestIcons;

        private volatile int versionImage = -1;

        private int appliedImageVersion = -1;

        public int ImageVersion
        {
            get { return versionImage; }
        }

        public void SetImageVersion(int version)
        {
            versionImage = version;
        }

        public bool isLowGraphic;

        public GraphicManager()
        {
            images = new Dictionary<int, Image>();
            timeRequestIcons = new Dictionary<int, long>();
            datas = new Dictionary<int, sbyte[]>();
        }

        public void Init()
        {
            isLowGraphic = Rms.LoadInt("isLowGraphic") == 1;
            Player.isPaintMedal = !(Rms.LoadInt("isPaintMedal") == 0);
        }

        public void CreateImage(int id)
        {
            try
            {
                if (images.ContainsKey(id))
                {
                    return;
                }
#if UNITY_EDITOR
                Image icon = GameCanvas.LoadImage("SmallImages/" + id);
                if (icon != null)
                {
                    images.Add(id, icon);
                    return;
                }
#endif
                sbyte[] array;
                if (TryTakeRawIconData(id, out array))
                {
                    Image image = Image.createImage(array, 0, array.Length);
                    if (image != null)
                    {
                        images[id] = image;
                    }
                    return;
                }

                long now = Utils.CurrentTimeMillis();
                long time = 0;
                if (timeRequestIcons.ContainsKey(id))
                {
                    time = timeRequestIcons[id];
                }
                else
                {
                    timeRequestIcons.Add(id, 0);
                }
                if (now - time < 5000)
                {
                    return;
                }

                timeRequestIcons[id] = now;
                ServerManager.instance.session.QueueIconRequest(id);
            }
            catch
            {
            }
        }

        public void PublishRawIconData(int id, sbyte[] data)
        {
            if (data == null || data.Length == 0)
            {
                return;
            }

            lock (iconDataLock)
            {
                datas[id] = data;
            }
        }

        private bool TryTakeRawIconData(int id, out sbyte[] data)
        {
            lock (iconDataLock)
            {
                if (!datas.TryGetValue(id, out data))
                {
                    return false;
                }

                datas.Remove(id);
                return true;
            }
        }

        private void EnsureImageVersionApplied()
        {
            int currentVersion = versionImage;
            if (appliedImageVersion == currentVersion)
            {
                return;
            }

            images.Clear();
            lock (iconDataLock)
            {
                datas.Clear();
            }
            timeRequestIcons.Clear();
            appliedImageVersion = currentVersion;
        }

        public void Draw(MyGraphics g, int id, int x, int y, int transform, int anchor)
        {
            if (id == -1)
            {
                return;
            }
            EnsureImageVersionApplied();
            if (images.ContainsKey(id))
            {
                Paint(g, images[id], transform, x, y, anchor);
            }
            else
            {
                CreateImage(id);
            }
        }

        public void Draw(MyGraphics g, int id, int x, int y, float angle)
        {
            if (id == -1)
            {
                return;
            }
            EnsureImageVersionApplied();
            if (images.ContainsKey(id))
            {
                Paint(g, images[id], angle, x, y, 3);
            }
            else
            {
                CreateImage(id);
            }
        }

        public void Draw(MyGraphics g, int id, int x, int y)
        {
            if (id == -1)
            {
                return;
            }
            EnsureImageVersionApplied();
            if (images.ContainsKey(id))
            {
                Paint(g, images[id], 0, x, y, 3);
            }
            else
            {
                CreateImage(id);
            }
        }

        public void Draw(MyGraphics g, int id, int x, int y, float angle, int anchor)
        {
            if (id == -1)
            {
                return;
            }
            EnsureImageVersionApplied();
            if (images.ContainsKey(id))
            {
                Paint(g, images[id], angle, x, y, anchor);
            }
            else
            {
                CreateImage(id);
            }
        }

        public void Paint(MyGraphics g, Image image, int transform, int x, int y, int anchor)
        {
            if (image == null)
            {
                return;
            }
            g.DrawRegionSmall(image, 0, 0, image.GetWidth(), image.GetHeight(), transform, x, y, anchor);
        }

        public void Paint(MyGraphics g, Image image, float angle, int x, int y, int anchor)
        {
            if (image == null)
            {
                return;
            }
            g.DrawRegionWithTransform(image, 0, 0, image.GetWidth(), image.GetHeight(), angle, x, y, anchor);
        }

        public string Decrypt(string text, string key)
        {
            byte[] array = Convert.FromBase64String(text);
            byte[] bytes = new TripleDESCryptoServiceProvider
            {
                Key = new MD5CryptoServiceProvider().ComputeHash(Encoding.UTF8.GetBytes(key)),
                Mode = CipherMode.ECB,
                Padding = PaddingMode.PKCS7
            }.CreateDecryptor().TransformFinalBlock(array, 0, array.Length);
            return Encoding.UTF8.GetString(bytes);
        }

        public string HexToString(string text)
        {
            byte[] array = new byte[text.Length / 2];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = Convert.ToByte(text.Substring(i * 2, 2), 16);
            }
            return Encoding.UTF8.GetString(array);
        }

        public string Encrypt(string toEncrypt, string key)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(toEncrypt);
            ICryptoTransform cryptoTransform = new TripleDESCryptoServiceProvider
            {
                Key = new MD5CryptoServiceProvider().ComputeHash(Encoding.UTF8.GetBytes(key)),
                Mode = CipherMode.ECB,
                Padding = PaddingMode.PKCS7
            }.CreateEncryptor();
            byte[] array = cryptoTransform.TransformFinalBlock(bytes, 0, bytes.Length);
            return Convert.ToBase64String(array, 0, array.Length);
        }

        public string StringToHex(string str)
        {
            StringBuilder stringBuilder = new StringBuilder();
            byte[] bytes = Encoding.UTF8.GetBytes(str);
            foreach (byte b in bytes)
            {
                stringBuilder.Append(b.ToString("X2"));
            }
            return stringBuilder.ToString();
        }


    }


}
