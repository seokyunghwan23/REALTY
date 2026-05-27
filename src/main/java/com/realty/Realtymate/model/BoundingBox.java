package com.realty.Realtymate.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class BoundingBox {
    private double minLat;
    private double maxLat;
    private double minLng;
    private double maxLng;
}
