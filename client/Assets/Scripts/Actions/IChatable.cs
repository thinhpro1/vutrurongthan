using Assets.Scripts.InputCustoms;
using System.Collections.Generic;

namespace Assets.Scripts.Actions
{
    public interface IChatable
    {
        void OnChatFromMe(string text);

        void OnChatFromMe(string name, List<TextField> textFields);
    }
}
