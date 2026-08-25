using System;
using System.Collections.Generic;
using System.Linq;

namespace Assets.Scripts.Commons
{
    public class RandomCollection<T>
    {
        private SortedDictionary<double, T> map = new SortedDictionary<double, T>();
        private Random random = new Random();
        private double total = 0;

        public RandomCollection()
        {
        }

        public void Add(double weight, T result)
        {
            if (weight <= 0)
            {
                return;
            }
            total += weight;
            map[total] = result;
        }

        public void SetDefault(T result)
        {
            double weight = 100 - total;
            if (weight <= 0)
            {
                return;
            }
            total += weight;
            map[total] = result;
        }

        public bool IsEmpty()
        {
            return map.Count == 0;
        }

        public T Next()
        {
            double value = random.NextDouble() * total;
            var higherEntry = map.FirstOrDefault(kv => kv.Key > value);
            return higherEntry.Value;
        }
    }
}
