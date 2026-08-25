using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;

namespace Assets.Scripts.Models
{
    public class FlyText
    {
        public MyFont myFont;

        public string text;

        public int x;

        public int y;

        public int dx;

        public int dy;

        public int tick;

        public int limitTime = 25;

        public int indexFirework;

        public static int[] imgFireworks = new int[] { 307, 308, 310, 311, 312 };

        public static Image levelUp1;

        public static Image levelUp2;

        public static Image completeTask1;

        public static Image completeTask2;

        public static int TYPE_LEVEL_UP = 1;

        public static int TYPE_COMPLETE_TASK = 2;

        public int type;

        static FlyText()
        {
            levelUp1 = GameCanvas.LoadImage("imgLevelUp1.png");
            levelUp2 = GameCanvas.LoadImage("imgLevelUp2.png");
            completeTask1 = GameCanvas.LoadImage("imgCompleteTask1.png");
            completeTask2 = GameCanvas.LoadImage("imgCompleteTask2.png");
        }

        public FlyText()
        {

        }

        public void Paint(MyGraphics g)
        {
            if (type == 1 || type == 2)
            {
                if (indexFirework < imgFireworks.Length)
                {
                    GraphicManager.instance.Draw(g, imgFireworks[indexFirework], x, y - 30, 0, MyGraphics.HCENTER | MyGraphics.BOTTOM);
                }
                if (type == 1)
                {
                    g.DrawImage(GameCanvas.gameTick % 10 < 5 ? levelUp1 : levelUp2, x, y, StaticObj.BOTTOM_HCENTER);
                }
                else if (type == 2)
                {
                    g.DrawImage(GameCanvas.gameTick % 10 < 5 ? completeTask1 : completeTask2, x, y, StaticObj.BOTTOM_HCENTER);
                }
            }
            else
            {
                myFont.DrawStringBorder(g, text, x, y, 2);
            }
        }

        public void updateFirework()
        {
            if (type == 0)
            {
                return;
            }
            if (GameCanvas.gameTick % 2 == 0)
            {
                indexFirework++;
            }
        }
    }
}
