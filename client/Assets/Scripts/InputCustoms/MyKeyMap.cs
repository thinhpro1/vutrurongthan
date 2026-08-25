using System.Collections.Generic;
using UnityEngine;

namespace Assets.Scripts.InputCustoms
{
    public class MyKeyMap
    {
        public static List<KeyCode> KeyInputs;

        public static List<KeyCode> KeyActions;

        static MyKeyMap()
        {
            KeyInputs = new List<KeyCode>();
            KeyInputs.Add(KeyCode.A);
            KeyInputs.Add(KeyCode.B);
            KeyInputs.Add(KeyCode.C);
            KeyInputs.Add(KeyCode.D);
            KeyInputs.Add(KeyCode.E);
            KeyInputs.Add(KeyCode.F);
            KeyInputs.Add(KeyCode.G);
            KeyInputs.Add(KeyCode.H);
            KeyInputs.Add(KeyCode.I);
            KeyInputs.Add(KeyCode.J);
            KeyInputs.Add(KeyCode.K);
            KeyInputs.Add(KeyCode.L);
            KeyInputs.Add(KeyCode.M);
            KeyInputs.Add(KeyCode.N);
            KeyInputs.Add(KeyCode.O);
            KeyInputs.Add(KeyCode.P);
            KeyInputs.Add(KeyCode.Q);
            KeyInputs.Add(KeyCode.R);
            KeyInputs.Add(KeyCode.S);
            KeyInputs.Add(KeyCode.T);
            KeyInputs.Add(KeyCode.U);
            KeyInputs.Add(KeyCode.V);
            KeyInputs.Add(KeyCode.W);
            KeyInputs.Add(KeyCode.X);
            KeyInputs.Add(KeyCode.Y);
            KeyInputs.Add(KeyCode.Z);
            KeyInputs.Add(KeyCode.Space);
            KeyInputs.Add(KeyCode.BackQuote);
            KeyInputs.Add(KeyCode.Alpha0);
            KeyInputs.Add(KeyCode.Alpha1);
            KeyInputs.Add(KeyCode.Alpha2);
            KeyInputs.Add(KeyCode.Alpha3);
            KeyInputs.Add(KeyCode.Alpha4);
            KeyInputs.Add(KeyCode.Alpha5);
            KeyInputs.Add(KeyCode.Alpha6);
            KeyInputs.Add(KeyCode.Alpha7);
            KeyInputs.Add(KeyCode.Alpha8);
            KeyInputs.Add(KeyCode.Alpha9);
            KeyInputs.Add(KeyCode.Minus);
            KeyInputs.Add(KeyCode.Plus);
            KeyInputs.Add(KeyCode.Tilde);
            KeyInputs.Add(KeyCode.Exclaim);
            KeyInputs.Add(KeyCode.At);
            KeyInputs.Add(KeyCode.Hash);
            KeyInputs.Add(KeyCode.Dollar);
            KeyInputs.Add(KeyCode.Percent);
            KeyInputs.Add(KeyCode.Caret);
            KeyInputs.Add(KeyCode.Ampersand);
            KeyInputs.Add(KeyCode.Asterisk);
            KeyInputs.Add(KeyCode.LeftParen);
            KeyInputs.Add(KeyCode.RightParen);
            KeyInputs.Add(KeyCode.Underscore);
            KeyInputs.Add(KeyCode.LeftBracket);
            KeyInputs.Add(KeyCode.RightBracket);
            KeyInputs.Add(KeyCode.Backslash);
            KeyInputs.Add(KeyCode.Semicolon);
            KeyInputs.Add(KeyCode.DoubleQuote);
            KeyInputs.Add(KeyCode.Period);
            KeyInputs.Add(KeyCode.Comma);
            KeyInputs.Add(KeyCode.Slash);
            KeyInputs.Add(KeyCode.LeftCurlyBracket);
            KeyInputs.Add(KeyCode.RightCurlyBracket);
            KeyInputs.Add(KeyCode.Pipe);
            KeyInputs.Add(KeyCode.Less);
            KeyInputs.Add(KeyCode.Greater);
            KeyInputs.Add(KeyCode.Question);
            KeyInputs.Add(KeyCode.Colon);
            KeyInputs.Add(KeyCode.KeypadEquals);
            KeyInputs.Add(KeyCode.Equals);
            KeyInputs.Add(KeyCode.Quote);

            KeyActions = new List<KeyCode>();
            KeyActions.Add(KeyCode.F1);
            KeyActions.Add(KeyCode.F2);
            KeyActions.Add(KeyCode.Tab);
            KeyActions.Add(KeyCode.UpArrow);
            KeyActions.Add(KeyCode.DownArrow);
            KeyActions.Add(KeyCode.LeftArrow);
            KeyActions.Add(KeyCode.RightArrow);
            KeyActions.Add(KeyCode.Return);
            KeyActions.Add(KeyCode.Backspace);
            KeyActions.Add(KeyCode.LeftShift);
            KeyActions.Add(KeyCode.RightShift);
            KeyActions.Add(KeyCode.KeypadEnter);
        }
    }
}
