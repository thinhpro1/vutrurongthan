using Assets.Scripts.Commons;
using Assets.Scripts.IOs;
using System.Security.Cryptography;
using System.Text;

namespace Assets.Scripts.GraphicCustoms
{
    public static class IconCache
    {
        public static void Save(int version, int iconId, sbyte[] pngData)
        {
            try
            {
                if (pngData == null || pngData.Length == 0)
                {
                    return;
                }

                byte[] encrypted = EncryptBytes(
                    Utils.Cast(pngData),
                    GetCryptoKey(version));
                Rms.Save(GetStorageKey(version, iconId), Utils.Cast(encrypted));
            }
            catch
            {
            }
        }

        public static sbyte[] Load(int version, int iconId)
        {
            try
            {
                sbyte[] stored = Rms.Load(GetStorageKey(version, iconId));
                if (stored == null || stored.Length == 0)
                {
                    return null;
                }

                byte[] decrypted = DecryptBytes(
                    Utils.Cast(stored),
                    GetCryptoKey(version));
                if (!IsPng(decrypted))
                {
                    return null;
                }

                return Utils.Cast(decrypted);
            }
            catch
            {
                return null;
            }
        }

        public static void SaveV2(int iconId, long fingerprint, sbyte[] pngData)
        {
            try
            {
                if (pngData == null || pngData.Length == 0)
                {
                    return;
                }

                byte[] encrypted = EncryptBytes(
                    Utils.Cast(pngData),
                    GetV2CryptoKey(iconId, fingerprint));
                Rms.Save(
                    GetV2StorageKey(iconId, fingerprint),
                    Utils.Cast(encrypted));
            }
            catch
            {
            }
        }

        public static sbyte[] LoadV2(int iconId, long fingerprint)
        {
            try
            {
                sbyte[] stored = Rms.Load(GetV2StorageKey(iconId, fingerprint));
                if (stored == null || stored.Length == 0)
                {
                    return null;
                }

                byte[] decrypted = DecryptBytes(
                    Utils.Cast(stored),
                    GetV2CryptoKey(iconId, fingerprint));
                if (!IsPng(decrypted))
                {
                    return null;
                }

                sbyte[] result = Utils.Cast(decrypted);
                if (ComputeFingerprint64(result) != fingerprint)
                {
                    return null;
                }

                return result;
            }
            catch
            {
                return null;
            }
        }

        public static long ComputeFingerprint64(sbyte[] data)
        {
            byte[] bytes = Utils.Cast(data);
            byte[] hash;
            using (SHA256 sha = SHA256.Create())
            {
                hash = sha.ComputeHash(bytes);
            }

            ulong value = 0;
            for (int i = 0; i < 8; i++)
            {
                value = (value << 8) | hash[i];
            }

            return unchecked((long)value);
        }

        private static string GetStorageKey(int version, int iconId)
        {
            return "icon_" + version + "_" + iconId;
        }

        private static string GetCryptoKey(int version)
        {
            return version + "" + version;
        }

        private static string FingerprintHex(long fingerprint)
        {
            return unchecked((ulong)fingerprint).ToString("x16");
        }

        private static string GetV2StorageKey(int iconId, long fingerprint)
        {
            return "icon_v2_" + iconId + "_" + FingerprintHex(fingerprint);
        }

        private static string GetV2CryptoKey(int iconId, long fingerprint)
        {
            return "v2:" + iconId + ":" + FingerprintHex(fingerprint);
        }

        private static byte[] EncryptBytes(byte[] data, string key)
        {
            using (var md5 = new MD5CryptoServiceProvider())
            using (var tripleDes = new TripleDESCryptoServiceProvider())
            {
                tripleDes.Key = md5.ComputeHash(Encoding.UTF8.GetBytes(key));
                tripleDes.Mode = CipherMode.ECB;
                tripleDes.Padding = PaddingMode.PKCS7;

                using (ICryptoTransform encryptor = tripleDes.CreateEncryptor())
                {
                    return encryptor.TransformFinalBlock(data, 0, data.Length);
                }
            }
        }

        private static byte[] DecryptBytes(byte[] data, string key)
        {
            using (var md5 = new MD5CryptoServiceProvider())
            using (var tripleDes = new TripleDESCryptoServiceProvider())
            {
                tripleDes.Key = md5.ComputeHash(Encoding.UTF8.GetBytes(key));
                tripleDes.Mode = CipherMode.ECB;
                tripleDes.Padding = PaddingMode.PKCS7;

                using (ICryptoTransform decryptor = tripleDes.CreateDecryptor())
                {
                    return decryptor.TransformFinalBlock(data, 0, data.Length);
                }
            }
        }

        private static bool IsPng(byte[] data)
        {
            return data != null
                && data.Length >= 8
                && data[0] == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
        }
    }
}
