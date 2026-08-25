using Assets.Scripts.Controllers;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using System;
using UnityEngine;

namespace Assets.Scripts.Models
{
    public class Spaceship
    {
        public int x;

        public int y;

        private int type;

        private bool isMe;

        private int y2;

        public Player player;

        private int dir;

        private bool isUp;

        private bool isDown;

        public bool isPaintFire;

        private int tPrepare;

        private int speed = 5;

        private int tDelayHole;

        private bool isPaintHole;

        private static Image[] imagesSpaceship = new Image[2];

        private static Image[] imagesSpaceshipVip = new Image[2];

        private static Image imageHole;

        private bool isShock;

        public static int tShock;

        private static int[,] iconsFire = new int[,] { { 88, 89, 90, 91 }, { 251, 252, 253, 254 } };

        private int ticks;

        private int level;

        private Image imageSpaceship;

        private Image imageSpaceshipOpen;

        static Spaceship()
        {
            imageHole = GameCanvas.LoadImage("imgHole.png");
            imagesSpaceship[0] = GameCanvas.LoadImage("imgSpaceship.png");
            imagesSpaceship[1] = GameCanvas.LoadImage("imgSpaceshipOpen.png");
            imagesSpaceshipVip[0] = GameCanvas.LoadImage("imgSpaceshipVip.png");
            imagesSpaceshipVip[1] = GameCanvas.LoadImage("imgSpaceshipVipOpen.png");
        }

        public Spaceship(Player player, int type)
        {
            this.player = player;
            this.x = player.x;
            this.y2 = this.y = 5;
            this.type = type; // 0 người bay lên, 1 người bay xuông
            this.isMe = Player.me.id == player.id;
            this.dir = player.dir;
            this.tPrepare = 0;
            int num = 0;
            this.level = player.spaceship;
            if (this.level == 0)
            {
                this.imageSpaceship = imagesSpaceship[0];
                this.imageSpaceshipOpen = imagesSpaceship[1];
            }
            else
            {
                this.imageSpaceship = imagesSpaceshipVip[0];
                this.imageSpaceshipOpen = imagesSpaceshipVip[1];
            }
            while (num < 100)
            {
                num++;
                this.y2 += 18;
                if (Map.IsWall(this.x, this.y2))
                {
                    if (this.y2 % 36 != 0)
                    {
                        this.y2 -= this.y2 % 36;
                    }
                    break;
                }
            }
            this.isDown = true;
            SoundMn.airShip();
        }

        public void Paint(MyGraphics g)
        {
            if (Player.isLoadingMap || !IsPaint())
            {
                return;
            }
            if (isDown)
            {
                if (tPrepare > 10)
                {
                    g.DrawImage((dir != 1) ? (x + 11) : (x - 11), y + 2, imageSpaceshipOpen, (dir == 1) ? 2 : 0, StaticObj.BOTTOM_HCENTER);
                }
                else
                {
                    g.DrawImage(x, y, imageSpaceship, (dir == 1) ? 2 : 0, StaticObj.BOTTOM_HCENTER);
                }
            }
            else if (tPrepare < 20)
            {
                g.DrawImage((dir != 1) ? (x + 11) : (x - 11), y + 2, imageSpaceshipOpen, (dir == 1) ? 2 : 0, StaticObj.BOTTOM_HCENTER);
            }
            else
            {
                g.DrawImage(x, y, imageSpaceship, (dir == 1) ? 2 : 0, StaticObj.BOTTOM_HCENTER);
            }
            if (isPaintFire && y != -80)
            {
                if (isDown && tPrepare == 0)
                {
                    GraphicManager.instance.Draw(g, iconsFire[level, ticks], x, y + 20, 180f, StaticObj.BOTTOM_HCENTER);
                }
                else if (isUp)
                {
                    GraphicManager.instance.Draw(g, iconsFire[level, ticks], x, y - imageSpaceship.GetHeight() - 20, 0, StaticObj.TOP_CENTER);
                }
            }
        }

        public void PaintHole(MyGraphics g)
        {
            if (Player.isLoadingMap || !IsPaint() || !isPaintHole)
            {
                return;
            }
            g.DrawImage(imageHole, x, y2 + 30, StaticObj.BOTTOM_HCENTER);
        }

        private bool IsPaint()
        {
            if (y < ScreenManager.instance.gameScreen.cmy - 100)
            {
                return false;
            }
            if (y > ScreenManager.instance.gameScreen.cmy + GameCanvas.h + 100)
            {
                return false;
            }
            if (x < ScreenManager.instance.gameScreen.cmx - 100)
            {
                return false;
            }
            if (x > ScreenManager.instance.gameScreen.cmx + GameCanvas.w + 100)
            {
                return false;
            }
            return true;
        }

        public void Update()
        {
            if (isPaintFire && y != -80 && GameCanvas.gameTick % 3 == 0)
            {
                if (ticks < iconsFire.GetLength(1) - 1)
                {
                    ticks++;
                }
                else
                {
                    ticks = 0;
                }
            }
            if (isDown)
            {
                isPaintFire = true;
                speed += 2;
                if (y2 - y < speed)
                {
                    y = y2;
                    isPaintFire = false;
                }
                else
                {
                    y += speed;
                }
                if (isMe && type == 1 && Player.me.isSpaceship)
                {
                    Player.me.x = x;
                    Player.me.y = y - 30;
                    Player.me.SetStatus(PlayerStatus.FALL);
                    ScreenManager.instance.gameScreen.LoadCamera(x, y);
                }
                if (player != null && !isMe && type == 1 && player.isSpaceship)
                {
                    player.x = x;
                    player.y = y - 30;
                    player.SetStatus(PlayerStatus.FALL);
                }
                if (Math.Abs(y - y2) < 50 && Map.IsWall(x, y))
                {
                    isPaintHole = true;
                    y = y2;
                    if (!isShock)
                    {
                        isShock = true;
                        tShock = 10;
                    }
                    tPrepare++;
                    if (tPrepare > 30)
                    {
                        tPrepare = 0;
                        isDown = false;
                        isUp = true;
                        isPaintFire = false;
                    }
                    if (type == 1)
                    {
                        if (isMe)
                        {
                            Player.me.isSpaceship = false;
                            Service.instance.PlayerMove();
                        }
                        else if (player != null)
                        {
                            player.isSpaceship = false;
                            if (!ScreenManager.instance.gameScreen.players.Contains(player))
                            {
                                ScreenManager.instance.gameScreen.players.Add(player);
                            }
                        }
                    }
                }
            }
            else if (isUp)
            {
                tPrepare++;
                if (tPrepare > 30)
                {
                    int num = y2 + 24 - y >> 2;
                    if (num > 90)
                    {
                        num = 90;
                    }
                    y -= num;
                    isPaintFire = true;
                }
                else
                {
                    if (tPrepare > 0 && type == 0)
                    {
                        if (isMe)
                        {
                            Player.me.isSpaceship = false;
                            if (!Player.me.IsDead())
                            {
                                Player.me.SetStatus(PlayerStatus.JUMP);
                                Player.me.vy = -3;
                            }
                        }
                        else if (player != null)
                        {
                            player.isSpaceship = false;
                            if (!player.IsDead())
                            {
                                player.SetStatus(PlayerStatus.JUMP);
                                player.vy = -3;
                            }
                        }
                    }
                    if (tPrepare > 12 && type == 0)
                    {
                        if (isMe)
                        {
                            Player.me.isSpaceship = true;
                        }
                        else if (player != null)
                        {
                            player.x = x;
                            player.y = y;
                            player.isSpaceship = true;
                        }
                    }
                }
                if (isMe)
                {
                    if (type == 0)
                    {
                        ScreenManager.instance.gameScreen.LoadCamera(x, y);
                    }
                }
                if (y <= -80)
                {
                    if (isMe && type == 0)
                    {
                        Controller.isStopReadMessage = false;
                        Player.isChangingMap = true;
                    }
                    if (!isMe && player != null && type == 0)
                    {
                        if (Player.me.playerFocus == player)
                        {
                            Player.me.playerFocus = null;
                            Player.isManualFocus = false;
                        }
                        ScreenManager.instance.gameScreen.players.Remove(player);
                    }
                    y = -80;
                    tDelayHole++;
                    if (tDelayHole > 80)
                    {
                        tDelayHole = 0;
                        ScreenManager.instance.gameScreen.spaceships.Remove(this);
                    }
                }
            }
        }
    }
}
