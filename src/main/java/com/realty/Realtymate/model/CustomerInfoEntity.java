package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "customer_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CustomerInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    private Integer minFloor;
    private Integer maxFloor;

    @Column(precision = 10, scale = 2)
    private BigDecimal minArea;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxArea;

    @Column(precision = 15, scale = 2)
    private BigDecimal maxDeposit;

    @Column(precision = 15, scale = 2)
    private BigDecimal maxMonthlyFee;

    @Column(columnDefinition = "POLYGON")  // MySQL의 POLYGON 타입 매핑
    private Polygon polygon;

    private Boolean alert; // MySQL tinyint(1) -> Java Boolean

    private String name;
    private String contact;
    private String kind;

    private Timestamp updatedDate;

    private String lastId;

    private String storeCategory;

    private String manager;

    private String chatId;

    public boolean isMinFloorPresent() {
        return minFloor != null;
    }


}
