using Assets.Scripts.Commons;
using Assets.Scripts.Controllers;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Entites;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Skills;
using System;
using System.Collections.Generic;
using System.Reflection;
using UnityEngine;

namespace Assets.Scripts.Services
{
    public class Service
    {
        public static Service instance = new Service();

        public const int ACTION_USE_ITEM = 0;

        public const int ACTION_UNDRESS_ITEM = 1;

        public const int ACTION_BUY_ITEM = 2;

        public const int ACTION_THROW_ITEM = 3;

        public static int ACTION_PUT_IN_ITEM = 4;

        public static int ACTION_TYPE_PUT_OUT_ITEM = 5;

        public static int ACTION_TYPE_SELL_ITEM = 6;

        public static int ACTION_TYPE_DISCIPLE_UNDRESS = 7;

        public static int ACTION_TYPE_DISCIPLE_WEAR = 8;

        public static int ACTION_TYPE_PET_WEAR = 9;

        public static int ACTION_TYPE_PET_UNDRESS = 10;

        public const int ACTION_UNDRESS_ITEM_OTHER = 11;

        public static int ACTION_TYPE_DISCIPLE_UNDRESS_OTHER = 12;

        public static int TYPE_UPGRADE_ITEM = 4;

        public static int TYPE_UPGRADE_STONE = 5;

        public static int TYPE_TRADE = 6;

        public static int TYPE_TRADE_FRIEND = 7;

        public static int TYPE_BOX = 8;

        public static int FRIEND_ACTION_SHOW = 0;

        public static int FRIEND_ACTION_ADD = 1;

        public static int FRIEND_ACTION_DEL = 2;

        public static int ENEMY_ACTION_SHOW = 0;

        public static int ENEMY_ACTION_DEL = 1;

        public static long lastTimeChangeZone;

        public void UpdateData(int index)
        {
            try
            {
                Message message = new Message(MessageName.UPDATE_DATA);
                message.writer().writeSByte((sbyte)index);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public void RequestIcon(int iconId)
        {
            try
            {
                Message message = new Message(-22);
                message.writer().WriteShort(iconId);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void Login(string username, string password)
        {
            try
            {
                Player.me = new Player();
                Message message = new Message(MessageName.LOGIN);
                message.writer().WriteUTF(ServerManager.VERSION);
                message.writer().WriteUTF(username);
                message.writer().WriteUTF(password);
                message.writer().writeSByte((sbyte)ServerManager.LOGIN_VERSION);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void CreatePlayer(string name, int gender)
        {
            try
            {
                Message message = new Message(MessageName.CREATE_PALYER);
                message.writer().WriteUTF(name);
                message.writer().writeByte(gender);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void Register(string username, string password)
        {
            try
            {
                Message message = new Message(-118); 
                message.writer().WriteUTF(username);
                message.writer().WriteUTF(password);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void FinishLoadMap()
        {
            try
            {
                Message message = new Message(-115);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void PlayerMove()
        {
            if (Player.isChangingMap || (Player.me.x == Player.me.xSend && Player.me.y == Player.me.ySend)
                || Controller.isStopReadMessage || Player.me.isSpaceship || Player.me.y <= 0)
            {
                return;
            }
            try
            {
                Message message = new Message((sbyte)(-114));
                Player.me.xSend = Player.me.x;
                Player.me.ySend = Player.me.y;
                message.writer().WriteShort(Player.me.x);
                message.writer().WriteShort(Player.me.y);
                SendMessage(message);
                message.cleanup();
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void RequestChangeMap()
        {
            try
            {
                Message message = new Message(-113);
                SendMessage(message);
                message.cleanup();
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void chat(string text)
        {
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-112);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void chatPlayer(int id, string text)
        {
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-110);
                message.writer().WriteInt(id);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void ChatGlobal(string text)
        {
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-111);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void ChatClan(string text)
        {
            if (Player.me.clan == null)
            {
                return;
            }
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-63);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void ChatTeam(string text)
        {
            if (Player.me.team == null)
            {
                return;
            }
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-62);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void chatPlayer(string text, int playerId)
        {
            if (text == null || text.Length == 0)
            {
                return;
            }
            if (text.Length > 200)
            {
                text = text.Substring(0, 200);
            }
            try
            {
                Message message = new Message(-61);
                message.writer().WriteInt(playerId);
                message.writer().WriteUTF(text);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void Attack(Entity focus)
        {
            try
            {
                Message message = new Message(-108);
                if (focus == null)
                {
                    message.writer().writeByte(-1);
                }
                else if (focus is Player)
                {
                    message.writer().writeByte(0);
                    message.writer().WriteInt(focus.id);
                }
                else
                {
                    message.writer().writeByte(1);
                    message.writer().WriteInt(focus.id);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void UseSkill(Skill skill, Entity focus)
        {
            try
            {
                Message message = new Message(-72);
                message.writer().writeSByte((sbyte)skill.template.id);
                if (focus != null && Player.me.mySkill.template.type != 1)
                {
                    if (focus is Monster)
                    {
                        message.writer().writeByte(1);
                    }
                    else if (focus is Player)
                    {
                        message.writer().writeByte(0);
                    }
                    message.writer().WriteInt(focus.id);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
            }
        }

        public void SelectSkill(int templateId)
        {
            /*try
            {
                Message message = new Message(-103);
                message.writer().writeSByte((sbyte)templateId);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }*/
        }

        public static void returnFromDie()
        {
            try
            {
                Message message = new Message(-97);
                SendMessage(message);
                message.cleanup();
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void liveFormDie()
        {
            try
            {
                Message message = new Message(-96);
                SendMessage(message);
                message.cleanup();
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void OpenMenu(int templateId)
        {
            try
            {
                Message message = new Message(-93);
                message.writer().writeSByte((sbyte)templateId);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void ConfirmMenu(int templateId, int select)
        {
            try
            {
                Message message = new Message(-92);
                message.writer().writeSByte((sbyte)templateId);
                message.writer().writeSByte((sbyte)select);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void UpPotential(int index, int point)
        {
            if (Player.me.task == null)
            {
                return;
            }
            if (Player.me.task.id < 1 || (Player.me.task.id == 1 && Player.me.task.index < 2))
            {
                InfoMe.addInfo("Chưa đủ cấp độ để nâng cấp!", 0);
                return;
            }
            try
            {
                Message message = new Message(-90);
                message.writer().writeSByte((sbyte)index);
                message.writer().WriteShort(point);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void UpPotentialDisciple(int index, int point)
        {
            try
            {
                Message message = new Message(-26);
                message.writer().WriteSByte(3);
                message.writer().writeSByte((sbyte)index);
                message.writer().WriteShort(point);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void DiscipleStatus()
        {
            try
            {
                Message message = new Message(-26);
                message.writer().WriteSByte(7);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void upSkill(int templateId, int point)
        {
            if (Player.me.task == null)
            {
                Debug.LogError("Invoke");
                return;
            }
            if (Player.me.task.id < 1 || (Player.me.task.id == 1 && Player.me.task.index < 1))
            {
                InfoMe.addInfo("Chưa đủ cấp độ để nâng cấp!", 0);
                return;
            }
            try
            {
                Message message = new Message(-89);
                message.writer().writeSByte((sbyte)templateId);
                // message.writer().WriteSByte((sbyte)point);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void ItemAction(int type, int index)//sử dụng item
        {
            try
            {
                Message message = new Message(-88);
                message.writer().writeSByte((sbyte)type);
                message.writer().writeSByte((sbyte)index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void Reward(int index)
        {
            try
            {
                Message message = new Message(-24);
                message.writer().writeSByte((sbyte)index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void SaveKeySkill(int tempId, int index)
        {
            try
            {
                Message message = new Message(-86);
                message.writer().writeSByte((sbyte)tempId);
                message.writer().writeSByte((sbyte)index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void RequestChangeArea(int areaId)
        {
            if (Map.areaId == areaId)
            {
                InfoMe.addInfo("Bạn đang ở khu vực này", 0);
                return;
            }
            if (Utils.CurrentTimeMillis() - lastTimeChangeZone < 500)
            {
                return;
            }
            InfoDlg.ShowWait();
            lastTimeChangeZone = Utils.CurrentTimeMillis();
            try
            {
                Message message = new Message(-84);
                message.writer().writeSByte((sbyte)areaId);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void RequestMapSpaceship(int select)
        {
            if (Player.me.hp <= 0 || Player.me.isDie)
            {
                InfoMe.addInfo("Không thể thực hiện khi đang kiệt sức", 0);
                return;
            }
            try
            {
                Message message = new Message(-59);
                message.writer().writeSByte((sbyte)select);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void PickItem(int itemMapId)
        {
            try
            {
                Message message = new Message(-79);
                message.writer().WriteInt(itemMapId);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void BuyItem(int tabIndex, int id, int quantity)
        {
            try
            {
                Message message = new Message(-78);
                message.writer().writeSByte((sbyte)tabIndex);
                message.writer().WriteInt(id);
                message.writer().WriteInt(quantity);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void sellItem(int index)
        {
            try
            {
                Message message = new Message(-76);
                message.writer().writeByte(index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void FriendActions(int type, int playerId, string name)
        {
            try
            {
                Message message = new Message(-75);
                message.writer().writeByte(type);
                if (type == 3)
                {
                    message.writer().WriteUTF(name);
                }
                else if (type != 0)
                {
                    message.writer().WriteInt(playerId);
                }
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void EnemyActions(int type, int playerId)
        {
            try
            {
                Message message = new Message(-74);
                message.writer().writeByte(type);
                if (type != 0)
                {
                    message.writer().WriteInt(playerId);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void TeamAction(int type, int playerId)
        {
            try
            {
                Message message = new Message(-71);
                message.writer().writeByte(type);
                if (type == 0 || type == 4 || type == 5 || type == 7)
                {
                    message.writer().WriteInt(playerId);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void soloActions(int playerId)
        {
            try
            {
                Message message = new Message(-66);
                message.writer().WriteInt(playerId);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void changePk(int type, int index)
        {
            try
            {
                Message message = new Message(-64);
                message.writer().writeByte(type);
                message.writer().writeByte(index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void Upgrade(Item item, List<int> items)
        {
            if (item == null)
            {
                return;
            }
            if (items == null || items.Count == 0)
            {
                return;
            }
            /*try
            {
                Message message = new Message(-58);
                message.writer().writeByte(item.indexUI);
                message.writer().writeByte(items.Count);
                foreach (int i in items)
                {
                    message.writer().writeByte(i);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }*/
            try
            {
                Message message = new Message(-37);
                message.writer().writeByte(items.Count + 1);
                message.writer().writeByte(item.indexUI);
                foreach (int i in items)
                {
                    message.writer().writeByte(i);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void Upgrade(List<int> items)
        {
            if (items == null || items.Count == 0)
            {
                return;
            }
            try
            {
                Message message = new Message(-37);
                message.writer().writeByte(items.Count);
                foreach (int i in items)
                {
                    message.writer().writeByte(i);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void StoneUpgrade(List<int> items)
        {
            Upgrade(items);
            /*if (items == null || items.Count == 0)
            {
                return;
            }
            try
            {
                Message message = new Message(-56);
                message.writer().writeByte(items[0]);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }*/
        }

        public static void RequestInfoPlayer(int id)
        {
            try
            {
                Message message = new Message(-50);
                message.writer().WriteInt(id);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void TradeAction(int type, int playerId, int index, int quantity)
        {
            try
            {
                Message message = new Message(-55);
                message.writer().writeByte(type);
                if (type == 0)
                {
                    message.writer().WriteInt(playerId);
                }
                else if (type == 3)
                {
                    message.writer().writeByte(index);
                    message.writer().WriteInt(quantity);
                }
                else if (type == 4)
                {
                    message.writer().writeByte(index);
                }
                else if (type == 5)
                {
                    if (index > Player.me.coin)
                    {
                        InfoMe.addInfo("Bạn không đủ xu để giao dịch.", 0);
                        return;
                    }
                    message.writer().WriteLong(quantity);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void RequestAchievement(int index)
        {
            try
            {
                Message message = new Message(-49);
                message.writer().writeByte(index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void ClientInput(List<TextField> textFields)
        {
            try
            {
                Message message = new Message(-46);
                foreach (var item in textFields)
                {
                    message.writer().WriteUTF(item.GetText());
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void LockAction()
        {
            try
            {
                Message message = new Message(-42);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public void SetShowMark()
        {
            try
            {
                Message message = new Message(-21);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public void SaveArea()
        {
            try
            {
                Message message = new Message(-20);
                SendMessage(message);
            }
            catch
            {
            }
        }

        public static void ClanAction(int type, int money_or_playerId, string content)
        {
            try
            {
                Message message = new Message(-44);
                if (type == -1 || type == 8)
                {
                    message.writer().writeByte(type);
                }
                else if (type == 0 || type == 1 || type == 2 || type == 5 || type == 6 || type == 7 || type == 9)
                {
                    message.writer().writeByte(type);
                    message.writer().WriteInt(money_or_playerId);
                }
                else if (type == 3 || type == 4)
                {
                    message.writer().writeByte(type);
                    if (type == 3 && content.Length > 50)
                    {
                        content = content.Substring(0, 50);
                    }
                    if (type == 4 && content.Length > 200)
                    {
                        content = content.Substring(0, 200);
                    }
                    message.writer().WriteUTF(content);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void ConsignmentAction(int type, int index, int id, int price, int quantity)
        {
            try
            {
                Message message = new Message(-41);
                message.writer().writeByte(type);
                if (type == 0)
                {
                    message.writer().writeByte(index);
                    message.writer().WriteInt(quantity);
                    message.writer().WriteInt(price);
                }
                else if (type == 1 || type == 2)
                {
                    message.writer().WriteInt(id);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void MiniGame(int action, int playerId)
        {
            try
            {
                Message message = new Message(-29);
                message.writer().writeByte(action);
                if (playerId != -1)
                {
                    message.writer().WriteInt(playerId);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void MissionAction(int action, int index)
        {
            try
            {
                Message message = new Message(-23);
                message.writer().writeByte(action);
                if (action != -1)
                {
                    message.writer().writeByte(index);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void TeleportToPlayer(int playerId)
        {
            try
            {
                Message message = new Message(-19);
                message.writer().WriteInt(playerId);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void Intrinsic(int id)
        {
            try
            {
                Message message = new Message(-13);
                message.writer().writeByte(id);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void LuckyPickMe(int action, int coin)
        {
            try
            {
                Message message = new Message(-12);
                message.writer().writeByte(action);
                if (action != 0)
                {
                    message.writer().WriteInt(coin);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void SetTab(bool isOpen, int tabID)
        {
            try
            {
                Message message = new Message(-11);
                message.writer().writeBoolean(isOpen);
                if (isOpen)
                {
                    message.writer().writeByte(tabID);
                }
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void Lucky(int index)
        {
            try
            {
                Message message = new Message(-10);
                message.writer().writeByte(index);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void ViewTop()
        {
            try
            {
                Message message = new Message(-9);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public void ViewLucky()
        {
            try
            {
                Message message = new Message(-8);
                SendMessage(message);
            }
            catch (Exception ex)
            {
                Debug.Log(ex.ToString());
            }
        }

        public static void SendMessage(Message message)
        {
            ServerManager.instance.session.SendMessage(message);
        }

        public void SendMessTaskTele(int mapId, string xy)
        {
            Message message = new Message(-7);
            message.writer().WriteInt(mapId);
            message.writer().WriteUTF(xy);
            SendMessage(message);
        }
    }
}
