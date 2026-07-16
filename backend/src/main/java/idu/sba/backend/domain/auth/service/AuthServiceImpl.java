package idu.sba.backend.domain.auth.service;

import idu.sba.backend.domain.auth.dto.*;
import idu.sba.backend.domain.auth.entity.EmailVerification;
import idu.sba.backend.domain.auth.entity.PasswordResetToken;
import idu.sba.backend.domain.auth.entity.Purpose;
import idu.sba.backend.domain.auth.repository.EmailVerificationRepository;
import idu.sba.backend.domain.auth.repository.PasswordResetTokenRepository;
import idu.sba.backend.domain.terms.entity.Term;
import idu.sba.backend.domain.terms.entity.UserAgreement;
import idu.sba.backend.domain.terms.repository.TermRepository;
import idu.sba.backend.domain.terms.repository.UserAgreementRepository;
import idu.sba.backend.domain.user.entity.User;
import idu.sba.backend.domain.user.entity.UserStatus;
import idu.sba.backend.domain.user.repository.UserRepository;
import idu.sba.backend.global.exception.BusinessException;
import idu.sba.backend.global.exception.ErrorCode;
import idu.sba.backend.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    //인증코드 유효시간 5분
    private static final int CODE_TTL_MINUTES = 5;

    //재설정 토큰 유효시간 30분
    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final JwtProvider jwtProvider;
    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${spring.mail.username}")
    private String mailFrom;

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
    public void sendCode(EmailSendCodeRequestDTO req) {
        LocalDateTime now = LocalDateTime.now();

        var lastOpt = emailVerificationRepository.findFirstByEmailOrderByCreateAtDesc(req.getEmail());

        lastOpt.ifPresent(last -> {
            if(last.isLocked()) {       //정지중 -> 재발송 거부
                throw new BusinessException(ErrorCode.CODE_LOCKED);
            }
            if(last.getCreateAt().isAfter(now.minusSeconds(60))) { // 60초 쿨타임
                    throw new BusinessException(ErrorCode.CODE_SEND_COOLDOWN);
            }
        });
        int carryStage = lastOpt.map(EmailVerification::getLockStage).orElse(0);

        //6자리 코드 생성
        String code = String.format("%06d",random.nextInt(1_000_000));

        //인증코드 레코드 저장
        emailVerificationRepository.save(
                EmailVerification.issue(req.getEmail(), code,req.getPurpose(),CODE_TTL_MINUTES,carryStage)
        );
        //이메일 전송x
        sendMail(req.getEmail(), code);
    }

    @Override
    public void verifyCode(EmailVerifyRequestDTO req) {



        //해당 이메일 최신 인증코드 1건 조회. 없으면 코드 불일치 처리
        EmailVerification ev = emailVerificationRepository
                .findFirstByEmailOrderByCreateAtDesc(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_MISMATCH));

        if(ev.isLocked()){  //정지중
            throw new BusinessException(ErrorCode.CODE_LOCKED);
        }

        //만료됐으면 410
        if(ev.isExpired()) {  //만료
            throw new BusinessException(ErrorCode.CODE_EXPIRED);
        }

        //코드가 안맞으면 410
        if(!ev.matches(req.getCode())){ //불일치
            ev.recordFail();
            emailVerificationRepository.save(ev);
            throw new BusinessException(ev.isLocked() ? ErrorCode.CODE_LOCKED : ErrorCode.CODE_MISMATCH);
        }

        //인증완료 처리
        ev.markVerified();
        emailVerificationRepository.save(ev);


    }

    @Override
    @Transactional
    public Long signup(SignupRequestDTO req) {

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
                        .nickname(req.getNickname())
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
        message.setFrom(mailFrom);
        message.setTo(to);                    // 받는 사람
        message.setSubject("[COGI] 이메일 인증코드");  // 제목
        message.setText("인증코드: " + code + "\n5분 안에 입력해주세요.");  // 본문
        mailSender.send(message);             // 완성된 메일 발송
    }


    @Override
    public TokenResponseDTO login(LoginRequestDTO req) {
        //이메일 유저 조회
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        //이미 잠긴 계정
        if(Boolean.TRUE.equals(user.getIsLocked())){
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        //정지된 계정
        if(user.getStatus() == UserStatus.SUSPENDED){
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        //비밀번호 검증
        if(!passwordEncoder.matches(req.getPassword(),user.getPassword())){
            user.increaseLoginFailCount(); //실패 회수 + 1, 5회이상 -> 계정잠금
            userRepository.save(user);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }


        //성공시 실패회수 초기화, 토큰발급
        user.resetLoginFailCount();
        String token = jwtProvider.createToken(user.getId(),user.getRole().name());
        return TokenResponseDTO.of(token,accessTokenExpiration);

    }

    @Override
    public String resetRequest(PasswordResetRequestDTO req) {
        EmailVerification ev = emailVerificationRepository.findFirstByEmailOrderByCreateAtDesc(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_MISMATCH));

        if(ev.isLocked()){
            throw new BusinessException(ErrorCode.CODE_LOCKED);
        }
        if (ev.isExpired()){
            throw new BusinessException(ErrorCode.CODE_EXPIRED);
        }

        if(!ev.matches(req.getCode())){
            ev.recordFail();
            emailVerificationRepository.save(ev);
            throw new BusinessException(ev.isLocked() ? ErrorCode.CODE_LOCKED : ErrorCode.CODE_MISMATCH);
        }

        //인증 완료
        ev.markVerified();
        emailVerificationRepository.save(ev);

        //가입된 계정인지 확인
        if(!userRepository.existsByEmail(req.getEmail())){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        //재설정 토큰 발급 (UUID로 랜덤 값 발급)
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(req.getEmail(),token,RESET_TOKEN_TTL_MINUTES));
            return token;
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetDTO req) {

        //새 비밀번호랑 새 비밀번호 재인증
        if(!req.getNewPassword().equals(req.getNewPasswordConfirm())){
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        //토큰 조회 + 검증
        PasswordResetToken token = passwordResetTokenRepository
                .findByResetToken(req.getResetToken())
                .orElseThrow(() ->  new BusinessException(ErrorCode.RESET_TOKEN_INVALID));

        //만료 되었는지
        if(token.isExpired()){
            throw new BusinessException(ErrorCode.RESET_TOKEN_EXPIRED);
        }

        //비밀번호 재설정 토큰을 사용한적이 있는지
        if (Boolean.TRUE.equals(token.getUsed())){
            throw new BusinessException(ErrorCode.RESET_TOKEN_USED);
        }

        //대상 유저 조회
        User user = userRepository.findByEmail(token.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        //기존 비밀번호와 같으면 거부
        if(passwordEncoder.matches(req.getNewPassword(),user.getPassword())){
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }

        //비밀번호 변경 + 계정 잠금 해제 + 토큰 사용처리
        user.resetPassword(passwordEncoder.encode(req.getNewPassword()));
        token.markUsed();


    }
}

