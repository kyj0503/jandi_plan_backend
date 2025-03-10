package com.jandi.plan_backend.user.controller;

import com.jandi.plan_backend.commu.dto.UserListDTO;
import com.jandi.plan_backend.security.JwtTokenProvider;
import com.jandi.plan_backend.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/manage/user")
public class ManageUserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public ManageUserController(UserService userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 유저 목록 조회 */
    @GetMapping("/all")
    public Map<String, Object> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token // 헤더의 Authorization에서 JWT 토큰 받기
    ) {
        // Jwt 토큰으로부터 유저 이메일 추출
        String jwtToken = token.replace("Bearer ", "");
        String userEmail = jwtTokenProvider.getEmail(jwtToken);

        Page<UserListDTO> userPage = userService.getAllUsers(userEmail, page, size);

        return Map.of(
                "pageInfo", Map.of(
                        "currentPage", userPage.getNumber(),  // 현재 페이지 번호
                        "currentSize", userPage.getContent().size(), //현재 페이지 리스트 갯수
                        "totalPages", userPage.getTotalPages(),  // 전체 페이지 번호 개수
                        "totalSize", userPage.getTotalElements() // 전체 게시물 리스트 개수
                ),
                "items", userPage.getContent()   // 현재 페이지의 게시물 데이터
        );
    }

    /** 부적절 유저 목록 조회 */
    @GetMapping("/restricted")
    public Map<String, Object> getRestrictedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token // 헤더의 Authorization에서 JWT 토큰 받기
    ) {
        // Jwt 토큰으로부터 유저 이메일 추출
        String jwtToken = token.replace("Bearer ", "");
        String userEmail = jwtTokenProvider.getEmail(jwtToken);

        Page<UserListDTO> userPage = userService.getRestrictedUsers(userEmail, page, size);

        return Map.of(
                "pageInfo", Map.of(
                        "currentPage", userPage.getNumber(),  // 현재 페이지 번호
                        "currentSize", userPage.getContent().size(), //현재 페이지 리스트 갯수
                        "totalPages", userPage.getTotalPages(),  // 전체 페이지 번호 개수
                        "totalSize", userPage.getTotalElements() // 전체 게시물 리스트 개수
                ),
                "items", userPage.getContent()   // 현재 페이지의 게시물 데이터
        );
    }

    /** 부적절 유저 제한/제한 해제 */
    //제한된 유저 -> 해제 / 일반 유저 -> 제한
    @PostMapping("/permit/{userId}")
    public ResponseEntity<?> permitUser(
        @PathVariable Integer userId,
        @RequestHeader("Authorization") String token // 헤더의 Authorization에서 JWT 토큰 받기
        ){
        // Jwt 토큰으로부터 유저 이메일 추출
        String jwtToken = token.replace("Bearer ", "");
        String userEmail = jwtTokenProvider.getEmail(jwtToken);

        Boolean isReported = userService.permitUser(userEmail, userId);
        return ResponseEntity.ok((isReported) ?
                "제한되었습니다" : "제한 해제되었습니다");
    }

    /** 부적절 유저 탈퇴 */
    //제한된 유저 -> 해제 / 일반 유저 -> 제한
    @PostMapping("/withdraw/{userId}")
    public ResponseEntity<?> withdrawUser(
        @PathVariable Integer userId,
        @RequestHeader("Authorization") String token // 헤더의 Authorization에서 JWT 토큰 받기
        ){
        // Jwt 토큰으로부터 유저 이메일 추출
        String jwtToken = token.replace("Bearer ", "");
        String userEmail = jwtTokenProvider.getEmail(jwtToken);

        Boolean isWithdraw = userService.withdrawUser(userEmail, userId);
        return ResponseEntity.ok((isWithdraw) ?
                "탈퇴되었습니다" : "탈퇴에 문제가 발생했습니다");
    }
}
