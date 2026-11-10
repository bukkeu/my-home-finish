package com.wanted.moneybeetle.domain.operation.model.service;

import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.dao.AdminLogRepository;
import com.wanted.moneybeetle.domain.operation.model.dto.AdminLogDTO;
import com.wanted.moneybeetle.domain.operation.model.dto.AdminLogListDTO;
import com.wanted.moneybeetle.domain.operation.model.entity.AdminLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   관리자 작업 기록 조회 (이슈 #39, API 113, NFR-14). 기록을 남기는 쪽은 AdminLogService 이다.
*   - 대상(targetType·targetId)·관리자(adminId)·기간(from·to 날짜, 둘 다 그날을 포함)으로 최신순 조회한다. 모두 선택 조건이다.
*   - 한 페이지는 최대 100건, 기본 50건이다.
*   - 관리자 닉네임은 member 도메인의 MemberService.findNicknames 로 한 번에 가져온다 (Repository 를 직접 쓰지 않는다).
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLogQueryService {

    private final AdminLogRepository adminLogRepository;
    private final MemberService memberService;

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 100;

    public AdminLogListDTO findLogs(String targetType, Long targetId, Long adminId,
                                    LocalDate from, LocalDate to, Integer page, Integer size) {
        int pageNo = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        var start = from == null ? null : from.atStartOfDay();
        var end = to == null ? null : to.plusDays(1).atStartOfDay();
        List<AdminLog> logs = adminLogRepository.search(blankToNull(targetType), targetId, adminId, start, end,
                PageRequest.of(pageNo, pageSize))
                .getContent();
        Map<Long, String> nicknames = memberService.findNicknames(logs.stream().map(AdminLog::getAdminId).toList());
        return new AdminLogListDTO(logs.stream()
                .map(log -> AdminLogDTO.of(log, nicknames.get(log.getAdminId())))
                .toList());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
