FROM elasticsearch:8.13.4

# 安装 IK 中文分词器（版本与 ES 严格对齐，官方发布源 release.infinilabs.com）
RUN elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.13.4.zip

# 安装拼音分词器（可选，用于拼音搜索）
# RUN elasticsearch-plugin install --batch \
#     https://release.infinilabs.com/analysis-pinyin/stable/elasticsearch-analysis-pinyin-8.13.4.zip
