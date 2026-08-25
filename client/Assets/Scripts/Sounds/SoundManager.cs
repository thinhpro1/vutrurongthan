using Assets.Scripts.IOs;
using System.Collections.Generic;

namespace Assets.Scripts.Sounds
{
    public class SoundManager
    {
        public static SoundManager instance = new SoundManager();

        public bool isPlay;

        public Dictionary<int, Sound> sounds;

        public SoundManager()
        {
            sounds = new Dictionary<int, Sound>();
        }

        public void Init()
        {
            isPlay = Rms.LoadInt("isPlaySound") != 0;

        }


    }
}
