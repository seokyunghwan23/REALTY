package com.realty.Realtymate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "dong_pnu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class DongPnuEntity {
    @Id
    @Column(name = "dong", nullable = false, length = 100)
    private String dong;
    @Column(name = "pnu", nullable = false, length = 100)
    private String pnu;
}
