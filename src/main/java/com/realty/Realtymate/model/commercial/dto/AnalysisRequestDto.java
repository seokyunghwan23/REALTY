package com.realty.Realtymate.model.commercial.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 상권 분석 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDto {
    private Double latitude;
    private Double longitude;
    private Integer radius;
    private String analysisType; // "radius" or "polygon"
    private List<double[]> polygonCoords; // [[lat, lng], [lat, lng], ...]
}
