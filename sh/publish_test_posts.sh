#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJsaSIsImp0aSI6IjAxY2MyYThjLTZmN2MtNGE2Ny05MjYxLWI0OGUxNjY1YTVjYSIsImlhdCI6MTc4MTc1NzU1MCwiZXhwIjoxNzgxODQzOTUwfQ.UkSFHAe4GP0c_TtE3ezwACUEtbOV4jgylWV_K-he-9fBORFpb08YCaMCxxwohllCr2Q0Bo4JAPXbktO1BJVtGA"
BASE_URL="http://localhost:8081"
CATEGORY_ID=4

# 科技主题帖子数据
declare -a TITLES=(
    "2024年最值得关注的AI技术趋势"
    "量子计算机商用化进展报告"
    "苹果Vision Pro深度体验评测"
    "ChatGPT-5即将发布：能力大幅提升"
    "自动驾驶L4级别技术突破"
    "RISC-V架构在物联网领域的应用"
    "6G通信技术研究最新进展"
    "脑机接口技术取得重大突破"
    "新型固态电池技术量产在即"
    "Web3.0与去中心化互联网的未来"
)

declare -a CONTENTS=(
    "<p>人工智能领域在2024年迎来了多项重大突破。大型语言模型的能力持续提升，多模态AI成为新的发展方向。从文本生成到图像、视频创作，AI正在改变内容生产的方方面面。</p><p>特别值得关注的是AI Agent技术的成熟，使得AI能够自主完成复杂任务，而不仅仅是简单的问答。</p>"
    "<p>量子计算领域传来振奋人心的消息。IBM、Google等公司的量子处理器已经达到数百个量子比特的规模，量子优越性在特定问题上得到进一步验证。</p><p>金融、制药、材料科学等领域开始探索量子计算的实际应用，预计未来3-5年内将出现首批商业化量子计算服务。</p>"
    "<p>苹果Vision Pro发布后，我们对其进行了为期一个月的深度体验。这款空间计算设备在显示效果、交互体验方面确实领先于市场上的其他产品。</p><p>眼球追踪+手势的交互方式非常自然，但在佩戴舒适度和应用生态方面仍有提升空间。售价2999美元的定价也限制了其普及速度。</p>"
    "<p>据可靠消息，OpenAI即将发布新一代语言模型GPT-5。相比前代产品，GPT-5在推理能力、代码生成、数学计算等方面有显著提升。</p><p>最令人期待的是其原生多模态能力，可以同时处理文本、图像、音频和视频输入，这将开启全新的应用场景。</p>"
    "<p>自动驾驶技术再次迎来里程碑时刻。多家科技公司和车企宣布其L4级别自动驾驶系统在特定场景下已实现商业化运营。</p><p>城市道路、高速公路、港口物流等场景的自动驾驶解决方案日趋成熟，法规和保险配套也在逐步完善。</p>"
    "<p>开源指令集架构RISC-V在物联网领域展现出强大潜力。相比ARM架构，RISC-V具有更高的灵活性和更低的授权成本。</p><p>众多芯片厂商开始推出基于RISC-V的物联网处理器，在智能家居、工业控制、可穿戴设备等领域得到广泛应用。</p>"
    "<p>虽然5G网络尚未完全普及，但6G技术的研究已经如火如荼地展开。太赫兹通信、空天地一体化网络、智能超表面等关键技术取得重要进展。</p><p>预计6G将在2030年左右实现商用，峰值速率将达到1Tbps，延迟降低到亚毫秒级别。</p>"
    "<p>Neuralink等脑机接口公司近期公布了令人振奋的研究成果。高带宽、低侵入式的脑机接口设备已成功植入人体，帮助瘫痪患者恢复部分运动能力。</p><p>未来脑机接口有望在医疗康复、人机交互、认知增强等领域发挥重要作用。</p>"
    "<p>固态电池技术即将迎来量产时代。多家电池企业和车企宣布固态电池产线建设进展顺利，预计2025年将有首批搭载固态电池的电动车上市。</p><p>固态电池在能量密度、安全性、充电速度等方面全面超越传统锂离子电池，将彻底改变电动汽车行业格局。</p>"
    "<p>Web3.0代表着互联网的下一个发展阶段。基于区块链的去中心化应用、数字身份、数字资产等概念正在从理想走向现实。</p><p>尽管仍面临性能、监管、用户体验等挑战，但Web3.0的核心理念——用户拥有自己的数据和数字资产——正在获得越来越多人的认可。</p>"
)

# 封面图片URL（部分帖子使用）
declare -a COVERS=(
    "https://picsum.photos/seed/ai/800/400"
    ""
    "https://picsum.photos/seed/vr/800/400"
    "https://picsum.photos/seed/chatgpt/800/400"
    ""
    "https://picsum.photos/seed/riscv/800/400"
    ""
    "https://picsum.photos/seed/brain/800/400"
    "https://picsum.photos/seed/battery/800/400"
    ""
)

echo "开始发布测试帖子..."
echo "===================="

for i in {0..9}; do
    echo ""
    echo "发布第 $((i+1)) 篇帖子: ${TITLES[$i]}"

    # 获取帖子ID
    POST_ID=$(curl -s -X GET "${BASE_URL}/post" \
        -H "Authorization: ${TOKEN}" \
        -H "Content-Type: application/json" | jq -r '.data')

    if [ "$POST_ID" == "null" ] || [ -z "$POST_ID" ]; then
        echo "  错误: 无法获取帖子ID"
        continue
    fi
    echo "  获取到帖子ID: ${POST_ID}"

    # 构建请求体
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

    # 发布帖子
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

    # 短暂延迟避免请求过快
    sleep 0.5
done

echo ""
echo "===================="
echo "测试帖子发布完成!"
