package com.wanted.moneybeetle.global.file;

/* comment.
*   담당 : SCM (회원·공통)
*   파일 업로드 저장 - 용도별로 용량·형식 검사
*   - saveImage : 프로필 이미지, 강의 썸네일 (2MB, jpg·png / DP-32)
*   - saveVideo : 강의 영상 (500MB, mp4 / DP-33)
*  */

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    // 이미지 (DP-32) : 2MB, jpg·png
    private static final List<String> IMAGE_EXT = List.of(".jpg", ".jpeg", ".png");
    private static final long IMAGE_MAX_SIZE = 2L * 1024 * 1024;

    // 영상 (DP-33) : 500MB, mp4
    private static final List<String> VIDEO_EXT = List.of(".mp4");
    private static final long VIDEO_MAX_SIZE = 500L * 1024 * 1024;

    @Value("${file.upload-dir}")
    private String uploadDir;                         // yaml에서 저장 폴더 값 가져오기

    // 프로필 이미지·강의 썸네일 저장
    public String saveImage(MultipartFile file) throws IOException {
        return save(file, IMAGE_EXT, IMAGE_MAX_SIZE);
    }

    // 강의 영상 저장
    public String saveVideo(MultipartFile file) throws IOException {
        return save(file, VIDEO_EXT, VIDEO_MAX_SIZE);
    }

    // 파일을 저장하고, 화면에서 쓸 주소("/uploads/저장이름")를 돌려줌 (밖에서는 saveImage / saveVideo 로만 부른다)
    private String save(MultipartFile file, List<String> allowedExt, long maxSize) throws IOException {

        // ① 용량 검사
        if (file.getSize() > maxSize) {
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        }

        // ② 원본 파일 이름 꺼내기 - 이름이 없거나 점(.)이 없으면 확장자를 알 수 없음 → 500 대신 형식 오류
        String originName = file.getOriginalFilename();
        if (originName == null || !originName.contains(".")) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        // ③ 확장자 분리 + 소문자
        String ext = originName.substring(originName.lastIndexOf(".")).toLowerCase(Locale.ROOT);

        // ④ 허용하지 않는 확장자면 업무 예외 던지기
        if (!allowedExt.contains(ext)) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        // ④-2 이름만 바꾼 파일 막기 - 파일 내용이 확장자와 같은 형식인지 확인
        if (!matchesContent(file, ext)) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        // ⑤ 중복 방지하기 위한 UUID
        String savedName = UUID.randomUUID().toString() +ext;

        // ⑥ 파일 저장할 폴더 생성
        File dir = new File(uploadDir).getAbsoluteFile();
        dir.mkdirs();

        // ⑦ 실제로 저장
        // 업로드한 파일을 생성하고 생성한 디렉터리에 저장할 이름으로 변환 후 저장
        file.transferTo(new File(dir, savedName));

        return "/uploads/" + savedName;
    }

    /* comment.
    *   파일 맨 앞 몇 바이트(매직 넘버)는 형식마다 정해져 있다
    *   - JPEG : FF D8 FF
    *   - PNG  : 89 50 4E 47 0D 0A 1A 0A
    *   - MP4  : 5~8번째 바이트가 "ftyp"
    *  */
    private boolean matchesContent(MultipartFile file, String ext) throws IOException {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }

        return switch (ext) {
            case ".jpg", ".jpeg" -> startsWith(head, read, 0xFF, 0xD8, 0xFF);
            case ".png" -> startsWith(head, read, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case ".mp4" -> read >= 8
                    && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            default -> false;
        };
    }

    // head 의 앞부분이 signature 와 같은지 비교 (읽은 길이가 모자라면 false)
    private boolean startsWith(byte[] head, int read, int... signature) {
        if (read < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
