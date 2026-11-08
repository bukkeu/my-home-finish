package com.wanted.moneybeetle.global.file;

import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/* comment.
*   FileStorageService 경계 조건 확인 (스프링 없이 단독 실행)
*   - 이름 없음 / 점 없음 / 허용하지 않는 확장자 / 이름만 바꾼 파일 / 잘리거나 다른 형식의 mp4 → FILE_TYPE_NOT_ALLOWED
*   - 이미지 2MB 초과 → FILE_TOO_LARGE, 정확히 2MB 는 통과
*  */
class FileStorageServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    // ftyp 상자(24바이트: 크기·"ftyp"·브랜드 "mp42"·버전·호환 브랜드 2개) + 뒤에 영상 데이터가 있는 형태
    private static final byte[] MP4 = mp4("mp42", 24, 16);

    private static byte[] mp4(String brand, int boxSize, int extraBytes) {
        byte[] data = new byte[boxSize + extraBytes];
        data[3] = (byte) boxSize;
        System.arraycopy("ftyp".getBytes(), 0, data, 4, 4);
        System.arraycopy(brand.getBytes(), 0, data, 8, 4);
        System.arraycopy("isommp42".getBytes(), 0, data, 16, 8);
        return data;
    }

    @TempDir
    Path uploadDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, null, content);
    }

    private void assertRejected(Runnable action, CommonErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void 진짜_이미지는_저장되고_주소를_돌려준다() throws Exception {
        String jpgUrl = service.saveImage(file("photo.JPG", JPEG));
        String pngUrl = service.saveImage(file("photo.png", PNG));

        assertThat(jpgUrl).startsWith("/uploads/").endsWith(".jpg");
        assertThat(pngUrl).startsWith("/uploads/").endsWith(".png");
        try (var files = Files.list(uploadDir)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    void 진짜_mp4_영상은_저장된다() throws Exception {
        assertThat(service.saveVideo(file("lecture.mp4", MP4))).endsWith(".mp4");
    }

    @Test
    void 이름이_없거나_점이_없으면_형식_오류() {
        assertRejected(() -> save(file(null, JPEG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertRejected(() -> save(file("photo", JPEG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 허용하지_않는_확장자는_형식_오류() {
        assertRejected(() -> save(file("doc.pdf", JPEG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertRejected(() -> saveVideo(file("photo.png", PNG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 이름만_바꾼_파일은_형식_오류() {
        byte[] text = "this is not an image".getBytes();
        assertRejected(() -> save(file("fake.png", text)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertRejected(() -> save(file("fake.jpg", PNG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertRejected(() -> saveVideo(file("fake.mp4", JPEG)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        assertRejected(() -> save(file("empty.png", new byte[0])), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void ftyp만_맞는_다른_형식이나_잘린_mp4는_형식_오류() {
        // CodeRabbit 예시 : 내용 없이 ftyp 만 있는 8바이트 파일
        byte[] onlyFtyp = {0, 0, 0, 8, 'f', 't', 'y', 'p'};
        assertRejected(() -> saveVideo(file("fake.mp4", onlyFtyp)), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        // QuickTime(.mov) 브랜드
        assertRejected(() -> saveVideo(file("movie.mp4", mp4("qt  ", 24, 16))), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
        // ftyp 상자만 있고 뒤에 영상 데이터가 없는 잘린 파일
        assertRejected(() -> saveVideo(file("cut.mp4", mp4("isom", 24, 0))), CommonErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 이미지는_정확히_2MB까지_허용하고_넘으면_용량_오류() throws Exception {
        byte[] exact = new byte[2 * 1024 * 1024];
        System.arraycopy(PNG, 0, exact, 0, PNG.length);
        assertThat(service.saveImage(file("max.png", exact))).endsWith(".png");

        byte[] over = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, over, 0, PNG.length);
        assertRejected(() -> save(file("big.png", over)), CommonErrorCode.FILE_TOO_LARGE);
    }

    // 람다 안에서 checked 예외(IOException)를 쓰기 위한 도우미
    private void save(MockMultipartFile f) {
        try {
            service.saveImage(f);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveVideo(MockMultipartFile f) {
        try {
            service.saveVideo(f);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
