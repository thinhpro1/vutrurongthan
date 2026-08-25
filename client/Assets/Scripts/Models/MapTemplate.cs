using Assets.Scripts.GraphicCustoms;
using System;
using System.Collections.Generic;

namespace Assets.Scripts.Models
{
    public class MapTemplate
    {
        public int id;

        public int type;

        public int planetId;

        public string name;

        public int row;

        public int column;

        public string data;

        public int[,] datas;

        public List<int> imgsBgr = new List<int>();

        public int[,] colorsBgr = new int[4, 3];

        public Image imgBgr;

        public int bgrId;

        // Line-based collision
        public bool isLine;
        public string dataLine;
        public int mapWidth;
        public int mapHeight;
        public List<TerrainLine> terrainLines = new List<TerrainLine>();

        public MapTemplate()
        {

        }

        public void ParseLineData()
        {
            if (string.IsNullOrEmpty(dataLine)) return;
            try
            {
                terrainLines.Clear();
                // Manual JSON parse (no external dependency)
                // Expected format: {"lines":[{"type":"ground","points":[[x,y],[x,y]]}]}
                string json = dataLine.Trim();
                // Parse MapWidth / MapHeight
                int mwIdx = json.IndexOf("\"MapWidth\"");
                if (mwIdx >= 0)
                {
                    int mwStart = json.IndexOf(':', mwIdx) + 1;
                    int mwEnd = json.IndexOf(',', mwStart);
                    if (mwEnd < 0) mwEnd = json.IndexOf('}', mwStart);
                    mapWidth = int.Parse(json.Substring(mwStart, mwEnd - mwStart).Trim());
                }
                int mhIdx = json.IndexOf("\"MapHeight\"");
                if (mhIdx >= 0)
                {
                    int mhStart = json.IndexOf(':', mhIdx) + 1;
                    int mhEnd = json.IndexOf(',', mhStart);
                    if (mhEnd < 0) mhEnd = json.IndexOf('}', mhStart);
                    mapHeight = int.Parse(json.Substring(mhStart, mhEnd - mhStart).Trim());
                }
                // Support both "Lines" (MapTool) and "lines" (manual)
                int linesIdx = json.IndexOf("\"Lines\"");
                if (linesIdx < 0) linesIdx = json.IndexOf("\"lines\"");
                if (linesIdx < 0) { isLine = false; return; }
                int linesStart = linesIdx + 8;
                // Find the array content
                int arrStart = json.IndexOf('[', linesStart);
                int depth = 0;
                int arrEnd = arrStart;
                for (int i = arrStart; i < json.Length; i++)
                {
                    if (json[i] == '[') depth++;
                    else if (json[i] == ']') { depth--; if (depth == 0) { arrEnd = i; break; } }
                }
                string linesContent = json.Substring(arrStart + 1, arrEnd - arrStart - 1);
                // Split by each line object { ... }
                int objDepth = 0;
                int objStart = -1;
                for (int i = 0; i < linesContent.Length; i++)
                {
                    if (linesContent[i] == '{') { if (objDepth == 0) objStart = i; objDepth++; }
                    else if (linesContent[i] == '}') { objDepth--; if (objDepth == 0) { ParseOneLine(linesContent.Substring(objStart, i - objStart + 1)); } }
                }
            }
            catch { isLine = false; }
        }

        private void ParseOneLine(string lineJson)
        {
            TerrainLine line = new TerrainLine();
            line.type = TerrainLineType.Block; // All lines are block polygons
            // Parse points - support both "Points": ["x, y"] (MapTool) and "points": [[x,y]]
            int ptsIdx = lineJson.IndexOf("\"Points\"");
            if (ptsIdx < 0) ptsIdx = lineJson.IndexOf("\"points\"");
            int ptsStart = ptsIdx + 10;
            int ptsArrStart = lineJson.IndexOf('[', ptsStart);
            // Check format: ["x, y"] vs [[x,y]]
            int nextChar = ptsArrStart + 1;
            while (nextChar < lineJson.Length && (lineJson[nextChar] == ' ' || lineJson[nextChar] == '\n' || lineJson[nextChar] == '\r')) nextChar++;
            if (nextChar < lineJson.Length && lineJson[nextChar] == '"')
            {
                // MapTool format: ["x, y", "x, y", ...]
                int ptsDepth = 0;
                int ptsArrEnd = ptsArrStart;
                for (int i = ptsArrStart; i < lineJson.Length; i++)
                {
                    if (lineJson[i] == '[') ptsDepth++;
                    else if (lineJson[i] == ']') { ptsDepth--; if (ptsDepth == 0) { ptsArrEnd = i; break; } }
                }
                string ptsContent = lineJson.Substring(ptsArrStart + 1, ptsArrEnd - ptsArrStart - 1);
                // Extract each "x, y" string
                bool inQuote = false;
                int qStart = -1;
                for (int i = 0; i < ptsContent.Length; i++)
                {
                    if (ptsContent[i] == '"')
                    {
                        if (!inQuote) { inQuote = true; qStart = i + 1; }
                        else
                        {
                            inQuote = false;
                            string ptStr = ptsContent.Substring(qStart, i - qStart);
                            string[] parts = ptStr.Split(',');
                            if (parts.Length >= 2)
                            {
                                line.points.Add(new LinePoint(int.Parse(parts[0].Trim()), int.Parse(parts[1].Trim())));
                            }
                        }
                    }
                }
            }
            else
            {
                // Original format: [[x,y],[x,y],...]
                int ptsDepth = 0;
                int ptsArrEnd = ptsArrStart;
                for (int i = ptsArrStart; i < lineJson.Length; i++)
                {
                    if (lineJson[i] == '[') ptsDepth++;
                    else if (lineJson[i] == ']') { ptsDepth--; if (ptsDepth == 0) { ptsArrEnd = i; break; } }
                }
                string ptsContent = lineJson.Substring(ptsArrStart + 1, ptsArrEnd - ptsArrStart - 1);
                int ptDepth = 0;
                int ptStart = -1;
                for (int i = 0; i < ptsContent.Length; i++)
                {
                    if (ptsContent[i] == '[') { if (ptDepth == 0) ptStart = i; ptDepth++; }
                    else if (ptsContent[i] == ']')
                    {
                        ptDepth--;
                        if (ptDepth == 0)
                        {
                            string ptStr = ptsContent.Substring(ptStart + 1, i - ptStart - 1);
                            string[] parts = ptStr.Split(',');
                            if (parts.Length >= 2)
                            {
                                line.points.Add(new LinePoint(int.Parse(parts[0].Trim()), int.Parse(parts[1].Trim())));
                            }
                        }
                    }
                }
            }
            terrainLines.Add(line);
        }

        public string GetPlanetName()
        {
            if (planetId == 0)
            {
                return "Trái đất";
            }
            if (planetId == 1)
            {
                return "Namek";
            }
            if (planetId == 2)
            {
                return "Sayain";
            }
            return "Kì bí";
        }
    }

    // Line collision data types
    public enum TerrainLineType
    {
        Block = 0
    }

    public class LinePoint
    {
        public int x, y;
        public LinePoint(int x, int y) { this.x = x; this.y = y; }
    }

    public class TerrainLine
    {
        public TerrainLineType type;
        public List<LinePoint> points = new List<LinePoint>();
    }
}
