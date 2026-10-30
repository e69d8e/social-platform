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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 滑块验证码图片生成器：缺口位置只在服务端，客户端拿到的是渲染好的图片。
 *
 * <p>几何约定（两种图共用同一套参数，改动必须前后端同步）：</p>
 * <ul>
 *   <li>背景 310×155；拼图方形主体边长 {@code SIZE = 65}，圆弧半径 {@code r = 10}。</li>
 *   <li>拼图块画布不再等于方形主体：形状四周有外凸/内凹圆弧，画布必须覆盖整个
 *       包围盒，否则拼图块的右侧凸起与底部会被切掉（曾经就是 65×65 直接裁剪，
 *       导致块与缺口对不上）。画布尺寸与方形主体的偏移量随响应一起下发。</li>
 *   <li>缺口 x 的取值区间保证「整个缺口形状」落在背景图内，不会贴边被裁。</li>
 * </ul>
 *
 * <p>前端 {@code SlideVerify.vue} 直接用 {@code blockWidth/blockHeight/blockOffsetX/blockOffsetY}
 * 定位，不再自己推导尺寸。</p>
 */
public final class SlideCaptchaImageUtil {

    public static final int WIDTH = 310;
    public static final int HEIGHT = 155;

    /** 拼图方形主体边长（对应前端老常量 l + r*2 + 3 中的主体部分） */
    private static final int SIZE = 65;
    /** 圆弧半径 */
    private static final int PIECE_R = 10;
    /** 描边抗锯齿留白 */
    private static final int EDGE_PAD = 3;

    /** 方形左上角位于原点的标准形状，用于计算画布包围盒 */
    private static final Shape UNIT_PATH = puzzlePath(0, 0);
    private static final Rectangle2D UNIT_BOUNDS = UNIT_PATH.getBounds2D();

    /** 方形左上角在拼图块画布内的偏移（左侧内凹圆弧需要留白） */
    private static final int BLOCK_OFFSET_X =
            (int) Math.ceil(-UNIT_BOUNDS.getMinX()) + EDGE_PAD;
    /** 方形左上角在拼图块画布内的纵向偏移（顶部外凸圆弧需要留白） */
    private static final int BLOCK_OFFSET_Y =
            (int) Math.ceil(-UNIT_BOUNDS.getMinY()) + EDGE_PAD;
    private static final int BLOCK_WIDTH =
            (int) Math.ceil(UNIT_BOUNDS.getWidth()) + BLOCK_OFFSET_X + EDGE_PAD;
    private static final int BLOCK_HEIGHT =
            (int) Math.ceil(UNIT_BOUNDS.getHeight()) + BLOCK_OFFSET_Y + EDGE_PAD;

    /** 缺口横向取值范围：左侧内凹圆弧最多外扩 ~3px，右侧凸起外扩 ~18px */
    private static final int X_MIN = 75;
    private static final int X_MAX = WIDTH - (int) Math.ceil(UNIT_BOUNDS.getMaxX()) - 4;
    private static final int Y_MIN = 30;
    private static final int Y_MAX = HEIGHT - SIZE - 10;

    /** 缺口填充色（半透明白），与前端无缺口时的视觉层次一致 */
    private static final Color GAP_FILL = new Color(255, 255, 255, 150);
    private static final Color GAP_STROKE = new Color(255, 255, 255, 220);
    /**
     * 圆角连接：缺口上沿是「直线 → 内凹圆弧」的尖角，
     * 默认的 miter 连接会在尖角处甩出 2~3px 的尖刺，
     * 既难看又会让拼图块顶到画布边缘被切掉。
     */
    private static final BasicStroke OUTLINE =
            new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private SlideCaptchaImageUtil() {
    }

    /**
     * @param x            缺口横向位置（即校验答案，不下发给客户端）
     * @param y            缺口纵向位置
     * @param bgImage      带缺口背景图 PNG base64
     * @param blockImage   拼图块 PNG base64（透明底，尺寸为 blockWidth×blockHeight）
     * @param blockWidth   拼图块画布宽
     * @param blockHeight  拼图块画布高
     * @param blockOffsetX 方形主体左边缘在画布内的 x
     * @param blockOffsetY 方形主体上边缘在画布内的 y
     * @param size         方形主体边长
     */
    public record CaptchaImage(int x, int y, String bgImage, String blockImage,
                               int blockWidth, int blockHeight,
                               int blockOffsetX, int blockOffsetY, int size) {
    }

    public static CaptchaImage generate() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x = rnd.nextInt(X_MIN, X_MAX + 1);
        int y = rnd.nextInt(Y_MIN, Y_MAX + 1);

        // 1. 先在「干净背景」上把缺口像素抠出来，作为拼图块的内容；
        //    顺序不能反：先画缺口再取块，块会被白色蒙层洗白，拼回去也对不上原图
        BufferedImage background = drawBackground();
        BufferedImage block = new BufferedImage(BLOCK_WIDTH, BLOCK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = block.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape piecePath = puzzlePath(BLOCK_OFFSET_X, BLOCK_OFFSET_Y);
        bg.setClip(piecePath);
        bg.translate(BLOCK_OFFSET_X - x, BLOCK_OFFSET_Y - y);
        bg.drawImage(background, 0, 0, null);
        bg.translate(x - BLOCK_OFFSET_X, y - BLOCK_OFFSET_Y);
        bg.setClip(null);
        bg.setColor(GAP_STROKE);
        bg.setStroke(OUTLINE);
        bg.draw(piecePath);
        bg.dispose();

        // 2. 再在主图上挖缺口（半透明白填充 + 描边），提示用户目标位置
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape gapPath = puzzlePath(x, y);
        g.setColor(GAP_FILL);
        g.fill(gapPath);
        g.setColor(GAP_STROKE);
        g.setStroke(OUTLINE);
        g.draw(gapPath);
        g.dispose();

        return new CaptchaImage(x, y, toBase64Png(background), toBase64Png(block),
                BLOCK_WIDTH, BLOCK_HEIGHT, BLOCK_OFFSET_X, BLOCK_OFFSET_Y, SIZE);
    }

    /**
     * 拼图路径，逐句对应前端滑动组件的历史绘制逻辑：
     * 方形主体 (x,y)-(x+SIZE,y+SIZE)，顶部内凹圆弧、右侧外凸圆弧、左侧内凹圆弧。
     */
    private static Shape puzzlePath(double x, double y) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y);
        appendArc(p, x + SIZE / 2.0, y - PIECE_R + 2, PIECE_R, 0.72 * Math.PI, 2.26 * Math.PI);
        p.lineTo(x + SIZE, y);
        appendArc(p, x + SIZE + PIECE_R - 2, y + SIZE / 2.0, PIECE_R, 1.21 * Math.PI, 2.78 * Math.PI);
        p.lineTo(x + SIZE, y + SIZE);
        p.lineTo(x, y + SIZE);
        appendArc(p, x + PIECE_R - 2, y + SIZE / 2.0, PIECE_R + 0.4, 2.76 * Math.PI, 1.24 * Math.PI);
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
