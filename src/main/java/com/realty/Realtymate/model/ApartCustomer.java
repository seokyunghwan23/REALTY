package com.realty.Realtymate.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.io.IOException;
import java.util.List;

@Entity
@Table(name = "apart_customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ApartCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "complex_list", nullable = false, columnDefinition = "json")
    private String complexListJson; // 실제 DB에는 문자열로 저장

    @Transient
    private List<String> complexList; // JSON -> List 변환용 (실제 DB 컬럼 아님)

    @Column(name = "alert", nullable = false)
    @ColumnDefault("TRUE")
    private boolean alert;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "chat_id")
    private String chatId;

    // ======= Getter, Setter =======

    @PostLoad
    private void loadComplexList() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            this.complexList = mapper.readValue(this.complexListJson, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new RuntimeException("complex_list JSON 파싱 실패", e);
        }
    }

    @PrePersist
    @PreUpdate
    private void saveComplexList() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            this.complexListJson = mapper.writeValueAsString(this.complexList);
        } catch (IOException e) {
            throw new RuntimeException("complex_list JSON 직렬화 실패", e);
        }
    }

    // ... 여기 아래로는 lombok 쓰면 간단하게 가능함
    // 예: @Getter @Setter @NoArgsConstructor @AllArgsConstructor 등
}