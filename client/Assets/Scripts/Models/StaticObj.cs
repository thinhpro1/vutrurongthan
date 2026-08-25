using Assets.Scripts.GraphicCustoms;

namespace Assets.Scripts.Models
{
    public class StaticObj
    {
        public static int TOP_CENTER = MyGraphics.TOP | MyGraphics.HCENTER;

        public static int TOP_LEFT = MyGraphics.TOP | MyGraphics.LEFT;

        public static int TOP_RIGHT = MyGraphics.TOP | MyGraphics.RIGHT;

        public static int BOTTOM_HCENTER = MyGraphics.BOTTOM | MyGraphics.HCENTER;

        public static int BOTTOM_LEFT = MyGraphics.BOTTOM | MyGraphics.LEFT;

        public static int BOTTOM_RIGHT = MyGraphics.BOTTOM | MyGraphics.RIGHT;

        public static int VCENTER_HCENTER = MyGraphics.VCENTER | MyGraphics.HCENTER;

        public static int VCENTER_LEFT = MyGraphics.VCENTER | MyGraphics.LEFT;

        public static int VCENTER_RIGHT = MyGraphics.VCENTER | MyGraphics.RIGHT;

        public static int[] imageFly = new int[] { 338, 339, 340, 341 };

        public static int[] blindImageId = new int[] { 328, 329, 336 };

        public static int[][][] CharInfo = new int[][][] {
            //0 stand
            new int[][]
            {
                new int[] { 0, -55, 134 },
                new int[] { 0, -20, 35 },
                new int[] { 0, -19, 19 }
            },
            //1 stand
            new int[][]
            {
                new int[] { 1, -55, 132 },
                new int[] { 0, -20, 33 },
                new int[] { 0, -19, 19 }
            },
            //2 run
            new int[][]
            {
                new int[] { 2, -37, 123 },
                new int[] { 1, -26, 60 },
                new int[] { 1, -21, 34 }
            },
            //3 run
            new int[][]
            {
                new int[] { 2, -37, 120 },
                new int[] { 2, -35, 50 },
                new int[] { 2, -28, 34 }
            },
            //4 run
            new int[][]
            {
                new int[] { 2, -37, 120 },
                new int[] { 3, -20, 50 },
                new int[] { 3, -20, 33 }
            },
            //5 run
            new int[][]
            {
                new int[] { 2, -37, 120 },
                new int[] { 4, -22, 48 },
                new int[] { 4, -31, 34 }
            },
            //6 run
            new int[][]
            {
                new int[] { 2, -37, 120 },
                new int[] { 5, -22, 50 },
                new int[] { 5, -23, 32 }
            },
            //7 run
            new int[][]
            {
                new int[] { 2, -37, 120 },
                new int[] { 5, -22, 50 },
                new int[] { 5, -23, 32 }
            },
            //8 jump
            new int[][]
            {
                new int[] { 0, -55, 157 },
                new int[] { 7, -35, 60 },
                new int[] { 7, -17, 41 }
            },
            //9 fly
            new int[][]
            {
                new int[] { 2, -28, 130 },
                new int[] { 8, -15, 60 },
                new int[] { 8, -56, 62 }
            },
            //10 fall
            new int[][]
            {
                new int[] { 0, -58, 157 },
                new int[] { 9, -40, 86 },
                new int[] { 7, -17, 41 }
            },
            // 11 đứng giơ 2 tay - giống rơi
            new int[][]
            {
                new int[] { 0, -47, 136 },
                new int[] { 9, -44, 100 },
                new int[] { 9, -45, 45 }
            },
            // 12 gồng đứng
            new int[][]
            {
                new int[] { 3, -47, 135 },
                new int[] { 10, -43, 68 },
                new int[] { 0, -28, 43 }
            },
            // 13 gồng bay
            new int[][]
            {
                new int[] { 3, -46, 140 },
                new int[] { 10, -42, 73 },
                new int[] { 7, -18, 50 }
            },
            // 14 choáng đứng
            new int[][]
            {
                new int[] { 0, -43, 132 },
                new int[] { 11, -27, 85 },
                new int[] { 9, -45, 45 }
            },
            // 15 choáng bay
            new int[][]
            {
                new int[] { 0, -44, 137 },
                new int[] { 11, -28, 90 },
                new int[] { 7, -18, 50 }
            },
            // 16 buff đứng
            new int[][]
            {
                new int[] { 0, -39, 138 },
                new int[] { 12, -16, 82 },
                new int[] { 9, -45, 45 }
            },
            // 17 buff bay
            new int[][]
            {
                new int[] { 0, -39, 141 },
                new int[] { 12, -16, 85 },
                new int[] { 7, -18, 50 }
            },
            // 18 tụ kame đứng
            new int[][]
            {
                new int[] { 0, -54, 143 },
                new int[] { 13, -24, 50 },
                new int[] { 9, -35, 32 }
            },
            // 19 tụ kame bay
            new int[][]
            {
                new int[] { 0, -60, 153 },
                new int[] { 13, -30, 60 },
                new int[] { 7, -17, 41 }
            },
            // 20 kame đứng
            new int[][]
            {
                new int[] { 0, -52, 150 },
                new int[] { 14, -18, 66 },
                new int[] { 9, -35, 32 }
            },
            // 21 kame bay
            new int[][]
            {
                new int[] { 0, -52, 160 },
                new int[] { 14, -18, 76 },
                new int[] { 7, -17, 41 }
            },      
            // 22 đỡ đòn
            new int[][]
            {
                new int[] { 0, -48, 155 },
                new int[] { 15, -15, 75 },
                new int[] { 10, -15, 42 }
            },
            // 23 đứng còng lưng vận
            new int[][]
            {
                new int[] { 0, -40, 122 },
                new int[] { 16, -38, 73 },
                new int[] { 9, -45, 45 }
            },
            // 24 đấm tay trái
            new int[][]
            {
                new int[] { 2, -45, 129 },
                new int[] { 17, -35, 70 },
                new int[] { 9, -45, 45 }
            },
            // 25 bay đấm tay trái
            new int[][]
            {
                new int[] { 2, -44, 138 },
                new int[] { 17, -34, 79 },
                new int[] { 7, -18, 50 }
            },
            // 26 đứng đấm tay phải
            new int[][]
            {
                new int[] { 2, -35, 124 },
                new int[] { 18, -16, 54 },
                new int[] { 9, -35, 32 }
            },
            // 27 bay đấm tay phải
            new int[][]
            {
                new int[] { 2, -35, 135 },
                new int[] { 18, -16, 65 },
                new int[] { 7, -17, 41 }
            },          
            // 28 đứng giớ tay trái về sau
            new int[][]
            {
                new int[] { 2, -39, 127 },
                new int[] { 19, -34, 65 },
                new int[] { 9, -45, 45 }
            },
            // 29 bay giớ tay trái về sau
            new int[][]
            {
                new int[] { 2, -35, 134 },
                new int[] { 19, -30, 73 },
                new int[] { 7, -18, 50 }
            },
            // 30
            new int[][]
            {
                new int[] { 2, -45, 138 },
                new int[] { 20, -39, 92 },
                new int[] { 11, -20, 68 }
            },
            // 31 tay giơ lên trời, đá chân trái
            new int[][]
            {
                new int[] { 0, -42, 136 },
                new int[] { 21, -13, 110 },
                new int[] { 12, -27, 72 }
            },
            // 32
            new int[][]
            {
                new int[] { 2, -36, 131 },
                new int[] { 20, -30, 85 },
                new int[] { 13, -10, 80 }
            },
            // 33 đứng giơ 2 tay - đứng thẳng
            new int[][]
            {
                new int[] { 0, -47, 136 },
                new int[] { 9, -44, 100 },
                new int[] { 0, -28, 43 }
            },
            // 34 choáng đứng thẳng
            new int[][]
            {
                new int[] { 0, -44, 132 },
                new int[] { 11, -28, 85 },
                new int[] { 0, -28, 43 }
            },
            // 35 buff đứng thẳng
            new int[][]
            {
                new int[] { 0, -40, 138 },
                new int[] { 12, -17, 82 },
                new int[] { 0, -28, 43 }
            },
            // 36 đứng thẳng giơ 1 tay
            new int[][]
            {
                new int[] { 0, -45, 133 },
                new int[] { 21, -16, 107 },
                new int[] { 9, -45, 45 }
            },
            // 37 bay thẳng giơ 1 tay
            new int[][]
            {
                new int[] { 0, -45, 140 },
                new int[] { 21, -16, 114 },
                new int[] { 7, -18, 50 }
            }
        };
    }
}
