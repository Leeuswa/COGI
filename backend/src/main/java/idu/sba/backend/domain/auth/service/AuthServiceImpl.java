package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.EmailSendCodeRequestDto;
import idu.sba.backend.domain.auth.dto.EmailVerifyRequestDto;
import idu.sba.backend.domain.auth.dto.SignupRequestDto;
import idu.sba.backend.domain.auth.entity.EmailVerification;
import idu.sba.backend.domain.auth.entity.Purpose;
import idu.sba.backend.domain.auth.repository.EmailVerificationRepository;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    //인증코드 유효시간 5분
    private static final int CODE_TTL_MINUTES = 5;

    //인증코드 난수 생성
    private final SecureRandom random = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final TermRepository termRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender; //메일 발송


    @Override
    @Transactional
    public void sendCode(EmailSendCodeRequestDto req) {

        //6자리 코드 생성
        String code = String.format("%06d",random.nextInt(1_000_000));

        //인증코드 레코드 저장
        emailVerificationRepository.save(
                EmailVerification.issue(req.getEmail(), code,req.getPurpose(),CODE_TTL_MINUTES)
        );

        //이메일 전송x
        sendMail(req.getEmail(), code);
    }

    @Override
    @Transactional
    public void verifyCode(EmailVerifyRequestDto req) {

        //해당 이메일 최신 인증코드 1건 조회. 없으면 코드 불일치 처리
        EmailVerification ev = emailVerificationRepository
                .findFirstByEmailOrderByCreateAtDesc(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_MISMATCH));

        //만료됐으면 410
        if(ev.isExpired()) {
            throw new BusinessException(ErrorCode.CODE_EXPIRED);
        }

        //코드가 안맞으면 410
        if(!ev.matches(req.getCode())){
            throw new BusinessException(ErrorCode.CODE_MISMATCH);
        }

        //인증완료 처리
        ev.markVerified();


    }

    @Override
    @Transactional
    public Long signup(SignupRequestDto req) {

        //비밀번호 확인
        if(!req.getPassword().equals(req.getPasswordConfirm())){
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        //이메일 인증 확인
        if(!emailVerificationRepository
                .existsByEmailAndPurposeAndVerifiedTrue(req.getEmail(), Purpose.SIGNUP)){
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        //이메일 중복확인
        if(userRepository.existsByEmail(req.getEmail())){
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        //필수 약관 전체 동의 확인 여부
        Set<Long> agreedIds = Set.copyOf(req.getTermIds());

        //필수 약관 목록
        List<Term> requiredTerms = termRepository.findByIsRequiredTrue();
        boolean allAgreed = requiredTerms.stream()
                .allMatch(term -> agreedIds.contains(term.getId())); //필수 약관 동의 확인

        if(!allAgreed){
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        //유저 저장
        User user = userRepository.save(
                User.builder()
                        .email(req.getEmail())
                        .password(passwordEncoder.encode(req.getPassword()))
                        .build());

        //동의 이력 저장
        List<UserAgreement> agreements = req.getTermIds().stream()
                        .map(termId -> UserAgreement.of(user.getId(),termId))
                .toList();
        userAgreementRepository.saveAll(agreements);

        return 0L;
    }

    //인증 메일코드 메일 발송
    private void sendMail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();  // 빈 메일 객체 생성
        message.setTo(to);                    // 받는 사람
        message.setSubject("[COGI] 이메일 인증코드");  // 제목
        message.setText("인증코드: " + code + "\n5분 안에 입력해주세요.");  // 본문
        mailSender.send(message);             // 완성된 메일 발송
    }
}

