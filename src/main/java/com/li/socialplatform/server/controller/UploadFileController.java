package com.li.socialplatform.server.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.DeleteFileUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.mapper.FileMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.server.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
@Tag(name = "文件上传", description = "图片上传与删除（MinIO 对象存储），支持帖子图片和头像")
@RequiredArgsConstructor
public class UploadFileController {
    private final SystemConstants systemConstants;

    private final FileMapper fileMapper;

    private final PostMapper postMapper;

    private final UserIdUtil userIdUtil;

    private final FileStorageService fileStorageService;

    private final DeleteFileUtil deleteFileUtil;

    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    ));

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /** 头像宽高比 1:1 */
    private static final double AVATAR_ASPECT_RATIO = 1.0;

    /** 宽高比允许误差（±2%，兼容轻微裁切偏差） */
    private static final double ASPECT_RATIO_TOLERANCE = 0.02;

    @PostMapping("/post")
    @Operation(summary = "上传帖子图片", description = "上传帖子图片（最大10MB，支持jpg/png/gif/webp等格式，不限制宽高比）")
    public Result uploadBlogImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile image,
            @Parameter(description = "帖子ID") @RequestParam("postId") Long postId) {
        return upload(image, postId, null, null);
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传用户头像（最大10MB，支持jpg/png/gif/webp等格式，宽高比须为1:1）")
    public Result uploadAvatarImage(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile image) {
        return upload(image, null, AVATAR_ASPECT_RATIO, "1:1");
    }

    @PostMapping("/background")
    @Operation(summary = "上传主页背景图", description = "上传个人主页背景图（最大10MB，支持jpg/png/gif/webp等格式，不限宽高比）")
    public Result uploadBackgroundImage(
            @Parameter(description = "背景图文件") @RequestParam("file") MultipartFile image) {
        return upload(image, null, null, null);
    }

    @GetMapping("/check")
    @Operation(summary = "秒传预检", description = "按文件 SHA-256 查询服务端是否已存在相同内容；命中则复用已有 URL 并补登一条复用记录，客户端可直接跳过文件传输")
    public Result checkFileHash(
            @Parameter(description = "文件 SHA-256（64 位十六进制小写）") @RequestParam("hash") String hash,
            @Parameter(description = "帖子ID（帖子图片必传，头像不传）") @RequestParam(value = "postId", required = false) Long postId) {
        if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
            return Result.error("无效的文件哈希");
        }
        hash = hash.toLowerCase();

        // 桶内已有同哈希内容（同哈希可能对应多条复用记录，LIMIT 1 取任意一条）
        com.li.socialplatform.pojo.entity.File file = fileMapper.selectOne(
                new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getHash, hash)
                        .last("LIMIT 1"));
        if (file == null) {
            return Result.ok(null); // 未命中：客户端需正常上传文件内容
        }

        // 命中：为当前用户/帖子补登一条复用记录（同组合已存在则幂等跳过）
        Long userId = userIdUtil.getUserId();
        Long count = fileMapper.selectCount(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                .eq(com.li.socialplatform.pojo.entity.File::getHash, hash)
                .eq(postId != null, com.li.socialplatform.pojo.entity.File::getPostId, postId)
                .eq(userId != null, com.li.socialplatform.pojo.entity.File::getUserId, userId));
        if (count <= 0) {
            fileMapper.insert(new com.li.socialplatform.pojo.entity.File(null, postId, userId, file.getUrl(), hash));
        }
        return Result.ok(systemConstants.baseUrl + file.getUrl());
    }

    private Result upload(MultipartFile image, Long postId, Double targetRatio, String ratioLabel) {
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

            // 若指定了目标比例（如头像1:1），则校验图片宽高比例
            if (targetRatio != null) {
                int[] dimensions = readImageDimensions(bytes);
                if (dimensions == null) {
                    return Result.error("无法解析图片宽高，请上传有效的图片文件");
                }
                double actualRatio = (double) dimensions[0] / dimensions[1];
                if (Math.abs(actualRatio - targetRatio) > targetRatio * ASPECT_RATIO_TOLERANCE) {
                    return Result.error(StrUtil.format("图片宽高比例须为{}，当前为{}×{}",
                            ratioLabel, dimensions[0], dimensions[1]));
                }
            }

            // 计算文件的SHA256哈希值
            String sha256Hash = calculateSHA256(bytes);
            // 获取当前登录用户id
            Long userId = userIdUtil.getUserId();
            // 判断文件是否已存在（同哈希可能被多个帖子/用户复用，LIMIT 1 取任意一条即可）
            com.li.socialplatform.pojo.entity.File existingFile = fileMapper.selectOne(
                    new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                            .eq(com.li.socialplatform.pojo.entity.File::getHash, sha256Hash)
                            .eq(postId != null, com.li.socialplatform.pojo.entity.File::getPostId, postId)
                            .eq(userId != null, com.li.socialplatform.pojo.entity.File::getUserId, userId)
                            .last("LIMIT 1"));
            if (existingFile != null) {
                return Result.ok(systemConstants.baseUrl + existingFile.getUrl());
            }
            com.li.socialplatform.pojo.entity.File file = fileMapper.selectOne(
                    new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                            .eq(com.li.socialplatform.pojo.entity.File::getHash, sha256Hash)
                            .last("LIMIT 1"));
            if (file != null) {
                // 插入数据库
                file.setPostId(postId);
                file.setUserId(userId);
                file.setId(null);
                fileMapper.insert(file);
                return Result.ok(systemConstants.baseUrl + file.getUrl());
            }


            String fileUrl = createNewFileName(originalFilename, sha256Hash);

            // 上传到 MinIO，对象名 = fileUrl 去掉开头的 /
            fileStorageService.upload(fileUrl.substring(1), bytes, image.getContentType());

            int success = fileMapper.insert(
                    new com.li.socialplatform.pojo.entity.File(null, postId, userId, fileUrl, sha256Hash));
            if (success <= 0) {
                return Result.error("文件上传失败");
            }

            log.debug("文件上传成功，SHA256: {}, 对象名: {}", sha256Hash, fileUrl);
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

        // 同一 url 可能因哈希复用存在多条记录，仅处理当前用户名下的记录
        List<com.li.socialplatform.pojo.entity.File> fileRows = fileMapper.selectList(
                new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getUrl, url));
        Long currentUserId = userIdUtil.getUserId();
        boolean owned = fileRows.stream().anyMatch(f -> Objects.equals(f.getUserId(), currentUserId));

        if (fileRows.isEmpty()) {
            return Result.ok();
        }
        if (!owned) {
            return Result.error("没有权限删除该文件");
        }

        com.li.socialplatform.pojo.entity.File fileEntity = fileRows.get(0);
        fileMapper.delete(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                .eq(com.li.socialplatform.pojo.entity.File::getUrl, url)
                .eq(com.li.socialplatform.pojo.entity.File::getUserId, userIdUtil.getUserId()));
        // 同哈希对象可能被其他记录复用（SHA-256 物理去重），仅当无其他引用时才删除存储对象
        Long count = fileMapper.selectCount(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                .eq(com.li.socialplatform.pojo.entity.File::getHash, fileEntity.getHash()));
        if (count <= 0) {
            deleteFileUtil.deleteFile(url);
        }
        return Result.ok();
    }

    @DeleteMapping("/delete/{postId}")
    @Operation(summary = "删除帖子所有文件", description = "删除指定帖子关联的所有图片，并清理无引用的物理对象")
    public Result deleteFile(
            @Parameter(description = "帖子ID") @PathVariable Long postId) {
        if (postId == null) {
            return Result.error("参数不能为空");
        }

        Long currentUserId = userIdUtil.getUserId();

        // 校验当前用户是否为帖子作者（若帖子已发布入库）
        Post post = postMapper.selectById(postId);
        if (post != null && !Objects.equals(post.getUserId(), currentUserId)) {
            return Result.error("没有权限删除该帖子的文件");
        }

        List<com.li.socialplatform.pojo.entity.File> currentFiles = fileMapper.selectList(
                new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getPostId, postId));

        if (currentFiles.isEmpty()) {
            return Result.ok();
        }

        // 校验这些文件是否为当前登录用户上传（防止越权删除未发布帖子的图片）
        boolean hasUnauthorizedFile = currentFiles.stream()
                .anyMatch(f -> !Objects.equals(f.getUserId(), currentUserId));
        if (hasUnauthorizedFile) {
            return Result.error("没有权限删除该帖子的文件");
        }

        fileMapper.delete(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                .eq(com.li.socialplatform.pojo.entity.File::getPostId, postId));

        for (com.li.socialplatform.pojo.entity.File file : currentFiles) {
            Long count = fileMapper.selectCount(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                    .eq(com.li.socialplatform.pojo.entity.File::getHash, file.getHash()));
            if (count <= 0) {
                deleteFileUtil.deleteFile(file.getUrl());
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
