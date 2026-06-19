#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJsaXNpIiwianRpIjoiZTYxMGQ2YTctYmZiMS00ZmYwLWJjYjgtOWQ4MTFjMTYwZTc1IiwiaWF0IjoxNzgxNzYwODMxLCJleHAiOjE3ODE4NDcyMzF9.Jp4eveeI9AznFqzo05lHOKMrIbburRPFJrwck1n9_r42IHD5TyGaa_p_wAB9TYvk5XZmvO6XRyFaBjzJO-CyvQ"
BASE_URL="http://localhost:8081"
CATEGORY_ID=5

declare -a TITLES=(
    "2024年旗舰手机横评：iPhone 16 Pro Max vs 三星S24 Ultra"
    "机械键盘选购指南：从轴体到键帽全面解析"
    "真无线耳机对比：AirPods Pro 2 vs 索尼WF-1000XM5"
    "4K显示器选购推荐：设计师和游戏玩家的最佳选择"
    "笔记本电脑轻薄本推荐：2024年最值得入手的5款"
    "智能家居入门：打造你的全屋智能生态系统"
    "固态硬盘选购攻略：PCIe 4.0 vs PCIe 5.0性能对比"
    "游戏手柄推荐：从Xbox到PS5的手柄选购指南"
    "平板电脑怎么选？iPad Pro vs 华为MatePad Pro深度对比"
    "充电宝选购指南：大容量与便携性的完美平衡"
    "机械硬盘还有购买价值吗？NAS存储方案解析"
    "蓝牙音箱推荐：从便携到家用全覆盖"
    "电子书阅读器对比：Kindle vs 文石 vs 掌阅"
    "台式机装机配置推荐：2024年高性价比方案"
    "智能手表选购：Apple Watch Ultra 2 vs 佳明Fenix 8"
)

declare -a CONTENTS=(
    "<p>2024年旗舰手机市场竞争激烈。iPhone 16 Pro Max搭载A18 Pro芯片，影像系统全面升级，新增拍照按钮和5倍光学变焦。三星S24 Ultra则配备骁龙8 Gen3处理器，S Pen手写笔和Galaxy AI功能是其亮点。</p><p>两款手机在性能、拍照、续航方面各有千秋。iOS和Android生态的选择也是重要考量因素。</p>"
    "<p>机械键盘的世界丰富多彩。轴体方面，Cherry MX系列是经典之选，红轴适合打字，青轴段落感强，茶轴则是万金油。国产轴体如佳达隆、凯华等也提供了更多选择。</p><p>键帽材质分为ABS、PBT和POM，PBT键帽耐磨不打油是首选。配列方面，87键和75%配列兼顾功能和桌面空间。</p>"
    "<p>AirPods Pro 2搭载H2芯片，主动降噪效果出色，自适应通透模式体验极佳，与苹果生态无缝衔接。索尼WF-1000XM5在音质方面更胜一筹，LDAC编码支持高解析度音频。</p><p>两款耳机各有优势：追求生态体验选AirPods，追求音质选索尼。佩戴舒适度和续航也是选购时需要考虑的因素。</p>"
    "<p>4K显示器已经成为专业工作和游戏的标配。设计师应关注色域覆盖（Adobe RGB 99%以上）、色准（Delta E<2）和出厂校色报告。</p><p>游戏玩家则应关注刷新率（144Hz以上）、响应时间（1ms）和HDR支持。推荐产品包括戴尔U2723QE、LG 27GP950等。</p>"
    "<p>2024年轻薄本市场竞争激烈。推荐5款产品：MacBook Air M3（续航王者）、联想YOGA Pro 14s（性价比之选）、华为MateBook X Pro（商务首选）、华硕灵耀14（轻薄标杆）、ThinkPad X1 Carbon（经典商务）。</p><p>选购时关注处理器性能、屏幕素质、续航能力和接口丰富度。</p>"
    "<p>智能家居让生活更加便捷舒适。入门建议从智能音箱（天猫精灵、小爱同学）开始，逐步扩展智能照明（Yeelight）、智能门锁、智能窗帘等设备。</p><p>选择统一的协议标准（Matter、Zigbee）能确保设备间的互联互通。全屋智能需要稳定的网络环境和合理的设备布局。</p>"
    "<p>PCIe 4.0固态硬盘顺序读取速度约7000MB/s，PCIe 5.0则可突破10000MB/s。但日常使用中，两者差距并不明显。</p><p>对于普通用户，PCIe 4.0 SSD性价比更高。专业用户和发烧友可以选择PCIe 5.0体验极致速度。选购时关注颗粒类型、缓存方案和散热设计。</p>"
    "<p>Xbox手柄是PC游戏的最佳伴侣，手感舒适，兼容性好。PS5 DualSense手柄的自适应扳机和触觉反馈带来了革命性的游戏体验。</p><p>Switch Pro手柄适合Nintendo玩家。此外，北通、飞智等国产手柄也提供了高性价比的选择。选购时关注连接方式、续航和人体工学设计。</p>"
    "<p>iPad Pro搭载M4芯片，性能强大，配合Apple Pencil Pro是创意工作者的利器。华为MatePad Pro在多屏协同和办公场景下表现出色。</p><p>如果深度使用苹果生态，iPad Pro是首选。如果需要更好的文件管理和多任务处理，华为MatePad Pro值得考虑。</p>"
    "<p>选购充电宝需要平衡容量和便携性。10000mAh适合日常通勤，20000mAh适合长途旅行。快充协议支持也很重要，PD、QC协议兼容性更广。</p><p>推荐品牌包括小米、安克、罗马仕等。注意航空运输限制：随身携带不超过100Wh（约27000mAh）。</p>"
    "<p>机械硬盘虽然速度不及固态硬盘，但在大容量存储方面仍有优势。NAS专用硬盘（西数红盘、希捷酷狼）专为7x24小时运行设计。</p><p>对于家庭NAS用户，建议SSD做系统盘，HDD做数据存储。组建RAID阵列可以兼顾速度和数据安全。</p>"
    "<p>便携蓝牙音箱推荐JBL Flip 6和Bose SoundLink Flex，防水防尘适合户外使用。家用音箱推荐Bose SoundLink Max和哈曼卡顿Aura Studio 4。</p><p>选购时关注音质表现、续航时间、防水等级和连接稳定性。支持多设备串联的音箱可以打造更好的立体声效果。</p>"
    "<p>Kindle退出中国市场后，文石和掌阅成为国内电子书阅读器的主力。文石BOOX开放安卓系统，支持多种阅读App。掌阅iReader专注阅读体验，书城资源丰富。</p><p>选购时关注屏幕尺寸、分辨率、前光均匀度和续航。7.8英寸适合漫画和PDF阅读，6英寸更适合纯文字阅读。</p>"
    "<p>2024年高性价比装机方案：CPU选AMD锐龙5 7600或Intel i5-14400F，显卡选RTX 4060 Ti或RX 7700 XT，内存16GB DDR5起步。</p><p>电源建议650W以上金牌认证，机箱注重散热和理线空间。整机预算控制在6000-8000元可以畅玩主流游戏。</p>"
    "<p>Apple Watch Ultra 2专为极限运动设计，钛合金表壳坚固耐用，双频GPS定位精准，潜水深度可达40米。佳明Fenix 8则是专业运动手表的标杆。</p><p>如果你是苹果用户且热爱户外运动，Ultra 2是最佳选择。如果你是专业运动员或需要超长续航，佳明Fenix 8更适合。</p>"
)

declare -a COVERS=(
    "https://picsum.photos/seed/phone/800/400"
    ""
    "https://picsum.photos/seed/earbuds/800/400"
    "https://picsum.photos/seed/monitor/800/400"
    ""
    "https://picsum.photos/seed/smarthome/800/400"
    "https://picsum.photos/seed/ssd/800/400"
    ""
    "https://picsum.photos/seed/tablet/800/400"
    "https://picsum.photos/seed/powerbank/800/400"
    ""
    "https://picsum.photos/seed/speaker/800/400"
    ""
    "https://picsum.photos/seed/pc/800/400"
    "https://picsum.photos/seed/watch/800/400"
)

echo "开始发布数码测试帖子..."
echo "===================="

for i in {0..14}; do
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
echo "数码测试帖子发布完成!"
