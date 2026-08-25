using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.Models
{
    public class BackgroudEffect
    {
        public static List<BackgroudEffect> effects = new List<BackgroudEffect>();

        private int[] x;

        private int[] y;

        private int[] vx;

        private int[] vy;

        public static int[] wP;

        public static Image imgSnow;

        public static Image imgHatMua;

        public static Image imgMua1;

        public static Image imgMua2;

        public static Image imgSao;

        private static Image imgLacay;

        private int sum;

        public int typeEff;

        public int xx;

        public int waterY;

        private int[] frame;

        private int[] t;

        private bool[] activeEff;

        public static Image imgChamTron1;

        public static Image imgChamTron2;

        public static bool isFog;

        public static bool isPaintFar;

        public static int nCloud;

        public static Image imgCloud1;

        public static Image imgFog;

        public static int cloudw;

        public static int xfog;

        public static int yfog;

        public static int fogw;

        public BackgroudEffect(int typeS)
        {
            typeEff = typeS;
            switch (typeEff)
            {
                case 1:
                case 2:
                case 5:
                case 6:
                case 7:
                case 11:
                case 12:
                    {
                        if (typeEff == 1)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/lacay.png");
                        }
                        if (typeEff == 2)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/lacay2.png");
                        }
                        if (typeEff == 5)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/lacay3.png");
                        }
                        if (typeEff == 6)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/lacay4.png");
                        }
                        if (typeEff == 7)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/lacay5.png");
                        }
                        if (typeEff == 11)
                        {
                            imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/tuyet.png");
                        }
                        if (typeEff == 12)
                        {
                            //imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/tuyet2.png");
                            if (Utils.random(0, 1) == 0)
                            {
                                imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/mai.png");
                            }
                            else
                            {
                                imgLacay = GameCanvas.LoadImage("Maps/BackgroundEffects/dao.png");
                            }
                        }

                        if (typeEff == 11 || typeEff == 12)
                        {
                            sum = 100;
                        }
                        else
                        {
                            sum = Utils.random(15, 25);
                        }
                        x = new int[sum];
                        y = new int[sum];
                        vx = new int[sum];
                        vy = new int[sum];
                        t = new int[sum];
                        frame = new int[sum];
                        activeEff = new bool[sum];
                        for (int j = 0; j < sum; j++)
                        {
                            x[j] = Utils.random(-10, Map.width + 10);
                            y[j] = Utils.random(0, Map.height);
                            frame[j] = Utils.random(0, 1);
                            t[j] = Utils.random(0, 1);
                            vx[j] = Utils.random(-3, 3);
                            vy[j] = Utils.random(1, 4);
                            if (typeEff == 11)
                            {
                                frame[j] = Utils.random(0, 2);
                                vx[j] = Math.Abs(Utils.random(1, 3));
                                vy[j] = Math.Abs(Utils.random(1, 3));
                            }
                        }
                        break;
                    }
            }
        }

        public void update()
        {
            switch (typeEff)
            {
                case 1:
                case 2:
                case 5:
                case 6:
                case 7:
                case 11:
                case 12:
                    {
                        for (int j = 0; j < sum; j++)
                        {
                            if (j % 3 != 0 && Map.IsWall(x[j], y[j] + Utils.random(20, 36)))
                            {
                                activeEff[j] = true;
                            }
                            if (j % 3 == 0 && y[j] > Map.height)
                            {
                                x[j] = Utils.random(-10, Map.width + 50);
                                y[j] = Utils.random(-50, 0);
                            }
                            if (!activeEff[j])
                            {
                                y[j] += vy[j];
                                x[j] += vx[j];
                                t[j]++;
                                int num = ((typeEff != 11) ? 4 : 3);
                                if (t[j] > ((typeEff == 2) ? 4 : 2))
                                {
                                    if (typeEff != 11)
                                    {
                                        frame[j]++;
                                    }
                                    t[j] = 0;
                                    if (frame[j] > num - 1)
                                    {
                                        frame[j] = 0;
                                    }
                                }
                            }
                            else
                            {
                                t[j]++;
                                if (t[j] == 100)
                                {
                                    t[j] = 0;
                                    x[j] = Utils.random(-10, Map.width + 50);
                                    y[j] = Utils.random(-50, 0);
                                    activeEff[j] = false;
                                }
                            }
                        }
                        break;
                    }
            }
        }

        public void paintFront(MyGraphics g)
        {
            if (GraphicManager.instance.isLowGraphic)
            {
                return;
            }
            switch (typeEff)
            {
                case 1:
                case 2:
                case 5:
                case 6:
                case 7:
                case 11:
                case 12:
                    paintLacay1(g, imgLacay);
                    break;
            }
        }

        public void paintLacay1(MyGraphics g, Image img)
        {
            int num = ((typeEff != 11) ? 4 : 3);
            for (int i = 0; i < sum; i++)
            {
                if (i % 3 == 0 && x[i] >= ScreenManager.instance.gameScreen.cmx && x[i] <= GameCanvas.w + ScreenManager.instance.gameScreen.cmx && y[i] >= ScreenManager.instance.gameScreen.cmy && y[i] <= GameCanvas.h + ScreenManager.instance.gameScreen.cmy)
                {
                    g.drawRegion(img, 0, MyGraphics.getImageHeight(img) / num * frame[i], MyGraphics.getImageWidth(img), MyGraphics.getImageHeight(img) / num, 0, x[i], y[i], 0);
                }
            }
        }

        public void paintLacay2(MyGraphics g, Image img)
        {
            int num = ((typeEff != 11) ? 4 : 3);
            for (int i = 0; i < sum; i++)
            {
                if (i % 3 != 0 && x[i] >= ScreenManager.instance.gameScreen.cmx && x[i] <= GameCanvas.w + ScreenManager.instance.gameScreen.cmx && y[i] >= ScreenManager.instance.gameScreen.cmy && y[i] <= GameCanvas.h + ScreenManager.instance.gameScreen.cmy)
                {
                    g.drawRegion(img, 0, MyGraphics.getImageHeight(img) / num * frame[i], MyGraphics.getImageWidth(img), MyGraphics.getImageHeight(img) / num, 0, x[i], y[i], 0);
                }
            }
        }

        public void paintBack(MyGraphics g)
        {
            switch (typeEff)
            {
                case 1:
                case 2:
                case 5:
                case 6:
                case 7:
                case 11:
                case 12:
                    paintLacay2(g, imgLacay);
                    break;
            }
        }

        public static void addEffect(int id)
        {
            effects.Add(new BackgroudEffect(id));
        }

        public static void CreateBgrEffect(int mapId)
        {
            if (GraphicManager.instance.isLowGraphic || !Main.isPC)
            {
                return;
            }
            addEffect(12);
            /* if (mapId == 10 || mapId == 15 || mapId == 16 || mapId == 17 || mapId == 18)
             {
                 addEffect(5);
                 return;
             }
             if (mapId == 0 || mapId == 1 || mapId == 9 || mapId == 11 || mapId == 12
                 || mapId == 13 || mapId == 14 || mapId == 19 || mapId == 20 || mapId == 21
                 || mapId == 23 || mapId == 24 || mapId == 26 || mapId == 27 || mapId == 28
                 || mapId == 29 || mapId == 30 || mapId == 32 || mapId == 35 || mapId == 43
                 || mapId == 44 || mapId == 45 || mapId == 46)
             {
                 addEffect(11);
                 return;
             }
             if (mapId == 3)
             {
                 addEffect(1);
                 return;
             }
             if (mapId == 2 || mapId == 4 || mapId == 7)
             {
                 addEffect(2);
                 return;
             }
             if (mapId == 5 || mapId == 6 || mapId == 8)
             {
                 addEffect(6);
                 return;
             }*/
        }

        public static void paintFrontAll(MyGraphics g)
        {
            if (GraphicManager.instance.isLowGraphic)
            {
                return;
            }
            for (int i = 0; i < effects.Count; i++)
            {
                effects[i].paintFront(g);
            }
        }

        public static void paintBackAll(MyGraphics g)
        {
            if (GraphicManager.instance.isLowGraphic)
            {
                return;
            }
            for (int i = 0; i < effects.Count; i++)
            {
                effects[i].paintBack(g);
            }
        }

        public static void updateEff()
        {
            if (GraphicManager.instance.isLowGraphic)
            {
                return;
            }
            for (int i = 0; i < effects.Count; i++)
            {
                effects[i].update();
            }
        }
    }
}
