using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.Sounds;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.Models
{
    public class SoundMn
    {
        public static int PICK_ITEM = 0;

        public static int MOVE = 1;

        public static int MEDIUM_PUNCH = 2;

        public static int MEDIUM_KICK = 3;

        public static int FLY = 4;

        public static int PANEL_OPEN = 5;

        public static int LOW_PUNCH = 6;

        public static int LOW_KICK = 7;

        public static int PANEL_CLICK = 8;

        public static int EAT_PEAN = 9;

        public static int NAMEK_KAME = 10;

        public static int XAYDA_KAME = 11;

        public static int EXPLODE_1 = 12;

        public static int TRAIDAT_KAME = 13;

        public static int HP_UP = 14;

        public static int THAIDUONGHASAN = 15;

        public static int HOISINH = 16;

        public static int BIG_EXPLODE = 17;

        public static int NAMEK_LAZER = 18;

        public static int FIREWORK = 19;

        public static int AIR_SHIP = 20;

        public static int TAITAONANGLUONG = 21;

        public static int GONG = 22;

        public static int BUTTON_CLOSE = 23;

        public static int BUTTON_CLICK = 24;

        public static int OPEN_DIALOG = 25;

        public static int MONSTER_KAME = 26;

        public static AudioClip SoundBGLoop;

        public static GameObject[] player;

        public static AudioClip[] music;

        public static GameObject playerSoundBgr;

        public static int status;

        public static int postem;

        public static int timestart;

        private static string filenametemp;

        private static float volumetem;

        public static bool isSound = true;

        public static bool isNotPlay;

        public static void Load()
        {
            SoundBGLoop = (AudioClip)Resources.Load("Sounds/train", typeof(AudioClip));
            playerSoundBgr = GameObject.Find("Main Camera").AddComponent<AudioSource>().gameObject;
            player = new GameObject[27];
            music = new AudioClip[player.Length];
            for (int i = 0; i < player.Length; i++)
            {
                getAssetSoundFile("Sounds/" + i, i);
            }
        }

        public static void playerFly()
        {
            SoundMn.playSound(FLY, 0.5f);
        }

        public static void playerRun(float volumn)
        {
            SoundMn.playSound(MOVE, volumn / 2f);
        }

        public static void playerFall()
        {
            SoundMn.playSound(MOVE, 0.3f);
        }

        public static void playerJump()
        {
            SoundMn.playSound(MOVE, 0.1f);
        }

        public static void playerPunch(bool isKick)
        {
            int num = Utils.random(0, 3);
            if (isKick)
            {
                SoundMn.playSound((num != 0) ? MEDIUM_KICK : LOW_KICK, 1f);
            }
            else
            {
                SoundMn.playSound((num != 0) ? MEDIUM_PUNCH : LOW_PUNCH, 1f);
            }
        }

        public static void panelOpen()
        {
            SoundMn.playSound(PANEL_OPEN, 0.5f);
        }

        public static void ButtonClose()
        {
            SoundMn.playSound(BUTTON_CLOSE, 0.5f);
        }

        public static void buttonClick()
        {
            SoundMn.playSound(BUTTON_CLICK, 0.5f);
        }

        public static void OpenMenu()
        {
            SoundMn.playSound(BUTTON_CLOSE, 0.5f);
        }

        public static void panelClick()
        {
            SoundMn.playSound(PANEL_CLICK, 0.5f);
        }

        public static void eatPeans()
        {
            SoundMn.playSound(EAT_PEAN, 0.5f);
        }

        public static void OpenDialog()
        {
            SoundMn.playSound(OPEN_DIALOG, 0.5f);
        }

        public static void thaiduonghasan()
        {
            SoundMn.playSound(THAIDUONGHASAN, 1f);
        }

        public static void taitao(float volume)
        {
            SoundMn.playSound(TAITAONANGLUONG, volume);
        }

        public static void taitao2(float volume)
        {
            SoundMn.playSound(FIREWORK, volume);
        }

        public static void traidatKame()
        {
            SoundMn.playSound(TRAIDAT_KAME, 1f);
        }

        public static void namekKame()
        {
            SoundMn.playSound(NAMEK_KAME, 0.3f);
        }

        public static void nameLazer()
        {
            SoundMn.playSound(NAMEK_LAZER, 0.3f);
        }

        public static void xaydaKame()
        {
            SoundMn.playSound(XAYDA_KAME, 0.3f);
        }

        public static void explodeKame()
        {
            SoundMn.playSound(EXPLODE_1, 0.3f);
        }

        public static void hoisinh()
        {
            SoundMn.playSound(HOISINH, 0.5f);
        }

        public static void explode()
        {
            SoundMn.playSound(BIG_EXPLODE, 0.5f);
        }

        public static void startExplode()
        {
            SoundMn.playSound(GONG, 1f);
        }

        public static void airShip()
        {
            SoundMn.playSound(AIR_SHIP, 1f);
        }

        public static void pickItem()
        {
            SoundMn.playSound(PICK_ITEM, 0.3f);
        }

        public static void playSoundBgr()
        {
            if (SoundManager.instance.isPlay)
            {
                playerSoundBgr.GetComponent<AudioSource>().loop = true;
                playerSoundBgr.GetComponent<AudioSource>().clip = SoundBGLoop;
                playerSoundBgr.GetComponent<AudioSource>().Play();
            }
        }

        public static void stopSoundBgr()
        {
            playerSoundBgr.GetComponent<AudioSource>().Stop();
        }

        public static void stopAll()
        {
            stopSoundBgr();
            for (int i = 0; i < player.Length; i++)
            {
                player[i].GetComponent<AudioSource>().Stop();
            }
        }

        public static void getAssetSoundFile(string fileName, int pos)
        {
            stop(pos);
            load(fileName, pos);
        }

        public static void load(string filename, int pos)
        {
            if (Thread.CurrentThread.Name == Main.mainThreadName)
            {
                __load(filename, pos);
            }
            else
            {
                _load(filename, pos);
            }
        }

        private static void _load(string filename, int pos)
        {
            if (status != 0)
            {
                Debug.LogError("CANNOT LOAD AUDIO " + filename + " WHEN LOADING " + filenametemp);
                return;
            }
            filenametemp = filename;
            postem = pos;
            status = 2;
            int i;
            for (i = 0; i < 100; i++)
            {
                Thread.Sleep(5);
                if (status == 0)
                {
                    break;
                }
            }
            if (i == 100)
            {
                Debug.LogError("TOO LONG FOR LOAD AUDIO " + filename);
                return;
            }
            Debug.Log("Load Audio " + filename + " done in " + i * 5 + "ms");
        }

        private static void __load(string filename, int pos)
        {
            music[pos] = (AudioClip)Resources.Load(filename, typeof(AudioClip));
            player[pos] = GameObject.Find("Main Camera").AddComponent<AudioSource>().gameObject;
        }

        public static void stop(int pos)
        {
            if (Thread.CurrentThread.Name == Main.mainThreadName)
            {
                __stop(pos);
            }
            else
            {
                _stop(pos);
            }
        }

        public static void _stop(int pos)
        {
            if (status != 0)
            {
                Debug.LogError("CANNOT STOP AUDIO WHEN STOPPING");
                return;
            }
            postem = pos;
            status = 4;
            int i;
            for (i = 0; i < 100; i++)
            {
                Thread.Sleep(5);
                if (status == 0)
                {
                    break;
                }
            }
            if (i == 100)
            {
                Debug.LogError("TOO LONG FOR STOP AUDIO");
            }
            else
            {
                Debug.Log("Stop Audio done in " + i * 5 + "ms");
            }
        }

        public static void __stop(int pos)
        {
            if (player[pos] != null)
            {
                player[pos].GetComponent<AudioSource>().Stop();
            }
        }

        public static void playSound(int id, float volume)
        {
            play(id, volume);
        }

        public static void play(int id, float volume)
        {
            if (!isNotPlay && SoundManager.instance.isPlay)
            {
                start(volume, id);
            }
        }

        public static void start(float volume, int pos)
        {
            if (Thread.CurrentThread.Name == Main.mainThreadName)
            {
                __start(volume, pos);
            }
            else
            {
                _start(volume, pos);
            }
        }

        public static void _start(float volume, int pos)
        {
            if (status != 0)
            {
                Debug.LogError("CANNOT START AUDIO WHEN STARTING");
                return;
            }
            volumetem = volume;
            postem = pos;
            status = 3;
            int i;
            for (i = 0; i < 100; i++)
            {
                Thread.Sleep(5);
                if (status == 0)
                {
                    break;
                }
            }
            if (i == 100)
            {
                Debug.LogError("TOO LONG FOR START AUDIO");
            }
            else
            {
                Debug.Log("Start Audio done in " + i * 5 + "ms");
            }
        }

        public static void __start(float volume, int pos)
        {
            if (player[pos] != null)
            {
                player[pos].GetComponent<AudioSource>().PlayOneShot(music[pos], volume);
            }
        }
    }
}
