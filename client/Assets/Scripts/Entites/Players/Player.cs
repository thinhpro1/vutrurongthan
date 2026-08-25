using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.Effects;
using Assets.Scripts.Entites.ItemMaps;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Npcs;
using Assets.Scripts.Frames;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;
using Assets.Scripts.Services;
using Assets.Scripts.Skills;
using Assets.Scripts.Tasks;
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Assets.Scripts.Entites.Players
{
    public class Player : Entity
    {
        public static bool isSelectMonster = true;

        public static bool isSelectNpc = true;

        public static bool isSelectPlayer = true;

        public static bool isSelectItemMap = true;

        public static bool isPaintMedal = true;

        public static bool isSelectEnemy;

        public int spaceship;

        public int sex;

        public int baseHp;

        public int baseMp;

        public int baseDamage;

        public int baseConstitution;

        public long maxDamage;

        public string critical;

        public string dodge;

        public int pointActivity;

        // giảm sát thương => giáp...
        public string reduceDamage;

        // giảm sát thương chí mạng
        public string reduceDamageCritical;

        // chính xác
       

        // tấn công khi đánh chí mạng
        public string criticalStrike;

        // hút máu
        public string bloodsucking;

        // hút mana
        public string manaSucking;

        // phản đòn
        public string strikeBack;


        public string accurate;
        public string critDamage;
        public string ignoreCrit;
        public string pierceArmor;

        public int pointSkill;

        public int level;

        public long coin;

        public long coinLock;

        public int diamond;

        public int ruby;

        public int pointPk;

        public long potentialUpDamage;

        public long potentialUpHp;

        public long potentialUpMp;

        public long potentialUpConstitution;

        public static bool isLoadingMap;

        public static bool isChangingMap;

        public static bool isLockKey;

        public static Player me;

        public static Player disciple;

        public static MonsterPet pet;

        public string statusDisciple;

        public static bool isMoveUp;

        public static bool isMoveDown;

        public static bool isMoveRight;

        public static bool isMoveLeft;

        public MovePoint currentMovePoint;

        public int vx;

        public int vy;

        public int xSend;

        public int ySend;

        public bool isCanFly;

        public int checkStatus;

        public int delayFall;

        public List<Entity> focus = new List<Entity>();

        public Player playerFocus;

        public ItemMap itemFocus;

        public Npc npcFocus;

        public Monster monsterFocus;

        public sbyte timeInjure;

        public ChatInfo chatInfo;

        public SkillPaint skillPaint;

        public int sType;

        public SkillDart dart;

        public List<Skill> skills;

        public Skill mySkill;

        public long power;

        public long potential;

        public Task task;

        public List<Item> itemsBag = new List<Item>();

        public List<Item> itemsBox = new List<Item>();

        public List<Item> itemsBody = new List<Item>();

        public List<Item> itemsOther = new List<Item>();

        private int timeFocusToMob;

        public static bool isManualFocus;

        public bool isSpaceship;

        public bool isWaitDart;

        public long lastTimeWaitDart;

        public Team team;

        public static bool isLogin;

        public int killId;

        public int typePk;

        public int typeFlag;

        public static int[] imgsFlag = new int[] { 749, 73, 74, 75, 76, 746, 747, 748, 3348, 3349 };

        public Clan clan;

        public Mount mount;

        private long lastTimeSoundPlayerRun;

        private long lastTimeSoundPlayerFly;

        private long lastTimeSoundPlayerRescure;

        public TaskDaily taskDaily;

        public long timeEffect;

        public int indexEffect;

        public long tTimeEffect;

        public long timeEffectEquip;

        public int indexEffectEquip;

        public long tTimeEffectEquip;

        public int countBarrack;

        public int levelEquip;

        public Bag bag;

        public bool isDisciple;

        public bool isBoss;

        public int stamina;

        public int maxStamina;

        public bool isFusion;

        public int tFusion;

        public int medal;

        public int aura = -1;

        public int xDie;

        public int yDie;

        public Frame head;

        public Frame body;

        public bool isCharge;

        public int dx;

        public int dy;

        public Player()
        {
            speed = 8;
            h = 96;
            w = 66;
            status = PlayerStatus.FALL;
            isCanFly = true;
            chatInfo = new ChatInfo();
        }

        public override void Paint(MyGraphics g)
        {
            if (!IsPaint() || isSpaceship)
            {
                return;
            }
            if (mount != null)
            {
                mount.PaintBehind(g);
            }
            PaintAura(g);
            PaintEffectPower(g);
            //PaintEffectEquip(g);
            if (bag != null && !bag.isFly)
            {
                bag.Paint(g, this);
            }
            PaintBody(g, x, y, dir);
            foreach (Effect effect in effects)
            {
                effect.Paint(g);
            }
            if (mount != null)
            {
                mount.PaintFront(g);
            }
            if (bag != null && bag.isFly)
            {
                bag.Paint(g, this);
            }
            PaintName(g);
        }

        public void PaintAura(MyGraphics g)
        {
            if (isBoss || aura == -1)
            {
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (status != PlayerStatus.STAND)
            {
                timeAura = now;
                return;
            }
            if (now - timeAura < 1500)
            {
                return;
            }
            if (FrameManager.instance.auras.ContainsKey(aura))
            {
                FrameManager.instance.auras[aura].Paint(g, x, y, dir);
            }
        }

        int indexAura;
        long timeAura;
        long tTimeAura;

        public void PaintEffectEquip(MyGraphics g)
        {
            if (!ScreenManager.instance.gameScreen.isShowEffectPower)
            {
                return;
            }
            if (isBoss)
            {
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (status != PlayerStatus.STAND)
            {
                timeEffectEquip = now;
                return;
            }
            if (now - timeEffectEquip < 1500)
            {
                return;
            }
            if (now - tTimeEffectEquip > 100)
            {
                tTimeEffectEquip = now;
                indexEffectEquip++;
            }
            int level = levelEquip;
            if (Equals(this) && itemsBody != null && itemsBody.Count >= 8)
            {
                level = 16;
                for (int i = 0; i < 8; i++)
                {
                    Item item = itemsBody[i];
                    if (item == null)
                    {
                        level = 0;
                        break;
                    }
                    else
                    {
                        int upgrade = item.GetUpgrade();
                        if (level > upgrade)
                        {
                            level = upgrade;
                        }
                    }
                }
            }
            if (level < 4)
            {
                return;
            }
            int paint;
            if (level < 8)
            {
                paint = 0;
            }
            else if (level < 12)
            {
                paint = 1;
            }
            else if (level < 14)
            {
                paint = 2;
            }
            else if (level < 16)
            {
                paint = 3;
            }
            else
            {
                paint = 4;
            }
            int[] effects = FrameManager.instance.effectEquips[paint];
            if (indexEffectEquip >= effects.Length)
            {
                indexEffectEquip = 0;
            }
            GraphicManager.instance.Draw(g, effects[indexEffectEquip], x, y, 0, StaticObj.VCENTER_HCENTER);
        }

        public void PaintEffectPower(MyGraphics g)
        {
            if (isBoss || level == 0)
            {
                return;
            }
            if (isFusion)
            {
                long current = Utils.CurrentTimeMillis();
                if (current - tTimeEffect > 100)
                {
                    tTimeEffect = current;
                    indexEffect++;
                }
                if (indexEffect > 3)
                {
                    indexEffect = 0;
                }
                GraphicManager.instance.Draw(g, 2256 + indexEffect, x, y + 25, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
                GraphicManager.instance.Draw(g, 1718 + indexEffect, x, y + 25, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
                return;
            }
            if (!ScreenManager.instance.gameScreen.isShowEffectPower)
            {
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (status != PlayerStatus.STAND)
            {
                timeEffect = now;
                return;
            }
            if (now - timeEffect < 1500)
            {
                return;
            }
            foreach (Effect effect in effects)
            {
                if (effect is EffectTime)
                {
                    if (((EffectTime)effect).template.effectImage != null)
                    {
                        return;
                    }
                }
            }
            int dx = 0;
            int dy = 0;
            int num = level / 10;
            num = 10;
            if (num == 0 || num == 1 || num == 2)
            {
                dy = 15;
            }
            else if (num == 9)
            {
                dy = 10;
            }
            else if (num == 3)
            {
                dy = 15;
                dx = -5;
            }
            else if (num == 4 || num == 8 || num == 7 || num == 6 || num == 10)
            {
                dy = 25;
            }
            else if (num == 5)
            {
                dx = -5;
                dy = 25;
            }
            int[] effectPowers = FrameManager.instance.effectPowers[num];
            if (now - tTimeEffect > 100)
            {
                tTimeEffect = now;
                indexEffect++;
            }
            if (indexEffect >= effectPowers.Length)
            {
                indexEffect = 0;
            }
            GraphicManager.instance.Draw(g, effectPowers[indexEffect], x + dx * dir, y + dy, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
        }

        public void PaintName(MyGraphics g)
        {
            // Dùng chiều cao thực của body sprite (height) thay vì hitbox h
            int spriteH = h; // fallback: dùng hitbox
            if (body != null && body.template.height > 0)
            {
                spriteH = body.template.height;
            }
            // Cộng thêm offset từ head.template.dy (âm = nhô lên trên)
            int headExtra = 0;
            if (head != null && head.template.dy < 0)
            {
                headExtra = -head.template.dy;
            }
            // Nhân vật lớn (height >= 150) thêm padding để thoáng hơn
            int padding = (spriteH >= 150) ? 30 : 15;
            int yPaint = y - spriteH - headExtra - padding;
            if (me.playerFocus != null && me.playerFocus.Equals(this))
            {
                g.DrawImage(GameScreen.imgSelect, x, yPaint, 3);
                yPaint -= GameScreen.imgSelect.GetHeight() / 2;
            }
            MyFont myFont = MyFont.text_white;
            if (isDisciple)
            {
                myFont = MyFont.text_blue;
            }
            if (typePk == 1 || typePk == 2 || typePk == 3 || (bag != null && bag.id == 0))
            {
                myFont = MyFont.text_red;
            }
            else if (team != null)
            {
                foreach (CmdTeamMember info in me.team.members)
                {
                    if (info.player.id == this.id)
                    {
                        myFont = MyFont.text_yellow;
                        break;
                    }
                }
            }
            bool isHide = false;
            if (Equals(me) && (playerFocus != null || npcFocus != null))
            {
                isHide = true;
            }
            if (!isHide)
            {
                yPaint -= myFont.GetHeight();
                myFont.DrawString(g, name, x, yPaint, 2, MyFont.text_grey);
                if (typePk == 2 || typeFlag > 0)
                {
                    int xPaint = x + 30 + myFont.GetWidth(name) / 2;
                    if (typePk == 2)
                    {
                        GraphicManager.instance.Draw(g, 77, xPaint, yPaint + myFont.GetHeight() / 2, 0, StaticObj.VCENTER_HCENTER);
                    }
                    else if (typeFlag > 0)
                    {
                        GraphicManager.instance.Draw(g, imgsFlag[typeFlag - 1], xPaint, yPaint + myFont.GetHeight() / 2, 0, StaticObj.VCENTER_HCENTER);
                    }
                }
                if (clan != null)
                {
                    yPaint -= myFont.GetHeight();
                    MyFont.text_blue.DrawString(g, clan.name, x, yPaint, 2, MyFont.text_grey);
                }
                if (medal != -1 && isPaintMedal && FrameManager.instance.medals.ContainsKey(medal))
                {
                    FrameManager.instance.medals[medal].Paint(g, x, yPaint + 30);
                }
            }
        }

        public void PaintBody(MyGraphics g, int x, int y, int dir)
        {
            /*if (head != null && head.template.id != 241)
            {
                head = FrameManager.instance.GetFrame(241);
            }
            if (body != null && body.template.id != 242)
            {
                body = FrameManager.instance.GetFrame(242);
            }*/
            if (head != null && head.icon != -1)
            {
                GraphicManager.instance.Draw(g, head.icon, x + head.template.dx * dir, y + head.template.dy, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
            }
            if (body != null && body.icon != -1)
            {
                GraphicManager.instance.Draw(g, body.icon, x + body.template.dx * dir, y + body.template.dy, (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
            }
            try
            {
                if (skillPaint != null)
                {
                    List<SkillPaintInfo> skillPaintInfos = skillPaint.info;
                    if (skillPaint.index < skillPaintInfos.Count)
                    {
                        SkillPaintInfo skillPaintInfo = skillPaintInfos[skillPaint.index];
                        List<SkillEffect> effects = skillPaintInfo.effects;
                        foreach (SkillEffect effect in effects)
                        {
                            SkillEffectInfo effectInfo = effect.effectInfo;
                            GraphicManager.instance.Draw(g, effectInfo.icons[effect.index], x + dir * (effectInfo.dx + dx + (skillPaint.isFly ? skillPaint.dxFly : 0)),
                                y + effectInfo.dy + dy + (skillPaint.isFly ? skillPaint.dyFly : 0), (dir == 1) ? 0 : 2, StaticObj.BOTTOM_HCENTER);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.LogException(ex);
            }
        }

        public void InitBody()
        {
            try
            {
                if (body != null && body.template.height >= 150)
                {
                    w = 90;
                    h = 150;
                    dx = 10;
                    dy = -10;
                    return;
                }
            }
            catch
            {
            }
            SetDefaultBody();
        }

        public void SetDefaultBody()
        {
            dx = 0;
            dy = 0;
            w = 66;
            h = 96;
        }

        public void UseMySkill()
        {
            if (hp <= 0)
            {
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (mySkill.timeCanUse > now)
            {
                return;
            }
            if (!IsCanUseSkill())
            {
                InfoMe.addInfo("Không đủ KI để sử dụng", 0);
                return;
            }
            mp -= GetManaUseSkill();
            mySkill.timeCanUse = now + mySkill.GetCoolDown();
            Entity entity = playerFocus;
            if (entity == null)
            {
                entity = monsterFocus;
            }
            SetAttack(entity);
        }

        public void UpdateSkill()
        {
            if (skillPaint == null)
            {
                return;
            }
            List<SkillPaintInfo> skillPaintInfos = skillPaint.info;
            if (skillPaint.index >= skillPaintInfos.Count)
            {
                if (skillPaint.isFly)
                {
                    SetStatus(PlayerStatus.FALL);
                }
                else
                {
                    SetStatus(PlayerStatus.STAND);
                }
                vx = 0;
                skillPaint = null;
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (skillPaint.updateTime == 0)
            {
                skillPaint.updateTime = now;
            }
            SkillPaintInfo skillPaintInfo = skillPaintInfos[skillPaint.index];
            if (head != null)
            {
                head.icon = head.template.action[skillPaintInfo.action[skillPaint.isFly ? 1 : 0]];
            }
            if (body != null)
            {
                body.icon = body.template.action[skillPaintInfo.action[skillPaint.isFly ? 1 : 0]];
            }
            List<SkillEffect> effects = skillPaintInfo.effects;
            bool isNext = true;
            foreach (SkillEffect effect in effects)
            {
                if (now - effect.updateTime > 10)
                {
                    effect.updateTime = now;
                    if (effect.index < effect.effectInfo.icons.Count - 1)
                    {
                        effect.index++;
                    }
                    else
                    {
                        if (skillPaintInfo.timeOut != -1 || effect.loop > 0 || effect.loop == -1 || skillPaintInfo.dart != null)
                        {
                            effect.index = 0;
                            if (effect.loop > 0)
                            {
                                effect.loop--;
                            }
                        }
                    }
                }
            }
            foreach (SkillEffect effect in effects)
            {
                if (effect.loop > 0)
                {
                    isNext = false;
                    break;
                }
            }
            if (skillPaintInfo.timeOut != -1)
            {
                if (!isCharge || now - skillPaint.updateTime > skillPaintInfo.timeOut)
                {
                    isNext = true;
                }
                else
                {
                    isNext = false;
                }
            }
            if (isNext)
            {
                if (skillPaintInfo.dart != null && dart == null)
                {
                    dart = new SkillDart(this, skillPaintInfo.dart, x + skillPaintInfo.dart.bullet.dx * dir, y + skillPaintInfo.dart.bullet.dy);
                }
                if (dart == null || !dart.template.isLine)
                {
                    skillPaint.updateTime = now;
                    skillPaint.index++;
                }
            }

        }

        public void SetAttack(Entity entity)
        {
            if (mySkill == null)
            {
                return;
            }
            if (Equals(me))
            {
                Service.instance.UseSkill(mySkill, entity);
            }
            SetSkillPaint(mySkill.GetSkillPaint());
        }

        public void SetSkillPaint(SkillPaint skillPaint)
        {
            if (skillPaint == null)
            {
                return;
            }
            SkillPaint paint = skillPaint.Clone();
            paint.isFly = !IsWallBottom();
            isCharge = false;
            foreach (SkillPaintInfo paintInfo in skillPaint.info)
            {
                if (paintInfo.timeOut != -1)
                {
                    isCharge = true;
                    break;
                }
            }
            this.skillPaint = paint;
            SetStatus(PlayerStatus.ACTION);
            if (monsterFocus != null)
            {
                monsterFocus.dir = x > monsterFocus.x ? 1 : -1;
            }
            if (paint.id == 36)
            {
                ScreenManager.instance.gameScreen.dem = 0;
                ScreenManager.instance.gameScreen.isUseFreez = true;
            }
        }

        public void SetSkillPaint(int paintId)
        {
            if (SkillManager.instance.paints.ContainsKey(paintId))
            {
                SetSkillPaint(SkillManager.instance.paints[paintId]);
            }
        }

        public bool IsAreaWaypoint()
        {
            foreach (var waypoint in Map.waypoints)
            {
                if (y <= waypoint.maxY && y >= waypoint.maxY - 150 && x >= waypoint.minX - 200 && x <= waypoint.minX + 200)
                {
                    return true;
                }
            }
            return false;
        }

        public Waypoint GetWaypoint()
        {
            if (isSpaceship)
            {
                return null;
            }
            foreach (var waypoint in Map.waypoints)
            {
                if (x >= waypoint.minX && x <= waypoint.maxX && y >= waypoint.minY && y <= waypoint.maxY)
                {
                    return waypoint;
                }
            }
            return null;
        }

        public override void Update()
        {
            base.Update();
            if (isSpaceship)
            {
                currentMovePoint = null;
            }
            chatInfo.Update();
            if (!Equals(me) && hp <= 0 && status != PlayerStatus.DEAD)
            {
                StartDie(x, y);
            }
            try
            {
                if (skillPaint != null)
                {
                    UpdateSkill();
                }
                if (dart != null)
                {
                    dart.Update();
                    if (Equals(me) && IsDead())
                    {
                        dart.Stop();
                    }
                }
            }
            catch
            {
            }
            if (isBlind)
            {
                if (head != null)
                {
                    head.icon = head.template.injure;
                }
                if (body != null)
                {
                    body.icon = body.template.injure;
                }
                return;
            }
            if (this.Equals(me))
            {
                if (x < 10)
                {
                    vx = 0;
                    x = 10;
                }
                else if (x > Map.width - 10)
                {
                    vx = 0;
                    x = Map.width - 10;
                }
                if (x < 50)
                {
                    if (!IsAreaWaypoint())
                    {
                        vx = 0;
                        x = 50;
                    }
                }
                else if (x > Map.width - 50 && !IsAreaWaypoint())
                {
                    vx = 0;
                    x = Map.width - 50;
                }
                if (!isChangingMap)
                {
                    Waypoint waypoint = GetWaypoint();
                    if (waypoint != null && waypoint.type != 2)
                    {
                        Service.instance.PlayerMove();
                        Service.instance.RequestChangeMap();
                        isLockKey = true;
                        isChangingMap = true;
                        InfoDlg.ShowWait();
                        return;
                    }
                }
                if (isChangingMap)
                {
                    return;
                }
                if (status != PlayerStatus.FALL && Math.Abs(x - xSend) + Math.Abs(y - ySend) >= 70 && y - ySend <= 0)
                {
                    Service.instance.PlayerMove();
                }
                if (isLockMove)
                {
                    currentMovePoint = null;
                }
                if (currentMovePoint != null)
                {
                    if (Math.Abs(x - currentMovePoint.xEnd) <= 16 && Math.Abs(y - currentMovePoint.yEnd) <= 16)
                    {
                        x = (currentMovePoint.xEnd + x) / 2;
                        y = currentMovePoint.yEnd;
                        currentMovePoint = null;
                        vx = (vy = 0);
                        if (IsWallBottom())
                        {
                            SetStatus(PlayerStatus.STAND);
                        }
                        else
                        {
                            SetStatus(PlayerStatus.FALL);
                        }
                        Service.instance.PlayerMove();
                    }
                    else
                    {
                        dir = (currentMovePoint.xEnd > x) ? 1 : (-1);
                        if (IsWallBottom())
                        {
                            SetStatus(PlayerStatus.RUN);
                            if (currentMovePoint != null)
                            {
                                vx = speed * dir;
                                vy = 0;
                            }
                            if (Math.Abs(x - currentMovePoint.xEnd) <= 10)
                            {
                                if (currentMovePoint.yEnd > y)
                                {
                                    currentMovePoint = null;
                                    SetStatus(PlayerStatus.STAND);
                                    vx = (vy = 0);
                                }
                                else
                                {
                                    x = currentMovePoint.xEnd;
                                    SetStatus(PlayerStatus.FLY);
                                    vy = -speed;
                                    vx = 0;
                                }
                            }
                            if (IsWallMid(speed * dir))
                            {
                                SetStatus(PlayerStatus.FLY);
                                vx = dir * speed;
                                vy = -speed;
                            }
                        }
                        else
                        {
                            if (currentMovePoint.yEnd < y + 10)
                            {
                                SetStatus(PlayerStatus.FLY);
                                vy = -speed;
                                if (Math.Abs(y - currentMovePoint.yEnd) <= 10)
                                {
                                    SetStatus(PlayerStatus.RUN);
                                    y = currentMovePoint.yEnd;
                                    vy = 0;
                                    vx = speed * dir;
                                }
                                if (Math.Abs(x - currentMovePoint.xEnd) <= 10)
                                {
                                    vx = 0;
                                }
                                else
                                {
                                    vx = speed * dir;
                                }
                            }
                            else
                            {
                                vy = speed;
                                if (status == PlayerStatus.FLY || status == PlayerStatus.RUN)
                                {
                                    vy = 0;
                                }
                                SetStatus(PlayerStatus.FALL);
                            }
                            if (currentMovePoint.yEnd > y && IsWallMid())
                            {
                                currentMovePoint = null;
                                vx = (vy = 0);
                                SetStatus(PlayerStatus.FALL);
                            }
                        }

                    }
                }
                SearchFocus();
                UpdateMove();
            }
            else
            {
                if (currentMovePoint != null)
                {
                    if (Math.Abs(x - currentMovePoint.xEnd) <= 16 && Math.Abs(y - currentMovePoint.yEnd) <= 16)
                    {
                        x = (currentMovePoint.xEnd + x) / 2;
                        y = currentMovePoint.yEnd;
                        currentMovePoint = null;
                        vx = (vy = 0);
                        if (IsWallBottom())
                        {
                            SetStatus(PlayerStatus.STAND);
                        }
                        else
                        {
                            SetStatus(PlayerStatus.FALL);
                        }
                    }
                    else
                    {
                        dir = (currentMovePoint.xEnd > x) ? 1 : (-1);
                        if (IsWallBottom())
                        {
                            SetStatus(PlayerStatus.RUN);
                            if (currentMovePoint != null)
                            {
                                vx = speed * dir;
                                vy = 0;
                            }
                            if (Math.Abs(x - currentMovePoint.xEnd) <= 10)
                            {
                                if (currentMovePoint.yEnd > y)
                                {
                                    currentMovePoint = null;
                                    SetStatus(PlayerStatus.STAND);
                                    vx = (vy = 0);
                                }
                                else
                                {
                                    x = currentMovePoint.xEnd;
                                    SetStatus(PlayerStatus.JUMP);
                                    vy = -speed;
                                    vx = 0;
                                }
                            }
                            if (IsWallMid(speed * dir))
                            {
                                SetStatus(PlayerStatus.JUMP);
                                vx = dir * speed;
                                vy = -speed;
                            }
                        }
                        else
                        {
                            if (currentMovePoint.yEnd < y + 10)
                            {
                                SetStatus(PlayerStatus.JUMP);
                                vy = -speed;
                                if (Math.Abs(y - currentMovePoint.yEnd) <= 10)
                                {
                                    SetStatus(PlayerStatus.RUN);
                                    y = currentMovePoint.yEnd;
                                    vy = 0;
                                    vx = speed * dir;
                                }
                                if (Math.Abs(x - currentMovePoint.xEnd) <= 10)
                                {
                                    vx = 0;
                                }
                                else
                                {
                                    vx = speed * dir;
                                }
                            }
                            else
                            {
                                vy = speed;
                                if (status == PlayerStatus.FLY || status == PlayerStatus.RUN)
                                {
                                    vy = 0;
                                }
                                SetStatus(PlayerStatus.FALL);
                            }
                            if (!IsWallBottom(h) && Math.Abs(vx) > speed / 2)
                            {
                                SetStatus(PlayerStatus.FLY);
                            }
                            if (currentMovePoint.yEnd > y && IsWallMid())
                            {
                                currentMovePoint = null;
                                vx = (vy = 0);
                                SetStatus(PlayerStatus.FALL);
                            }
                        }

                    }
                }
            }

            if (skillPaint == null && !isSpaceship)
            {
                switch (status)
                {
                    case PlayerStatus.STAND:
                        UpdateStand();
                        break;
                    case PlayerStatus.RUN:
                        UpdateRun();
                        break;
                    case PlayerStatus.JUMP:
                        UpdateJump();
                        break;
                    case PlayerStatus.FALL:
                        UpdateFall();
                        break;
                    case PlayerStatus.START_DEAD:
                        UpdateStartDead();
                        break;
                    case PlayerStatus.DEAD:
                        UpdateDead();
                        break;
                    case PlayerStatus.FLY:
                        UpdateFly();
                        break;
                    case PlayerStatus.ACTION:
                        //UpdateSkill();
                        break;
                }
            }

            if (mount != null)
            {
                UpdateMount();
            }
            if (timeInjure > 0)
            {
                timeInjure--;
                if (skillPaint == null)
                {
                    if (head != null)
                    {
                        head.icon = head.template.injure;
                    }
                    if (body != null)
                    {
                        body.icon = body.template.injure;
                    }
                }
            }
        }

        public void UpdateMount()
        {
            if (mount == null)
            {
                return;
            }
            mount.Update(this);
        }

        public void SetMountStart()
        {
            if (mount == null)
            {
                return;
            }
            mount.Start(this);
        }

        private void UpdateStartDead()
        {
            frameTick++;
            x += (xDie - x) / 4;
            if (frameTick > 7)
            {
                y += (yDie - y) / 4;
            }
            else
            {
                y += frameTick - 10;
            }
            if (Math.Abs(xDie - x) < 10 && Math.Abs(yDie - y) < 10)
            {
                x = xDie;
                y = yDie;
                SetStatus(PlayerStatus.DEAD);
            }
            if (head != null)
            {
                head.icon = head.template.injure;
            }
            if (body != null)
            {
                body.icon = body.template.injure;
            }
        }

        private void UpdateDead()
        {
            frameTick++;
            if (frameTick > 30)
            {
                frameTick = 0;
            }
            int index = 0;
            if (frameTick % 15 >= 5)
            {
                index = 1;
            }
            if (head != null)
            {
                head.icon = head.template.dead[index];
            }
            if (body != null)
            {
                body.icon = body.template.dead[index];
            }
        }

        private void UpdateFall()
        {
            if ((x <= 50 && vx < 0) || (x >= Map.width - 50 && vx > 0))
            {
                vx = 0;
            }
            if (y + 12 >= Map.height)
            {
                SetStatus(PlayerStatus.STAND);
                vx = 0;
                vy = 0;
                return;
            }
            if (IsWallBottom())
            {
                if (Equals(me) && (y - ySend != 0 || x - xSend != 0))
                {
                    Service.instance.PlayerMove();
                }
                SetStatus(PlayerStatus.STAND);
                vx = 0;
                vy = 0;
                AddEffectMove(1);
                return;
            }
            vy++;
            if (vy > speed * 3 / 2)
            {
                vy = speed * 3 / 2;
            }
            x += vx;
            y += vy;
            while (IsWallBottom(-1))
            {
                y--;
            }
            if (IsWallMid())
            {
                vx = 0;
                if (Map.template != null && Map.template.isLine)
                {
                    x -= dir;
                }
                else if (dir == 1)
                {
                    x = Map.GetTileXofPixel(x + w / 2) + w / 2;
                }
                else
                {
                    x = Map.GetTileXofPixel(x - w / 2 - 1) + Map.size + w / 2;
                }
            }
            if (IsWallBottom())
            {
                if (Equals(me) && (y - ySend != 0 || x - xSend != 0))
                {
                    Service.instance.PlayerMove();
                }
                vx = (vy = 0);
                SetStatus(PlayerStatus.STAND);
                AddEffectMove(1);
                return;
            }
            if (mount != null && mount.isShow)
            {
                frameTick++;
                if (frameTick > 30)
                {
                    frameTick = 0;
                }
                int index = 0;
                if (frameTick % 15 >= 5)
                {
                    index = 1;
                }
                if (head != null)
                {
                    head.icon = head.template.stand[index];
                }
                if (body != null)
                {
                    body.icon = body.template.stand[index];
                }
            }
            else
            {
                if (head != null)
                {
                    head.icon = head.template.fall;
                }
                if (body != null)
                {
                    body.icon = body.template.fall;
                }
            }
        }

        private void UpdateFly()
        {
            SetMountStart();
            if ((x <= 50 && vx < 0) || (x > Map.width - 50 - Math.Abs(vx) && vx > 0))
            {
                SetStatus(PlayerStatus.FALL);
                vx = 0;
                vy = 5;
                return;
            }
            if (y - h < 0 || Map.IsWall(x, y - h))
            {
                if (y - h < 0)
                {
                    y = h;
                }
                SetStatus(PlayerStatus.FALL);
                vx = 0;
                vy = 5;
                return;
            }
            /*if (Math.Abs(vx) <= 4)
            {
                if (vy < 0)
                {
                    vy = 0;
                }
                if (vy > speed)
                {
                    vy = speed;
                }
            }*/
            if (IsWallMid())
            {
                vx = 0;
                if (Map.template != null && Map.template.isLine)
                {
                    x -= dir;
                }
                else if (dir == 1)
                {
                    x = Map.GetTileXofPixel(x + w / 2) - w / 2;
                }
                else
                {
                    x = Map.GetTileXofPixel(x - w / 2 - 1) + Map.size + w / 2;
                }
            }
            x += vx;
            y += vy;
            if (Equals(me))
            {
                if (vx > 0)
                {
                    vx--;
                }
                else if (vx < 0)
                {
                    vx++;
                }
                else if (vy == 0)
                {
                    Service.instance.PlayerMove();
                    SetStatus(PlayerStatus.FALL);
                    vy = 5;
                    return;
                }
                if (currentMovePoint == null)
                {
                    bool flag = false;
                    for (int i = 0; i < h; i++)
                    {
                        if (Map.IsWall(x, y + i))
                        {
                            flag = true;
                            break;
                        }
                    }
                    if (flag)
                    {
                        vx = 0;
                        vy = speed / 2;
                        SetStatus(PlayerStatus.FALL);
                        return;
                    }
                }
                if (Math.Abs(x - xSend) > 96 || Math.Abs(y - ySend) > 24)
                {
                    Service.instance.PlayerMove();
                }
            }
            if (mount != null && mount.isShow)
            {
                frameTick++;
                if (frameTick > 30)
                {
                    frameTick = 0;
                }
                int index = 0;
                if (frameTick % 15 >= 5)
                {
                    index = 1;
                }
                if (head != null)
                {
                    head.icon = head.template.stand[index];
                }
                if (body != null)
                {
                    body.icon = body.template.stand[index];
                }
            }
            else
            {
                if (head != null)
                {
                    head.icon = head.template.fly;
                }
                if (body != null)
                {
                    body.icon = body.template.fly;
                }
                if (GameCanvas.gameTick % 15 == 0)
                {
                    EffectImage effectImage = new EffectImage(-1, 0, 0, 30, -1, -1 );
                    effects.Add(new EffectLoop(this, effectImage, 1, -w, this.h * 2 / 3, StaticObj.VCENTER_HCENTER, dir < 0 ? 2 : 0));
                }
            }
        }

        private void UpdateStand()
        {
            frameTick++;
            if (frameTick > 30)
            {
                frameTick = 0;
            }
            int index = 0;
            if (frameTick % 15 >= 5)
            {
                index = 1;
            }
            if (head != null)
            {
                head.icon = head.template.stand[index];
            }
            if (body != null)
            {
                body.icon = body.template.stand[index];
            }
        }

        public bool IsCanMove(int dir, int vx)
        {
            return !IsWallMid(vx) || this.dir != dir;
        }

        private void UpdateRun()
        {
            x += vx;
            if (IsWallMid())
            {
                vx = 0;
                if (Map.template != null && Map.template.isLine)
                {
                    x -= dir;
                }
                else if (dir == 1)
                {
                    x = Map.GetTileXofPixel(x + w / 2) - w / 2;
                }
                else
                {
                    x = Map.GetTileXofPixel(x - w / 2 - 1) + Map.size + w / 2;
                }
                SetStatus(PlayerStatus.STAND);
                return;
            }
            if (Equals(me))
            {
                if (vx > 0)
                {
                    vx--;
                }
                else if (vx < 0)
                {
                    vx++;
                }
                else
                {
                    if (x - xSend != 0)
                    {
                        Service.instance.PlayerMove();
                    }
                    SetStatus(PlayerStatus.STAND);
                    return;
                }
            }
            if (!IsWallBottom())
            {
                if (Equals(me) && x - xSend != 0)
                {
                    Service.instance.PlayerMove();
                }
                SetStatus(PlayerStatus.FALL);
                return;
            }
            long now = Utils.CurrentTimeMillis();
            if (now - timeFrame > 60)
            {
                timeFrame = now;
                if (frameTick < 5)
                {
                    frameTick++;
                }
                else
                {
                    frameTick = 0;
                    AddEffectMove(0);
                }
            }
            if (head != null)
            {
                head.icon = head.template.run[frameTick];
            }
            if (body != null)
            {
                body.icon = body.template.run[frameTick];
            }
        }

        public void UpdateJump()
        {
            SetMountStart();
            if (IsWallBottom())
            {
                AddEffectMove(1);
            }
            if (Map.IsWall(x, y - h - vy) && vy < 0)
            {
                vy = 1;
            }
            x += vx;
            y += vy;
            if (y < h)
            {
                y = h;
                vy = -1;
            }
            if (!isMoveUp)
            {
                vy += 2;
            }
            if (vy > 0)
            {
                vy = 0;
            }
            if (IsWallMid())
            {
                vx = 0;
                if (Map.template != null && Map.template.isLine)
                {
                    x -= dir;
                }
                else if (dir == 1)
                {
                    x = Map.GetTileXofPixel(x + w / 2) - w / 2;
                }
                else
                {
                    x = Map.GetTileXofPixel(x - w / 2 - 1) + Map.size + w / 2;
                }
            }
            if (vy == 0)
            {
                if (Equals(me) && x - xSend != 0)
                {
                    Service.instance.PlayerMove();
                }
                SetStatus(PlayerStatus.FALL);
                vx = 0;
                return;
            }
            if (Map.IsWall(x, y - h) || (y - h) < 0)
            {
                if (Equals(me) && x - xSend != 0)
                {
                    Service.instance.PlayerMove();
                }
                SetStatus(PlayerStatus.FALL);
                if (y < h)
                {
                    y = h;
                }
                y = (Map.template != null && Map.template.isLine) ? y : Map.GetTileYofPixel(y);
                return;
            }
            if (mount != null && mount.isShow)
            {
                frameTick++;
                if (frameTick > 30)
                {
                    frameTick = 0;
                }
                int index = 0;
                if (frameTick % 15 >= 5)
                {
                    index = 1;
                }
                if (head != null)
                {
                    head.icon = head.template.stand[index];
                }
                if (body != null)
                {
                    body.icon = body.template.stand[index];
                }
            }
            else
            {
                if (head != null)
                {
                    head.icon = head.template.jump;
                }
                if (body != null)
                {
                    body.icon = body.template.jump;
                }
            }
        }

        public void UpdateMove()
        {
            if (isLockMove || isSpaceship)
            {
                return;
            }
            switch (status)
            {
                case PlayerStatus.STAND:
                    {
                        if (isMoveUp)
                        {
                            if (x - xSend != 0 || y - ySend != 0)
                            {
                                Service.instance.PlayerMove();
                            }
                            SetStatus(PlayerStatus.JUMP);
                            vx = dir * speed / 3;
                            vy = -speed;
                        }
                        else if (isMoveLeft && !IsWallMid())
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                            else
                            {
                                if (x - xSend != 0)
                                {
                                    Service.instance.PlayerMove();
                                }
                                if (IsCanMove(-1, -speed))
                                {
                                    SetStatus(PlayerStatus.RUN);
                                    vx = -speed;
                                }
                            }
                        }
                        else if (isMoveRight && !IsWallMid())
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                            else
                            {
                                if (x - xSend != 0)
                                {
                                    Service.instance.PlayerMove();
                                }
                                if (IsCanMove(1, speed))
                                {
                                    SetStatus(PlayerStatus.RUN);
                                    vx = speed;
                                }
                            }
                        }
                        break;
                    }
                case PlayerStatus.RUN:
                    {
                        if (isMoveUp)
                        {
                            if (x - xSend != 0 || y - ySend != 0)
                            {
                                Service.instance.PlayerMove();
                            }
                            SetStatus(PlayerStatus.JUMP);
                            vy = -speed;
                        }
                        else if (isMoveLeft)
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                            else
                            {
                                vx = -speed;
                            }
                        }
                        else if (isMoveRight)
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                            else
                            {
                                vx = speed;
                            }
                        }
                        break;
                    }
                case PlayerStatus.JUMP:
                    {
                        if (isMoveLeft)
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                            else
                            {
                                vx = -speed;
                            }
                        }
                        else if (isMoveRight)
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                            else
                            {
                                vx = speed;
                            }
                        }
                        if (isMoveUp)
                        {
                            bool flag = true;
                            for (int i = 0; i < h; i++)
                            {
                                if (Map.IsWall(x, y + i) || y + i > Map.height - Map.size)
                                {
                                    flag = false;
                                    break;
                                }
                            }
                            if (flag)
                            {
                                vy = -speed;
                            }
                        }
                        break;
                    }
                case PlayerStatus.FALL:
                    {
                        if (isMoveUp)
                        {
                            if ((x - xSend != 0 || y - ySend != 0) && (Math.Abs(x - xSend) > 96 || Math.Abs(y - me.ySend) > 24))
                            {
                                Service.instance.PlayerMove();
                            }
                            vy = -speed;
                            SetStatus(PlayerStatus.JUMP);
                        }
                        if (isMoveLeft)
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                            else
                            {
                                vx = -speed;
                                bool flag = true;
                                for (int i = 0; i < h; i++)
                                {
                                    if (Map.IsWall(x, y + i) || y + i > Map.height - Map.size)
                                    {
                                        flag = false;
                                        break;
                                    }
                                }
                                if (flag && x > 50 + Math.Abs(vx) && x < Map.width - 50 - Math.Abs(vx) && !Map.IsWall(x - w / 2 - 1, y - 1))
                                {
                                    SetStatus(PlayerStatus.FLY);
                                    vy = 0;
                                }
                            }
                        }
                        else if (isMoveRight)
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                            else
                            {
                                vx = speed;
                                bool flag = true;
                                for (int i = 0; i < h; i++)
                                {
                                    if (Map.IsWall(x, y + i) || y + i > Map.height - Map.size)
                                    {
                                        flag = false;
                                        break;
                                    }
                                }
                                if (flag && x > 50 + Math.Abs(vx) && x < Map.width - 50 - Math.Abs(vx) && !Map.IsWall(x + w / 2, y - 1))
                                {
                                    SetStatus(PlayerStatus.FLY);
                                    vy = 0;
                                }
                            }
                        }
                        break;
                    }
                case PlayerStatus.FLY:
                    {
                        if (isMoveUp)
                        {
                            if ((x - xSend != 0 || y - ySend != 0) && (Math.Abs(x - xSend) > 96 || Math.Abs(y - ySend) > 24))
                            {
                                Service.instance.PlayerMove();
                            }
                            vy = -speed;
                            SetStatus(PlayerStatus.JUMP);
                        }
                        else if (isMoveLeft)
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                            else
                            {
                                vx = -(speed + 1);
                            }
                        }
                        else if (isMoveRight)
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                            else
                            {
                                vx = speed + 1;
                            }
                        }
                        break;
                    }
                case PlayerStatus.DEAD:
                    {
                        if (isMoveLeft)
                        {
                            if (dir == 1)
                            {
                                dir = -1;
                            }
                        }
                        else if (isMoveRight)
                        {
                            if (dir == -1)
                            {
                                dir = 1;
                            }
                        }
                        break;
                    }

            }
        }

        public void StartDie(int x, int y)
        {
            if (Equals(me))
            {
                isLockMove = true;
            }
            xDie = x;
            yDie = y;
            hp = 0;
            SetStatus(PlayerStatus.START_DEAD);
        }

        public void LiveFromDead()
        {
            hp = maxHp;
            mp = maxMp;
            SetStatus(PlayerStatus.FALL);
        }

        public void DoInjure(long hp, bool isCritical)
        {
            this.hp -= hp;
            if (this.hp < 0)
            {
                this.hp = 0;
            }
            if (hp <= 0)
            {
                ScreenManager.instance.gameScreen.StartFlyText(MyFont.text_fly_white, PlayerText.miss, x, y - h, 0, -2);
            }
            else if (isCritical)
            {
                ScreenManager.instance.gameScreen.StartFlyText(MyFont.text_fly_yellow, "-" + Utils.GetMoneys(hp), x, y - h, 0, -2);
            }
            else
            {
                ScreenManager.instance.gameScreen.StartFlyText(MyFont.text_fly_red, "-" + Utils.GetMoneys(hp), x, y - h, 0, -2);
            }
            if (hp > 0)
            {
                timeInjure = 10;
            }
            if (isDie)
            {
                isDie = false;
                isLockKey = false;
            }
        }

        public override void PaintShadow(MyGraphics g)
        {
            if (isSpaceship)
            {
                return;
            }
            PaintEffectEquip(g);
            base.PaintShadow(g);
        }

        public override void UpdateShadow()
        {
            if (isSpaceship)
            {
                return;
            }
            base.UpdateShadow();
        }

        public void SetStatus(string status)
        {
            if (this.status != status)
            {
                this.status = status;
                frameTick = 0;
                frameIndex = 0;
            }
        }

        public void AddEffectMove(int type)
        {
            if (type == 0)
            {
                effects.Add(new EffectLoop(this, 6, 1, w / 2 - 5, 0, -1));
            }
            if (type == 1)
            {
                effects.Add(new EffectLoop(this, 7, 1, 0, 0, StaticObj.BOTTOM_HCENTER));
            }
        }

        public void MoveTo(int toX, int toY)
        {
            if (Math.Abs(toX - x) > 500 || Math.Abs(toY - y) > 500)
            {
                x = toX;
                y = toY;
                vx = vy = 0;
                currentMovePoint = null;
                if (IsWallBottom())
                {
                    SetStatus(PlayerStatus.STAND);
                }
                else
                {
                    SetStatus(PlayerStatus.FALL);
                }
                return;
            }
            if (currentMovePoint == null)
            {
                currentMovePoint = new MovePoint(toX, toY);
                return;
            }
            currentMovePoint.xEnd = toX;
            currentMovePoint.yEnd = toY;
        }

        public void FindNextFocusByKey()
        {
            if (skillPaint != null)
            {
                return;
            }
            ScreenManager.instance.gameScreen.cmdQues.isShow = false;
            focus.Clear();
            int minX = ScreenManager.instance.gameScreen.cmx + 10;
            int minY = ScreenManager.instance.gameScreen.cmy + 10;
            int maxX = ScreenManager.instance.gameScreen.cmx + GameCanvas.w - 10;
            int maxY = ScreenManager.instance.gameScreen.cmy + GameCanvas.h - 10;

            int index = 0;

            foreach (var player in ScreenManager.instance.gameScreen.players)
            {
                if (player.x >= minX && player.y >= minY && player.x <= maxX && player.y <= maxY)
                {
                    if ((isSelectEnemy && IsCanAttack(player)) || (isSelectPlayer && !isSelectEnemy))
                    {
                        focus.Add(player);
                        if (playerFocus != null && playerFocus.Equals(player))
                        {
                            index = focus.Count;
                        }
                    }
                }
            }

            if (isSelectItemMap)
            {
                foreach (var itemMap in ScreenManager.instance.gameScreen.itemMaps)
                {
                    if (itemMap.x >= minX && itemMap.y >= minY && itemMap.x <= maxX && itemMap.y <= maxY)
                    {
                        focus.Add(itemMap);
                        if (itemFocus != null && itemFocus.Equals(itemMap))
                        {
                            index = focus.Count;
                        }
                    }
                }
            }

            if (isSelectMonster)
            {
                foreach (var mob in ScreenManager.instance.gameScreen.monsters)
                {
                    if (mob.x >= minX && mob.y >= minY && mob.x <= maxX && mob.y <= maxY)
                    {
                        focus.Add(mob);
                        if (monsterFocus != null && monsterFocus.Equals(mob))
                        {
                            index = focus.Count;
                        }
                    }
                }
            }

            if (isSelectNpc)
            {
                foreach (var npc in ScreenManager.instance.gameScreen.npcs)
                {
                    if (npc.x >= minX && npc.y >= minY && npc.x <= maxX && npc.y <= maxY)
                    {
                        focus.Add(npc);
                        if (npcFocus != null && npcFocus.Equals(npc))
                        {
                            index = focus.Count;
                        }
                    }
                }
            }

            if (focus.Count > 0)
            {
                if (index >= focus.Count)
                {
                    index = 0;
                }
                FocusManualTo(focus[index]);
            }
            else
            {
                monsterFocus = null;
                npcFocus = null;
                playerFocus = null;
                itemFocus = null;
            }
        }

        public bool IsCanAttack(Player player)
        {
            if (Equals(player))
            {
                return false;
            }
            if (player == null || mySkill == null)
            {
                return false;
            }
            if (mySkill.template.id == 10)
            {
                return true;
            }
            if (player.IsDead())
            {
                return false;
            }
            if (typePk == 1 && player.typePk == 1 && player.id == killId)
            {
                return true;
            }
            if (player.typePk == 3)
            {
                return true;
            }

            if (Map.mapId != 0 && Map.mapId != 22)
            {
                if (typePk == 2)
                {
                    return true;
                }
                if (player.typePk == 2)
                {
                    return true;
                }
                if (typeFlag > 0 && player.typeFlag > 0 && (typeFlag != player.typeFlag || player.typeFlag == 1))
                {
                    return true;
                }
            }
            return false;
        }

        public void SearchFocus()
        {
            if (me.monsterFocus != null && monsterFocus.hp <= 0 && timeFocusToMob > 5)
            {
                timeFocusToMob = 5;
                return;
            }
            if (me.skillPaint != null || me.dart != null)
            {
                timeFocusToMob = 100;
                return;
            }
            if (timeFocusToMob > 0)
            {
                timeFocusToMob--;
                return;
            }
            if (GameCanvas.gameTick % 2 == 0 || IsCanAttack(playerFocus))
            {
                return;
            }
            GameScreen gameScreen = ScreenManager.instance.gameScreen;
            if ((gameScreen.isCanAutoPlay || gameScreen.isAutoFindMob) && gameScreen.isAutoPlay)
            {
                return;
            }
            int num = 60;
            int[] array = new int[4] { -1, -1, -1, -1 };
            int num2 = gameScreen.cmx - 10;
            int num3 = gameScreen.cmx + GameCanvas.w + 10;
            int cmy = gameScreen.cmy;
            int num4 = gameScreen.cmy + GameCanvas.h + 10;
            if (isManualFocus)
            {
                if ((monsterFocus != null && !monsterFocus.IsDead() && num2 <= monsterFocus.x && monsterFocus.x <= num3 && cmy <= monsterFocus.y && monsterFocus.y <= num4)
                    || (npcFocus != null && num2 <= npcFocus.x && npcFocus.x <= num3 && cmy <= npcFocus.y && npcFocus.y <= num4)
                    || (playerFocus != null && num2 <= playerFocus.x && playerFocus.x <= num3 && cmy <= playerFocus.y && playerFocus.y <= num4)
                    || (itemFocus != null && num2 <= itemFocus.x && itemFocus.x <= num3 && cmy <= itemFocus.y && itemFocus.y <= num4))
                {
                    return;
                }
                isManualFocus = false;
            }
            num2 = me.x - 160;
            num3 = me.x + 160;
            cmy = me.y - 60;
            num4 = me.y + 60;
            if (npcFocus == null)
            {
                if (isSelectNpc)
                {
                    for (int i = 0; i < gameScreen.npcs.Count; i++)
                    {
                        Npc npc = gameScreen.npcs[i];
                        int num5 = Math.Abs(me.x - npc.x);
                        int num6 = Math.Abs(me.y - npc.y);
                        int num7 = ((num5 <= num6) ? num6 : num5);
                        num2 = me.x - 160;
                        num3 = me.x + 160;
                        cmy = me.y - 60;
                        num4 = me.y + 60;
                        if (num2 <= npc.x && npc.x <= num3 && cmy <= npc.y && npc.y <= num4 && (npcFocus == null || num7 < array[1]))
                        {
                            npcFocus = npc;
                            array[1] = num7;
                        }
                    }
                }
            }
            else
            {
                if (num2 <= npcFocus.x && npcFocus.x <= num3 && cmy <= npcFocus.y && npcFocus.y <= num4)
                {
                    clearFocus(1);
                    return;
                }
                npcFocus = null;
                if (isSelectNpc)
                {
                    for (int j = 0; j < gameScreen.npcs.Count; j++)
                    {
                        Npc npc2 = gameScreen.npcs[j];
                        int num8 = Math.Abs(me.x - npc2.x);
                        int num9 = Math.Abs(me.y - npc2.y);
                        int num10 = ((num8 <= num9) ? num9 : num8);
                        num2 = me.x - 160;
                        num3 = me.x + 160;
                        cmy = me.y - 60;
                        num4 = me.y + 60;
                        if (num2 <= npc2.x && npc2.x <= num3 && cmy <= npc2.y && npc2.y <= num4 && (npcFocus == null || num10 < array[1]))
                        {
                            npcFocus = npc2;
                            array[1] = num10;
                        }
                    }
                }
            }
            if (itemFocus == null)
            {
                if (isSelectItemMap)
                {
                    for (int k = 0; k < gameScreen.itemMaps.Count; k++)
                    {
                        ItemMap itemMap = gameScreen.itemMaps[k];
                        int num11 = Math.Abs(me.x - itemMap.x);
                        int num12 = Math.Abs(me.y - itemMap.y);
                        int num13 = ((num11 <= num12) ? num12 : num11);
                        if (num11 > 48 || num12 > 48 || (itemFocus != null && num13 >= array[3]))
                        {
                            continue;
                        }
                        itemFocus = itemMap;
                        array[3] = num13;
                    }
                }
            }
            else
            {
                if (num2 <= itemFocus.x && itemFocus.x <= num3 && cmy <= itemFocus.y && itemFocus.y <= num4)
                {
                    clearFocus(3);
                    return;
                }
                itemFocus = null;
                if (isSelectItemMap)
                {
                    for (int l = 0; l < gameScreen.itemMaps.Count; l++)
                    {
                        ItemMap itemMap2 = gameScreen.itemMaps[l];
                        int num14 = Math.Abs(me.x - itemMap2.x);
                        int num15 = Math.Abs(me.y - itemMap2.y);
                        int num16 = ((num14 <= num15) ? num15 : num14);
                        if (num2 > itemMap2.x || itemMap2.x > num3 || cmy > itemMap2.y || itemMap2.y > num4 || (itemFocus != null && num16 >= array[3]))
                        {
                            continue;
                        }
                        itemFocus = itemMap2;
                        array[3] = num16;
                    }
                }
            }
            num2 = me.x - me.mySkill.GetDx() - 90;
            num3 = me.x + me.mySkill.GetDx() + 90;
            cmy = me.y - me.mySkill.GetDy() - num - 120;
            num4 = me.y + me.mySkill.GetDy() + 120;
            if (num4 > me.y + 180)
            {
                num4 = me.y + 180;
            }
            if (monsterFocus == null)
            {
                if (isSelectMonster)
                {
                    for (int m = 0; m < gameScreen.monsters.Count; m++)
                    {
                        Monster mob = gameScreen.monsters[m];
                        if (mob is MonsterPet)
                        {
                            continue;
                        }
                        int num17 = Math.Abs(me.x - mob.x);
                        int num18 = Math.Abs(me.y - mob.y);
                        int num19 = ((num17 <= num18) ? num18 : num17);
                        if (num2 <= mob.x && mob.x <= num3 && cmy <= mob.y && mob.y <= num4 && (monsterFocus == null || num19 < array[0]))
                        {
                            monsterFocus = mob;
                            array[0] = num19;
                        }
                    }
                }
            }
            else
            {
                if (!monsterFocus.IsDead() && num2 <= monsterFocus.x && monsterFocus.x <= num3 && cmy <= monsterFocus.y && monsterFocus.y <= num4)
                {
                    clearFocus(0);
                    return;
                }
                monsterFocus = null;
                if (isSelectMonster)
                {
                    for (int n = 0; n < gameScreen.monsters.Count; n++)
                    {
                        Monster mob2 = gameScreen.monsters[n];
                        if (mob2 is MonsterPet)
                        {
                            continue;
                        }
                        int num20 = Math.Abs(me.x - mob2.x);
                        int num21 = Math.Abs(me.y - mob2.y);
                        int num22 = ((num20 <= num21) ? num21 : num20);
                        if (num2 <= mob2.x && mob2.x <= num3 && cmy <= mob2.y && mob2.y <= num4 && (monsterFocus == null || num22 < array[0]))
                        {
                            monsterFocus = mob2;
                            array[0] = num22;
                        }
                    }
                }
            }
            if (!gameScreen.isCanAutoPlay || !gameScreen.isAutoPlay)
            {
                if (playerFocus == null)
                {
                    for (int num23 = 0; num23 < gameScreen.players.Count; num23++)
                    {
                        Player player = gameScreen.players[num23];
                        if (!player.isDisciple)
                        {
                            int num24 = Math.Abs(me.x - player.x);
                            int num25 = Math.Abs(me.y - player.y);
                            int num26 = ((num24 <= num25) ? num25 : num24);
                            if (num2 <= player.x && player.x <= num3 && cmy <= player.y && player.y <= num4 && (playerFocus == null || num26 < array[2]))
                            {
                                if ((isSelectEnemy && IsCanAttack(player)) || (isSelectPlayer && !isSelectEnemy))
                                {
                                    playerFocus = player;
                                    array[2] = num26;
                                }
                            }
                        }
                    }
                }
                else
                {
                    if (num2 <= playerFocus.x && playerFocus.x <= num3 && cmy <= playerFocus.y && playerFocus.y <= num4)
                    {
                        clearFocus(2);
                        return;
                    }
                    playerFocus = null;
                    for (int num27 = 0; num27 < gameScreen.players.Count; num27++)
                    {
                        Player char2 = gameScreen.players[num27];
                        int num28 = Math.Abs(me.x - char2.x);
                        int num29 = Math.Abs(me.y - char2.y);
                        int num30 = ((num28 <= num29) ? num29 : num28);
                        if (num2 <= char2.x && char2.x <= num3 && cmy <= char2.y && char2.y <= num4 && (playerFocus == null || num30 < array[2]))
                        {
                            if ((isSelectEnemy && IsCanAttack(char2)) || (isSelectPlayer && !isSelectEnemy))
                            {
                                playerFocus = char2;
                                array[2] = num30;
                            }
                        }
                    }
                }
            }

            int num31 = -1;
            for (int num32 = 0; num32 < array.Length; num32++)
            {
                if (num31 == -1)
                {
                    if (array[num32] != -1)
                    {
                        num31 = num32;
                    }
                }
                else if (array[num32] < array[num31] && array[num32] != -1)
                {
                    num31 = num32;
                }
            }
            clearFocus(num31);
            if (this.Equals(me) && IsCanAttack(Player.me.playerFocus))
            {
                npcFocus = null;
                itemFocus = null;
            }
        }

        public void clearFocus(int index)
        {
            switch (index)
            {
                case 0:
                    npcFocus = null;
                    playerFocus = null;
                    itemFocus = null;
                    break;
                case 1:
                    monsterFocus = null;
                    playerFocus = null;
                    itemFocus = null;
                    break;
                case 2:
                    monsterFocus = null;
                    npcFocus = null;
                    itemFocus = null;
                    break;
                case 3:
                    monsterFocus = null;
                    npcFocus = null;
                    playerFocus = null;
                    break;
            }
        }

        public bool IsCanUseSkill()
        {
            return mp >= GetManaUseSkill();
        }

        public int GetManaUseSkill()
        {
            try
            {
                if (mySkill.template.typeMana != 1)
                {
                    return mySkill.GetManaUse();
                }
                return (int)(mySkill.GetManaUse() * maxMp / 100);
            }
            catch
            {
            }
            return 0;
        }

        public bool IsBagFull()
        {
            return itemsBag != null && itemsBag.All(item => item != null);
        }

        public void FocusManualTo(Entity objectz)
        {
            if (objectz == null)
            {
                return;
            }
            if (objectz is Monster)
            {
                monsterFocus = (Monster)objectz;
                npcFocus = null;
                playerFocus = null;
                itemFocus = null;
            }
            else if (objectz is Npc)
            {
                monsterFocus = null;
                npcFocus = (Npc)objectz;
                playerFocus = null;
                itemFocus = null;
            }
            else if (objectz is Player)
            {
                monsterFocus = null;
                npcFocus = null;
                playerFocus = (Player)objectz;
                itemFocus = null;
            }
            else if (objectz is ItemMap)
            {
                monsterFocus = null;
                npcFocus = null;
                playerFocus = null;
                itemFocus = (ItemMap)objectz;
            }
            isManualFocus = true;
        }

        public Skill GetSkillByTemplateId(int id)
        {
            foreach (Skill skill in skills)
            {
                if (skill.template.id == id)
                {
                    return skill;
                }
            }
            return null;
        }

        public bool IsSkillUseAlone()
        {
            if (mySkill != null)
            {
                return mySkill.template.type == 1;
            }
            return false;
        }

        public bool IsCanUseMySkill()
        {
            if (mySkill == null)
            {
                return false;
            }
            if (mySkill.level == 0)
            {
                return false;
            }
            if (mySkill.template.typeMana == 0 && mp < mySkill.GetManaUse())
            {
                InfoMe.addInfo(PlayerText.NOT_ENOUGH_MP, 0);
                return false;
            }
            if (mySkill.template.typeMana == 1 && mp * 100 / maxMp < mySkill.GetManaUse())
            {
                InfoMe.addInfo(PlayerText.NOT_ENOUGH_MP, 0);
                return false;
            }
            return true;
        }

        public void AddChatInfo(string info)
        {
            chatInfo.AddChatInfo(info);
        }

        public string GetStrPercentLevel()
        {
            if (level >= GameCanvas.levels.Count - 1)
            {
                return "0.00%";
            }
            long min = GameCanvas.levels[level].power;
            long max = GameCanvas.levels[level + 1].power;
            return string.Format("{0:0.##}", (float)(power - min) * 100f / (max - min));
        }

        public static int GetLevel(long power)
        {
            for (int i = 0; i < GameCanvas.levels.Count; i++)
            {
                if (power >= GameCanvas.levels[i].power)
                {
                    if (i < GameCanvas.levels.Count - 1 && power < GameCanvas.levels[i + 1].power)
                    {
                        return i;
                    }
                    else if (i == GameCanvas.levels.Count - 1)
                    {
                        return i;
                    }
                }
            }
            return 0;
        }

        public void FusionComplete()
        {
            isFusion = false;
            isLockKey = false;
            tFusion = 0;
        }

        public void SetFusion(int fusion)
        {
            tFusion = 0;
            if (Equals(me))
            {
                ScreenManager.instance.panel.Close();
                isLockKey = true;
            }
            isFusion = true;
        }

        public bool IsWallMid()
        {
            try
            {
                int y = this.y;
                if (IsWallBottom())
                {
                    y--;
                }
                // Line map: only check center point (fast + avoids edge-sticking)
                if (Map.template != null && Map.template.isLine)
                {
                    return Map.IsWall(x, y - h / 2);
                }
                int start = x - w / 2 + 1;
                int end = x + w / 2 - 1;
                for (int i = y - h + 1; i <= y; i++)
                {
                    for (int j = start; j <= end; j++)
                    {
                        if (Map.IsWall(j, i))
                        {
                            return true;
                        }
                    }
                }
            }
            catch
            {
            }
            return false;
        }

        public bool IsWallMid(int vx)
        {
            try
            {
                int y = this.y;
                if (IsWallBottom())
                {
                    y--;
                }
                // Line map: only check center point after move
                if (Map.template != null && Map.template.isLine)
                {
                    return Map.IsWall(x + vx, y - h / 2);
                }
                int start = x + vx - w / 2 + 1;
                int end = x + vx + w / 2 + 1;
                for (int i = y - h + 1; i <= y; i++)
                {
                    for (int j = start; j <= end; j++)
                    {
                        if (Map.IsWall(j, i))
                        {
                            return true;
                        }
                    }
                }
            }
            catch
            {
            }
            return false;
        }

        public bool IsWallBottom()
        {
            try
            {
                // Line map: only check center x position
                if (Map.template != null && Map.template.isLine)
                {
                    return Map.IsWall(x, y);
                }
                int start = x - w / 2 + 1;
                int end = x + w / 2 - 1;
                for (int i = start; i <= end; i++)
                {
                    if (Map.IsWall(i, y))
                    {
                        return true;
                    }
                }
                return false;
            }
            catch
            {
            }
            return false;
        }

        public bool IsWallBottom(int bonus)
        {
            try
            {
                // Line map: only check center x position
                if (Map.template != null && Map.template.isLine)
                {
                    return Map.IsWall(x, y + bonus);
                }
                int start = x - w / 2 + 1;
                int end = x + w / 2 - 1;
                for (int i = start; i <= end; i++)
                {
                    if (Map.IsWall(i, y + bonus))
                    {
                        return true;
                    }
                }
            }
            catch
            {
            }
            return false;
        }


    }
}
