"""
================================================================================
RAG 微服务 - 独立 Python FastAPI 项目（完整参考模板）
================================================================================

【学习引导 - 请按以下顺序阅读理解】：

📖 第一层：整体架构理解
   → 这个文件的定位是什么？它是一个独立的 HTTP 服务
   → 谁来调用它？Java 后端通过 HTTP POST 调用
   → 它的核心职责是什么？只负责"检索相关文档"，不负责生成回答

📖 第二层：数据流理解（从请求到响应的完整链路）
   用户问题(Java) → POST /rag/search → [Python内部处理] → 返回文档列表(给Java)

   内部处理流程（5个步骤）：
     Step 1: 问题向量化 (question → embedding vector)
     Step 2: 向量相似度检索 (Chroma: cosine similarity)
     Step 3: 关键词检索 (BM25: term frequency matching)
     Step 4: 混合融合 (α×向量分 + (1-α)×BM25分)
     Step 5: 排序截断 (取 top_k 条返回)

📖 第三层：每个组件的"为什么"
   - 为什么选 Chroma？轻量级、嵌入式、无需单独部署数据库服务
   - 为什么用 BGE Embedding？中文效果好、开源免费、本地CPU可运行
   - 为什么做混合检索？向量擅长语义模糊匹配，BM25擅长精确关键词匹配
   - 为什么设 α=0.6？语义理解在问答场景通常比关键词更重要（可调参实验）

📖 第四层：代码结构
   - 数据模型 (Pydantic BaseModel) → 定义输入输出格式（类似Java的DTO）
   - 全局变量 → 存储初始化好的组件实例（生产环境应用依赖注入/单例模式）
   - 初始化函数 init_rag_system() → 启动时加载文档、构建索引（一次性开销大）
   - 接口函数 rag_search() → 每次调用的核心逻辑（要快！）

【运行方式】: uvicorn main:app --reload --port 8001
【端口选择】: 8001（避免与 Java 的 8080 冲突）
"""

# ============================================================================
# 【第1部分：导入依赖】
# ============================================================================

# from fastapi import FastAPI, HTTPException
# from pydantic import BaseModel, Field
# from typing import List, Optional
# import os
# 
# # LangChain 组件（RAG 的核心框架）
# # LangChain 是什么？→ 一个编排 LLM 应用各环节的"胶水框架"
# # 它不自己实现算法，而是把各种工具串成一条"链"(Chain)
# from langchain_community.document_loaders import PyPDFLoader, TextLoader      # 文档加载器
# from langchain.text_splitter import RecursiveCharacterTextSplitter           # 文本切分器
# from langchain_community.embeddings import HuggingFaceEmbeddings            # Embedding模型封装
# from langchain_community.vectorstores import Chroma                         # 向量数据库
# from rank_bm25 import BM25Okapi                                             # BM25关键词检索算法
# from langchain_community.cross_encoders import HuggingFaceCrossEncoder      # 重排序模型（可选）

# ============================================================================
# 【第2部分：创建 FastAPI 应用实例】
# ============================================================================

# app = FastAPI(title="RAG Microservice", version="1.0.0")
# 
# # FastAPI 类似于什么？→ 相当于 Java 的 @RestController + Spring MVC
# # 它会自动：生成 Swagger 文档 / 参数校验 / JSON 序列化/反序列化

# ============================================================================
# 【第3部分：数据模型定义】（对应 Java 的 DTO/Entity）
# ============================================================================

"""
【原理说明】：Pydantic BaseModel vs Java 的区别

Java 方式:
  public class RagRequest {
      @NotNull private String question;
      @Min(1) private Integer topK = 5;
      // getter/setter...
  }

Python (Pydantic) 方式:
  class RagRequest(BaseModel):
      question: str          # 类型注解 + 校验一体
      top_k: int = 5         # 默认值

关键区别：Pydantic 自动完成校验+序列化，不需要手写 getter/setter/validator
"""

# class RagRequest(BaseModel):
#     """
#     RAG 检索请求 - Java 后端发过来的数据格式
#     
#     【思考】为什么 question 要设 min_length=1？
#     → 防止空字符串或纯空格的无效请求浪费资源
#     """
#     question: str = Field(..., min_length=1, description="用户问题")
#     top_k: int = Field(default=5, ge=1, le=20, description="返回文档数量")

# 
# class SourceDocument(BaseModel):
#     """
#     单条检索结果文档
#     
#     【思考】为什么要同时返回 content + source + score？
#     → content: 给 LLM 看的上下文内容
#     → source: 给用户看的引用来源（如"来自员工手册.pdf 第3页"）
#     → score: 用于调试和评估（判断检索质量是否达标）
#     """
#     content: str = Field(..., description="文档内容片段")
#     source: str = Field(..., description="来源文件名")
#     score: float = Field(..., description="相关度分数")


# class RagResponse(BaseModel):
#     """
#     RAG 检索响应 - 返回给 Java 后端的数据格式
#     
#     【思考】为什么要返回 total_found 和 query_time_ms？
#     → total_found: 帮助 Java 端判断是否有足够的相关文档（太少可能需要降级到纯LLM模式）
#     → query_time_ms: 监控性能，如果超过阈值可以考虑缓存或降级策略
#     """
#     contexts: List[SourceDocument] = Field(..., description="检索到的上下文文档")
#     total_found: int = Field(..., description="命中文档总数")
#     query_time_ms: int = Field(..., description="查询耗时(毫秒)")

# ============================================================================
# 【第4部分：全局状态管理】（核心概念：单例/懒加载）
# ============================================================================

"""
【重要原理】为什么用全局变量？

在 Java/Spring 中我们习惯用 @Service + @Autowired 来管理单例对象。
但在 Python FastAPI 中，最简单的方式就是模块级全局变量。

⚠️ 生产环境的注意事项：
  1. 这些对象占用大量内存（特别是向量索引），不适合每次请求都重建
  2. 所以在 startup 时一次性初始化，后续所有请求复用
  3. 多进程部署时（uvicorn workers=N），每个进程各自持有一份副本
  4. 如果数据量大（>10万文档），应考虑外部向量数据库（Milvus/Pinecone）

【内存占用估算】（供参考）：
  - BGE-small-zh: ~120MB (Embedding 模型)
  - Chroma (1000文档): ~50MB (向量索引)
  - BM25 索引: ~10MB (倒排索引)
  - 总计约: ~180MB（完全可接受的量级）
"""

# vectorstore: Optional[Chroma] = None              # Chroma 向量数据库实例
# bm25_index: Optional[BM25Okapi] = None             # BM25 关键词索引实例
# documents_cache: list = []                          # 缓存原始分块文档（BM25按索引取内容用）
# cross_encoder: Optional[HuggingFaceCrossEncoder] = None  # 重排序模型（可选）

# ============================================================================
# 【第5部分：系统初始化函数】（启动时调用一次，耗时较长）
# ============================================================================

# def init_rag_system(docs_path: str = "./data/documents"):
#     """
#     RAG 系统初始化 - 加载文档并构建所有索引
#     
#     【类比理解】这相当于搜索引擎的"建索引"阶段
#     Google 搜索前要先爬取网页 → 分词 → 建立倒排索引
#     我们这里：加载本地文档 → 切分 → 向量化 → 建立向量索引 + BM25索引
#     
#     【执行时机】：
#       - 开发环境：启动时自动调用（@app.on_event("startup")）
#       - 生产环境：通过 /rag/init 接口手动触发（支持热更新文档）
#       
#     【预计耗时】（首次运行）：
#       - 100页 PDF 文档: ~30-60秒
#       - 主要时间花在：Embedding 模型推理（向量化每段文本）
#     """
#     global vectorstore, bm25_index, documents_cache, cross_encoder
#     
#     print("🚀 正在初始化 RAG 系统...")
#     
#     # ===== 步骤 1: 加载文档 =====
#     """
#     DocumentLoader 的作用：
#     将不同格式的文件（PDF/TXT/MD/Word）统一转换为 Document 对象
#     
#     Document 对象结构：
#       {
#         page_content: "文档的实际文本内容...",
#         metadata: {
#           source: "员工手册.pdf",    # 来源文件名
#           page: 3,                   # 页码（PDF特有）
#           ...
#         }
#       }
#     
#     【扩展】如果需要处理 Word/Excel/HTML，可以添加：
#       - UnstructuredLoader (通用解析)
#       - docx2txt (Word)
#       - pandas (Excel表格)
#     """
#     documents = []
#     for filename in os.listdir(docs_path):
#         filepath = os.path.join(docs_path, filename)
#         if filename.endswith(".pdf"):
#             loader = PyPDFLoader(filepath)
#             documents.extend(loader.load())
#         elif filename.endswith(".txt"):
#             loader = TextLoader(filepath)
#             documents.extend(loader.load())
#     
#     print(f"📄 已加载 {len(documents)} 页/段文档")
#     
#     # ===== 步骤 2: 文本切分（Text Splitting）=====
#     """
#     【为什么必须切分？】
#     - LLM 的上下文窗口有限（如 GLM-4.5-air: 128K tokens）
#     - 但单个 PDF 可能有几十万字
#     - 如果整篇文档塞进去：①超token限制 ②噪音太多影响检索精度 ③成本爆炸
#     
#     【切分策略对比】（你需要实验验证的部分！）：
#     
#     ┌─────────────────┬──────────────┬──────────────┬──────────────┐
#     │ 策略            │ chunk_size   │ 优点          │ 缺点          │
#     ├─────────────────┼──────────────┼──────────────┼──────────────┤
#     │ 固定长度切分    │ 512字符      │ 实现简单      │ 可能切断句子   │
#     │ 递归字符分割    │ 512字符      │ 按段落/句切   │ 仍有边界问题   │
#     │ 语义分割        │ 不固定       │ 保持语义完整   │ 需额外模型     │
#     │ （本文选用）    │              │              │              │
#     └─────────────────┴──────────────┴──────────────┴──────────────┘
#     
#     【参数详解】：
#       chunk_size=512: 每块最多512个字符（约200-300个汉字）
#       chunk_overlap=50: 相邻块重叠50个字符（防止信息被切断在两块之间）
#       separators: 优先按段落切 → 句子切 → 标点切 → 空格切 → 强制切
#     
#     【你的实验任务】（W5-7 重点）：
#       尝试不同的 chunk_size: [256, 512, 1024]
#       记录 Top1 准确率变化，找到最优值
#     """
#     text_splitter = RecursiveCharacterTextSplitter(
#         chunk_size=512,
#         chunk_overlap=50,
#         length_function=len,
#         separators=["\n\n", "\n", "。", "！", "？", " ", ""]
#     )
#     splits = text_splitter.split_documents(documents)
#     print(f"✂️  切分为 {len(splits)} 个文本块")
#     
#     # ===== 步骤 3: 初始化 Embedding 模型 =====
#     """
#     【什么是 Embedding（嵌入/向量化）？】
#     
#     核心思想：把文本变成数字向量（一串浮点数数组）
#     
#     例如："年假政策" → [0.123, -0.456, 0.789, ..., 0.234]  (384维/768维)
#           "休假制度" → [0.130, -0.448, 0.785, ..., 0.241]  (很接近↑)
#           "火锅食谱"  → [-0.891, 0.234, -0.567, ..., -0.123]  (离得很远↓)
#     
#     语义相近的文本 → 向量空间中也接近 → 可以用余弦相似度计算相关性
#     
#     【为什么选 BAAI/bge-small-zh-v1.5？】
#       - bge = Beijing Academy of Artificial Intelligence (智源研究院)
#       - small: 模型小（384维，约120MB），CPU可跑，速度快
#       - zh-v1.5: 中文优化版本 v1.5
#       - 开源免费，效果媲美商业API（OpenAI text-embedding-3-small）
#       
#     【替代方案对比】（你路线图中的实验项）：
#       ├─ OpenAI text-embedding-3-small: 效果好但收费，$0.02/百万token
#       ├─ m3e-base: 中文也不错，稍逊BGE
#       └─ BGE-large: 更大(768维)，更准但更慢，适合对质量要求极高的场景
#     """
#     embedding_model = HuggingFaceEmbeddings(
#         model_name="BAAI/bge-small-zh-v1.5",
#         model_kwargs={'device': 'cpu'},  # CPU 运行（无GPU也能跑！）
#         encode_kwargs={'normalize_embeddings': True}  # 归一化（余弦距离=点积）
#     )
#     print(f"🤖 Embedding 模型加载完成: BAAI/bge-small-zh-v1.5")
#     
#     # ===== 步骤 4: 构建向量索引 (ChromaDB) =====
#     """
#     【ChromaDB 是什么？】
#     轻量级向量数据库，特点：
#       - 嵌入式运行：不需要单独安装数据库服务（像 SQLite 一样）
#       - 持久化：数据保存到本地磁盘，重启不丢失
#       - 内存+磁盘混合：常用数据在内存，大数据集溢出到磁盘
#     
#     【向量索引的工作原理】（简化版）：
#       1. 把每个文本块的 Embedding 向量存入索引
#       2. 查询时：把问题也变成向量
#       3. 计算 query 向量 与 所有文档向量的 余弦相似度
#       4. 返回最相似的 top-k 个文档
#     
#     【与 SQL 数据库的对比】：
#       SQL: SELECT * FROM docs WHERE content LIKE '%关键词%'  ← 精确匹配
#       向量: 找出与 query 最"语义相似"的文档                    ← 语义匹配
#       
#     例: 用户问"怎么请病假？"
#       SQL LIKE 可能找不到（如果没有精确包含"请病假"这三个字）
#       但向量搜索能找到"员工因病无法出勤时的请假流程"（语义相同）
#     """
#     persist_dir = "./data/chroma_db"
#     vectorstore = Chroma.from_documents(
#         documents=splits,
#         embedding=embedding_model,
#         persist_directory=persist_dir
#     )
#     print(f"📊 Chroma 向量库构建完成，存储于 {persist_dir}")
#     
#     # ===== 步骤 5: 构建 BM25 关键词索引 =====
#     """
#     【BM25 是什么？】
#     经典的信息检索算法（搜索引擎的核心技术之一）
#     
#     工作原理（简化版）：
#       1. 对所有文档分词，建立"词 → 文档列表"的倒排索引
#       2. 查询时分词，查找包含查询词的文档
#       3. 根据词频(TF)、逆文档频率(IDF)、文档长度等计算相关性得分
#     
#     【为什么有了向量检索还要 BM25？】
#     
#     场景对比：
#     ├── 用户问: "GLM-4.5-air 的 token 价格是多少？"
#     │   向量搜索 ✅ 能找到（语义匹配"定价/费用/收费标准"）
#     │   BM25 搜索 ✅ 能找到（精确匹配"GLM-4.5-air"、"token"、"价格"）
#     │
#     ├── 用户问: "公司的缩写是 ABC 吗？"
#     │   向量搜索 ❌ 可能找到错误文档（"ABC公司"和"XYZ公司"语义上都是公司名）
#     │   BM25 搜索 ✅ 精确匹配"ABC"这个词
#     │
#     └── 结论: 两者互补！混合使用效果最佳
#     
#     【混合检索公式】（后面 search 函数中使用）：
#       final_score = α × vector_score + (1-α) × bm25_score
#       其中 α ∈ [0, 1]，通常设为 0.6 或 0.7（偏向语义）
#     """
#     tokenized_docs = [doc.page_content.split() for doc in splits]
#     bm25_index = BM25Okapi(tokenized_docs)
#     documents_cache = splits  # 缓存分块后的文档，BM25按索引号取内容
#     print(f"🔍 BM25 索引构建完成")
#     
#     # ===== 步骤 6: CrossEncoder 重排序（可选，首次使用时懒加载）=====
#     """
#     【CrossEncoder 是什么？重排序又是什么？】
#     
#     两阶段检索架构（Retrieval + Reranking）：
#     
#     Stage 1 - 粗检索（召回）:
#       从几万个文档中快速找出 50 个候选（用向量/BM25）
#       → 目标：快！宁可多召回也不能漏掉
#       
#     Stage 2 - 精排序（排序）:
#       对这 50 个候选逐一精细评分（用 CrossEncoder）
#       → 目标：准！选出最好的 5 个
#     
#     【为什么不分两步直接一步到位？】
#       CrossEncoder 需要 (query, document) 配对推理，计算量大
#       如果对 10000 个文档都跑一遍 CrossEncoder：太慢了（~分钟级）
#       所以先用快速方法筛出候选，再对少量候选精排
#       
#     【类比】就像招聘：
#       Stage 1: 筛简历（看基本条件，快速淘汰大部分）
#       Stage 2: 面试（深入考察少数候选人）
#     """
#     # cross_encoder = HuggingFaceCrossEncoder(model_name="BAAI/bge-reranker-base")
#     
#     print("✅ RAG 系统初始化完成！")

# ============================================================================
# 【第6部分：核心接口 - RAG 检索】（每次请求都会调用这里）
# ============================================================================

# @app.post("/rag/search", response_model=RagResponse)
# async def rag_search(request: RagRequest):
#     """
#     🔑 核心接口：RAG 检索
#     
#     【调用方】Java 后端的 RagService 通过 RestTemplate 调用此接口
#     【输入】用户的问题 + 需要返回多少条文档
#     【输出】相关文档列表 + 元数据（来源、分数等）
#     
#     ⏱ 性能目标：< 500ms（不含网络传输时间）
#     """
#     import time
#     start_time = time.time()
#     
#     # 安全检查：确保已初始化
#     if vectorstore is None or bm25_index is None:
#         raise HTTPException(status_code=503, detail="RAG 系统未初始化")
#     
#     question = request.question
#     top_k = request.top_k
#     
#     # ===== 步骤 1: 向量语义检索 =====
#     """
#     similarity_search_with_score 返回的是 (Document, score) 元组列表
#     - score 是距离（越小越相似），不是相似度（越大越相似）
#     - Chroma 默认用的是余弦距离 = 1 - cosine_similarity
#     - 所以需要转换：cosine_similarity = 1 - distance_score
#     
#     为什么 k=top_k*2?
#     因为后续要做混合融合，多召回一些候选再筛选，提高覆盖率
#     """
#     vector_results = vectorstore.similarity_search_with_score(question, k=top_k * 2)
#     
#     # ===== 步骤 2: BM25 关键词检索 =====
#     """将查询文本分词（简单空格分词，中文场景需配合 jieba 分词器）"""
#     tokenized_query = question.split()
#     bm25_scores = bm25_index.get_scores(tokenized_query)
#     # argsort: 返回分数降序排列的索引位置
#     bm25_top_indices = bm25_scores.argsort()[-(top_k * 2):][::-1]
#     
#     # ===== 步骤 3: 混合检索融合（Score Fusion）=====
#     """
#     【核心算法】混合检索分数融合
#     
#     思路：同一个文档可能在两种检索方法中都出现，也可能只在一种中出现
#     我们需要合并去重，然后计算综合得分
#     
#     【归一化的必要性】：
#       向量分数范围: [0, 1]（余弦相似度）
#       BM25 分数范围: [0, 无上限]（取决于词频）
#       直接相加会导致某一方的权重过大
#       → 所以必须先归一化到 [0, 1] 区间
#     
#     【alpha 权重的选择依据】（你的实验重点！）：
#       α = 0.0: 纯 BM25（精确关键词匹配）
#       α = 0.5: 各占一半
#       α = 0.7: 偏向语义理解
#       α = 1.0: 纯向量检索
#       
#       推荐：从 0.6 开始尝试，根据评估集准确率调整
#       你的 W5-7 任务：尝试 [0.3, 0.5, 0.6, 0.7, 0.9]，记录 Top1 准确率变化
#     """
#     candidates = {}  # {doc_content: {vec_score, bm25_score, source}}
#     
#     # 处理向量检索结果
#     max_vec_score = max((s for _, s in vector_results), default=1)
#     for doc, score in vector_results:
#         key = doc.page_content
#         # 归一化：distance → similarity
#         norm_vec_score = 1 - (score / max_vec_score) if max_vec_score > 0 else 0
#         if key not in candidates:
#             candidates[key] = {'vec': norm_vec_score, 'bm25': 0, 'source': doc.metadata.get('source', 'unknown')}
#     
#     # 处理 BM25 检索结果
#     if len(bm25_scores) > 0:
#         max_bm25_score = max(bm25_scores) if max(bm25_scores) > 0 else 1
#         for idx in bm25_top_indices:
#             if idx < len(documents_cache):
#                 doc = documents_cache[idx]
#                 key = doc.page_content
#                 norm_bm25 = bm25_scores[idx] / max_bm25_score if max_bm25_score > 0 else 0
#                 if key in candidates:
#                     candidates[key]['bm25'] = norm_bm25  # 合并：两种方法都找到了
#                 else:
#                     candidates[key] = {'vec': 0, 'bm25': norm_bm25, 'source': doc.metadata.get('source', 'unknown')}
#     
#     # 计算最终混合分数
#     alpha = 0.6  # ← 这是你要调的关键参数！
#     scored_results = []
#     for content, scores in candidates.items():
#         hybrid_score = alpha * scores['vec'] + (1 - alpha) * scores['bm25']
#         scored_results.append({
#             'content': content,
#             'source': scores['source'],
#             'score': round(hybrid_score, 4)
#         })
#     
#     # 排序并截断
#     scored_results.sort(key=lambda x: x['score'], reverse=True)
#     top_results = scored_results[:top_k]
#     
#     # ===== 步骤 4: CrossEncoder 重排序（可选）=====
#     """
#     【何时启用？】
#       - 当你对 Top1 准确率有极高要求时 (>90%)
#       - 当延迟预算充足时（CrossEncoder 会增加 ~100-300ms）
#       - 取消下面的注释即可启用
#     """
#     # if cross_encoder is not None and len(top_results) > 1:
#     #     pairs = [[question, r['content']] for r in top_results]
#     #     rerank_scores = cross_encoder.predict(pairs)
#     #     for i, r in enumerate(top_results):
#     #         r['score'] = round(float(rerank_scores[i]), 4)
#     #     top_results.sort(key=lambda x: x['score'], reverse=True)
#     
#     elapsed_ms = int((time.time() - start_time) * 1000)
#     
#     return RagResponse(
#         contexts=[SourceDocument(**r) for r in top_results],
#         total_found=len(scored_results),
#         query_time_ms=elapsed_ms
#     )

# ============================================================================
# 【第7部分：辅助接口 - 初始化 & 健康检查】
# ============================================================================

# @app.post("/rag/init")
# async def init_rag(docs_path: str = "./data/documents"):
#     """手动触发 RAG 系统初始化（用于更新文档后热加载）"""
#     try:
#         init_rag_system(docs_path)
#         return {"status": "success", "message": f"RAG 系统初始化完成"}
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=f"初始化失败: {str(e)}")

# 
# @app.get("/rag/health")
# async def health_check():
#     """
#     健康检查接口
#     
#     【用途】
#     - Java 端启动时检查此接口，判断 RAG 服务是否可用
#     - 如果不可用，可以降级到"纯LLM对话模式"（不走 RAG）
#     - Kubernetes/Docker 中用于 liveness probe
#     """
#     return {
#         "status": "healthy",
#         "vectorstore_initialized": vectorstore is not None,
#         "bm25_initialized": bm25_index is not None,
#         "document_count": len(documents_cache) if documents_cache else 0
#     }

# ============================================================================
# 【第8部分：应用生命周期钩子】
# ============================================================================

# @app.on_event("startup")
# async def startup_event():
#     """FastAPI 启动时自动执行的回调函数"""
#     # 仅当文档目录存在时才自动初始化
#     if os.path.exists("./data/documents"):
#         init_rag_system()

# ============================================================================
# 【第9部分：程序入口】（直接 python main.py 时执行）
# ============================================================================

# if __name__ == "__main__":
#     import uvicorn
#     
#     # 创建必要目录
#     os.makedirs("./data/documents", exist_ok=True)   # 放测试文档
#     os.makedirs("./data/chroma_db", exist_ok=True)     # Chroma 持久化存储
#     
#     # 启动服务器
#     # host=0.0.0.0: 允许外部访问（不仅是 localhost）
#     # port=8001: 避免 8080 冲突（Java 通常用 8080）
#     # reload=True: 开发模式下代码修改后自动重启
#     uvicorn.run(app, host="0.0.0.0", port=8001)
