#!/bin/bash

TOKEN="Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJsaSIsImp0aSI6IjcxNmNhMmNlLWE4ZTQtNDRjZS1iZTQ4LWU3ZTdhOTNhMmFiNCIsImlhdCI6MTc4MTc1OTg5NCwiZXhwIjoxNzgxODQ2Mjk0fQ.aq7lZBl-jOak9EIri3QmA4WoJ4Y9yf0avmd4MbTjNYy7mRJuB_zidxV44T6VOMFwbWUe60Ov6GOihWEqSxA6IA"
BASE_URL="http://localhost:8081"
CATEGORY_ID=3

declare -a TITLES=(
    "大语言模型的涌现能力：从GPT到Claude的演进"
    "AI绘画工具对比：Midjourney vs Stable Diffusion"
    "机器学习工程师必备的10个Python库"
    "Transformer架构详解：注意力机制的革命"
    "AI在医疗影像诊断中的应用与挑战"
    "强化学习在游戏AI中的突破性进展"
    "自然语言处理2024年技术盘点"
    "边缘AI：让智能走进终端设备"
    "AI伦理：我们需要什么样的人工智能？"
    "从零开始搭建你的第一个神经网络"
)

declare -a CONTENTS=(
    "<p>大语言模型展现出的涌现能力一直是AI领域最令人惊叹的现象。当模型参数达到一定规模后，会突然具备之前不存在的能力，如逻辑推理、代码生成、数学计算等。</p><p>从GPT-3到GPT-4，再到Claude 3，每一代模型都在涌现能力上带来了新的惊喜。研究人员正在努力理解这一现象背后的机制。</p>"
    "<p>AI绘画工具已经成为设计师和创作者的得力助手。本文对比两款最受欢迎的AI绘画工具：Midjourney和Stable Diffusion。</p><p>Midjourney以其出色的美学风格著称，生成的图像艺术感强；Stable Diffusion则以开源、可定制化为优势，用户可以在本地部署并进行深度定制。</p>"
    "<p>对于机器学习工程师来说，选择合适的工具库至关重要。本文精选了10个最实用的Python库：NumPy、Pandas、Scikit-learn、TensorFlow、PyTorch、XGBoost、LightGBM、Hugging Face Transformers、MLflow和Optuna。</p><p>掌握这些库将大幅提升你的机器学习开发效率。</p>"
    "<p>2017年Google提出的Transformer架构彻底改变了深度学习的格局。其核心的自注意力机制允许模型捕捉序列中任意位置之间的依赖关系，解决了RNN的长距离依赖问题。</p><p>本文将深入解析Transformer的编码器-解码器结构、多头注意力机制、位置编码等核心概念。</p>"
    "<p>AI在医疗影像诊断领域展现出巨大潜力。从肺部CT扫描的结节检测到眼底图像的病变识别，AI系统在某些任务上已经达到甚至超过了专业医生的水平。</p><p>但数据隐私、模型可解释性、监管合规等问题仍是AI医疗应用面临的主要挑战。</p>"
    "<p>强化学习在游戏AI领域取得了令人瞩目的成就。从AlphaGo击败围棋世界冠军，到OpenAI Five在Dota 2中战胜职业战队，AI在复杂博弈游戏中不断突破人类极限。</p><p>最新研究将强化学习应用于更复杂的战略游戏和即时策略游戏，推动了通用游戏AI的发展。</p>"
    "<p>2024年自然语言处理领域继续高速发展。大语言模型的能力持续提升，多语言支持更加完善，检索增强生成（RAG）技术日益成熟。</p><p>值得关注的趋势包括：更长的上下文窗口、更准确的指令遵循能力、以及在专业领域（法律、医疗、金融）的深度应用。</p>"
    "<p>边缘AI是将人工智能算法部署在终端设备上的技术。相比云端AI，边缘AI具有低延迟、高隐私、低带宽消耗等优势。</p><p>随着专用AI芯片的发展，智能手机、智能摄像头、工业传感器等设备都能运行复杂的AI模型，实现真正的分布式智能。</p>"
    "<p>随着AI技术的快速发展，AI伦理问题日益受到关注。算法偏见、数据隐私、就业影响、自主武器等问题引发了广泛的社会讨论。</p><p>我们需要建立完善的AI治理框架，确保人工智能技术的发展符合人类的整体利益，在创新与责任之间找到平衡。</p>"
    "<p>本文将带你从零开始搭建一个简单的神经网络。我们将使用Python和NumPy，不依赖任何深度学习框架，手动实现前向传播、反向传播、梯度下降等核心算法。</p><p>通过这个过程，你将深入理解神经网络的工作原理，为后续学习更复杂的模型打下坚实基础。</p>"
)

declare -a COVERS=(
    "https://picsum.photos/seed/llm/800/400"
    ""
    "https://picsum.photos/seed/python/800/400"
    "https://picsum.photos/seed/transformer/800/400"
    ""
    "https://picsum.photos/seed/rl/800/400"
    ""
    "https://picsum.photos/seed/edge/800/400"
    "https://picsum.photos/seed/ethics/800/400"
    ""
)

echo "开始发布AI测试帖子..."
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
echo "AI测试帖子发布完成!"
