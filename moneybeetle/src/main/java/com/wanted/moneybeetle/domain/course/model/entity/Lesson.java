package com.wanted.moneybeetle.domain.course.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테이블 lesson (영상 + 요약글, 강의당 1~3개)
 *   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#157)
 *  */
@Entity
@Table(name = "lesson")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lessonId;

    private Long courseId;          // 같은 도메인이지만 우선 ID로 참조 (연관관계는 팀 합의 전)
    private int sortOrder;          // 영상 순서 1~3
    private String title;
    private String videoUrl;
    private int durationSeconds;    // 재생 시간(초)
    private String summary;         // 요약글 (2,000자 이내)

    /* 재생 위치·본 시간이 0초보다 작거나 영상 길이를 넘지 않게 맞춘다 */
    public int clampSeconds(int seconds) {
        int notNegative = Math.max(seconds, 0);
        return durationSeconds > 0 ? Math.min(notNegative, durationSeconds) : notNegative;
    }

    /* 새 영상 (API 78). 순서·내용 검사는 서비스가 끝낸 뒤 부른다 */
    public static Lesson create(Long courseId, int sortOrder, String title, String videoUrl,
                                int durationSeconds, String summary) {
        Lesson lesson = new Lesson();
        lesson.courseId = courseId;
        lesson.sortOrder = sortOrder;
        lesson.change(title, videoUrl, durationSeconds, summary);
        return lesson;
    }

    /* 영상 제목·파일·길이·요약글 바꾸기 (API 78) */
    public void change(String title, String videoUrl, int durationSeconds, String summary) {
        this.title = title;
        this.videoUrl = videoUrl;
        this.durationSeconds = durationSeconds;
        this.summary = summary;
    }

    /* 영상 순서 바꾸기 (API 78). (강의, 순서)가 유일해서 서비스가 빈 자리로 하나씩 옮긴다 */
    public void changeOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
