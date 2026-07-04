pipeline {
    agent any

    environment {
        // 镜像名称
        IMAGE_NAME = 'social-platform'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        // 从 Jenkins Credentials 中读取敏感信息
        PASSWORD        = credentials('sp-password')
        DASH_SCOPE_API_KEY = credentials('sp-dashscope-api-key')
        IMAGE_PATH      = '/usr/local/nginx/html/imgs'
        NGINX_DIR       = '/usr/local/nginx'
    }

    options {
        timestamps()                        // 控制台输出带时间戳
        timeout(time: 30, unit: 'MINUTES')  // 整体超时 30 分钟
        disableConcurrentBuilds()           // 禁止并行构建
    }

    // 注意：Jenkins 服务器需预装 JDK 21 和 Docker
    // Maven 构建使用项目自带的 ./mvnw wrapper，无需额外安装

    stages {
        stage('检出代码') {
            steps {
                git url: 'git@github.com:e69d8e/social-platform.git',
                    branch: 'main',
                    credentialsId: 'sp-github-ssh-key'
            }
        }

        stage('Maven 构建') {
            steps {
                sh './mvnw clean package -DskipTests -B'
            }
        }

        stage('单元测试') {
            steps {
                sh './mvnw test -B'
            }
            post {
                always {
                    // 收集测试报告
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('构建 Docker 镜像') {
            steps {
                sh """
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest
                """
            }
        }

        stage('部署') {
            when {
                branch 'main'   // 仅 main 分支触发部署
            }
            steps {
                sh '''
                    # 从 Jenkins Credentials 生成 .env 文件（不入库）
                    cat > .env <<EOF
PASSWORD=${PASSWORD}
DASH_SCOPE_API_KEY=${DASH_SCOPE_API_KEY}
IMAGE_PATH=${IMAGE_PATH}
NGINX_DIR=${NGINX_DIR}
CORS_ORIGIN=http://127.0.0.1:5173
EOF

                    # 停止旧容器（保留基础设施服务）
                    docker compose stop sp-app 2>/dev/null || true
                    docker compose rm -f sp-app 2>/dev/null || true

                    # 使用新镜像启动应用（依赖服务保持运行）
                    docker compose up -d sp-app
                '''
            }
        }
    }

    post {
        success {
            echo "✅ 构建成功: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            echo "❌ 构建失败，请检查日志"
        }
        always {
            // 清理构建过程中产生的悬空镜像
            sh 'docker image prune -f 2>/dev/null || true'
        }
    }
}
