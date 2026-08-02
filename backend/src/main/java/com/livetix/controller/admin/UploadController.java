package com.livetix.controller.admin;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.livetix.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class UploadController {

    @Value("${livetix.upload.dir}")
    private String uploadDir;

    @Value("${livetix.upload.url-prefix:/uploads}")
    private String urlPrefix;

    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"};
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @SaCheckLogin
    @SaCheckRole("admin")
    @PostMapping("/upload/image")
    public Result<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("文件为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail("文件大小不能超过 10MB");
        }

        try {
            // 28 修复: 用魔术数字（文件头）校验真实类型，替代可伪造的 Content-Type
            byte[] header = new byte[8];
            file.getInputStream().read(header);

            String allowedExt = validateMagicNumber(header);
            if (allowedExt == null) {
                return Result.fail("仅支持 JPG/PNG/GIF/WebP 格式");
            }

            // 解析为绝对路径
            Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();

            // 确保基础目录存在
            File baseDir = basePath.toFile();
            if (!baseDir.exists()) {
                boolean created = baseDir.mkdirs();
                log.info("Created upload base dir: {} -> {}", basePath, created);
            }

            // 按日期分目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path uploadPath = basePath.resolve(dateDir);
            Files.createDirectories(uploadPath);

            // 28: 只使用魔术数字验证的安全扩展名，不信任客户端原始扩展名
            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + allowedExt;
            Path filePath = uploadPath.resolve(fileName);

            // 保存
            file.transferTo(filePath.toFile());
            log.info("File saved to: {}", filePath.toAbsolutePath());

            // 访问 URL
            String url = urlPrefix + "/" + dateDir + "/" + fileName;
            log.info("Upload OK: {} -> {}", file.getOriginalFilename(), url);

            return Result.ok("上传成功", Map.of("url", url, "name", file.getOriginalFilename()));
        } catch (Exception e) {
            log.error("Upload failed", e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    /**
     * 28: 通过文件头魔术数字验证真实文件类型
     * @return 安全扩展名（不含点），null 表示类型不支持
     */
    private String validateMagicNumber(byte[] header) {
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            return "png";
        }
        // GIF: 47 49 46 38
        if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46 && header[3] == (byte) 0x38) {
            return "gif";
        }
        // WebP: 52 49 46 46 ... 57 45 42 50
        if (header[0] == (byte) 0x52 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                && header[8] == (byte) 0x57 && header[9] == (byte) 0x45 && header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
            return "webp";
        }
        return null;
    }
}
