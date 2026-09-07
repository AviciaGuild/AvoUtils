package info.avicia.avoutils.features.emojis.animation;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodes animated GIF files into individual AnimationFrames with compositing and timing.
 */
public final class GifDecoder {

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private GifDecoder() {}

    public static boolean isGif(byte[] data) {
        if (data == null || data.length < 6) return false;
        return (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' &&
                data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a');
    }

    public static List<AnimationFrame> decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return List.of();
        }

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available");
        }

        ImageReader reader = readers.next();
        List<AnimationFrame> frames = new ArrayList<>();

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(stream, false);
            int numFrames = reader.getNumImages(true);
            if (numFrames == 0) {
                return frames;
            }

            int canvasWidth = 0;
            int canvasHeight = 0;
            if (data != null && data.length >= 10) {
                canvasWidth = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
                canvasHeight = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
            }
            if (canvasWidth <= 0 || canvasHeight <= 0) {
                canvasWidth = reader.getWidth(0);
                canvasHeight = reader.getHeight(0);
            }
            canvasWidth = Math.max(1, canvasWidth);
            canvasHeight = Math.max(1, canvasHeight);

            BufferedImage master = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D masterGraphics = master.createGraphics();
            try {
                masterGraphics.setBackground(TRANSPARENT);
                BufferedImage prevMaster = null;

                for (int i = 0; i < numFrames; i++) {
                    BufferedImage rawFrame = reader.read(i);
                    if (rawFrame == null) {
                        continue;
                    }
                    IIOMetadata metadata = reader.getImageMetadata(i);

                    int x = 0;
                    int y = 0;
                    int delayMs = 100;
                    String disposalMethod = "none";

                    if (metadata != null) {
                        try {
                            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
                            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                                if ("ImageDescriptor".equalsIgnoreCase(node.getNodeName())) {
                                    NamedNodeMap attrs = node.getAttributes();
                                    Node left = attrs.getNamedItem("imageLeftPosition");
                                    Node top = attrs.getNamedItem("imageTopPosition");
                                    if (left != null) x = Integer.parseInt(left.getNodeValue());
                                    if (top != null) y = Integer.parseInt(top.getNodeValue());
                                } else if ("GraphicControlExtension".equalsIgnoreCase(node.getNodeName())) {
                                    NamedNodeMap attrs = node.getAttributes();
                                    Node delay = attrs.getNamedItem("delayTime");
                                    Node disposal = attrs.getNamedItem("disposalMethod");
                                    if (delay != null) {
                                        int hundredths = Integer.parseInt(delay.getNodeValue());
                                        delayMs = (hundredths <= 1 ? 10 : hundredths) * 10;
                                    }
                                    if (disposal != null) {
                                        disposalMethod = disposal.getNodeValue();
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    x = Math.max(0, x);
                    y = Math.max(0, y);

                    int reqW = Math.max(master.getWidth(), x + rawFrame.getWidth());
                    int reqH = Math.max(master.getHeight(), y + rawFrame.getHeight());
                    if (reqW > master.getWidth() || reqH > master.getHeight()) {
                        BufferedImage expanded = new BufferedImage(reqW, reqH, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D expG = expanded.createGraphics();
                        try {
                            expG.drawImage(master, 0, 0, null);
                        } finally {
                            expG.dispose();
                        }
                        masterGraphics.dispose();
                        master = expanded;
                        masterGraphics = master.createGraphics();
                        masterGraphics.setBackground(TRANSPARENT);
                    }

                    if ("restoreToPrevious".equalsIgnoreCase(disposalMethod)) {
                        prevMaster = copyImage(master);
                    }

                    masterGraphics.drawImage(rawFrame, x, y, null);
                    frames.add(new AnimationFrame(copyImage(master), delayMs));

                    if ("restoreToBackgroundColor".equalsIgnoreCase(disposalMethod)) {
                        Composite orig = masterGraphics.getComposite();
                        masterGraphics.setComposite(AlphaComposite.Clear);
                        masterGraphics.fillRect(x, y, rawFrame.getWidth(), rawFrame.getHeight());
                        masterGraphics.setComposite(orig);
                    } else if ("restoreToPrevious".equalsIgnoreCase(disposalMethod) && prevMaster != null) {
                        master = copyImage(prevMaster);
                        masterGraphics.dispose();
                        masterGraphics = master.createGraphics();
                        masterGraphics.setBackground(TRANSPARENT);
                    }
                }
            } finally {
                masterGraphics.dispose();
            }
        } finally {
            reader.dispose();
        }

        return frames;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }
}
