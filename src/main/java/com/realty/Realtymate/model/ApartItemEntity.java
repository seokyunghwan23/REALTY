package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "apart_items")
@Getter
@Setter
@NoArgsConstructor
public class ApartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", unique = true, length = 255)
    private String itemId;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "dong", length = 255)
    private String dong;

    @Column(name = "floor", length = 255)
    private String floor;

    @Column(name = "ho", length = 255)
    private String ho;

    @Column(name = "area", precision = 15, scale = 2)
    private BigDecimal area;

    @Column(name = "deal_price", precision = 15, scale = 2)
    private BigDecimal dealPrice;

    @Column(name = "deposit", precision = 15, scale = 2)
    private BigDecimal deposit;

    @Column(name = "monthly_fee", precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "management_fee", precision = 15, scale = 2)
    private BigDecimal managementFee;

    @Column(name = "trade_type", length = 255)
    private String tradeType;

    @Column(name = "updated_date")
    private Timestamp updatedDate;

    @Column(name = "apart_name", length = 255)
    private String apartName;

    @Column(name = "agent_name", length = 255)
    private String agentName;
}
