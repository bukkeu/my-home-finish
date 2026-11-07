package com.wanted.moneybeetle.global.file;

/* comment.
*   담당 : SCM (회원·공통)
*   파일 업로드 저장 (프로필 이미지 2MB jpg·png, 강의 썸네일)
*   DP-32
*  */

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    // 허용 확장자 (DP-32)
    private static final List<String> ALLOWED_EXT = List.of(".jpg", ".jpeg", ".png");

    @Value("${file.upload-dir}")
    private String uploadDir;                         // ② yaml에서 저장 폴더 값 가져오기

    // 파일을 저장하고, 화면에서 쓸 주소("/uploads/저장이름")를 돌려줌
    public String save(MultipartFile file) throws IOException {

        // ③ 원본 파일 이름 꺼내기
        String originName = file.getOriginalFilename();

        // ④ 확장자 분리 + 소문자
        String ext = originName.substring(originName.lastIndexOf(".")).toLowerCase(Locale.ROOT);

        // ⑤ 허용하지 않는 확장자면 업무 예외 던지기
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        // ⑥ 중복 방지하기 위한 UUID
        String savedName = UUID.randomUUID().toString() +ext;

        // ⑦ 파일 저장할 폴더 생성
        File dir = new File(uploadDir).getAbsoluteFile();
        dir.mkdirs();

        // ⑧ 실제로 저장
        // 업로드한 파일을 생성하고 생성한 디렉터리에 저장할 이름으로 변환 후 저장
        file.transferTo(new File(dir, savedName));

        return "/uploads/" + savedName;
    }
}
