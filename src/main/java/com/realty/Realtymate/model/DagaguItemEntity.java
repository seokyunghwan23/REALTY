package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dagagu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DagaguItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, length = 100)
    private String itemId;

    @Column(name = "agent", nullable = false, length = 100)
    private String agent;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform;

    @Column(name = "kind", nullable = false, length = 50)
    private String kind;
}

