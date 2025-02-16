package com.jandi.plan_backend.commu.controller;

import com.jandi.plan_backend.commu.dto.*;
import com.jandi.plan_backend.commu.entity.Community;
import com.jandi.plan_backend.commu.service.CommunityService;
import com.jandi.plan_backend.user.dto.AuthResponse;
import com.jandi.plan_backend.user.security.JwtTokenProvider;
import io.jsonwebtoken.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;
    private final JwtTokenProvider jwtTokenProvider; // 추가


    public CommunityController(CommunityService communityService, JwtTokenProvider jwtTokenProvider) {
        this.communityService = communityService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 페이지 단위로 게시물 리스트 조회 */
    @GetMapping("/posts")
    public Map<String, Object> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<CommunityListDTO> postsPage = communityService.getAllPosts(page, size);

        return Map.of(
                "pageInfo", Map.of(
                        "currentPage", postsPage.getNumber(),  // 현재 페이지 번호
                        "currentSize", postsPage.getContent().size(), //현재 페이지 리스트 갯수
                        "totalPages", postsPage.getTotalPages(),  // 전체 페이지 번호 개수
                        "totalSize", postsPage.getTotalElements() // 전체 게시물 리스트 개수
                ),
                "items", postsPage.getContent()   // 현재 페이지의 게시물 데이터
        );
    }

    /** 특정 게시물의 정보 조회 */
    @GetMapping("/post")
    public CommunityItemDTO getPost(@RequestParam Integer postId) {
        return communityService.getPostItem(postId);
    }

    /** 페이지 단위로 특정 게시물의 댓글만 조회 */
    @GetMapping("/comments")
    public Map<String, Object> getParentComments(
            @RequestParam(required = false) Integer postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ParentCommentDTO> parentCommentsPage = communityService.getParentComments(postId, page, size);

        return Map.of(
                "pageInfo", Map.of(
                        "currentPage", parentCommentsPage.getNumber(),
                        "currentSize", parentCommentsPage.getContent().size(),
                        "totalPages", parentCommentsPage.getTotalPages(),
                        "totalSize", parentCommentsPage.getTotalElements()
                ),
                "items", parentCommentsPage.getContent()
        );
    }

    /**페이지 단위로 특정 댓글의 답글만 조회*/
    @GetMapping("/replies")
    public Map<String, Object> getReplies(
            @RequestParam(required = false) Integer parentCommentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        Page<repliesDTO> repliesPage = communityService.getReplies(parentCommentId, page, size);

        return Map.of(
                "pageInfo", Map.of(
                        "currentPage", repliesPage.getNumber(),
                        "currentSize", repliesPage.getContent().size(),
                        "totalPages", repliesPage.getTotalPages(),
                        "totalSize", repliesPage.getTotalElements()
                ),
                "items", repliesPage.getContent()
        );
    }

    @PostMapping("/posts/write")
    public ResponseEntity<?> writePost(
            @RequestHeader("Authorization") String token, // 헤더의 Authorization에서 JWT 토큰 받기
            @RequestBody CommunityWritePostDTO postDTO // JSON 형식으로 게시글 작성 정보 받기
    ){

        // Jwt 토큰으로부터 유저 이메일 추출
        String jwtToken = token.replace("Bearer ", "");
        String userEmail = jwtTokenProvider.getEmail(jwtToken);

        // 게시글 저장 및 반환
        CommunityWriteRespDTO savedPost = communityService.writePost(postDTO, userEmail);
        return ResponseEntity.ok(savedPost);
    }
}
