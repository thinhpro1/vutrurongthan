using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using System.Collections.Generic;

namespace Assets.Scripts.Models
{
    public class Clan
    {
        public int id;

        public string name;

        public int roleId;

        public string slogan;

        public string notification;

        public string createTime;

        public long exp;

        public int level;

        public List<string> histories = new List<string>();

        public List<CmdClanMember> members = new List<CmdClanMember>();

        public int maxMember;

        public long maxExp;

        public long coin;

        public string GetName()
        {
            return this.name;
        }

        public string GetSlogan()
        {
            return this.slogan;
        }

        public string GetNotification()
        {
            return this.notification;
        }

        public string GetCreateTime()
        {
            return this.createTime;
        }

        public long GetExp()
        {
            return this.exp;
        }

        public List<CmdClanMember> GetMembers()
        {
            return this.members;
        }

        public List<string> GetHistories()
        {
            return this.histories;
        }

        public int GetLevel()
        {
            return this.level;
        }

        public void SetId(int id)
        {
            this.id = id;
        }

        public void SetName(string name)
        {
            this.name = name;
        }

        public void SetRoleId(int roleId)
        {
            this.roleId = roleId;
        }

        public void SetSlogan(string slogan)
        {
            this.slogan = slogan;
        }

        public void SetNotification(string notification)
        {
            this.notification = notification;
        }

        public void SetCreateTime(string createTime)
        {
            this.createTime = createTime;
        }

        public void SetExp(long exp)
        {
            this.exp = exp;
        }

        public void SetMembers(List<CmdClanMember> clanMembers)
        {
            this.members = clanMembers;
        }

        public void SetHistories(List<string> histories)
        {
            this.histories = histories;
        }

        public void SetLevel(int level)
        {
            this.level = level;
        }

        public int GetMaxPlayer()
        {
            return this.maxMember;
        }

        public void SetMaxMember(int maxPlayer)
        {
            this.maxMember = maxPlayer;
        }

        public long GetMaxExp()
        {
            return this.maxExp;
        }

        public void SetMaxExp(long maxExp)
        {
            this.maxExp = maxExp;
        }
    }
}
