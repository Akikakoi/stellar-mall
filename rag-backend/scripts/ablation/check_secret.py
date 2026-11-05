"""比对 rag .env 与后端 application-dev.yml 的共享 JWT 密钥是否一致（不打印明文）。"""
import re

env = open('D:/Desktop/stellar-mall/rag-backend/.env', encoding='utf-8').read()
env_secret = re.search(r'STELLAR_USER_SECRET_KEY=(\S+)', env).group(1)

yml = open('D:/Desktop/stellar-mall/stellar-server/src/main/resources/application-dev.yml',
           encoding='utf-8').read()
line = re.search(r'user-secret-key:(.*)', yml).group(1).strip()
# 去掉可能的行内注释与引号
line = line.split('#')[0].strip().strip('"').strip("'")
print('env len:', len(env_secret), '| yml len:', len(line), '| equal:', env_secret == line)
