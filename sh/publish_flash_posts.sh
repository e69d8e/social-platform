#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ3YW5nd3UiLCJqdGkiOiIzZDEyZDc3OS0xNGE0LTQ1NDItYmNmMi01ZjY2ZjgxMGI5YjEiLCJpYXQiOjE3ODE3NjA5ODgsImV4cCI6MTc4MTg0NzM4OH0.wJUQY6s9bn3KpRYipsFtjn1j2qHlrXhZaxj2PAIdGObcCpfNRV--YPIBYqtA4CMR1Do9t-XHr0CEDBvYgErZbA"
BASE_URL="http://localhost:8081"
CATEGORY_ID=7

declare -a TITLES=(
    "安卓手机刷机入门：从解锁Bootloader开始"
    "小米手机刷机完全教程：MIUI到第三方ROM"
    "三星手机刷机指南：Odin工具详细使用教程"
    "华为手机解锁Bootloader与刷机全流程"
    "一加手机刷机教程：氧OS与氢OS互刷"
    "OPPO/Realme手机刷机：深度测试与刷机步骤"
    "自定义Recovery安装教程：TWRP详解"
    "Magisk Root教程：获取安卓手机最高权限"
    "刷机变砖怎么办？救砖教程大全"
    "iPhone刷机降级教程：从SHSH备份到OTA延迟升级"
)

declare -a CONTENTS=(
    "<p>刷机的第一步是解锁Bootloader。不同品牌的解锁方式不同：小米需要申请解锁码并等待7天，一加可以直接在开发者选项中解锁，华为已关闭官方解锁通道。</p><p>解锁前务必备份数据，因为解锁过程会清除手机所有数据。同时要注意，解锁可能会影响保修，部分银行App在解锁后无法使用。</p>"
    "<p>小米手机刷机相对简单。首先在官网申请解锁权限，绑定账号等待7天后使用小米解锁工具解锁。然后刷入第三方Recovery（推荐TWRP），通过TWRP刷入第三方ROM。</p><p>推荐ROM包括：Pixel Experience（类原生）、LineageOS（稳定流畅）、crDroid（功能丰富）。刷机前务必备份数据和当前系统。</p>"
    "<p>三星手机刷机主要使用Odin工具。下载对应机型的固件包（从SamFw或Frija下载），手机进入Download模式（音量下+电源键），连接电脑后在Odin中选择固件文件刷入。</p><p>注意事项：刷机会清除数据，确保电量充足，不要中断刷机过程。国行和国际版固件不通用，刷错可能导致变砖。</p>"
    "<p>华为手机目前已关闭官方Bootloader解锁通道，老款机型可以通过第三方工具（如华为解锁码计算器）获取解锁码。解锁后可以刷入第三方Recovery和ROM。</p><p>新款华为手机由于鸿蒙系统的限制，刷机选择较少。建议关注XDA论坛和花粉俱乐部获取最新的刷机资源和教程。</p>"
    "<p>一加手机是刷机爱好者的首选品牌之一。官方支持解锁Bootloader，开发者社区活跃，第三方ROM资源丰富。</p><p>氧OS（国际版）和氢OS（国内版）可以互刷，但需要注意基带版本匹配。刷入方法：解锁BL → 刷入TWRP → 刷入ROM包 → 刷入GApps（如需要）。</p>"
    "<p>OPPO和Realme手机刷机需要先申请深度测试权限。在设置中开启开发者选项，提交深度测试申请，审核通过后可以解锁Bootloader。</p><p>解锁后使用Fastboot命令刷入第三方Recovery，然后通过Recovery刷入ROM。注意部分机型的深度测试名额有限，需要抢名额。</p>"
    "<p>TWRP（Team Win Recovery Project）是最流行的第三方Recovery。它支持触屏操作、备份恢复、刷入ZIP包、文件管理等功能。</p><p>安装方法：下载对应机型的TWRP镜像，通过Fastboot命令刷入（fastboot flash recovery twrp.img）。首次进入TWRP建议先做一次完整备份。</p>"
    "<p>Magisk是目前最主流的Root解决方案。它采用Systemless方式获取Root权限，不会修改系统分区，支持SafetyNet检测绕过。</p><p>安装步骤：解锁BL → 刷入TWRP → 在TWRP中刷入Magisk ZIP → 重启后安装Magisk App。通过Magisk模块可以实现各种系统级功能扩展。</p>"
    "<p>刷机变砖分为软砖和硬砖两种。软砖（卡Logo、无限重启）可以通过进入Recovery模式或Fastboot模式修复。硬砖（完全无响应）需要使用深度刷机工具或售后维修。</p><p>常见救砖方法：小米用MiFlash线刷官方固件，三星用Odin刷入官方包，高通9008模式可以救大部分砖机。刷机前做好备份是防砖的最佳策略。</p>"
    "<p>iPhone刷机降级需要SHSH2 blobs文件。使用tsschecker工具保存当前版本的SHSH2，日后可以通过FutureRestore工具降级到该版本。</p><p>OTA延迟升级是另一种玩法：安装描述文件后不立即升级，等苹果关闭验证后再升级，这样可以保留降级的可能性。注意iOS 16以上版本的降级限制较多。</p>"
)

declare -a COVERS=(
    ""
    "https://picsum.photos/seed/xiaomi/800/400"
    "https://picsum.photos/seed/samsung/800/400"
    ""
    "https://picsum.photos/seed/oneplus/800/400"
    "https://picsum.photos/seed/oppo/800/400"
    ""
    "https://picsum.photos/seed/magisk/800/400"
    "https://picsum.photos/seed/brick/800/400"
    ""
)

echo "开始发布刷机教程帖子..."
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
echo "刷机教程帖子发布完成!"
