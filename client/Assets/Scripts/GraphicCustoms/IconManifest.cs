using System.Collections.Generic;

namespace Assets.Scripts.GraphicCustoms
{
    public class IconManifest
    {
        public static IconManifest instance = new IconManifest();

        private readonly object sync = new object();

        private Dictionary<int, long> entries = new Dictionary<int, long>();

        private bool ready;

        private int generation;

        public bool IsReady
        {
            get
            {
                lock (sync)
                {
                    return ready;
                }
            }
        }

        public int Generation
        {
            get
            {
                lock (sync)
                {
                    return generation;
                }
            }
        }

        public void BeginRefresh()
        {
            lock (sync)
            {
                ready = false;
            }
        }

        public void Replace(Dictionary<int, long> entries)
        {
            Dictionary<int, long> snapshot =
                new Dictionary<int, long>(entries);
            lock (sync)
            {
                this.entries = snapshot;
                ready = true;
                generation++;
            }
        }

        public bool TryGetFingerprint(int iconId, out long fingerprint)
        {
            lock (sync)
            {
                if (!ready)
                {
                    fingerprint = 0L;
                    return false;
                }

                return entries.TryGetValue(iconId, out fingerprint);
            }
        }
    }
}
