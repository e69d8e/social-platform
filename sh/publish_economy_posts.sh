#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ3YW5nd3UiLCJqdGkiOiIzZDEyZDc3OS0xNGE0LTQ1NDItYmNmMi01ZjY2ZjgxMGI5YjEiLCJpYXQiOjE3ODE3NjA5ODgsImV4cCI6MTc4MTg0NzM4OH0.wJUQY6s9bn3KpRYipsFtjn1j2qHlrXhZaxj2PAIdGObcCpfNRV--YPIBYqtA4CMR1Do9t-XHr0CEDBvYgErZbA"
BASE_URL="http://localhost:8081"
CATEGORY_ID=6

declare -a TITLES=(
    "2024年全球经济展望：复苏与挑战并存"
    "个人理财入门：从零开始构建你的财务自由之路"
    "股票投资基础知识：新手必读指南"
    "房地产市场分析：当前是买房的好时机吗？"
    "通货膨胀对普通人的影响及应对策略"
    "数字货币的未来：比特币与央行数字货币"
    "基金定投攻略：懒人理财的最佳方式"
    "创业融资指南：从天使轮到IPO的完整路径"
    "养老金规划：如何为退休生活做好准备"
    "跨境电商新机遇：中国品牌出海趋势分析"
)

declare -a CONTENTS=(
    "<p>2024年全球经济呈现复杂态势。一方面，主要经济体逐步走出疫情阴影，消费和投资活动回暖；另一方面，地缘政治冲突、供应链重构和气候变化等因素带来不确定性。</p><p>中国经济在结构转型中寻找新动能，数字经济、绿色产业和高端制造业成为增长引擎。投资者需要密切关注央行政策走向和国际经济形势变化。</p>"
    "<p>个人理财的核心是建立健康的财务习惯。首先，制定预算并严格执行，了解自己的收入和支出情况。其次，建立紧急备用金，通常建议为3-6个月的生活开支。</p><p>在此基础上，合理配置资产：保险保障、稳健投资和适度增值。记住，理财是一场马拉松，长期坚持比短期收益更重要。</p>"
    "<p>股票投资是普通人参与资本市场的重要方式。入门需要了解基本概念：市盈率、市净率、股息率等估值指标，以及资产负债表、利润表等财务报表。</p><p>投资策略方面，价值投资和成长投资是两大主流流派。建议新手从指数基金开始，逐步学习个股分析。记住：投资有风险，入市需谨慎。</p>"
    "<p>房地产市场受政策、经济和人口等多重因素影响。当前市场呈现分化态势：一线城市核心区域依然保值，三四线城市面临去库存压力。</p><p>购房决策应综合考虑自身需求、财务状况和市场环境。刚需购房者关注政策窗口期，投资购房者需要更加谨慎。房子是用来住的，不是用来炒的。</p>"
    "<p>通货膨胀侵蚀购买力，影响每个人的日常生活。食品、能源和住房价格上涨直接影响生活成本。</p><p>应对通胀的策略包括：增加收入来源、合理配置抗通胀资产（如黄金、房产、股票）、减少不必要开支、利用通胀挂钩理财产品等。保持财务弹性是关键。</p>"
    "<p>比特币作为首个去中心化数字货币，已成为一种另类投资资产。其总量有限的特性使其被部分投资者视为数字黄金。</p><p>与此同时，各国央行积极研发央行数字货币（CBDC），数字人民币已在多个城市试点。未来，私人数字货币和央行数字货币将共同构建新的数字金融生态。</p>"
    "<p>基金定投是最适合普通投资者的理财方式之一。通过定期定额投资，可以平滑市场波动，降低择时风险。</p><p>定投策略建议：选择宽基指数基金、坚持长期投资（3年以上）、设置合理的止盈目标、市场低迷时适当加大投入。定投的核心是纪律和耐心。</p>"
    "<p>创业融资是企业发展的重要环节。种子轮和天使轮关注团队和创意，A轮验证商业模式，B轮及以后追求规模化增长。</p><p>融资过程中，清晰的商业计划、合理的估值预期和良好的投资人关系至关重要。IPO是企业发展的重要里程碑，但不是唯一目标。</p>"
    "<p>养老规划越早开始越好。假设60岁退休，如果从25岁开始每月定投1000元，按年化7%收益计算，退休时可积累约200万元。</p><p>养老资金来源包括：社保养老金、企业年金、个人储蓄和投资。建议建立多元化的养老资产组合，确保退休后的生活质量。</p>"
    "<p>跨境电商为中国品牌出海提供了新机遇。东南亚、中东和拉美市场增长迅速，TikTok Shop、SHEIN等平台改变了传统贸易模式。</p><p>品牌出海需要关注本地化运营、供应链建设、合规经营和品牌塑造。从卖产品到做品牌，是中国跨境电商企业的升级方向。</p>"
)

declare -a COVERS=(
    "https://picsum.photos/seed/global/800/400"
    ""
    "https://picsum.photos/seed/stock/800/400"
    "https://picsum.photos/seed/house/800/400"
    ""
    "https://picsum.photos/seed/crypto/800/400"
    "https://picsum.photos/seed/fund/800/400"
    ""
    "https://picsum.photos/seed/pension/800/400"
    "https://picsum.photos/seed/crossborder/800/400"
)

echo "开始发布经济测试帖子..."
echo "===================="

for i in {0..9}; do
    echo ""
    echo "发布第 $((i+1)) 篇帖子: ${TITLES[$i]}"

    POST_ID=$(curl -s -X GET "${BASE_URL}/post" \
        -H "Authorization: ${TOKEN}" \
        -H "Content-Type: application/json" | jq -r '.data')

    if [ "$POST_ID" == "null" ] || [ -z "$POST_ID" ]; then
        echo "  错误: 无法获取帖子ID"
        continue
    fi
    echo "  获取到帖子ID: ${POST_ID}"

    COVER=${COVERS[$i]}
    if [ -n "$COVER" ]; then
        BODY=$(cat <<EOF
{
    "id": ${POST_ID},
    "title": "${TITLES[$i]}",
    "content": "${CONTENTS[$i]}",
    "categoryId": ${CATEGORY_ID},
    "cover": "${COVER}"
}
EOF
)
    else
        BODY=$(cat <<EOF
{
    "id": ${POST_ID},
    "title": "${TITLES[$i]}",
    "content": "${CONTENTS[$i]}",
    "categoryId": ${CATEGORY_ID}
}
EOF
)
    fi

    RESPONSE=$(curl -s -X POST "${BASE_URL}/post" \
        -H "Authorization: ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${BODY}")

    CODE=$(echo $RESPONSE | jq -r '.code')
    MESSAGE=$(echo $RESPONSE | jq -r '.message')

    if [ "$CODE" == "1" ]; then
        echo "  ✓ 发布成功"
    else
        echo "  ✗ 发布失败: ${MESSAGE}"
    fi

    sleep 0.5
done

echo ""
echo "===================="
echo "经济测试帖子发布完成!"
