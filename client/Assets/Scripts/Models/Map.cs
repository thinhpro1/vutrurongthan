using System;
using Assets.Scripts.Commons;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System.Collections.Generic;

namespace Assets.Scripts.Models
{
    internal class Map
    {
        public static int mapId;

        public static int type;

        public static int planetId;

        public static int areaId;

        public static sbyte size = 72;

        public static int column;

        public static int row;

        public static int width; // column * size

        public static int height; // row * size

        public static int[,] maps;

        public static List<Waypoint> waypoints;

        public static Image[] imgMaps;

        public static Image bong;

        public static string data;

        public static string name;

        public static Dictionary<int, Image> imgBgrs = new Dictionary<int, Image>();

        public static MapTemplate template;

        public static int cmxMini;

        public static int cmyMini;

        public static Dictionary<int, MapTemplate> mapTemplates = new Dictionary<int, MapTemplate>();

        public static List<EffectMap> backGrounds = new List<EffectMap>();

        public static int[] SHOCK_X = new int[] { 9, -9, 9, -9 };

        public static int[] SHOCK_Y = new int[] { 9, -9, -9, 9 };

        public static long updateTime;

        static Map()
        {
        }

        public static void Init()
        {
            bong = GameCanvas.LoadImage("imgBong.png");
            waypoints = new List<Waypoint>();
        }

        public static void paint(MyGraphics g)
        {
            if (Player.isLoadingMap)
            {
                return;
            }

            if (template.imgsBgr[0] != -1)
            {
                g.SetColor(template.colorsBgr[0, 0], template.colorsBgr[0, 1], template.colorsBgr[0, 2]);
                g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
                if (!GraphicManager.instance.isLowGraphic)
                {
                    int wB = 0;
                    while (wB < GameCanvas.w)
                    {
                        g.DrawImage(imgBgrs[template.imgsBgr[0]], wB, GameCanvas.h / 5 * 4, StaticObj.BOTTOM_LEFT);
                        wB += imgBgrs[template.imgsBgr[0]].GetWidth();
                    }

                    g.SetColor(template.colorsBgr[1, 0], template.colorsBgr[1, 1], template.colorsBgr[1, 2]);
                    g.FillRect(0, GameCanvas.h / 5 * 4, GameCanvas.w, GameCanvas.h / 5);

                    int hB = GameCanvas.h - (ScreenManager.instance.gameScreen.cmy >> 1) / 2;
                    wB = 0 - (ScreenManager.instance.gameScreen.cmx >> 2);
                    while (wB < GameCanvas.w)
                    {
                        g.DrawImage(imgBgrs[template.imgsBgr[1]], wB, hB, StaticObj.BOTTOM_LEFT);
                        wB += imgBgrs[template.imgsBgr[1]].GetWidth();
                    }

                    if (hB < GameCanvas.h)
                    {
                        g.SetColor(template.colorsBgr[2, 0], template.colorsBgr[2, 1], template.colorsBgr[2, 2]);
                        g.FillRect(0, hB, GameCanvas.w, GameCanvas.h - hB);
                    }

                    hB = GameCanvas.h - (ScreenManager.instance.gameScreen.cmy >> 1) / 2 + GameCanvas.h / 5;
                    wB = 0 - (ScreenManager.instance.gameScreen.cmx >> 1);
                    while (wB < GameCanvas.w)
                    {
                        g.DrawImage(imgBgrs[template.imgsBgr[2]], wB, hB, StaticObj.BOTTOM_LEFT);
                        wB += imgBgrs[template.imgsBgr[2]].GetWidth();
                    }

                    if (hB < GameCanvas.h)
                    {
                        g.SetColor(template.colorsBgr[3, 0], template.colorsBgr[3, 1], template.colorsBgr[3, 2]);
                        g.FillRect(0, hB, GameCanvas.w, GameCanvas.h - hB);
                    }
                }
            }

            if (Spaceship.tShock > 0)
            {
                g.DrawImage(template.imgBgr, -ScreenManager.instance.gameScreen.cmx + SHOCK_X[Spaceship.tShock % SHOCK_X.Length], -ScreenManager.instance.gameScreen.cmy + SHOCK_Y[Spaceship.tShock % SHOCK_Y.Length]);
            }
            else
            {
                g.DrawImage(template.imgBgr, -ScreenManager.instance.gameScreen.cmx, -ScreenManager.instance.gameScreen.cmy);
            }

            g.Translate(-ScreenManager.instance.gameScreen.cmx, -ScreenManager.instance.gameScreen.cmy);
            long now = Utils.CurrentTimeMillis();
            if (now - updateTime > 100)
            {
                updateTime = now;
                for (int i = 0; i < backGrounds.Count; i++)
                {
                    EffectMap effect = backGrounds[i];
                    effect.index++;
                }
            }
            for (int i = 0; i < backGrounds.Count; i++)
            {
                EffectMap effect = backGrounds[i];
                if (effect.index >= effect.icons.Length)
                {
                    effect.index = 0;
                }
                GraphicManager.instance.Draw(g, effect.icons[effect.index], effect.x, effect.y, 0, StaticObj.BOTTOM_HCENTER);
            }
            foreach (Effect effect in ScreenManager.instance.gameScreen.effects)
            {
                effect.Paint(g);
            }
            if (ScreenManager.instance.gameScreen.isShowDragon)
            {
                g.Reset();
                g.SetColor(0, 0.4f);
                g.FillRect(0, 0, GameCanvas.w, GameCanvas.h);
            }
            g.Reset();
        }

        public static void LoadBgr()
        {
            backGrounds.Clear();
            // noel
            /* int num = Utils.random(500, 800);
             int[][] icons = new int[][] { new int[] { 377, 378, 379 }, new int[] { 380, 381, 382 } };
             while (num < width - 500)
             {
                 EffectMap effectInfoImage = new EffectMap();
                 effectInfoImage.icons = icons[Utils.random(0, 1)];
                 effectInfoImage.x = num;
                 effectInfoImage.y = GetYSd(num) - 13;
                 backGrounds.Add(effectInfoImage);
                 num += Utils.random(500, 600);
             }*/

            // tết
            /*int num = Utils.random(500, 800);
            int[][] icons = new int[][] { new int[] { 383, 384 }, new int[] { 385, 386 } };
            while (num < width - 500)
            {
                EffectMap effectInfoImage = new EffectMap();
                effectInfoImage.icons = icons[Utils.random(0, 1)];
                effectInfoImage.x = num;
                effectInfoImage.y = GetYSd(num) - 13;
                backGrounds.Add(effectInfoImage);
                num += Utils.random(500, 600);
            }*/
        }

        public static int GetYSd(int x)
        {
            if (template != null && template.isLine)
            {
                return GetYSdLine(x);
            }
            int num = 0;
            int ySd = 0;
            while (num < 90)
            {
                num++;
                ySd += size;
                if (IsWall(x, ySd))
                {
                    if (ySd % size != 0)
                    {
                        ySd -= ySd % size;
                    }
                    break;
                }
            }
            return ySd;
        }


        public static bool IsWall(int px, int py)
        {
            if (template != null && template.isLine)
            {
                return IsWallLine(px, py);
            }
            int indexY = px / size;
            int indexX = py / size;
            int num = 0;
            try
            {
                num = maps[indexX, indexY];
            }
            catch
            {
            }
            return num != 0;
        }

        // ==================== LINE COLLISION ====================

        private static bool IsWallLine(int px, int py)
        {
            if (px < 0 || px >= width || py < 0 || py >= height)
            {
                return true;
            }
            // Ray casting: cast horizontal ray from (px, py) to the right
            // Count intersections with ALL line segments (ground + block)
            int crossings = 0;
            foreach (TerrainLine line in template.terrainLines)
            {
                if (line.points.Count < 2) continue;
                for (int i = 0; i < line.points.Count - 1; i++)
                {
                    float y1 = line.points[i].y;
                    float y2 = line.points[i + 1].y;
                    float x1 = line.points[i].x;
                    float x2 = line.points[i + 1].x;
                    if ((y1 <= py && y2 > py) || (y2 <= py && y1 > py))
                    {
                        float t = (float)(py - y1) / (y2 - y1);
                        float xIntersect = x1 + t * (x2 - x1);
                        if (px < xIntersect)
                        {
                            crossings++;
                        }
                    }
                }
            }
            return (crossings % 2) == 1;
        }

        private static int GetYSdLine(int xSd)
        {
            int bestY = height;
            foreach (TerrainLine line in template.terrainLines)
            {
                if (line.points.Count >= 2)
                {
                    float? groundY = GetLineYAtX(line, xSd);
                    if (groundY.HasValue && (int)groundY.Value < bestY)
                    {
                        bestY = (int)groundY.Value;
                    }
                }
            }
            return bestY;
        }

        private static float? GetLineYAtX(TerrainLine line, float x)
        {
            for (int i = 0; i < line.points.Count - 1; i++)
            {
                float x1 = line.points[i].x;
                float x2 = line.points[i + 1].x;
                float minX = Math.Min(x1, x2);
                float maxX = Math.Max(x1, x2);
                if (x >= minX && x <= maxX)
                {
                    if (Math.Abs(x2 - x1) < 0.001f) return line.points[i].y;
                    float t = (x - x1) / (x2 - x1);
                    return line.points[i].y + t * (line.points[i + 1].y - line.points[i].y);
                }
            }
            return null;
        }

        public static int GetTileYofPixel(int py)
        {
            return py / size * size;
        }

        public static int GetTileXofPixel(int px)
        {
            return px / size * size;
        }

        public static bool IsVoDaiMap()
        {
            if (mapId == 22 || mapId == 25)
            {
                return true;
            }
            return false;
        }


    }
}
