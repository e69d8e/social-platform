#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ6aGFuZ3NhbiIsImp0aSI6ImIzZDMyNzM0LTVhYTctNGRiNS04M2I2LTI4ZDNjNjZlMDMxZiIsImlhdCI6MTc4MTc2MDU4NiwiZXhwIjoxNzgxODQ2OTg2fQ.ZyFv3kUfi0SzLU3BStnPV9eU4PV6AVSRnmOFRlZxr6DdUP_Ik4mw1PRz24VrvNeOgNTX_uoNTgN7ioyXgOmHBQ"
BASE_URL="http://localhost:8081"
CATEGORY_ID=2

declare -a TITLES=(
    "风光摄影入门：如何拍出震撼的风景照"
    "人像摄影用光技巧：自然光与人造光的完美结合"
    "街头摄影的艺术：捕捉城市中的决定性瞬间"
    "微距摄影指南：探索微观世界的奇妙细节"
    "夜景摄影完全教程：从器材到后期全覆盖"
    "黑白摄影的魅力：用光影讲述故事"
    "旅行摄影必备技巧：记录旅途中的美好瞬间"
    "手机摄影进阶：用手机拍出专业级照片"
    "野生动物摄影：耐心与技术的完美结合"
    "极简摄影美学：少即是多的视觉哲学"
)

declare -a CONTENTS=(
    "<p>风光摄影是最受欢迎的摄影类型之一。要拍出令人震撼的风景照，首先要学会观察光线。黄金时段（日出后和日落前一小时）的柔和光线能为照片增添温暖的色调。</p><p>构图方面，善用前景引导线、三分法则和框架构图，能让画面更有层次感和深度。记得使用三脚架保证画面清晰，小光圈获取大景深。</p>"
    "<p>光线是人像摄影的灵魂。自然光拍摄时，窗边的柔和散射光是最佳选择，能营造出细腻的肤质和自然的氛围。逆光拍摄可以创造出美丽的轮廓光和发丝光。</p><p>人造光方面，学习使用柔光箱、反光板和闪光灯的组合，可以精确控制光线的方向和质感，打造出专业级的人像作品。</p>"
    "<p>街头摄影的魅力在于它的不可预知性。亨利·卡蒂埃-布列松所说的决定性瞬间，正是街头摄影的精髓所在。</p><p>建议使用35mm或50mm定焦镜头，保持低调，融入环境。预判场景、等待元素汇聚，当所有要素完美对齐时按下快门，这就是街头摄影的乐趣。</p>"
    "<p>微距摄影为我们打开了一个全新的视觉世界。一朵花的花蕊、一片叶子的脉络、一只昆虫的复眼，在微距镜头下都展现出令人惊叹的细节。</p><p>拍摄微距需要稳定的支撑（三脚架或独脚架）、精确的对焦（手动对焦配合焦点堆叠）、以及适当的景深控制。环形闪光灯是微距拍摄的好帮手。</p>"
    "<p>夜景摄影充满魅力但也充满挑战。器材方面，一台高感表现良好的相机、大光圈镜头和稳固的三脚架是必备装备。</p><p>拍摄技巧包括：使用低ISO保证画质、小光圈拍出星芒效果、长时间曝光捕捉光轨、以及利用白平衡营造不同氛围。后期处理中，降噪和色调调整是关键步骤。</p>"
    "<p>黑白摄影剥离了色彩的干扰，让观者更专注于光影、质感和构图。它是一种纯粹的视觉表达方式。</p><p>拍摄黑白照片时，要学会用灰度的眼光观察世界。高对比度场景、丰富的纹理、强烈的明暗对比都是黑白摄影的好题材。后期处理中，调整各色通道的明暗关系可以创造出不同的影调效果。</p>"
    "<p>旅行摄影不仅是记录风景，更是讲述故事。出发前做好目的地调研，了解最佳拍摄时间和地点。</p><p>拍摄时注意捕捉当地的人文风情、特色建筑和日常生活。不要只拍大场景，也要关注细节和特写。随身携带轻便的器材，保持灵活机动，随时准备捕捉精彩瞬间。</p>"
    "<p>如今的手机摄影能力已经非常强大。掌握一些技巧，用手机也能拍出专业级的照片。</p><p>善用网格线辅助构图，点击屏幕对焦并调整曝光，使用HDR模式应对大光比场景，利用人像模式营造虚化效果。后期推荐使用Snapseed或VSCO进行调色，让你的手机照片更上一层楼。</p>"
    "<p>野生动物摄影是极具挑战性的摄影类型。它需要超乎寻常的耐心、扎实的摄影技术、以及对动物行为的深入了解。</p><p>器材方面，长焦镜头（至少300mm）是必备装备。拍摄时要注意伪装和隐蔽，保持安全距离，尊重动物的自然栖息地。了解动物的习性和活动规律，能大大提高拍摄成功率。</p>"
    "<p>极简摄影追求的是少即是多的美学理念。通过简化画面元素，突出主体，创造出干净、纯粹的视觉效果。</p><p>大面积的留白、简洁的线条、单一的色调、几何形状的运用，都是极简摄影的常见手法。在纷繁复杂的世界中发现简洁之美，需要摄影师敏锐的观察力和独特的审美视角。</p>"
)

declare -a COVERS=(
    "https://picsum.photos/seed/landscape/800/400"
    ""
    "https://picsum.photos/seed/street/800/400"
    "https://picsum.photos/seed/macro/800/400"
    ""
    "https://picsum.photos/seed/bw/800/400"
    ""
    "https://picsum.photos/seed/phone/800/400"
    "https://picsum.photos/seed/wildlife/800/400"
    ""
)

echo "开始发布摄影测试帖子..."
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
echo "摄影测试帖子发布完成!"
