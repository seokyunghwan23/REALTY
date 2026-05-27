package com.realty.Realtymate.model.commercial.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 상권 분석 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {
    private String status;
    private String message;
    private Map<String, Object> data;
    private Map<String, Object> metadata;
}
