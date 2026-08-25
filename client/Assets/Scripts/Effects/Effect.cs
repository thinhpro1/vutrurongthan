using Assets.Scripts.Entites;
using Assets.Scripts.GraphicCustoms;

namespace Assets.Scripts.Effects
{
    public abstract class Effect
    {
        public Entity entity;

        public int tick;

        public long currentTick;

        public abstract void Paint(MyGraphics g);

        public abstract void Update();

        public abstract bool IsClear();

    }
}
