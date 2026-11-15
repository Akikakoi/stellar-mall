"""比对 rag .env 与后端 application-dev.yml 的共享 JWT 密钥是否一致（不打印明文）。"""
import re
from pathlib import Path

RAG_ROOT = Path(__file__).resolve().parents[2]
env = (RAG_ROOT / ".env").read_text(encoding="utf-8")
env_secret = re.search(r'STELLAR_USER_SECRET_KEY=(\S+)', env).group(1)

yml = (RAG_ROOT.parent / "stellar-server" / "src" / "main" / "resources" / "application-dev.yml").read_text(
    encoding="utf-8")
line = re.search(r'user-secret-key:(.*)', yml).group(1).strip()
# 去掉可能的行内注释与引号
line = line.split('#')[0].strip().strip('"').strip("'")
print('env len:', len(env_secret), '| yml len:', len(line), '| equal:', env_secret == line)
