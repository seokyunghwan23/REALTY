package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(
        name = "agent_customer",
        indexes = @Index(name = "spatial_index_polygon", columnList = "polygon", unique = false)
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AgentCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // JPA에서는 Long(BIGINT) 권장

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "kind", length = 50)
    private String kind;

    @Column(name = "polygon", nullable = false, columnDefinition = "POLYGON NOT NULL SRID 4326")
    private Polygon polygon;

    @Column(name = "alert", nullable = false)
    @ColumnDefault("TRUE")
    private boolean alert;

    @Column(name = "region_name", nullable = true, length = 255)
    private String regionName;

    @Column(name = "manager_name", nullable = true, length = 100)
    private String managerName;  // 추가된 필드 (담당자)

    @Column(name = "chat_id", nullable = true, length = 50)
    private String chatId;  // 추가된 필드 (텔레그램 chat_id)

    @Column(name = "min_floor", nullable = true, length = 50)
    private Integer minFloor;  // 추가된 필드 (텔레그램 chat_id)

    @Column(name = "max_floor", nullable = true, length = 50)
    private Integer maxFloor;  // 추가된 필드 (텔레그램 chat_id)

    public boolean isMinFloorPresent() {
        return minFloor != null;
    }
}

    /*
    INSERT INTO agent_customer (agent_name, kind, polygon, alert, region_name, manager_name, chat_id)
    VALUES (
    '베스트',
            '공장창고',
    ST_GeomFromText('POLYGON((37.4846237 126.9393611, 37.4818397 126.9375157, 37.4786811 126.9442749, 37.4763396 126.9568169, 37.4760842 126.9590056, 37.4790982 126.9603145, 37.4833722 126.9543386, 37.4846237 126.9393611))', 4326),
        TRUE,
        '평택',
        '김청미',
        '-1002507016327'
        );
        */