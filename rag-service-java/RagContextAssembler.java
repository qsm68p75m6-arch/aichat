/**
 * ============================================================================
 * RagContextAssembler.java - RAG 上下文组装器（Prompt 拼接的核心逻辑）
 * ============================================================================
 *
 * 【学习引导 - 请按以下顺序阅读理解】：
 *
 * 📖 第一层：这个类的唯一职责
 *    它只做一件事：把「RAG 检索到的文档」+ 「用户的问题」组装成「完整的 Prompt」
 *    
 *    为什么单独抽成一个类？（单一职责原则）
 *    - RagService 负责"获取文档"
 *    - RagContextAssembler 负责"组装 Prompt"
 *    - AiUtil 负责"调用 LLM 生成回答"
 *    各司其职，修改其中一处不影响其他部分
 *
 * 📖 第二层：Prompt 结构理解
 *
 *    最终发给 LLM 的完整 Prompt 长这样：
 *
 *    ════════════════════════════════════════
 *    System Prompt (系统提示词):
 *      "你是一个智能问答助手。请根据参考文档回答..."
 *      
 *    User Prompt (用户消息):
 *      "【参考文档】
 *       --- 文档 1 ---
 *       （从 RAG 检索到的内容）
 *       
 *       ---
 *       用户问题：XXX"
 *    ════════════════════════════════════════
 *
 *    LLM 看到这个 Prompt 后：
 *    1. 先读参考文档（RAG 检索到的相关段落）
 *    2. 再看用户问题
 *    3. 基于文档内容来回答（减少幻觉/编造）
 *
 * 📖 第三层：关键设计考量
 *    - System Prompt 要强调"基于文档回答"，否则 LLM 可能忽略文档自由发挥
 *    - 如果没有检索到相关文档，要明确告知 LLM（避免它强行编造答案）
 *    - 引用来源信息也要传进去（让 LLM 能在回答中标注"根据XX文档..."）
 */

// package com.example.demo.rag;
//
// import com.example.demo.rag.RagService.RagResponse;
// import org.springframework.stereotype.Component;
//
// /**
//  * RAG 上下文组装器
//  *
//  * 【角色定位】连接"检索"和"生成"的桥梁
//  *
//  * 完整链路中的位置：
//  *   用户问题 → [RagService.search()] → 得到文档列表
//  *                                        ↓
//  *                              [RagContextAssembler.assemblePrompt()]
//  *                                        ↓
//  *                              得到完整的 systemPrompt + userPrompt
//  *                                        ↓
//  *                              [AiUtil.streamChat()] → 调 GLM API → 流式输出
//  */
// @Component
// public class RagContextAssembler {
//
//     /**
//      * ================================================================
//      * 第 1 部分：System Prompt 模板（固定不变的部分）
//      * ================================================================
//      *
//      * 【什么是 System Prompt？】
//      * 在 ChatGLM/GPT 等 LLM 中，每段对话有两个角色：
//      * - system: 设定 AI 的身份和行为规则（类似"人设"或"指令书"）
//      * - user: 用户实际发送的消息
//      *
//      * System Prompt 在一次会话中通常只设置一次（首条消息），
//      * 之后的所有 user 消息都会受其约束。
//      *
//      * 【为什么这里的 System Prompt 特别强调"不要编造"？】
//      * 这是 RAG 场景最大的风险——幻觉(Hallucination)！
//      *
//      * 例：用户问"公司的股票代码是多少？"
//      *   如果参考文档中没有这个信息：
//      *   - 没有 System Prompt 约束 → LLM 可能编造一个看起来合理的代码 ❌
//      *   - 有 System Prompt 约束 → LLM 会说"文档中未找到相关信息" ✅
//      *
//      * 你的评估指标中有"幻觉率 < 4%"的要求，System Prompt 是第一道防线。
//      */
//     // private static final String RAG_SYSTEM_PROMPT_TEMPLATE = """
//     //     你是一个智能问答助手。请根据【参考文档】中的信息回答用户的问题。
//     //
//     //     要求：
//     //     1. 回答必须基于参考文档的内容，不要编造信息
//     //     2. 如果参考文档中没有相关信息，请明确告知"文档中未找到相关内容"
//     //     3. 引用时请注明信息来源（如"根据XX文档..."）
//     //     4. 回答要简洁、准确、有条理
//     //     """;
//
//     /**
//      * ================================================================
//      * 第 2 部分：User Prompt 模板（每次请求动态变化的部分）
//      * ================================================================
//      *
//      * 【两个占位符 %s】
//      * - %s 第1处：插入 RAG 检索到的文档上下文（动态内容）
//      * - %s 第2处：插入用户的原始问题
//      *
//      * 【为什么用 String.format() 而不是直接拼接？】
//      * - 模板和变量分离，结构清晰
//      * - 方便后续调整格式（如改 Markdown 表格形式）
//      * - 与你的 PromptTemplate 数据库表配合（W3-4 任务：版本化管理）
//      */
//     // private static final String USER_PROMPT_WITH_CONTEXT_TEMPLATE = """
//     //     【参考文档】
//     //     %s
//     //
//     //     ---
//     //     
//     //     用户问题：%s
//     //     """;
//
//     /**
//      * ================================================================
//      * 第 3 部分：核心方法 - 组装完整 Prompt
//      * ================================================================
//      *
//      * 【输入】
//      *   - ragResponse: RAG 检索到的文档结果（可能为空！）
//      *   - userQuestion: 用户的原始问题
//      *
//      * 【输出】
//      *   - String[0]: systemPrompt（固定的行为规则）
//      *   - String[1]: userPrompt（包含上下文 + 问题）
//      *
//      * 【调用示例】
//      *   RagResponse result = ragService.search("年假政策");
//      *   String[] prompts = assembler.assemblePrompt(result, "年假政策");
//      *   // 然后把 prompts[0], prompts[1] 传给你的 AiUtil.streamChat()
//      */
//     // public String[] assemblePrompt(RagResponse ragResponse, String userQuestion) {
//     //     // Step 1: System Prompt 固定使用模板
//     //     String systemPrompt = RAG_SYSTEM_PROMPT_TEMPLATE.trim();
//     //     
//     //     // Step 2: 构建带上下文的 User Prompt
//     //     String contextText = buildContextSection(ragResponse);
//     //     String userPrompt = String.format(USER_PROMPT_WITH_CONTEXT_TEMPLATE, 
//     //                                       contextText, userQuestion);
//     //     
//     //     return new String[]{systemPrompt, userPrompt.trim()};
//     // }
//
//     /**
//      * ================================================================
//      * 第 4 部分：构建上下文区域（内部方法）
//      * ================================================================
//      *
//      * 【处理边界情况】
//      * - ragResponse 为 null → 返回"暂无相关参考文档"
//      * - contexts 列表为空 → 同上
//      * - 正常情况 → 遍历每个 SourceDocument 格式化输出
//      *
//      * 【格式化输出的意义】
//      * 不是简单地把 content 拼一起，而是加上序号、来源、相关度等信息，
//      * 这样 LLM 可以：
//      *   ① 区分多个文档的边界
//      *   ② 知道哪个文档更可信（score 高的优先参考）
//      *   ③ 在回答时能正确引用来源
//      */
//     // private String buildContextSection(RagResponse ragResponse) {
//     //     if (ragResponse == null || ragResponse.getContexts() == null 
//     //             || ragResponse.getContexts().isEmpty()) {
//     //         return "（暂无相关参考文档）";
//     //         /**
//     //          * 【思考】为什么要显式告知"无参考文档"而不是留空？
//     //          * 如果留空，LLM 不知道是没有文档还是忘了放文档
//     //          * 明确告知后，LLM 可以基于自身知识回答（但需注明非来自参考文档）
//     //          */
//     //     }
//     //
//     //     StringBuilder sb = new StringBuilder();
//     //     int index = 1;
//     //     for (RagService.SourceDocument doc : ragResponse.getContexts()) {
//     //         sb.append(String.format(
//     //             "\n【文档 %d】(来源: %s | 相关度: %.1f%%)\n%s\n",
//     //             index,                           // 序号（从1开始）
//     //             doc.getSource(),                // 来源文件名
//     //             doc.getScore() * 100,            // 百分比形式（更直观）
//     //             doc.getContent()                 // 文档正文
//     //         ));
//     //         index++;
//     //     }
//     //
//     //     return sb.toString().trim();
//     // }
//
//     /**
//      * ================================================================
//      * 第 5 部分：生成引用来源说明（附加到回答末尾）
//      * ================================================================
//      *
//      * 【用途】
//      * 当 LLM 的流式回答全部结束后，
//      * 在末尾追加一行引用来源，让用户知道答案来自哪些文件。
//      *
//      * 效果示例：
//      *   "...（LLM 的回答内容）...
//      *   
//      *   ---
//      *   📚 参考来源: 员工手册.pdf、人事制度.pdf（共检索 12 条相关文档）"
//      *
//      * 【为什么不在 System Prompt 中让 LLM 自己加引用？】
//      * 因为流式输出时不好控制格式，而且 LLM 可能遗漏或编造来源。
//      * 由我们在代码层面追加更可靠（基于真实的检索结果）。
//      *
//      * @param ragResponse RAG 检索结果
//      * @return 引用说明字符串（无结果时返回空串）
//      */
//     // public String buildCitation(RagResponse ragResponse) {
//     //     if (ragResponse == null || ragResponse.getContexts() == null 
//     //             || ragResponse.getContexts().isEmpty()) {
//     //         return "";  // 无引用信息时不追加任何内容
//     //     }
//     //
//     //     StringBuilder sb = new StringBuilder("\n\n---\n📚 参考来源: ");
//     //     
//     //     /**
//     //      * distinct() 去重：
//     //      * 同一个 PDF 可能有 3 个段落被检索到（3 个不同的 chunk）
//     //      * 但来源文件名都是"员工手册.pdf"
//     //      * 显示时只需出现一次
//     //      */
//     //     var sources = ragResponse.getContexts().stream()
//     //             .map(RagService.SourceDocument::getSource)
//     //             .distinct()  // 去重
//     //             .toList();
//     //
//     //     sb.append(String.join("、", sources));  // 用中文顿号连接
//     //     sb.append(String.format(" (共检索 %d 条相关文档)", ragResponse.getTotalFound()));
//     //     
//     //     return sb.toString();
//     // }
// // }
