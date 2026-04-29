/**
 * ============================================================================
 * RagService.java - RAG 微服务客户端（Java → Python 的桥梁）
 * ============================================================================
 *
 * 【学习引导 - 请按以下顺序阅读理解】：
 *
 * 📖 第一层：定位理解
 *    这个类是什么？→ 一个 Spring @Service，负责"跨语言调用"
 *    它不包含任何 AI 算法逻辑，只是通过 HTTP 把请求发给 Python，拿回结果
 *    类似于：你用 RestTemplate 调微信支付接口，这里调的是自己的 Python 服务
 *
 * 📖 第二层：数据流
 *    AiController.chatWithRAG() → RagService.search(question) 
 *      → [HTTP POST] → Python /rag/search → 返回 RagResponse
 *      → buildContextFromResponse() → 组装成字符串给 AiUtil → GLM API
 *
 * 📖 第三层：关键设计决策
 *    - 为什么用 RestTemplate？→ Spring 原生支持，简单直接
 *    - 为什么失败时返回空响应而非异常？→ 保证可用性：RAG 挂了也能走纯 LLM 模式
 *    - 为什么有 isHealthy() 方法？→ 启动时检查，决定是否展示"知识库模式"开关
 */

// package com.example.demo.rag;
//
// import com.fasterxml.jackson.annotation.JsonProperty;
// import lombok.Data;
// import lombok.AllArgsConstructor;
// import lombok.NoArgsConstructor;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.RestTemplate;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// import java.util.List;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.stream.Collectors;

/**
 * RAG 微服务客户端
 *
 * 【类比理解】这个类相当于你项目中的"AI工具类"(AiUtil)，
 * 只不过 AiUtil 调的是智谱 GLM 的外部 API，
 * 而 RagService 调的是你自己部署的 Python RAG 服务。
 *
 * 两者的共同点：
 *   ① 都是通过 HTTP 发送 JSON 请求
 *   ② 都需要处理超时、错误等异常情况
 *   ③ 都需要把返回结果转换为 Java 对象使用
 */
// @Service
// public class RagService {
//
//     private static final Logger log = LoggerFactory.getLogger(RagService.class);
//     
//     /**
//      * Python RAG 服务地址
//      *
//      * 【思考题】：为什么这里硬编码了？
//      * → 开发阶段够用。生产环境应该放到 application.yml 配置文件中，
//      *   用 @Value("${rag.service-url}") 注入，方便不同环境切换。
//      *
//      * 端口为什么是 8001？
//      * → 避免与你 Java 应用的 8080 端口冲突
//      */
//     private final String ragServiceUrl = "http://localhost:8001";
//     
//     /** Spring 提供的 HTTP 客户端模板类 */
//     private final RestTemplate restTemplate;
//
//     public RagService() {
//         this.restTemplate = new RestTemplate();
//     }
//
//     // ==========================================================================
//     // 【第1部分：数据传输对象 - 对应 Python 端的 Pydantic 模型】
//     // ==========================================================================
//
//     /**
//      * 【重要概念】Java DTO ↔ Python Pydantic 的对应关系
//      *
//      * Python 端定义:
//      *   class RagRequest(BaseModel):
//      *       question: str
//      *       top_k: int = 5
//      *
//      * Java 端必须完全匹配字段名和类型:
//      *   public class RagRequest {
//      *       private String question;    // ← 字段名必须一致！JSON 序列化靠名字匹配
//      *       private Integer topK;       // ← 注意: Python 的 top_k 变成 Java 的 topK（驼峰）
//      *   }
//      *
//      * ⚠️ 关键坑点：@JsonProperty("top_k") 注解的作用
//      *   Java 字段名用驼峰(topK)，但 JSON 里 Python 用下划线(top_k)
//      *   这个注解告诉 Jackson："序列化为 JSON 时用这个名字"
//      */
//
//     /**
//      * RAG 检索请求 - 发送给 Python 的数据格式
//      */
//     // @Data                          // Lombok 自动生成 getter/setter/toString/equals
//     // @AllArgsConstructor             // 全参构造函数
//     // @NoArgsConstructor              // 无参构造函数
//     // public static class RagRequest {
//     //     @JsonProperty("question")
//     //     private String question;      // 用户问题（必填）
//     //     
//     //     @JsonProperty("top_k")        // JSON 中用下划线命名
//     //     private Integer topK = 5;     // 返回文档数量（默认5条）
//     // }
//
//     /**
//      * 单个检索结果文档
//      */
//     // @Data
//     // @AllArgsConstructor
//     // @NoArgsConstructor
//     // public static class SourceDocument {
//     //     @JsonProperty("content")
//     //     private String content;       // 文档片段内容（给 LLM 读的）
//     //     
//     //     @JsonProperty("source")
//     //     private String source;        // 来源文件名（给用户看的引用标注）
//     //     
//     //     @JsonProperty("score")
//     //     private Double score;         // 相关度分数 0-1（调试/评估用的）
//     // }
//
//     /**
//      * RAG 检索响应 - 从 Python 接收的数据格式
//      */
//     // @Data
//     // @AllArgsConstructor
//     // @NoArgsConstructor
//     // public static class RagResponse {
//     //     @JsonProperty("contexts")
//     //     private List<SourceDocument> contexts;  // 检索到的文档列表
//     //     
//     //     @JsonProperty("total_found")
//     //     private Integer totalFound;             // 总共命中多少文档
//     //     
//     //     @JsonProperty("query_time_ms")
//     //     private Integer queryTimeMs;            // 检索耗时(毫秒)
//     // }
//
//     // ==========================================================================
//     // 【第2部分：核心方法 - 调用 RAG 服务】
//     // ==========================================================================
//
//     /**
//      * 调用 Python RAG 服务执行检索
//      *
//      * 【完整链路】
//      * 1. 构建 HTTP 请求体 (JSON)
//      * 2. 设置请求头 Content-Type: application/json
//      * 3. POST 到 http://localhost:8001/rag/search
//      * 4. 等待响应并反序列化为 RagResponse 对象
//      * 5. 记录日志 + 返回结果
//      *
//      * @param question 用户输入的问题
//      * @return RAG 检索结果（包含相关文档上下文），失败时返回空列表
//      */
//     // public RagResponse search(String question) {
//     //     return search(question, 5);  // 默认取前5条
//     // }
//
//     /**
//      * 调用 RAG 服务检索（指定返回数量）
//      *
//      * @param question 用户问题
//      * @param topK 返回文档数量（建议 3-10）
//      * @return RAG 检索响应对象
//      */
//     // public RagResponse search(String question, int topK) {
//     //     try {
//     //         String url = ragServiceUrl + "/rag/search";  // 拼接完整的接口路径
//     //         
//     //         // 构建请求对象（会被自动序列化为 JSON）
//     //         RagRequest request = new RagRequest(question, topK);
//     //         
//     //         // 设置 HTTP 请求头
//     //         HttpHeaders headers = new HttpHeaders();
//     //         headers.setContentType(MediaType.APPLICATION_JSON);  // 告诉对方：我发的是 JSON
//     //         
//     //         // 将 请求头 + 请求体 打包成 HttpEntity
//     //         HttpEntity<RagRequest> entity = new HttpEntity<>(request, headers);
//     //         
//     //         log.info("🔍 正在调用 RAG 服务检索: {}", question);
//     //         long startTime = System.currentTimeMillis();  // 计时开始
//     //         
//     //         /**
//     //          * postForObject 参数说明:
//     //          * - url: 请求地址
//     //          * - entity: 请求实体（包含 header 和 body）
//     //          * - RagResponse.class: 响应要反序列化的目标类型
//     //          *
//     //          * 底层发生了什么？
//     //          * 1. Jackson 把 RagRequest 序列化成 JSON 字符串
//     //          * 2. RestTemplate 发送 HTTP POST 请求
//     //          * 3. 收到响应后，Jackson 把 JSON 反序列化成 RagResponse 对象
//     //          * 4. 自动返回给你
//     //          */
//     //         RagResponse response = restTemplate.postForObject(url, entity, RagResponse.class);
//     //         
//     //         long elapsed = System.currentTimeMillis() - startTime;
//     //         log.info("✅ RAG 检索完成, 耗时 {}ms, 返回 {} 条结果", 
//     //                  elapsed, response != null ? response.getContexts().size() : 0);
//     //         
//     //         return response;
//     //         
//     //     } catch (Exception e) {
//     //         /**
//     //          * 【关键设计决策】为什么 catch 后返回空而不是抛异常？
//     //          *
//     //          * 场景分析：
//     //          * - 如果 RAG 服务挂了（网络不通/Python 进程崩溃）
//     //          * - 如果抛异常 → Controller 会返回 500 错误给前端 → 用户体验差
//     //          * - 如果返回空响应 → 可以降级为"纯LLM对话模式"继续工作
//     //          *
//     //          * 这就是软件工程中的 **优雅降级** 思想：
//     //          * 核心功能可用，增强功能不可用时自动关闭增强功能
//     //          */
//     //         log.error("❌ RAG 服务调用失败: {}", e.getMessage());
//     //         return new RagResponse(Collections.emptyList(), 0, 0);  // 安全降级
//     //     }
//     // }
//
//     // ==========================================================================
//     // 【第3部分：辅助方法 - 结果处理】
//     // ==========================================================================
//
//     /**
//      * 将 RAG 检索结果组装成上下文字符串
//      *
//      * 【用途】
//      * 这个方法的输出会拼接到 Prompt 中，作为 LLM 回答问题的参考资料
//      *
//      * 输入示例（RagResponse）:
//      *   contexts: [
//      *     {content: "年假规定...", source: "员工手册.pdf", score: 0.92},
//      *     {content: "请假流程...", source: "人事制度.pdf", score: 0.85}
//      *   ]
//      *
//      * 输出示例（String）:
//      *   "
//      *   【参考文档】
//      *   
//      *   --- 文档 1 (来源: 员工手册.pdf, 相关度: 0.92) ---
//      *   年假规定...
//      *   
//      *   --- 文档 2 (来源: 人事制度.pdf, 相关度: 0.85) ---
//      *   请假流程...
//      *   "
//      *
//      * @param response RAG 检索响应
//      * @return 格式化的上下文字符串（可直接拼接入 Prompt）
//      */
//     // public String buildContextFromResponse(RagResponse response) {
//     //     if (response == null || response.getContexts() == null || response.getContexts().isEmpty()) {
//     //         return "";  // 无结果时返回空字符串
//     //     }
//     //     
//     //     StringBuilder contextBuilder = new StringBuilder();
//     //     contextBuilder.append("\n\n【参考文档】\n");
//     //     
//     //     int i = 1;
//     //     for (SourceDocument doc : response.getContexts()) {
//     //         contextBuilder.append(String.format(
//     //             "\n--- 文档 %d (来源: %s, 相关度: %.2f) ---\n%s\n",
//     //             i,                    // 序号
//     //             doc.getSource(),     // 来源文件名
//     //             doc.getScore(),      // 相关度（保留两位小数）
//     //             doc.getContent()     // 文档内容
//     //         ));
//     //         i++;
//     //     }
//     //     
//     //     return contextBuilder.toString();
//     // }
//
//     /**
//      * 检查 RAG 服务是否可用（健康检查）
//      *
//      * 【使用场景】
//      * - 应用启动时调用此方法
//      * - 如果返回 true → 前端显示"知识库模式"开关
//      * - 如果返回 false → 隐藏该开关，只提供纯对话模式
//      *
//      * @return true=服务正常可用, false=服务不可用
//      */
//     // public boolean isHealthy() {
//     //     try {
//     //         String url = ragServiceUrl + "/rag/health";
//     //         Object result = restTemplate.getForObject(url, Object.class);
//     //         return result != null;  // 有响应就认为健康
//     //     } catch (Exception e) {
//     //         log.warn("⚠️  RAG 服务不可用: {}", e.getMessage());
//     //         return false;
//     //     }
//     // }
//
//     // ------------------------------------------------------------------
//     // 工具方法：提取纯内容列表
//     // ------------------------------------------------------------------
//     // public List<String> extractContents(RagResponse response) {
//     //     if (response == null || response.getContexts() == null) {
//     //         return Collections.emptyList();
//     //     }
//     //     return response.getContexts().stream()
//     //             .map(SourceDocument::getContent)
//     //             .collect(Collectors.toList());
//     // }
//
//     // ------------------------------------------------------------------
//     // 工具方法：提取去重的来源列表（用于页面底部显示"参考来源"标签）
//     // ------------------------------------------------------------------
//     // public List<String> extractSources(RagResponse response) {
//     //     if (response == null || response.getContexts() == null) {
//     //         return Collections.emptyList();
//     //     }
//     //     return response.getContexts().stream()
//     //             .map(SourceDocument::getSource)
//     //             .distinct()  // 去重（同一来源可能有多条匹配）
//     //             .collect(Collectors.toList());
//     // }
// // }
