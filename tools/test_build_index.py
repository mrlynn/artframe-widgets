"""python3 -m unittest discover -s tools: signing and revocation in build_index.py."""

import base64
import datetime
import json
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_index  # noqa: E402
import ed25519  # noqa: E402

KEY = bytes(range(32))
NOW = datetime.datetime(2026, 11, 3, 12, tzinfo=datetime.timezone.utc)


class SigningTest(unittest.TestCase):
    def index(self, key=KEY, revoked=()):
        return build_index.signed_index([{"name": "A", "id": "x.a"}], list(revoked), "test", key, NOW)

    def test_round_trip(self):
        data, sig = self.index()
        self.assertTrue(ed25519.verify(ed25519.public_key(KEY), data, base64.b64decode(sig)))

    def test_tampered_byte_fails(self):
        data, sig = self.index()
        bad = data.replace(b"x.a", b"x.b")
        self.assertFalse(ed25519.verify(ed25519.public_key(KEY), bad, base64.b64decode(sig)))

    def test_wrong_key_fails(self):
        data, sig = self.index()
        self.assertFalse(ed25519.verify(ed25519.public_key(bytes(32)), data, base64.b64decode(sig)))

    def test_dates_and_revoked(self):
        data, _ = self.index(revoked=[{"id": "x.b", "versions": "*", "reason": "r"}])
        j = json.loads(data)
        self.assertEqual((j["format"], j["issuedAt"], j["expires"]), (2, "2026-11-03T12:00:00Z", "2026-12-03T12:00:00Z"))
        self.assertEqual(j["revoked"][0]["id"], "x.b")

    def test_unsigned_without_key(self):
        self.assertIsNone(self.index(key=None)[1])

    def test_revoked_version_matching(self):
        r = [{"id": "x.a", "versions": ["1.0.0"], "reason": "r"}, {"id": "x.b", "versions": "*", "reason": "r"}]
        self.assertTrue(build_index.is_revoked(r, "x.a", "1.0.0"))
        self.assertFalse(build_index.is_revoked(r, "x.a", "1.0.1"))
        self.assertTrue(build_index.is_revoked(r, "x.b", "9.9.9"))

    def test_key_from_env_or_file(self):
        b64 = base64.b64encode(KEY).decode()
        os.environ[build_index.KEY_ENV] = b64
        self.assertEqual(build_index.load_key(), KEY)
        path = Path(os.environ.get("TMPDIR", "/tmp")) / "artframe-test.key"
        path.write_text(b64 + "\n")
        os.environ[build_index.KEY_ENV] = str(path)
        try:
            self.assertEqual(build_index.load_key(), KEY)
        finally:
            path.unlink()
            del os.environ[build_index.KEY_ENV]
        self.assertIsNone(build_index.load_key())


if __name__ == "__main__":
    unittest.main()
