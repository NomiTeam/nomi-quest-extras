"""Real SQLite engine + independent connections, not production JDBC validation."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import sqlite3
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'src/main/java/org/nomiteam/quests/DurableQuota.java').read_text()
insert = re.search(r'"(INSERT INTO nomi_quest_daily_quota [^"]+)"', source)[1]
delete = re.search(r'"(DELETE FROM nomi_quest_daily_quota [^"]+)"', source)[1]
with tempfile.TemporaryDirectory(dir=ROOT / '.work') as tmp:
    db = str(Path(tmp) / 'quota.db')
    with sqlite3.connect(db) as c:
        c.execute('CREATE TABLE nomi_quest_daily_quota (player_uuid VARCHAR(36) NOT NULL, quota_day VARCHAR(10) NOT NULL, request_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(player_uuid,quota_day))')
        c.execute('CREATE TABLE odq_fixture (recent_rerolls INTEGER)')
        c.execute('INSERT INTO odq_fixture VALUES (0)')
    gate = threading.Barrier(2)
    def claim(token):
        with sqlite3.connect(db) as c:
            gate.wait()
            try:
                c.execute(insert, ('player', '2026-10-26', token))
                return True
            except sqlite3.IntegrityError:
                return False
    with ThreadPoolExecutor(2) as pool:
        a = pool.submit(claim, 'a'); b = pool.submit(claim, 'b')
        assert sum((a.result(), b.result())) == 1
    with sqlite3.connect(db) as c:
        c.execute('UPDATE odq_fixture SET recent_rerolls=0')
    with sqlite3.connect(db) as c:
        try:
            c.execute(insert, ('player', '2026-10-26', 'second'))
            raise AssertionError('old ODQ save must not reopen quota')
        except sqlite3.IntegrityError:
            pass
        c.execute(delete, ('player', '2026-10-26', 'wrong-token'))
        assert c.execute('SELECT COUNT(*) FROM nomi_quest_daily_quota').fetchone()[0] == 1
        c.execute(insert, ('player', '2026-10-27', 'next-day'))
print('PASS real SQLite independent connections: atomic competition, old ODQ overwrite isolation, token delete, next day; production JDBC remains pending')
