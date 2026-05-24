package com.li.socialplatform.server.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@RequiredArgsConstructor
public class UploadFileController {
    private final SystemConstants systemConstants;

    private final FileMapper fileMapper;

    private final UserIdUtil userIdUtil;

    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    ));

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // http://localhost:8080/imgs/0/1/73197b62-3682-4f43-bb9e-e2593b62d10d.png
    @PostMapping("/post")
    public Result uploadBlogImage(@RequestParam("file") MultipartFile image, @RequestParam("postId") Long postId) {
        return upload(image, postId);
    }

    //    http://localhost:8080/imgs/1/11/829f7288-f66e-4b8c-8c64-a9b2942270ac.png
    @PostMapping("/avatar")
    public Result uploadAvatarImage(@RequestParam("file") MultipartFile image) {
        return upload(image, null);
    }

    private Result upload(@RequestParam("file") MultipartFile image, Long postId) {
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

            File destFile = new File(systemConstants.imageUploadDir, fileUrl);
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
    public Result deleteFile(@RequestParam("url") String url) {

        if (url == null || url.isEmpty()) {
            return Result.error("文件名称不能为空");
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

        File file = new File(systemConstants.imageUploadDir, url);
        if (file.isDirectory()) {
            return Result.ok();
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
    public Result deleteFile(@PathVariable Long postId) {
        if (postId == null) {
            return Result.error("参数不能为空");
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
                File fileToDelete = new File(systemConstants.imageUploadDir, file.getUrl());
                FileUtil.del(fileToDelete);
            }
        }
        return Result.ok();
    }


    private String createNewFileName(String originalFilename, String sha256Hash) {
        // 获取原始文件名的后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);

        // 使用SHA256哈希值作为文件名的一部分，避免重复文件
        String name = sha256Hash.substring(0, 16); // 取前16位作为文件名

        // 生成目录
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;

        // 判断目录是否存在
        File dir = new File(systemConstants.imageUploadDir, StrUtil.format("/{}/{}", d1, d2));
        if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            log.info("创建目录：{}", mkdir);
        }
        // 生成文件名
        return StrUtil.format("/{}/{}/{}.{}", d1, d2, name, suffix);
    }


    // 计算SHA-256哈希值
    private String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);

        // 将字节数组转换为十六进制字符串
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
