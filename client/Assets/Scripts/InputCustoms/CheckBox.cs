using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;

namespace Assets.Scripts.InputCustoms
{
    public class CheckBox
    {
        public int x;

        public int y;

        public int width;

        public int height;

        public bool isFocus;

        public bool isCheck;

        public static Image[] images;

        static CheckBox()
        {
            images = new Image[4];
            for (int i = 0; i < images.Length; i++)
            {
                images[i] = GameCanvas.LoadImage("imgCheck" + i);
            }
        }

        public CheckBox()
        {
            isFocus = false;
            isCheck = false;
            width = 40;
            height = 40;
        }

        public CheckBox(int x, int y)
        {
            isFocus = false;
            isCheck = false;
            width = 40;
            height = 40;
            this.x = x;
            this.y = y;
        }

        public void paint(MyGraphics g)
        {
            if (isFocus)
            {
                if (isCheck)
                {
                    g.DrawImage(images[3], x, y);
                }
                else
                {
                    g.DrawImage(images[1], x, y);
                }
            }
            else
            {
                if (isCheck)
                {
                    g.DrawImage(images[2], x, y);
                }
                else
                {
                    g.DrawImage(images[0], x, y);
                }
            }
        }

        public void pointerClicked(int x, int y)
        {
            if (x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height)
            {
                isCheck = !isCheck;
            }
        }

        public void pointerReleased(int x, int y)
        {

        }

        public void pointerMove(int x, int y)
        {
            if (x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height)
            {
                isFocus = true;
            }
            else
            {
                isFocus = false;
            }
        }
    }
}
