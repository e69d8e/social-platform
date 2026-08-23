package com.li.socialplatform.server.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.mapper.FileMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author e69d8e
 * @since 2025/12/9 14:25
 */
@Slf4j
@RestController
@RequestMapping("/upload")
@Tag(name = "文件上传", description = "图片上传与删除，支持帖子图片和头像")
@RequiredArgsConstructor
public class UploadFileController {
    private final SystemConstants systemConstants;

    private final FileMapper fileMapper;

    private final PostMapper postMapper;

    private final UserIdUtil userIdUtil;

    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    ));

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 启动时校验图片上传目录，尽早发现权限/配置问题。
     * 校验失败仅记录错误日志，不阻塞应用启动；上传时会给出明确错误提示。
     */
    @PostConstruct
    public void initUploadDir() {
        File dir = new File(systemConstants.imageUploadDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("图片上传目录创建失败：{}，请检查 IMAGE_PATH 配置及目录权限", dir.getAbsolutePath());
            return;
        }
        if (!dir.isDirectory()) {
            log.error("图片上传目录不是一个目录：{}，请检查 IMAGE_PATH 配置", dir.getAbsolutePath());
            return;
        }
        if (!dir.canWrite()) {
            log.error("图片上传目录不可写：{}，请为应用运行用户授予写权限", dir.getAbsolutePath());
            return;
        }
        log.info("图片上传目录校验通过：{}", dir.getAbsolutePath());
    }

    /** 头像宽高比 1:1 */
    private static final double AVATAR_ASPECT_RATIO = 1.0;

    /** 帖子封面宽高比 5:3 */
    private static final double POST_COVER_ASPECT_RATIO = 5.0 / 3.0;

    /** 宽高比允许误差（±2%，兼容轻微裁切偏差） */
    private static final double ASPECT_RATIO_TOLERANCE = 0.02;

    @PostMapping("/post")
    @Operation(summary = "上传帖子封面", description = "上传帖子封面（最大10MB，支持jpg/png/gif/webp等格式，宽高比须为5:3）")
    public Result uploadBlogImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile image,
            @Parameter(description = "帖子ID") @RequestParam("postId") Long postId) {
        return upload(image, postId, POST_COVER_ASPECT_RATIO, "5:3");
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传用户头像（最大10MB，支持jpg/png/gif/webp等格式，宽高比须为1:1）")
    public Result uploadAvatarImage(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile image) {
        return upload(image, null, AVATAR_ASPECT_RATIO, "1:1");
    }

    private Result upload(MultipartFile image, Long postId, double targetRatio, String ratioLabel) {
        try {
            if (image.isEmpty()) {
                return Result.error("文件不能为空");
            }

            if (image.getSize() > MAX_FILE_SIZE) {
                return Result.error("文件大小不能超过10MB");
            }

            String originalFilename = image.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                return Result.error("无效的文件名");
            }

            String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase();
            if (!ALLOWED_IMAGE_TYPES.contains(suffix)) {
                return Result.error("不支持的文件类型");
            }

            // 获取文件bytes
            byte[] bytes = image.getBytes();

            // 校验图片宽高比例（头像1:1，帖子封面5:3）
            int[] dimensions = readImageDimensions(bytes);
            if (dimensions == null) {
                return Result.error("无法解析图片宽高，请上传有效的图片文件");
            }
            double actualRatio = (double) dimensions[0] / dimensions[1];
            if (Math.abs(actualRatio - targetRatio) > targetRatio * ASPECT_RATIO_TOLERANCE) {
                return Result.error(StrUtil.format("图片宽高比例须为{}，当前为{}×{}",
                        ratioLabel, dimensions[0], dimensions[1]));
            }

            // 计算文件的SHA256哈希值
            String sha256Hash = calculateSHA256(bytes);
            // 获取当前登录用户id
            Long userId = userIdUtil.getUserId();
            // 判断文件是否已存在
            com.li.socialplatform.pojo.entity.File existingFile = fileMapper.selectOne(
                    new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                            .eq(com.li.socialplatform.pojo.entity.File::getHash, sha256Hash)
                            .eq(postId != null, com.li.socialplatform.pojo.entity.File::getPostId, postId)
                            .eq(userId != null, com.li.socialplatform.pojo.entity.File::getUserId, userId));
            if (existingFile != null) {
                return Result.ok(systemConstants.baseUrl + existingFile.getUrl());
            }
            com.li.socialplatform.pojo.entity.File file = fileMapper.selectOne(
                    new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                            .eq(com.li.socialplatform.pojo.entity.File::getHash, sha256Hash));
            if (file != null) {
                // 插入数据库
                file.setPostId(postId);
                file.setUserId(userId);
                file.setId(null);
                fileMapper.insert(file);
                return Result.ok(systemConstants.baseUrl + file.getUrl());
            }


            String fileUrl = createNewFileName(originalFilename, sha256Hash);

            File destFile = new File(systemConstants.imageUploadDir, fileUrl).getAbsoluteFile();

            // 确保存储子目录存在（并发时 mkdirs 可能返回 false，需重新确认）
            File parentDir = destFile.getParentFile();
            if (!parentDir.isDirectory() && !parentDir.mkdirs() && !parentDir.isDirectory()) {
                log.error("创建图片存储目录失败：{}，当前运行用户无写权限", parentDir.getAbsolutePath());
                return Result.error("图片保存失败：服务器上传目录无写权限，请联系管理员检查 IMAGE_PATH 目录权限");
            }

            log.info("保存文件到: {}", destFile.getAbsolutePath());
            image.transferTo(destFile);

            int success = fileMapper.insert(
                    new com.li.socialplatform.pojo.entity.File(null, postId, userId, fileUrl, sha256Hash));
            if (success <= 0) {
                return Result.error("文件上传失败");
            }

            log.debug("文件上传成功，SHA256: {}, 文件名: {}", sha256Hash, fileUrl);
            return Result.ok(systemConstants.baseUrl + fileUrl);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件", description = "根据URL删除单个文件（需验证所有权）")
    public Result deleteFile(
            @Parameter(description = "文件URL") @RequestParam("url") String url) {

        if (url == null || url.isEmpty()) {
            return Result.error("文件名称不能为空");
        }

        if (!url.startsWith(systemConstants.baseUrl)) {
            return Result.error("无效的文件路径格式");
        }

        url = url.substring(systemConstants.baseUrl.length());

        if (!url.startsWith("/")) {
            return Result.error("无效的文件路径格式");
        }

        if (url.contains("..")) {
            return Result.error("非法的文件路径");
        }

        com.li.socialplatform.pojo.entity.File fileEntity = fileMapper.selectOne(
                new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getUrl, url));

        if (fileEntity == null) {
            return Result.ok();
        }

        if (!Objects.equals(fileEntity.getUserId(), userIdUtil.getUserId())) {
            return Result.error("没有权限删除该文件");
        }

        String relativePath = url.startsWith("/") ? url.substring(1) : url;
        File file = new File(systemConstants.imageUploadDir, relativePath);
        if (file.isDirectory()) {
            return Result.ok();
        }

        // 验证最终路径确实在允许的目录内
        try {
            String canonicalPath = file.getCanonicalPath();
            String canonicalBase = new File(systemConstants.imageUploadDir).getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalBase + File.separator)) {
                return Result.error("非法的文件路径");
            }
        } catch (Exception e) {
            return Result.error("路径解析失败");
        }

        boolean deleted = FileUtil.del(file);
        if (deleted) {
            fileMapper.deleteById(fileEntity.getId());
            return Result.ok();
        } else {
            return Result.error("文件删除失败");
        }
    }

    @DeleteMapping("/delete/{postId}")
    @Operation(summary = "删除帖子所有文件", description = "删除指定帖子关联的所有图片，并清理无引用的物理文件")
    public Result deleteFile(
            @Parameter(description = "帖子ID") @PathVariable Long postId) {
        if (postId == null) {
            return Result.error("参数不能为空");
        }

        // 校验当前用户是否为帖子作者
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return Result.error("帖子不存在");
        }
        if (!Objects.equals(post.getUserId(), userIdUtil.getUserId())) {
            return Result.error("没有权限删除该帖子的文件");
        }
        List<com.li.socialplatform.pojo.entity.File> currentFiles = fileMapper.selectList(
                new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getPostId, postId));
        fileMapper.delete(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                .eq(com.li.socialplatform.pojo.entity.File::getPostId, postId));

        for (com.li.socialplatform.pojo.entity.File file : currentFiles) {
            String hash = file.getHash();
            Long count = fileMapper.selectCount(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                    .eq(com.li.socialplatform.pojo.entity.File::getHash, hash));
            if (count <= 0) {
                String relativePath = file.getUrl().startsWith("/") ? file.getUrl().substring(1) : file.getUrl();
                File fileToDelete = new File(systemConstants.imageUploadDir, relativePath);
                FileUtil.del(fileToDelete);
            }
        }
        return Result.ok();
    }


    /**
     * 读取图片宽高（优先只读文件头，避免完整解码开销）。
     * 无法识别时返回 null。
     */
    private int[] readImageDimensions(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } catch (Exception e) {
                    log.debug("读取图片文件头获取尺寸失败，尝试完整解码: {}", e.getMessage());
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.debug("创建图片输入流失败: {}", e.getMessage());
        }
        // 兜底：完整解码读取宽高
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception e) {
            log.warn("完整解码后仍无法获取图片宽高", e);
        }
        return null;
    }


    private String createNewFileName(String originalFilename, String sha256Hash) {
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        String name = sha256Hash.substring(0, 16);

        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;

        return StrUtil.format("/{}/{}/{}.{}", d1, d2, name, suffix);
    }


    private String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
