package idu.sba.backend.domain.payment.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    private Long id; // name에 따른 id  -> users 테이블의 plan_id에 대입

    @Column(nullable = false , unique = true , length = 20)
    private String name; // FREE , PRO ,MAX 이름 지정

    @Column(name = "daily_credit_limit", nullable = false)
    private int dailyCreditLimit; //일일 크레딧 한도 ( FREE는 20 , PRO는 40  , MAX는 70)


    @Column(name = "allowed_models", nullable = false, length = 255)
    private String allowedModels; // 허용 모델 목록, 콤마 구분 문자열 (누적형 전체 저장)

    @Column(nullable = false)
    private int price; // 월 구독료(원)


}

