package com.example.demo.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagSearchRequest {
    @JsonProperty("question")
    private String question;  //用户问题
    @JsonProperty("top_k")
    private Integer topK=5;  //返回几条，默认5
}
