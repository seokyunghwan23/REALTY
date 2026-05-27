package com.realty.Realtymate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "iamground_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class IamGroundItemsEntity {
    @Id
    @Column(name = "item_id", nullable = false, length = 100)
    private String itemId;
}
