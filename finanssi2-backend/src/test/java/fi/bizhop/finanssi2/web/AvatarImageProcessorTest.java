package fi.bizhop.finanssi2.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AvatarImageProcessorTest {
    final AvatarImageProcessor processor = new AvatarImageProcessor();

    @Test
    void pngIsCroppedResizedAndEncodedAsBoundedJpegDataUrl() throws Exception {
        var input = image(600, 300, BufferedImage.TYPE_INT_RGB, Color.BLUE, "png");
        var result = processor.process(new MockMultipartFile("file", "photo.png", "image/png", input));
        assertTrue(result.startsWith("data:image/jpeg;base64,"));
        var bytes = Base64.getDecoder().decode(result.substring(result.indexOf(',') + 1));
        assertTrue(bytes.length <= 64 * 1024);
        var output = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertEquals(256, output.getWidth());
        assertEquals(256, output.getHeight());
        assertTrue((output.getRGB(128, 128) & 0x0000FF) > 240);
    }

    @Test
    void transparentPixelsAreFlattenedOnNeutralBackground() throws Exception {
        var transparent = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        var input = encode(transparent, "png");
        var result = processor.process(new MockMultipartFile("file", "clear.png", "image/png", input));
        var bytes = Base64.getDecoder().decode(result.substring(result.indexOf(',') + 1));
        var output = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        var pixel = new Color(output.getRGB(128, 128));
        assertTrue(Math.abs(pixel.getRed() - pixel.getGreen()) < 4);
        assertTrue(Math.abs(pixel.getGreen() - pixel.getBlue()) < 4);
        assertTrue(pixel.getRed() > 220);
    }

    @Test
    void malformedUnsupportedOversizedAndHugeImagesAreRejected() throws Exception {
        assertThrows(ResponseStatusException.class, () -> processor.process(new MockMultipartFile("file", "bad.png", "image/png", new byte[] {1, 2, 3})));
        assertThrows(ResponseStatusException.class, () -> processor.process(new MockMultipartFile("file", "vector.svg", "image/svg+xml", "<svg/>".getBytes())));
        var tooLarge = new byte[2 * 1024 * 1024 + 1];
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> processor.process(new MockMultipartFile("file", "big.png", "image/png", tooLarge)))
                .getStatusCode().value());
        var huge = image(4097, 1, BufferedImage.TYPE_INT_RGB, Color.BLACK, "png");
        assertThrows(ResponseStatusException.class, () -> processor.process(new MockMultipartFile("file", "huge.png", "image/png", huge)));
    }

    private static byte[] image(int width, int height, int type, Color color, String format) throws Exception {
        var image = new BufferedImage(width, height, type);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return encode(image, format);
    }

    private static byte[] encode(BufferedImage image, String format) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }
}
