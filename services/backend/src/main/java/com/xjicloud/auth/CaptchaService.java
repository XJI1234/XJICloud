package com.xjicloud.auth;

import com.xjicloud.common.BusinessException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private static final String REDIS_PREFIX = "xjicloud:captcha:";
    private static final String RATE_PREFIX = "xjicloud:captcha-rate:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration RATE_WINDOW = Duration.ofSeconds(60);
    private static final int RATE_LIMIT = 20;
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 4;
    private static final int IMG_WIDTH = 140;
    private static final int IMG_HEIGHT = 52;

    /** Atomically get and delete captcha value (compatible with Redis without GETDEL). */
    private static final DefaultRedisScript<String> GET_DEL_SCRIPT = new DefaultRedisScript<>(
            """
            local v = redis.call('GET', KEYS[1])
            if v then
              redis.call('DEL', KEYS[1])
            end
            return v
            """,
            String.class
    );

    /**
     * Increment rate counter; set TTL only on first hit so the window never becomes permanent.
     * Returns the count after increment.
     */
    private static final DefaultRedisScript<Long> RATE_INCR_SCRIPT = new DefaultRedisScript<>(
            """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CaptchaResponse generate(String clientIp) {
        enforceRateLimit(clientIp);

        String code = randomCode();
        String captchaKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(REDIS_PREFIX + captchaKey, code, TTL);

        String base64Image = renderBase64(code);
        return new CaptchaResponse(captchaKey, "data:image/png;base64," + base64Image);
    }

    public boolean validate(String captchaKey, String code) {
        if (captchaKey == null || code == null) {
            return false;
        }
        String key = REDIS_PREFIX + captchaKey;
        String expected = redisTemplate.execute(GET_DEL_SCRIPT, List.of(key));
        if (expected == null) {
            return false;
        }
        return expected.equalsIgnoreCase(code.trim());
    }

    private void enforceRateLimit(String clientIp) {
        String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        String rateKey = RATE_PREFIX + ip;
        Long count = redisTemplate.execute(
                RATE_INCR_SCRIPT,
                List.of(rateKey),
                String.valueOf(RATE_WINDOW.toSeconds())
        );
        if (count != null && count > RATE_LIMIT) {
            throw new BusinessException("验证码请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    private String renderBase64(String code) {
        BufferedImage image = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(20, 24, 32));
        g.fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT);

        g.setColor(new Color(155, 142, 200, 80));
        for (int i = 0; i < 60; i++) {
            int x = random.nextInt(IMG_WIDTH);
            int y = random.nextInt(IMG_HEIGHT);
            g.fillOval(x, y, 2, 2);
        }

        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(94, 196, 196, 60 + random.nextInt(60)));
            int x1 = random.nextInt(IMG_WIDTH / 3);
            int y1 = random.nextInt(IMG_HEIGHT);
            int x2 = IMG_WIDTH - random.nextInt(IMG_WIDTH / 3);
            int y2 = random.nextInt(IMG_HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        int charWidth = IMG_WIDTH / CODE_LENGTH;
        for (int i = 0; i < CODE_LENGTH; i++) {
            String ch = String.valueOf(code.charAt(i));
            int fontSize = 26 + random.nextInt(8);
            Font font = new Font("SansSerif", Font.BOLD, fontSize);
            g.setFont(font);

            int r = 94 + random.nextInt(60);
            int gr = 196 - random.nextInt(40);
            int b = 196 + random.nextInt(30);
            g.setColor(new Color(r, gr, b));

            double angle = (random.nextDouble() - 0.5) * 0.4;
            int x = i * charWidth + 12 + random.nextInt(6);
            int y = IMG_HEIGHT / 2 + fontSize / 3 + random.nextInt(6) - 3;

            g.rotate(angle, x + charWidth / 4.0, y);
            g.drawString(ch, x, y);
            g.rotate(-angle, x + charWidth / 4.0, y);
        }

        g.dispose();

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }

    public record CaptchaResponse(String captchaKey, String captchaImage) {}
}
