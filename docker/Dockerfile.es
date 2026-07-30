FROM elasticsearch:7.17.25

# 安装 IK 中文分词器（版本与 ES 严格对齐）
RUN elasticsearch-plugin install --batch \
    https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.17.25/elasticsearch-analysis-ik-7.17.25.zip

# 安装拼音分词器（可选，用于拼音搜索）
# RUN elasticsearch-plugin install --batch \
#     https://github.com/medcl/elasticsearch-analysis-pinyin/releases/download/v7.17.25/elasticsearch-analysis-pinyin-7.17.25.zip
