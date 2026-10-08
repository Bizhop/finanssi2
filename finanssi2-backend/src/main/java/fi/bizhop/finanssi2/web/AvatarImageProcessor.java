package fi.bizhop.finanssi2.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

@Component
public class AvatarImageProcessor {
    static final int MAX_INPUT_BYTES = 2 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final int OUTPUT_DIMENSION = 256;
    static final int MAX_JPEG_BYTES = 64 * 1024;

    public String process(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Choose a JPEG or PNG image");
        if (file.getSize() > MAX_INPUT_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar input must be at most 2 MiB");
        try {
            var bytes = file.getBytes();
            if (bytes.length > MAX_INPUT_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar input must be at most 2 MiB");
            var image = decode(bytes);
            var jpeg = encodeJpeg(centerCrop(image));
            if (jpeg.length > MAX_JPEG_BYTES) throw invalid("Image could not be compressed below 64 KiB");
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        } catch (IOException error) {
            throw invalid("Invalid image file");
        }
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Only JPEG and PNG images are supported");
            var reader = readers.next();
            try {
                var format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!format.equals("JPEG") && !format.equals("JPG") && !format.equals("PNG"))
                    throw invalid("Only JPEG and PNG images are supported");
                reader.setInput(input, true, true);
                var width = reader.getWidth(0);
                var height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION)
                    throw invalid("Image dimensions must not exceed 4096 × 4096");
                var image = reader.read(0);
                if (image == null) throw invalid("Invalid image file");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage centerCrop(BufferedImage source) {
        var side = Math.min(source.getWidth(), source.getHeight());
        var left = (source.getWidth() - side) / 2;
        var top = (source.getHeight() - side) / 2;
        var image = new BufferedImage(OUTPUT_DIMENSION, OUTPUT_DIMENSION, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xF2F2F2));
            graphics.fillRect(0, 0, OUTPUT_DIMENSION, OUTPUT_DIMENSION);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, OUTPUT_DIMENSION, OUTPUT_DIMENSION, left, top, left + side, top + side, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        for (float quality : new float[] {0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f}) {
            var writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) throw new IOException("JPEG writer unavailable");
            var writer = writers.next();
            var bytes = new ByteArrayOutputStream();
            try (var output = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(output);
                var parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
                writer.write(null, new IIOImage(image, null, null), parameters);
                output.flush();
            } finally {
                writer.dispose();
            }
            var encoded = bytes.toByteArray();
            if (encoded.length <= MAX_JPEG_BYTES) return encoded;
        }
        throw invalid("Image could not be compressed below 64 KiB");
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
