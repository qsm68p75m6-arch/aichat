package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PageRequest {

    @NotNull(message = "页码不能为空")
    @DecimalMin(value = "1", message = "页码必须大于等于1")
    private Integer page;

    @NotNull(message = "每页数量不能为空")
    @Positive(message = "每页数量必须大于0")
    @DecimalMin(value = "1", message = "每页数量大于等于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size;

    private String username; // 可选

    // getter / setter
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}

