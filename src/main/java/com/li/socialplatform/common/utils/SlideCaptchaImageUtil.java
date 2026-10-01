package com.li.socialplatform.common.utils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 滑块验证码图片生成器：缺口位置只在服务端，客户端拿到的是渲染好的图片。
 *
 * 几何参数必须与前端 src/components/SlideVerify.vue 保持一致，改动需两边同步：
 *  - w=310, h=155, l=42, r=10，L = l + 2r + 3 = 65
 *  - 缺口横向位置 x ∈ [75, 235]，即前端 minOffset = L+10、maxOffset = w-(L+10)
 *  - 拼图块纵向位置 y ∈ [10+2r, h-(L+10)] = [30, 80]
 */
public final class SlideCaptchaImageUtil {

    public static final int WIDTH = 310;
    public static final int HEIGHT = 155;
    /** 前端 L = l + r*2 + 3 */
    private static final int L = 65;
    /** 前端 r */
    private static final int PIECE_R = 10;
    private static final int X_MIN = 75;
    private static final int X_MAX = 235;
    private static final int Y_MIN = 30;
    private static final int Y_MAX = 80;
    /** 与前端 draw() 的 rgba(255,255,255,0.7) 一致 */
    private static final Color GAP_COLOR = new Color(255, 255, 255, 179);

    private SlideCaptchaImageUtil() {
    }

    /**
     * @param x          缺口横向位置（即校验用的答案，不下发给客户端）
     * @param y          拼图块纵向位置
     * @param bgImage    带缺口背景图 PNG base64
     * @param blockImage L×L 透明底拼图块 PNG base64
     */
    public record CaptchaImage(int x, int y, String bgImage, String blockImage) {
    }

    public static CaptchaImage generate() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x = rnd.nextInt(X_MIN, X_MAX + 1);
        int y = rnd.nextInt(Y_MIN, Y_MAX + 1);

        BufferedImage background = drawBackground();
        // 主图画缺口：半透明白色填充 + 描边，对应前端 draw(ctx, x, y, l, r, "fill")
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape gapPath = puzzlePath(x, y);
        g.setColor(GAP_COLOR);
        g.fill(gapPath);
        g.setStroke(new BasicStroke(2));
        g.draw(gapPath);
        g.dispose();

        // 拼图块：按前端 blockCtx 的 clip + getImageData 逻辑，把缺口区域裁剪到 L×L 透明画布
        Shape piecePath = puzzlePath(0, PIECE_R * 2 + 1);
        BufferedImage block = new BufferedImage(L, L, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = block.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setClip(piecePath);
        bg.translate(-x, -(y - PIECE_R * 2 - 1));
        bg.drawImage(background, 0, 0, null);
        bg.translate(x, y - PIECE_R * 2 - 1);
        bg.setClip(null);
        bg.setColor(GAP_COLOR);
        bg.setStroke(new BasicStroke(2));
        bg.draw(piecePath);
        bg.dispose();

        return new CaptchaImage(x, y, toBase64Png(background), toBase64Png(block));
    }

    /**
     * 拼图路径，逐句对应前端 SlideVerify.vue 的 draw()：
     * 方形主体 (x,y)-(x+l,y+l)，顶部/右侧外凸圆弧，左侧内收圆弧。
     */
    private static Shape puzzlePath(double x, double y) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y);
        appendArc(p, x + L / 2.0, y - PIECE_R + 2, PIECE_R, 0.72 * Math.PI, 2.26 * Math.PI);
        p.lineTo(x + L, y);
        appendArc(p, x + L + PIECE_R - 2, y + L / 2.0, PIECE_R, 1.21 * Math.PI, 2.78 * Math.PI);
        p.lineTo(x + L, y + L);
        p.lineTo(x, y + L);
        appendArc(p, x + PIECE_R - 2, y + L / 2.0, PIECE_R + 0.4, 2.76 * Math.PI, 1.24 * Math.PI);
        p.lineTo(x, y);
        p.closePath();
        return p;
    }

    /**
     * Canvas 的 arc 与 Java2D Arc2D 在 y 轴向下的用户空间中使用同一套角度参数化
     * （θ 从 +x 轴起算，θ 增大视觉上顺时针），可直接换算：终点角-起始角=extent，负值表示逆时针。
     */
    private static void appendArc(Path2D.Double p, double cx, double cy, double r,
                                  double from, double to) {
        double start = Math.toDegrees(from);
        double extent = Math.toDegrees(to - from);
        p.append(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, Arc2D.OPEN), true);
    }

    /** 程序化生成背景：随机双色渐变 + 半透明圆形斑点，不依赖外部图片资源 */
    private static BufferedImage drawBackground() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, pastel(rnd), WIDTH, HEIGHT, pastel(rnd)));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        for (int i = 0; i < 18; i++) {
            g.setColor(new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256),
                    rnd.nextInt(30, 110)));
            int d = rnd.nextInt(24, 96);
            g.fillOval(rnd.nextInt(-d / 2, WIDTH), rnd.nextInt(-d / 2, HEIGHT), d, d);
        }
        g.dispose();
        return img;
    }

    private static Color pastel(ThreadLocalRandom rnd) {
        return new Color(rnd.nextInt(100, 256), rnd.nextInt(100, 256), rnd.nextInt(100, 256));
    }

    private static String toBase64Png(BufferedImage img) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成滑块验证码图片失败", e);
        }
    }
}
