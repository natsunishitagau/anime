"""查看数据库中的番剧数据，用于设计评测集"""
import sys, io, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pymysql
pymysql.install_as_MySQLdb()
import MySQLdb

conn = MySQLdb.connect(
    host='localhost', port=3306,
    user='anime_user', password='Anime@123',
    database='anime_db', charset='utf8mb4'
)
cursor = conn.cursor()

# 总览
cursor.execute("SELECT COUNT(*) FROM anime")
total = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM anime WHERE synopsis IS NOT NULL AND synopsis != '' AND CHAR_LENGTH(synopsis) > 10")
with_synopsis = cursor.fetchone()[0]
print(f"总番剧: {total}, 有简介的: {with_synopsis}")

# 高分番剧 Top 30
cursor.execute("SELECT id, title, score, year FROM anime ORDER BY score DESC LIMIT 30")
print("\n=== 高分番剧 Top 30 ===")
for r in cursor.fetchall():
    print(f"  [{r[0]}] {r[1]} (评分: {r[2]}, 年份: {r[3]})")

# 热门 IP 搜索
keywords = [
    ("海贼", "海贼王/航海王/ONE PIECE"),
    ("火影", "火影/Naruto"),
    ("巨人", "进击的巨人"),
    ("鬼灭", "鬼灭之刃"),
    ("咒术", "咒术回战"),
    ("龙珠", "龙珠/Dragon Ball"),
    ("EVA", "EVA/新世纪福音战士"),
    ("钢之炼金术师", "钢炼"),
    ("命运石之门", "石头门"),
    ("刀剑神域", "SAO"),
    ("魔禁", "魔法禁书目录"),
    ("银魂", "银魂"),
    ("Fate", "Fate"),
    ("凉宫", "凉宫春日"),
    ("死亡笔记", "死亡笔记"),
    ("鲁路修", "鲁路修/反叛的鲁路修"),
    ("哆啦A梦", "哆啦A梦/机器猫"),
    ("名侦探柯南", "柯南"),
]
print("\n=== 热门 IP 覆盖情况 ===")
for kw, desc in keywords:
    cursor.execute(f"SELECT id, title, score FROM anime WHERE title LIKE '%{kw}%' LIMIT 3")
    rows = cursor.fetchall()
    if rows:
        for r in rows:
            print(f"  [{r[0]}] {r[1]} (评分: {r[2]}) ← {desc}")
    else:
        print(f"  ❌ 未找到: {desc}")

# 查看一条 synopsis 样例
cursor.execute("SELECT id, title, SUBSTRING(synopsis, 1, 500) FROM anime WHERE id = 52991")
r = cursor.fetchone()
if r:
    print(f"\n=== synopsis 样例 (id={r[0]}, {r[1]}) ===")
    print(f"  {r[2]}...")

cursor.close()
conn.close()
