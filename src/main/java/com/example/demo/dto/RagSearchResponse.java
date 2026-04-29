package com.example.demo.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@AllArgsConstructor
@NoArgsConstructor
@Data

public class RagSearchResponse {
    @JsonProperty("contexts")
    private List<RagContextItem> contexts;    //文档列表
    @JsonProperty("total_score")
    private Integer total_score;    //命中分数
    @JsonProperty("query_time_ms")
    private  Integer queryTimeMs;    //耗时
}


