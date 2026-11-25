"""Exercise real MySQL constraints in a newly created disposable database.

Set MYSQL_TEST_SOCKET (isolated local server), or MYSQL_TEST_CONFIG (client config).
MYSQL_BIN optionally selects mysql.exe/mysql. Never opens the application's local config.
Requires an account allowed to CREATE/DROP the randomly named test database.
"""
import os
from pathlib import Path
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ['signup', 'group', 'diary', 'like', 'comment', 'photo']


class SocialSchemaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        binary = os.environ.get('MYSQL_BIN', 'mysql')
        socket = os.environ.get('MYSQL_TEST_SOCKET')
        config = os.environ.get('MYSQL_TEST_CONFIG')
        if bool(socket) == bool(config):
            raise RuntimeError('Set exactly one of MYSQL_TEST_SOCKET or MYSQL_TEST_CONFIG for an isolated test server.')
        if socket:
            cls.client = [binary, '--no-defaults', '--protocol=SOCKET', '--socket=' + socket, '--user=root']
        else:
            cls.client = [binary, '--defaults-extra-file=' + str(Path(config).resolve())]
        cls.client += ['--batch', '--skip-column-names', '--default-character-set=utf8mb4']
        cls.database = 'liod_social_test_' + uuid.uuid4().hex
        cls.sql('CREATE DATABASE ' + cls.database + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', use_db=False)
        cls.addClassCleanup(lambda: cls.sql('DROP DATABASE ' + cls.database, use_db=False))
        cls.apply_schemas()

    @classmethod
    def sql(cls, query, *, check=True, use_db=True):
        command = cls.client + (['--database=' + cls.database] if use_db else [])
        result = subprocess.run(command, input=query, text=True, capture_output=True, encoding='utf-8')
        if check and result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result

    @classmethod
    def apply_schemas(cls):
        for name in SCHEMAS:
            cls.sql((ROOT / 'backend/src/main/resources' / (name + '-schema.sql')).read_text(encoding='utf-8'))

    def setUp(self):
        self.sql("""
            DELETE FROM diaries; DELETE FROM diary_groups; DELETE FROM users;
            INSERT INTO users(id, username, password_hash, nickname) VALUES
                (1,'owner','test-only','방장'),(2,'friend','test-only','친구'),(3,'other','test-only','다른 친구');
            INSERT INTO diary_groups(id,name,owner_id) VALUES (10,'첫 일기장',1),(20,'다른 일기장',1);
            INSERT INTO diary_group_members(group_id,user_id,role) VALUES (10,1,'OWNER'),(10,2,'MEMBER'),(20,1,'OWNER');
            INSERT INTO diaries(id,group_id,author_id,title,content) VALUES
                (100,10,2,'친구 일기','내용'),(200,20,1,'다른 일기','내용');
        """)

    def reject(self, query, code):
        result = self.sql(query, check=False)
        self.assertNotEqual(result.returncode, 0, 'invalid SQL operation was accepted: ' + query)
        self.assertIn('ERROR ' + str(code), result.stderr)

    def test_like_is_unique_per_user_and_diary(self):
        self.sql('INSERT INTO diary_likes(diary_id,user_id) VALUES (100,1)')
        self.reject('INSERT INTO diary_likes(diary_id,user_id) VALUES (100,1)', 1062)
        self.sql('INSERT INTO diary_likes(diary_id,user_id) VALUES (100,2),(200,1)')
        self.assertEqual(self.sql('SELECT COUNT(*) FROM diary_likes').stdout.strip(), '3')

    def test_photo_is_unique_per_diary_and_storage_key(self):
        self.sql("INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES (100,'a.png','image/png',1)")
        self.reject("INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES (100,'b.png','image/png',1)", 1062)
        self.reject("INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES (200,'a.png','image/png',1)", 1062)

    def test_photo_size_and_type_constraints(self):
        for content_type, size in [('image/gif',1),('image/png',0),('image/jpeg',5242881)]:
            self.reject(f"INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES (100,'a.png','{content_type}',{size})", 3819)
        self.sql("INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES (100,'a.jpg','image/jpeg',5242880)")

    def test_invalid_relations_and_empty_comment_are_rejected(self):
        self.reject('INSERT INTO diary_likes(diary_id,user_id) VALUES (999,1)',1452)
        self.reject("INSERT INTO diary_comments(diary_id,author_id,content) VALUES (100,999,'내용')",1452)
        self.reject("INSERT INTO diary_comments(diary_id,author_id,content) VALUES (100,1,'   ')",3819)

    def add_social_rows(self):
        self.sql("""
            INSERT INTO diary_likes(diary_id,user_id) VALUES (100,2),(200,1);
            INSERT INTO diary_comments(diary_id,author_id,content) VALUES (100,2,'남을 댓글'),(200,1,'다른 댓글');
            INSERT INTO diary_photos(diary_id,storage_key,content_type,size_bytes) VALUES
                (100,'a.png','image/png',10),(200,'b.png','image/png',10);
        """)

    def test_diary_deletion_cascades_only_related_rows(self):
        self.add_social_rows()
        self.sql('DELETE FROM diaries WHERE id=100')
        for table in ['diary_likes','diary_comments','diary_photos']:
            self.assertEqual(self.sql(f'SELECT diary_id FROM {table}').stdout.strip(),'200')
        self.assertEqual(self.sql('SELECT COUNT(*) FROM users').stdout.strip(),'3')

    def test_leaving_preserves_existing_social_records(self):
        self.add_social_rows()
        self.sql("DELETE FROM diary_group_members WHERE group_id=10 AND user_id=2 AND role='MEMBER'")
        for table in ['diary_likes','diary_comments','diary_photos']:
            self.assertEqual(self.sql(f'SELECT COUNT(*) FROM {table} WHERE diary_id=100').stdout.strip(),'1')

    def test_reapplying_schema_preserves_rows(self):
        self.add_social_rows()
        self.apply_schemas()
        for table in ['diary_likes','diary_comments','diary_photos']:
            self.assertEqual(self.sql(f'SELECT COUNT(*) FROM {table}').stdout.strip(),'2')


if __name__ == '__main__':
    unittest.main(verbosity=2)
