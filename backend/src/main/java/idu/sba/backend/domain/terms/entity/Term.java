package idu.sba.backend.domain.terms.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TermType type;  // SERVICE / PRIVACY / PAYMENT / MARKETING

    private String version; //약관 버전 - 버전 단위 관리

    private String title; //약관 제목

    @Column(columnDefinition = "TEXT") // TEXT 타입(VARCHAR 길이 제한보다 길다)
    private String content; // 약관 본문
    private Boolean isRequired = true; //필수 여부 (MARKETING만 false)

    private LocalDate effectiveDate; // 시행일
}
