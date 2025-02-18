package com.jandi.plan_backend.user.controller;

import com.jandi.plan_backend.user.dto.*;
import com.jandi.plan_backend.user.service.UserService;
import com.jandi.plan_backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * UserController는 회원가입, 로그인, 이메일 인증, 비밀번호 찾기, 그리고
 * 인증된 사용자의 상세 프로필 정보를 조회하는 기능을 제공하는 REST 컨트롤러입니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    // 사용자 관련 비즈니스 로직을 처리하는 서비스 객체
    private final UserService userService;
    // Spring Security의 인증 처리를 위한 AuthenticationManager
    private final AuthenticationManager authenticationManager;
    // JWT 토큰 생성 및 검증 기능을 제공하는 JwtTokenProvider
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 생성자 주입을 통해 필요한 의존성들을 할당합니다.
     *
     * @param userService             사용자 관련 로직을 처리하는 서비스
     * @param authenticationManager   인증 처리 객체
     * @param jwtTokenProvider        JWT 토큰 생성 및 검증 객체
     */
    public UserController(UserService userService,
                          AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 회원가입 엔드포인트.
     * 클라이언트가 전송한 UserRegisterDTO를 이용해 회원가입을 처리하고,
     * 인증 이메일 발송 후 성공 메시지를 반환합니다.
     *
     * @param dto 회원가입에 필요한 정보 (이메일, 사용자 이름, 비밀번호 등)
     * @return 성공 시 회원가입 완료 메시지
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterDTO dto) {
        // 회원가입 로직 수행 (회원가입 및 인증 이메일 발송)
        userService.registerUser(dto);
        // 성공 메시지 반환
        return ResponseEntity.ok("회원가입 완료! 이메일의 링크를 클릭하면 인증이 완료됨");
    }

    /**
     * 로그인 엔드포인트.
     * 클라이언트로부터 전송된 UserLoginDTO를 이용하여 사용자 인증을 시도하고,
     * 인증에 성공하면 JWT 토큰을 발급하여 응답으로 반환합니다.
     *
     * @param userLoginDTO 로그인에 필요한 이메일과 비밀번호 정보를 담은 DTO
     * @return JWT 토큰을 포함한 AuthResponse 객체
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("로그인 시도, 이메일: {}", userLoginDTO.getEmail());
        // 사용자 인증을 위해 UsernamePasswordAuthenticationToken 생성 및 인증 시도
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userLoginDTO.getEmail(), userLoginDTO.getPassword())
        );
        // 인증 성공 시, JWT 토큰 생성
        String token = jwtTokenProvider.createToken(userLoginDTO.getEmail());
        log.info("로그인 성공, 이메일: {}, JWT 토큰 생성됨", userLoginDTO.getEmail());
        return ResponseEntity.ok(new AuthResponse(token));
    }

    /**
     * 이메일 인증 엔드포인트.
     * 클라이언트가 요청 파라미터로 전달한 인증 토큰을 이용해 이메일 인증을 처리합니다.
     *
     * @param token 이메일 인증을 위한 토큰 (쿼리 파라미터)
     * @return 인증 성공 시 성공 메시지, 실패 시 오류 메시지
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyByToken(@RequestParam("token") String token) {
        // 토큰을 통한 이메일 인증 처리
        boolean result = userService.verifyEmailByToken(token);
        if (result) {
            return ResponseEntity.ok("이메일 인증 완료됨");
        }
        return ResponseEntity.badRequest().body("인증 실패, 토큰이 유효하지 않거나 만료됨");
    }

    /**
     * 비밀번호 찾기(임시 비밀번호 발급) 엔드포인트.
     * 클라이언트가 요청 본문에 포함한 이메일 주소로 임시 비밀번호를 발급하고,
     * 해당 이메일로 임시 비밀번호 안내 메일을 전송합니다.
     *
     * @param request Map 형식으로 전달된 이메일 정보 (키: "email")
     * @return 임시 비밀번호 발급 완료 메시지
     */
    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        // 요청 맵에서 "email" 키로 이메일 값을 추출
        String email = request.get("email");
        // 비밀번호 재발급 로직 수행 및 이메일 전송
        userService.forgotPassword(email);
        return ResponseEntity.ok("임시 비밀번호 발급됨, 이메일 확인");
    }

    /**
     * 인증된 사용자의 상세 정보를 조회하는 엔드포인트.
     * JWT 토큰으로 인증된 사용자 정보를 기반으로 해당 사용자의 프로필 정보를 반환합니다.
     * 반환되는 정보에는 이메일, 퍼스트네임, 라스트네임, 생성일, 업데이트일, 유저네임,
     * 인증 여부, 신고 여부, 그리고 프로필 사진의 공개 URL이 포함됩니다.
     *
     * @param customUserDetails 인증된 사용자 정보 (CustomUserDetails)
     * @return 사용자 상세 정보를 담은 UserInfoResponseDto (JSON 형식)
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal com.jandi.plan_backend.security.CustomUserDetails customUserDetails) {
        // 인증된 사용자 정보가 없는 경우 401 에러 반환
        if (customUserDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }
        // 인증된 사용자 정보에서 사용자 고유 ID 추출
        Integer userId = customUserDetails.getUserId();
        // 사용자 상세 정보 조회 (UserService에서 조회)
        UserInfoResponseDto dto = userService.getUserInfo(userId);
        return ResponseEntity.ok(dto);
    }
}
