using System.Collections.Generic;

namespace Assets.Scripts.Models
{
    public class Dart
    {
        public List<EffectPlayerPaint> startDarts = new List<EffectPlayerPaint>();

        public List<int> midDarts = new List<int>();

        public List<int> endDarts = new List<int>();

        public int dx;

        public int dy;

        public Dart()
        {
        }

        public Dart Clone()
        {
            Dart dart = new Dart();

            dart.dx = dx;
            dart.dy = dy;
            dart.startDarts.AddRange(startDarts);
            dart.endDarts.AddRange(endDarts);
            dart.midDarts.AddRange(midDarts);

            return dart;
        }
    }
}
