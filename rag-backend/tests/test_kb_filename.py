from app.services.kb_service import _safe_filename


def test_strips_unix_path_traversal():
    assert _safe_filename("../../etc/passwd") == "passwd"


def test_strips_windows_path_traversal():
    assert _safe_filename(r"..\..\Windows\system.ini") == "system.ini"


def test_keeps_plain_filename():
    assert _safe_filename("产品手册.pdf") == "产品手册.pdf"
    assert _safe_filename("   ") == ""