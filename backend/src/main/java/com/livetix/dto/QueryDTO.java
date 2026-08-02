package com.livetix.dto;

import lombok.Data;

@Data
public class QueryDTO {

    private Integer page = 1;
    private Integer pageSize = 10;
    private String keyword;
    private Long categoryId;
    private Integer status;
    private String sortField;
    private String sortOrder;     // asc / desc
}
