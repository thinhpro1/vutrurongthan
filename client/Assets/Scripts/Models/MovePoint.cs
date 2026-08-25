namespace Assets.Scripts.Models
{
    public class MovePoint
    {
        public MovePoint(int xEnd, int yEnd, int act, int dir)
        {
            this.xEnd = xEnd;
            this.yEnd = yEnd;
            this.dir = dir;
            this.status = act;
        }

        public MovePoint(int xEnd, int yEnd)
        {
            this.xEnd = xEnd;
            this.yEnd = yEnd;
        }

        public int xEnd;

        public int yEnd;

        public int dir;

        public int cvx;

        public int cvy;

        public int status;
    }
}
