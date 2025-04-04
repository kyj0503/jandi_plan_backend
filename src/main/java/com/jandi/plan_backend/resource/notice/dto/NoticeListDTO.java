package com.jandi.plan_backend.resource.notice.dto;

import com.jandi.plan_backend.resource.notice.entity.Notice;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NoticeListDTO {
    private final Integer noticeId;
    private final LocalDateTime createdAt;
    private final String title;
    private final String content;

    public NoticeListDTO(Notice notice) {
        this.noticeId = notice.getNoticeId();
        this.createdAt = notice.getCreatedAt();
        this.title = notice.getTitle();
        this.content = notice.getContents();
    }
}
