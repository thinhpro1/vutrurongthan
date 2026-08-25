using Assets.Scripts.Commons;
using Assets.Scripts.Networks;
using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.IOs
{
    public class Rms
    {
        public static int status;

        public static sbyte[] data;

        public static string filename;

        private const int INTERVAL = 5;

        private const int MAXTIME = 500;

        public static string persistentDataPath;

        public static void Save(string filename, sbyte[] data)
        {
            __saveRMS(filename, data);
            /*if (Thread.CurrentThread.Name == Main.mainThreadName)
            {
                __saveRMS(filename, data);
            }
            else
            {
                _saveRMS(filename, data);
            }*/
        }

        public static sbyte[] Load(string filename)
        {
            return __loadRMS(filename);
            /*if (Thread.CurrentThread.Name == Main.mainThreadName)
            {
                return __loadRMS(filename);
            }
            return _loadRMS(filename);*/
        }

        public static string LoadString(string fileName)
        {
            sbyte[] array = Load(fileName);
            if (array == null)
            {
                return null;
            }
            DataInputStream dataInputStream = new DataInputStream(array);
            try
            {
                string result = dataInputStream.readUTF();
                dataInputStream.close();
                return result;
            }
            catch (Exception ex)
            {
            }
            return null;
        }

        public static byte[] convertSbyteToByte(sbyte[] var)
        {
            byte[] array = new byte[var.Length];
            for (int i = 0; i < var.Length; i++)
            {
                if (var[i] > 0)
                {
                    array[i] = (byte)var[i];
                }
                else
                {
                    array[i] = (byte)(var[i] + 256);
                }
            }
            return array;
        }

        public static void SaveString(string filename, string data)
        {
            DataOutputStream dataOutputStream = new DataOutputStream();
            try
            {
                dataOutputStream.writeUTF(data);
                Save(filename, dataOutputStream.toByteArray());
                dataOutputStream.close();
            }
            catch (Exception ex)
            {
            }
        }

        public static void update()
        {
            if (status == 2)
            {
                status = 1;
                __saveRMS(filename, data);
                status = 0;
            }
            else if (status == 3)
            {
                status = 1;
                data = __loadRMS(filename);
                status = 0;
            }
        }

        public static int LoadInt(string file)
        {
            sbyte[] array = Load(file);
            return (array != null) ? array[0] : (-1);
        }

        public static void SaveInt(string file, int x)
        {
            try
            {
                Save(file, new sbyte[1] { (sbyte)x });
            }
            catch (Exception)
            {
            }
        }

        public static string GetDocumentsPath()
        {
            return persistentDataPath;
        }

        private static void __saveRMS(string filename, sbyte[] data)
        {
            try
            {
                filename = StringToHex(Encrypt(filename, ServerManager.VERSION)).ToLower();
                string text = GetDocumentsPath() + "/" + filename;
                FileStream fileStream = new FileStream(text, FileMode.Create);
                fileStream.Write(Utils.Cast(data), 0, data.Length);
                fileStream.Flush();
                fileStream.Close();
            }
            catch (Exception ex)
            {
            }
        }

        private static sbyte[] __loadRMS(string filename)
        {
            try
            {
                filename = StringToHex(Encrypt(filename, ServerManager.VERSION)).ToLower();
                FileStream fileStream = new FileStream(GetDocumentsPath() + "/" + filename, FileMode.Open);
                byte[] array = new byte[fileStream.Length];
                fileStream.Read(array, 0, array.Length);
                fileStream.Close();
                sbyte[] array2 = Utils.Cast(array);
                return Utils.Cast(array);
            }
            catch (Exception ex)
            {
                return null;
            }
        }


        public static string ByteArrayToString(byte[] ba)
        {
            string text = BitConverter.ToString(ba);
            return text.Replace("-", string.Empty);
        }

        public static byte[] StringToByteArray(string hex)
        {
            int length = hex.Length;
            byte[] array = new byte[length / 2];
            for (int i = 0; i < length; i += 2)
            {
                array[i / 2] = Convert.ToByte(hex.Substring(i, 2), 16);
            }
            return array;
        }

        public static void ClearAll()
        {
            FileInfo[] files = new DirectoryInfo(GetDocumentsPath() + "/").GetFiles();
            foreach (FileInfo fileInfo in files)
            {
                fileInfo.Delete();
            }
        }

        public static void deleteRecord(string name)
        {
            try
            {
                PlayerPrefs.DeleteKey(name);
            }
            catch
            {
            }
        }

        public static string Decrypt(string text, string key)
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

        public static string HexToString(string text)
        {
            byte[] array = new byte[text.Length / 2];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = Convert.ToByte(text.Substring(i * 2, 2), 16);
            }
            return Encoding.UTF8.GetString(array);
        }

        public static string Encrypt(string toEncrypt, string key)
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

        public static string StringToHex(string str)
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
