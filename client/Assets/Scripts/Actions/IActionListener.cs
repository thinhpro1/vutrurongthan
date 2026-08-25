namespace Assets.Scripts.Actions
{
    public interface IActionListener
    {
        void Perform(int actionId, object p);
    }
}
