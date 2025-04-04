package com.jandi.plan_backend.commu.comment.dto;

import lombok.Data;
import lombok.NonNull;

/**
 * 댓글 작성 시 클라이언트로부터 전달되는 데이터를 담는 DTO
 * 댓글 관계(parentCommentId)와 댓글 내용을 저장한다.
 */
@Data
public class CommentReqDTO {

    @NonNull
    String contents;
}
