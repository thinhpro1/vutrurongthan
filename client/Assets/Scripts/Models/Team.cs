using System.Collections.Generic;
using Assets.Scripts.Commands;

namespace Assets.Scripts.Models
{
    public class Team
    {
        public int id;

        public int status;

        public List<CmdTeamMember> members = new List<CmdTeamMember>();

        public string GetStrStatus()
        {
            if (status == 0)
            {
                return "Không khóa";
            }
            if (status == 1)
            {
                return "Đã khóa";
            }
            return "Tự động duyệt";
        }
    }
}
