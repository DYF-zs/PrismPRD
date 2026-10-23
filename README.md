# PrismPRD

本地单人使用的 AI PRD 工作台，支持文档编写、AI 修改与评审、版本管理、测试用例生成和知识库检索。

## 项目介绍

PrismPRD 将需求文档的编写、质量检查和交付串联在同一个工作台中。前端采用 React、TypeScript 和 TipTap，后端采用 Java / Spring Boot，PostgreSQL 保存业务数据；Spring AI 统一接入模型服务，Ollama / bge-m3 在本机生成知识向量。

### 主要功能

- **模型设置**：支持 DeepSeek、智谱 AI 和 OpenAI 兼容接口，在网页中管理平台、模型名称、API 地址和 API Key，保存多份配置并统一启用。对话、评审、改写、修复和测试用例生成共用当前模型，切换影响新任务，执行中的任务使用已捕获的配置。支持结构化输出与工具调用连接测试，历史评审记录实际使用的平台与模型。
- **AI 工作台**：首页直接描述需求开始对话，中央与 AI 讨论，右侧编辑 PRD 草稿；最近对话列表和草稿预览的宽度可拖动调整，并保存在当前浏览器。顶部导航可切换工作台、文档库和评审标准。
- **PRD 管理**：空白、模板或 AI 起草入口，文档列表、搜索、状态筛选和标题管理；首页和文档库可直接上传 Markdown、TXT、DOCX 创建 PRD。文档所有者可在文档库表格右侧或移动端卡片中永久删除文档及其版本、评审、对话和测试数据，共享知识库保留。
- **文档编辑**：富文本、多级标题、目录定位、自动保存，以及 Markdown、TXT、DOCX 导入。导入覆盖草稿前确认，保留历史内容可先创建版本。
- **文档阅读**：顶部切换阅读预览与编辑，左侧访问版本评审、测试用例和知识库；可折叠目录紧邻正文，AI 对话位于右侧。宽屏可拖动分界线调整各区宽度，双击恢复默认宽度，设置保存在当前浏览器。窄屏通过面板切换阅读、对话与工具，文档库以卡片展示。Mermaid 代码块支持图形预览与源码编辑。阅读预览中单击打开链接，编辑模式中使用 Ctrl/⌘ + 单击，支持正文内章节锚点。
- **AI 对话与修改**：每份 PRD 可维护多个对话；AI 结合正文与知识资料起草、澄清和补充内容，以补丁预览修改，由用户接受或拒绝。
- **选区 AI**：针对选中文字或段落提出改写、扩写、精简等要求，预览后确认应用。
- **质量评审**：在左侧「版本与评审」中选择固定版本，发起或再次评审，每次报告独立保存；再次评审默认沿用该版本上次使用的评审标准版本。展示总分、维度分数、雷达图、证据和改进建议，支持问题定位、处理状态和 AI 修复。所有者可永久删除单次评审，当前版本得分和状态按剩余成功记录更新，已应用的正文修改保留。删除操作需要确认，文档有评审或 AI 任务执行时需等待完成。内置标准覆盖 12 个维度，支持自定义评审标准、发布版本和历史追溯。
- **版本与导出**：选中版本查看对应评审，点击「使用此版本」确认恢复正文并设为草稿基线，展示该版本对应评分；其他版本保留。自动保存写入草稿，「创建版本」生成固定快照，版本号持续递增。支持文字对比、Markdown / PDF 导出；固定版本的标题与内容独立保存。所有者可在版本「更多」菜单永久删除版本及其评审、测试数据，已应用的正文修改保留。当前基线需先切换到其他版本再删除；唯一版本删除后保留正文并解除版本基线。文档有评审或 AI 任务执行时需等待完成后使用或删除版本。
- **测试用例与验收**：基于固定版本生成正常、边界、异常和安全场景的用例，分析可测试性，并将确认后的验收补充写入草稿。这些内容用于测试设计与验收规划。
- **知识库与 RAG**：创建或关联知识库，上传 Markdown、TXT、DOCX、文本型 PDF，查看索引状态、重新索引和检索来源。AI 对话与评审可引用有权限的资料片段。

典型流程：编写或导入 PRD → AI 完善 → 创建版本 → 评审与修复 → 创建交付版本 → 生成用例、导出文档。

## 环境准备

以下步骤适用于 Windows x64。PrismPRD 应用、PostgreSQL 和 Ollama 分别运行在三个 Docker 容器中，使用者按顺序完成安装与初始化。命令在项目根目录的 PowerShell 终端执行。

### 1. 安装软件

安装 [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/)，按安装说明启用 WSL 2 和硬件虚拟化，使用 Linux 容器。安装 [PowerShell 7](https://learn.microsoft.com/en-us/powershell/scripting/install/install-powershell-on-windows)，打开 Docker Desktop 并等待引擎就绪；运行 `docker version` 应显示 Client 和 Server 信息。

项目需要以下环境，分别由对应的 Docker 镜像提供：

| 依赖 | 用途与镜像 |
| --- | --- |
| Node.js 22.12+、npm | 构建前端，`node:22-bookworm-slim` |
| Java 21 JDK、Maven 3.9 | 构建后端，`maven:3.9-eclipse-temurin-21` |
| Java 21 JRE | 运行应用，`eclipse-temurin:21-jre-jammy` |
| PostgreSQL 16 | 保存业务数据，`postgres:16` |
| Ollama、bge-m3 | 生成 1024 维知识向量，`ollama/ollama` 和 [bge-m3](https://ollama.com/library/bge-m3) |

Spring Boot、Spring AI 等 Java 依赖由 Maven 根据 `backend/pom.xml` 下载；React、TipTap 等前端依赖由 npm 根据锁文件下载。Dockerfile 负责在上述镜像中编译项目。首次准备需要联网访问镜像仓库、Maven、npm 和模型下载服务，并预留数 GB 磁盘空间。AI 请求使用所配置平台的 API。

### 2. 填写配置

首次使用时创建 `.env`；已有配置时按示例核对并编辑文件。

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，填写随机生成的较长数据库密码 `PRISM_DB_PASSWORD`，其余项目按示例保留。配置采用 `KEY=value` 格式，值直接填写，注释独占一行。AI 平台和 API Key 在启动后的「模型设置」页面填写。

容器内数据库主机为 `prismprd-postgres`，Ollama 地址为 `http://prismprd-ollama:11434`；`localhost` 在容器内表示容器自身。`.env` 含数据库凭据，应私有保存。

### 3. 下载镜像并构建应用

```powershell
docker pull node:22-bookworm-slim
docker pull maven:3.9-eclipse-temurin-21
docker pull eclipse-temurin:21-jre-jammy
docker pull postgres:16
docker pull ollama/ollama
docker build -t prismprd:local .
docker network create prismprd
```

### 4. 创建数据库和模型服务

读取数据库配置并创建 [PostgreSQL 容器](https://hub.docker.com/_/postgres)：

```powershell
. .\scripts\dotenv.ps1
$settings = Read-PrismDotEnv -Path .\.env
docker run -d --name prismprd-postgres --network prismprd `
  -e "POSTGRES_DB=$($settings.PRISM_DB_NAME)" `
  -e "POSTGRES_USER=$($settings.PRISM_DB_USER)" `
  -e "POSTGRES_PASSWORD=$($settings.PRISM_DB_PASSWORD)" `
  -v prismprd-postgres-data:/var/lib/postgresql/data postgres:16
docker exec prismprd-postgres pg_isready -U $settings.PRISM_DB_USER -d $settings.PRISM_DB_NAME
```

等待最后一条命令显示 `accepting connections`，初始化期间可重复执行该命令。数据库名称、用户和密码在数据卷首次初始化时生效。

按 [Ollama 官方说明](https://docs.ollama.com/docker) 创建 CPU 模式容器并下载模型：

```powershell
docker run -d --name prismprd-ollama --network prismprd `
  -v prismprd-ollama-models:/root/.ollama ollama/ollama
docker exec prismprd-ollama ollama pull bge-m3
docker exec prismprd-ollama ollama list
```

确认模型列表中出现 `bge-m3`。模型保存在 Docker 数据卷中，后续启动可复用。

### 5. 创建应用容器

```powershell
docker create --name prismprd-app --network prismprd --restart on-failure:5 `
  --env-file .env --stop-timeout 60 -p 127.0.0.1:8080:8080 `
  -v prismprd-knowledge:/app/data/knowledge `
  -v prismprd-settings:/app/data/settings prismprd:local
```

应用启动失败时最多自动重试五次。数据库表结构由 Flyway 在应用启动时创建或迁移。

## 日常使用

先打开 Docker Desktop，随后启动三个已创建的容器：

```powershell
.\start.ps1
docker logs -f prismprd-app
```

日志出现 `Started PrismPrdApplication` 后，打开 `http://127.0.0.1:8080`。按 `Ctrl+C` 退出日志查看；应用继续运行。关闭服务：

```powershell
.\stop.ps1
```

首次进入页面，打开顶部「模型设置」，添加配置，填写平台 API Key 和模型标识。API 地址填写根路径：DeepSeek 为 `https://api.deepseek.com`，智谱为 `https://open.bigmodel.cn/api/paas`，OpenAI 为 `https://api.openai.com/v1`，兼容服务按其文档填写。容器访问本机 API 服务使用 `host.docker.internal`。支持 HTTPS 地址和显式本机 HTTP 地址；私有网络地址与重定向会被拦截。

测试连接会发送两条短请求，检查结构化输出与工具调用，可能产生 API 费用。首份配置保存后自动启用；其余配置通过「启用」切换，所有 AI 功能共用当前配置。编辑时 Key 留空保留原值，修改平台或地址后须重新填写 Key。已有 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` 和 `DEEPSEEK_BASE_URL` 配置可在首次初始化时导入为 DeepSeek 配置。

修改 `.env` 后，先关闭服务，执行 `docker rm prismprd-app`，再按第 5 步重新创建应用容器并启动。更新源码后先重新执行 `docker build -t prismprd:local .`，再重新创建应用容器。

启动问题可查看 `docker logs prismprd-app`、`docker logs prismprd-postgres` 和 `docker logs prismprd-ollama`。确认 `.env` 中的服务地址、数据库凭据，以及 `docker exec prismprd-ollama ollama list` 中的模型名称。

## 数据与使用范围

- 默认使用固定本地用户，适用于本机单实例运行。
- 文档、对话、版本、评审和模型配置保存在 `prismprd-postgres-data`，知识原文件保存在 `prismprd-knowledge`，向量模型保存在 `prismprd-ollama-models`，API Key 加密密钥保存在 `prismprd-settings` 的 `model.key` 文件中；关闭服务后保留数据。备份与迁移时，数据库、知识原文件和加密密钥配套保存；加密密钥缺失时须恢复文件或重新填写 API Key。
- 容器内数据库是独立实例，本机原生数据库的数据可通过备份恢复迁入。数据卷的删除会永久删除其中的数据。
- AI 请求会联网调用配置的模型服务；检索片段随请求发送，请按资料保密要求使用。
- AI 修改由用户确认后写入正文；知识文件支持 Markdown、TXT、DOCX 和文本型 PDF。
- DOCX 提取文字、基础标题、列表和表格；扫描型 PDF 可在 OCR 处理后作为参考资料上传。PDF 导出采用文档内容排版，外链图片显示说明与地址。
- 保存冲突和过期 AI 补丁会被拦截，页面保留用户输入供处理。评审及生成结果与其来源版本关联。
- Mermaid 图形在页面中渲染，Markdown / PDF 导出保留代码。图形采用统一安全配置，单图最多 20,000 字符、500 行、300 条边；正文可使用 HTTP、HTTPS 和邮件链接，相对链接按当前站点解析。页面建议使用近期版本的 Chrome、Edge、Firefox 或 Safari 17.4 及以上版本。

第三方来源与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
