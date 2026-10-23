# 第三方声明

PrismPRD 的 Agent 框架、Spring AI 模型客户端注册设计及知识导入、分块、Ollama 向量化和检索工具流程基于 JChatMind 组件实现。相关代码遵循以下许可证。

## JChatMind

MIT License

Copyright (c) 2025 程序员Carl

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## 字体与其他依赖

Mermaid 12.2.0 用于文档图形预览，采用 MIT License，版权归 Knut Sveidqvist 所有。许可证随前端资源保存在 `ui/public/licenses/mermaid-LICENSE.txt`。

PDF 字体采用 Noto Sans SC 和 Noto Emoji，许可为 SIL Open Font License 1.1。字体许可、官方来源和哈希位于 `backend/src/main/resources/fonts/`。

后端依赖及版本由 `backend/pom.xml` 定义，前端依赖及版本由 `ui/package.json` 和 `ui/package-lock.json` 定义。构建和分发时应保留各依赖提供的 LICENSE 与 NOTICE。
