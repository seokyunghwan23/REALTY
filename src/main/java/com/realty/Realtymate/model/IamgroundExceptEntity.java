package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "iamground_except")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class IamgroundExceptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "deposit")
    private Integer deposit;

    @Column(name = "monthly_fee")
    private Integer monthlyFee;
}
