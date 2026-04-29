package com.example.demo.rag;
import com.example.demo.dto.RagContextItem;
import com.example.demo.dto.RagSearchRequest;
import com.example.demo.dto.RagSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
@Service
public class RagServiceClient {
    private static final Logger log = LoggerFactory.getLogger(RagServiceClient.class);
    private  final static String ragServiceUrl = "http://localhost:8081";
    private  final RestTemplate restTemplate;
    public RagServiceClient(){
        this.restTemplate=new RestTemplate();
    }
    /**
     * 检索（默认前5条）
     */
    public RagSearchResponse search(String question){
        return search(question,5);
        }

/**
 * 调用 Python RAG 服务执行检索
 *
 * 完整链路：构建请求对象 → 序列化JSON → POST到Python → 反序列化返回 RagSearchResponse
 */
public RagSearchResponse search(String question,int topK){
    try{
        String url = ragServiceUrl+"/rag/search";
        //1.构建请求对象（Jackson自动序列化为JSON）
        RagSearchRequest request = new RagSearchRequest(question,topK);
        //2.设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //3.打包成HttpEntity(header+body)
        HttpEntity<RagSearchRequest> entity = new HttpEntity<>(request,headers);

        log.info("正在调用RAG服务检索：{}",question);
        long startTime= System.currentTimeMillis();

        //4.发送Post请求。自动反序列化为RagSearchResponse
        RagSearchResponse response = restTemplate.postForObject(url,entity,RagSearchResponse.class);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("RAG检索完成，耗时{}ms，返回{}条结果",
            elapsed,
            response != null && response.getContexts() !=null ? response.getContexts().size() : 0);

        return response;
    }catch (Exception e){
        //优雅返回：RAG挂了返回空响应。不抛异常。保证可用性
        log.error("RAG服务调用失败，()",e.getMessage(),0,0);
        return  new RagSearchResponse(Collections.emptyList(),0,0);
    }
}
    /**
     * 健康检查：判断 RAG 服务是否可用
     */

    public boolean isHealthy() {
        try {
            String url = ragServiceUrl + "/rag/search";
            Object result = restTemplate.getForObject(url, Object.class);
            return result != null;
        } catch (Exception e) {
            log.warn("RAG服务不可用：{}", e.getMessage());
            return false;
        }

}


}


