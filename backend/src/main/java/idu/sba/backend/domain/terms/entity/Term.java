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
    private Boolean isRequired = true; //필수 여부 (MARKETING는 선택,PAYMENT는 false)

    private LocalDate effectiveDate; // 시행일

    // 관리자 약관 본문 수정 (ADM). 약관은 버전 단위 관리라 본문이 바뀌면 버전을 올리고 시행일을 갱신한다.
    public void updateContent(String content) {
        this.content = content;
        this.version = bumpMinor(this.version);
        this.effectiveDate = LocalDate.now(); // 새 버전의 시행일 = 수정 시점
    }

    // "1.0" -> "1.1", "1.9" -> "1.10". 형식이 예상과 다르면 안전하게 뒤에 ".1"을 붙인다.
    private static String bumpMinor(String version) {
        if (version == null || version.isBlank()) return "1.0";
        int dot = version.lastIndexOf('.');
        if (dot < 0) {
            try { return String.valueOf(Long.parseLong(version.trim()) + 1); }
            catch (NumberFormatException e) { return version + ".1"; }
        }
        String head = version.substring(0, dot);
        String tail = version.substring(dot + 1).trim();
        try { return head + "." + (Long.parseLong(tail) + 1); }
        catch (NumberFormatException e) { return version + ".1"; }
    }
}
