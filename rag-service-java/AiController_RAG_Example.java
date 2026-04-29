/**
 * ============================================================================
 * AiController_RAG_Example.java - Controller 层新增 RAG 接口示例
 * ============================================================================
 *
 * 【学习引导 - 请按以下顺序阅读理解】：
 *
 * 📖 第一层：这个文件是什么？
 *    这不是完整的 AiController.java 文件！
 *    而是你需要在**现有**的 AiController.java 中**添加/修改**的代码片段。
 *
 * 📖 第二层：接口在整体架构中的位置
 *
 *    前端 (Vue/React)
 *       │
 *       │ POST /api/chat/rag-stream  {question: "..."}
 *       ▼
 *    ┌─────────────────────┐
 *    │  AiController       │  ← 你要修改的这个类
 *    │    ├─ chatStream()  │     已有：纯 LLM 对话
 *    │    └─ chatWithRAG()│ ← 新增：RAG 增强对话（本文件内容）
 *    └────────┬────────────┘
 *             │
 *             │ [内部调用]
 *             ├──────────────────┬──────────────────┐
 *             ▼                  ▼                  ▼
 *      RagService.search()   RagContextAssembler   AiUtil.streamChat()
 *      (调用Python获取文档)   (组装Prompt)        (调GLM生成回答)
 *             │                  │                  │
 *             └──────────────────┴──────────────────┘
 *                                │
 *                                ▼
 *                    SseEmitter 流式返回给前端
 *
 * 📖 第三层：关键设计决策（与现有 chatStream 的对比）
 *
 * | 维度 | chatStream (已有) | chatWithRAG (新增) |
 * |------|------------------|---------------------|
 * | 流程 | question → GLM API → 返回 | question → RAG检索 → 组装Prompt → GLM → 返回 |
 * | 复杂度 | 简单（1步） | 较多（3步） |
 * | 回答质量 | 依赖模型自身知识 | 基于企业文档，更精准 |
 * | 适用场景 | 闲聊、通用问答 | 业务知识查询 |
 * | 降级策略 | 无 | RAG失败时自动降级为纯LLM模式 |
 *
 * 📖 第四层：你需要做的具体操作
 *    ① 打开你现有的 AiController.java
 *    ② 添加 @Autowired 的 RagService 和 RagContextAssembler 字段
 *    ③ 复制 chatWithRAG 方法到你的 Controller 类中
 *    ④ 复制 RagChatRequest 内部类
 *    ⑤ 根据你的实际包名/方法名做适配调整
 */

/**
 * ============================================================================
 * 操作步骤 1: 在 AiController 中添加依赖注入
 * ============================================================================
 *
 * 在你现有的 AiController 类中，添加这两个字段注入：
 *
 * （找到你现有的 @Autowired 字段附近，添加以下两行）
 */
//
// @Autowired
// private RagService ragService;              // RAG 服务客户端（调用 Python）
//
// @Autowired
// private RagContextAssembler ragContextAssembler;  // Prompt 组装器

/**
 * ============================================================================
 * 操作步骤 2: 新增 RAG 对话接口方法
 * ============================================================================
 *
 * 将下面的方法复制到你的 AiController 类中
 * 注意根据你的实际情况调整：
 *   - 包名、import 路径
 *   - 端点路径（/api/chat/rag-stream 可自定义）
 *   - SseEmitter 的超时时间（当前设为 120 秒）
 *   - 注释中提到的 streamChatToEmitter 方法名（替换为你实际的流式输出方法）
 */
//
// /**
//  * RAG 增强对话接口（新增）
//  *
//  * 【完整数据流】
//  *
//  *  ┌──────────┐     1.用户问题      ┌──────────────┐
//  *  │  前端     │ ─────────────────► │ AiController  │
//  *  │          │                    │ .chatWithRAG()│
//  *  └──────────┘                    └──────┬───────┘
//  *                                         │
//  *                          2.HTTP POST    │
//  *                          {question}     ▼
//  *                                    ┌──────────────┐
//  *                                    │  Python RAG   │
//  *                                    │  /rag/search  │
//  *                                    └──────┬───────┘
//  *                                           │
//  *                           3.{contexts}    │
//  *                           {sources}       │
//  *                           {scores}        ▼
//  *                                    ┌────────────────────┐
//  *                                    │ RagContextAssembler  │
//  *                                    │ .assemblePrompt()   │
//  *                                    └──────┬─────────────┘
//  *                                           │
//  *                         4.systemPrompt + userPrompt(含上下文)
//  *                                           ▼
//  *                                    ┌──────────────┐
//  *                                    │  AiUtil       │
//  *                                    │  .streamChat()│
//  *                                    └──────┬───────┘
//  *                                           │
//  *                         5.SSE stream (LLM生成的文字)
//  *                                           ▼
//  *                                    ┌──────────────┐
//  *                                    │    前端       │
//  *                                    │  渲染显示     │
//  *                                    └──────────────┘
//  *
//  * @param request 包含用户问题和会话ID
//  * @return SseEmitter 流式响应对象
//  */
// @PostMapping(value = "/chat/rag-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
// public SseEmitter chatWithRAG(@RequestBody RagChatRequest request) {
//     log.info("🤖 收到 RAG 对话请求: sessionId={}, question={}", 
//              request.getSessionId(), request.getQuestion());
//
//     /**
//      * 【SseEmitter 是什么？】
//      * Server-Sent Events emitter = 服务端推送事件发射器
//      *
//      * 这是 Spring 提供的 SSE 流式响应工具类，
//      * 你在现有项目中应该已经用过（chatStream 方法）。
//      *
//      * 工作原理：
//      * 1. 创建一个 SseEmitter 对象并返回给前端（HTTP 连接保持打开）
//      * 2. 在异步线程中逐步发送事件（emitter.send()）
//      * 3. 全部完成后调用 emitter.complete()
//      *
//      * 为什么用异步线程池？
//      * → 因为 RAG 检索 + LLM 生成可能需要 10-60 秒
//      * → 如果在 Tomcat IO 线程中执行，会阻塞线程导致无法处理其他请求
//      * → 你项目中的 aiTaskExecutor 正是为了解决这个问题设计的！
//      */
//     SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时
//
//     // 使用已有的专用线程池执行耗时操作
//     aiTaskExecutor.execute(() -> {
//         try {
//             
//             // ============================================================
//             // 步骤 1: 调用 Python RAG 服务进行文档检索
//             // ============================================================
//             /**
//              * 【这里就是 Java → Python 跨语言调用的核心位置】
//              *
//              * 内部发生的事：
//              * 1. RestTemplate 发送 HTTP POST 到 localhost:8001/rag/search
//              * 2. Python FastAPI 收到请求，执行向量+BM25混合检索
//              * 3. 返回 JSON 格式的文档列表
//              * 4. Jackson 自动反序列化为 RagResponse Java 对象
//              *
//              * top_k=5 表示：返回最相关的 5 个文档片段
//              * 这个值可以根据场景调整：
//              *   - 短问答（如"几点下班"）：top_k=3 足够
//              *   - 长分析（如"总结年度政策变化"）：top_k=8-10 更全面
//              */
//             RagService.RagResponse ragResult = ragService.search(request.getQuestion(), 5);
//
//             // ============================================================
//             // 步骤 2: 将检索结果组装成完整的 Prompt
//             // ============================================================
//             /**
//              * 【组装后的 Prompt 长什么样？】
//              *
//              * System:
//              * "你是一个智能问答助手。请根据【参考文档】中的信息回答..."
//              *
//              * User:
//              * "【参考文档】
//              *  【文档 1】(来源: 员工手册.pdf | 相关度: 92.3%)
//              *  入职满一年员工享有5天年假...
//              *
//              *  ---
//              *  用户问题：年假政策是怎样的？"
//              *
//              * LLM 收到这样的 Prompt 后，就会基于参考文档来回答，
//              * 而不是基于它训练数据中的通用知识（可能过时或错误）。
//              */
//             String[] prompts = ragContextAssembler.assemblePrompt(ragResult, request.getQuestion());
//             String systemPrompt = prompts[0];
//             String userPromptWithCtx = prompts[1];
//
//             // ============================================================
//             // 步骤 3: 向前端发送检索状态（可选的用户体验优化）
//             // ============================================================
//             /**
//              * 【为什么要单独发一个状态事件？】
//              *
//              * 用户视角的时间线：
//              * T=0s   点击发送按钮
//              * T=0.1s 看到"正在检索知识库..."（就是这个事件）
//              * T=1s   开始显示 LLM 生成的回答文字（逐字出现）
//              * T=10s  回答完成 + 底部显示引用来源
//              *
//              * 如果没有这个状态提示：
//              * 用户在 T=0~T=1s 会看到空白，以为卡死了
//              * 加上后用户体验更好（类似 ChatGPT 的"思考中..."提示）
//              */
//             emitter.send(SseEmitter.event()
//                     .name("rag_status")           // 自定义事件名（前端用 event.source 匹配）
//                     .data("{\"status\":\"retrieved\", \"doc_count\":" 
//                           + (ragResult != null ? ragResult.getContexts().size() : 0) + "}"));
//
//             // ============================================================
//             // 步骤 4: 调用 GLM API 生成流式回答（复用现有逻辑）
//             // ============================================================
//             /**
//              * 【重要！这里需要调用你已有的流式输出方法】
//              *
//              * 你的 AiController 中应该已经有类似的方法：
//              *   private void streamChatToEmitter(SseEmitter emitter, String systemPrompt, 
//              *                                     String userMessage, String sessionId)
//              *
//              * 这里直接复用它即可！唯一的区别是：
//              *   - 原来: userMessage = 用户原始问题
//              *   - 现在: userMessage = 含有参考文档上下文的问题（userPromptWithCtx）
//              *
//              * 取消下面这行的注释，并把方法名改为你实际的
//              */
//             // streamChatToEmitter(emitter, systemPrompt, userPromptWithCtx, request.getSessionId());
//
//             // ============================================================
//             // 步骤 5: 回答结束后追加引用来源
//             // ============================================================
//             /**
//              * 【为什么在最后追加而不是让 LLM 自己生成引用？】
//              *
//              * 1. LLM 可能遗漏引用（尤其长回答时）
//              * 2. LLM 可能编造不存在的文档名（幻觉问题）
//              * 3. 我们基于真实的检索结果生成引用，100%准确可靠
//              *
//              * 效果示例：
//              * "......（LLM 完整回答）"
//              *
//              * ---
//              * 📚 参考来源: 员工手册.pdf、人事制度.pdf (共检索 12 条相关文档)
//              */
//             String citation = ragContextAssembler.buildCitation(ragResult);
//             if (!citation.isEmpty()) {
//                 emitter.send(SseEmitter.event()
//                         .data(citation));
//             }
//
//             // 标记流式响应结束
//             emitter.complete();
//
//         } catch (Exception e) {
//             log.error("❌ RAG 对话处理异常: {}", e.getMessage(), e);
//             try {
//                 /**
//                  * 【错误处理策略：通知前端出错了】
//                  *
//                  * 通过自定义 error 事件告知前端，
//                  * 前端可以据此显示友好的错误提示（而非白屏或卡死）
//                  */
//                 emitter.send(SseEmitter.event()
//                         .name("error")
//                         .data("{\"error\":\"RAG 处理失败: " + e.getMessage() + "\"}"));
//                 emitter.completeWithError(e);
//             } catch (Exception ex) {
//                 emitter.completeWithError(ex);
//             }
//         }
//     });
//
//     return emitter;
// }

/**
 * ============================================================================
 * 操作步骤 3: 新增请求 DTO（内部类或独立文件均可）
 * ============================================================================
 *
 * 这个 DTO 定义了前端调用 RAG 接口时需要传的参数格式
 *
 * 【对比你现有的聊天请求 DTO】
 * 现有的可能只有: { message, sessionId }
 * RAG 接口增加了: useRag 开关字段（允许前端切换普通模式和知识库模式）
 */
//
// @Data
// @AllArgsConstructor
// @NoArgsConstructor
// public static class RagChatRequest {
//     
//     /** 用户的问题（必填，不能为空） */
//     @NotBlank(message = "问题不能为空")
//     private String question;
//     
//     /** 
//      * 会话ID（用于多轮对话历史管理）
//      * 
//      * 【思考题】RAG 场景下还需要多轮对话吗？
//      * 需要！例如：
//      *   第1轮: "公司年假怎么算？" → RAG检索 + LLM回答
//      *   第2轮: "那病假呢？"     → 应该结合第1轮的上下文
//      *   
//      * 但注意：RAG检索通常只针对当轮问题，
//      * 历史上下文主要通过 LLM 自身的 memory/session 机制处理
//      */
//     private String sessionId;
//     
//     /** 
//      * 是否启用 RAG 模式（默认开启）
//      * 
//      * 【用途】
//      * 前端可以提供一个切换按钮："📚 知识库模式" ON/OFF
//      * OFF 时走原有的纯 LLM 对话流程（chatStream）
//      * ON 时走 RAG 增强流程（chatWithRAG）
//      * 
//      * 这样用户可以自由选择是否使用知识库检索
//      */
//     private Boolean useRag = true;  
// }

/**
 * ============================================================================
 * 补充：前端如何区分普通模式和 RAG 模式？
 * ============================================================================
 *
 * 方案 A（推荐）：两个独立的接口
 *   普通: POST /api/chat/stream        → 调用 chatStream()
 *   RAG:   POST /api/chat/rag-stream    → 调用 chatWithRAG()
 *   → 前端根据用户选择的模式调用不同接口
 *
 * 方案 B：统一接口 + 参数判断
 *   POST /api/chat/stream
 *   { message: "...", useRag: true/false }
 *   → Controller 内部判断 useRag 决定走哪条逻辑
 *   → 优点: 前端简单；缺点: 接口职责不够单一
 *
 * 本示例采用方案 A，更清晰且易于维护
 */
