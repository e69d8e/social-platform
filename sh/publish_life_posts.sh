#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJsaSIsImp0aSI6ImI2ZDUyNjViLTlhNmEtNDA4ZS1hODEwLTEyNWQ1ZWM1ZjA3NiIsImlhdCI6MTc4MTc2MTE5MiwiZXhwIjoxNzgxODQ3NTkyfQ.gRvugWXiHF1Zbhp8P6HshYC_UDYSopv_9T02_NzTOugY20-Ct-yjgWzLfUIdez-NsjF1AKokOAfcGEk7VFqRdA"
BASE_URL="http://localhost:8081"
CATEGORY_ID=1

declare -a TITLES=(
    "独居生活指南：一个人也要好好生活"
    "极简主义生活方式：断舍离的正确打开方式"
    "租房避坑指南：租房前必须知道的10件事"
    "职场新人必读：如何快速适应新环境"
    "高效时间管理：告别拖延症的实用方法"
    "居家健身指南：不去健身房也能练出好身材"
    "厨房小白入门：10道零失败的家常菜"
    "养猫新手指南：从接猫回家到日常护理"
    "周末好去处：城市周边短途旅行推荐"
    "睡眠质量提升攻略：如何拥有高质量的睡眠"
)

declare -a CONTENTS=(
    "<p>独居并不意味着孤独，而是一种与自己相处的生活方式。学会享受独处的时光，培养自己的兴趣爱好，建立规律的生活作息。</p><p>安全方面要注意：安装智能门锁和摄像头，不随意给陌生人开门，记住紧急联系电话。定期与家人朋友保持联系，让关心你的人放心。</p>"
    "<p>极简主义不是一味地扔东西，而是有意识地选择真正重要的物品。断舍离的核心是：断绝不需要的东西，舍弃多余的物品，脱离对物品的执着。</p><p>实践建议：从一个抽屉开始整理，一年没用过的物品果断处理，购物前思考是否真正需要。生活空间清爽了，心灵也会更加轻松。</p>"
    "<p>租房是很多年轻人必须面对的问题。签约前要注意：核实房东身份和房产证，检查房屋设施是否完好，明确水电燃气费用标准，了解退租条件和押金退还规则。</p><p>看房时注意采光、通风、隔音、周边配套等因素。建议拍照记录房屋现状，避免退租时产生纠纷。合同条款要仔细阅读，不要怕麻烦。</p>"
    "<p>初入职场难免紧张和不适应。建议：主动了解公司文化和规章制度，虚心向前辈请教，不要害怕犯错但要善于总结。</p><p>人际关系方面：保持真诚友善，但不要过于掏心掏肺；做好本职工作比社交更重要；学会向上管理，及时汇报工作进展。</p>"
    "<p>拖延症是时间管理的最大敌人。克服拖延的方法：将大任务拆分成小步骤，使用番茄工作法（25分钟专注+5分钟休息），设置明确的截止日期。</p><p>推荐工具：日历App管理日程，待办清单记录任务，时间追踪App分析时间使用情况。记住，完成比完美更重要。</p>"
    "<p>不去健身房也能锻炼出好身材。自重训练是最方便的居家健身方式：俯卧撑锻炼胸肌和手臂，深蹲锻炼腿部，平板锻炼核心，引体向上锻炼背部。</p><p>建议每周锻炼3-4次，每次30-45分钟。配合合理的饮食（高蛋白、适量碳水、低油低糖），坚持3个月就能看到明显效果。</p>"
    "<p>厨房小白也能做出美味家常菜。推荐10道零失败菜品：西红柿炒蛋、酸辣土豆丝、蒜蓉西兰花、可乐鸡翅、红烧肉、宫保鸡丁、麻婆豆腐、清蒸鲈鱼、蛋炒饭、番茄蛋汤。</p><p>新手建议从简单的炒菜开始，掌握火候和调味的基本技巧。做饭是一种生活技能，也是一种治愈心灵的方式。</p>"
    "<p>养猫是一件幸福又需要责任心的事。接猫回家前准备好：猫粮、猫砂盆、猫砂、猫碗、猫抓板、猫窝。新猫到家需要适应期，不要急于互动。</p><p>日常护理包括：定期驱虫（体内3个月一次，体外1个月一次）、每年疫苗、定期体检、每天铲屎、保持饮水充足。绝育是对猫咪负责的选择。</p>"
    "<p>周末不想宅家？城市周边有很多好去处。推荐类型：古镇古村（感受历史文化）、自然风景区（呼吸新鲜空气）、农家乐（体验田园生活）、温泉度假村（放松身心）。</p><p>出行建议：提前查看天气和交通状况，准备好必需物品，合理安排行程不要赶路。有时候，说走就走的旅行反而更有惊喜。</p>"
    "<p>睡眠质量直接影响生活质量和工作效率。改善睡眠的方法：保持规律作息（即使周末也不要相差太大）、睡前1小时远离电子屏幕、保持卧室凉爽黑暗安静。</p><p>助眠小技巧：睡前泡脚、喝热牛奶、听白噪音、做深呼吸练习。避免睡前饮酒、喝咖啡、剧烈运动。如果长期失眠，建议就医咨询。</p>"
)

declare -a COVERS=(
    "https://picsum.photos/seed/alone/800/400"
    ""
    "https://picsum.photos/seed/rent/800/400"
    "https://picsum.photos/seed/office/800/400"
    ""
    "https://picsum.photos/seed/yoga/800/400"
    "https://picsum.photos/seed/cook/800/400"
    ""
    "https://picsum.photos/seed/travel/800/400"
    "https://picsum.photos/seed/sleep/800/400"
)

echo "开始发布生活测试帖子..."
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
echo "生活测试帖子发布完成!"
