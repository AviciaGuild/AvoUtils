package info.avicia.avoutils.features.emojis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.emojis.animation.AnimatedImageDecoder;
import info.avicia.avoutils.features.emojis.animation.AnimationFrame;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the custom emoji images in the pack in line with {@code custom_emojis.json}: reads the list, drops the images
 * of removed emojis, downloads new or changed ones, and caches their animation data and source URLs.
 */
final class EmojiDownloader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final Path packDir;
    private final Map<String, AnimationCacheEntry> animationCache = new ConcurrentHashMap<>();
    private final Path animationCachePath;
    private final Path manifestPath;

    public record AnimationCacheEntry(int frameCount, int[] frameDelays) {
    }

    public record EmojiManifest(String hash, Map<String, String> urls) {
    }

    /**
     * What a load found: the emojis whose images are ready, sorted by name, and whether any file changed.
     * {@code interrupted} means the load stopped before saving anything.
     */
    record LoadResult(List<String> readyEmojis, boolean changed, boolean interrupted) {
    }

    private record EmojiList(Map<String, String> emojis, String hash) {
    }

    private record Downloads(Map<String, String> currentUrls, Set<String> readyEmojis, boolean anyNewImage) {
    }

    EmojiDownloader(Path packDir) {
        this.packDir = packDir;
        this.animationCachePath = packDir.resolve("assets/avoutils/animations.json");
        this.manifestPath = packDir.resolve("assets/avoutils/manifest.json");
    }

    Map<String, AnimationCacheEntry> animationCache() {
        return animationCache;
    }

    Path manifestPath() {
        return manifestPath;
    }

    /**
     * Brings the images in line with the list: read the list, drop removed images, download what changed, save the
     * caches. Returns null if the list is empty or can't be read.
     */
    LoadResult loadAndCache() throws IOException {
        loadAnimationCache();
        EmojiList list = readEmojiList();
        if (list.emojis().isEmpty()) {
            return null;
        }

        Path texturesDir = packDir.resolve("assets/avoutils/textures/font");
        Files.createDirectories(texturesDir);

        EmojiManifest existingManifest = loadManifest();
        boolean manifestChanged = existingManifest == null || !Objects.equals(list.hash(), existingManifest.hash());
        Map<String, String> cachedUrls = (existingManifest != null && existingManifest.urls() != null)
                ? existingManifest.urls()
                : Collections.emptyMap();

        boolean removedAny = dropRemovedImages(list.emojis(), cachedUrls, texturesDir);
        Downloads downloads = downloadChanged(list.emojis(), cachedUrls, texturesDir);

        if (Thread.currentThread().isInterrupted()) {
            return new LoadResult(List.of(), false, true);
        }

        if (downloads.anyNewImage() || manifestChanged) {
            saveManifest(new EmojiManifest(list.hash(), downloads.currentUrls()));
            saveAnimationCache();
        }

        List<String> sortedNames = new ArrayList<>(downloads.readyEmojis());
        Collections.sort(sortedNames);
        return new LoadResult(sortedNames, removedAny || downloads.anyNewImage() || manifestChanged, false);
    }

    /** The emoji list shipped in the mod's resources, and its hash. */
    private EmojiList readEmojiList() {
        Map<String, String> allEmojis = new HashMap<>();
        String currentHash = "";

        try (InputStream is = EmojiDownloader.class.getResourceAsStream("/assets/avoutils/custom_emojis.json")) {
            if (is != null) {
                byte[] rawBytes = is.readAllBytes();
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    currentHash = HexFormat.of().formatHex(digest.digest(rawBytes));
                } catch (Exception ignored) {
                }

                try (InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(rawBytes), StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> setEmojis = GSON.fromJson(isr, type);
                    if (setEmojis != null) {
                        allEmojis.putAll(setEmojis);
                    }
                }
            } else {
                AvoUtilsMod.LOGGER.error("[AvoUtils] custom_emojis.json not found in mod resources!");
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to load custom emojis from resources", e);
        }
        return new EmojiList(allEmojis, currentHash);
    }

    /** Clean up any old images that were removed from custom_emojis.json; true if there were any. */
    private boolean dropRemovedImages(Map<String, String> allEmojis, Map<String, String> cachedUrls, Path texturesDir) {
        boolean changed = false;
        for (String cachedName : new ArrayList<>(cachedUrls.keySet())) {
            if (!allEmojis.containsKey(cachedName)) {
                try {
                    Files.deleteIfExists(texturesDir.resolve(EmojiPackWriter.safeNameFor(cachedName) + ".png"));
                } catch (Exception ignored) {
                }
                animationCache.remove(cachedName);
                changed = true;
            }
        }
        return changed;
    }

    /** Downloads the images that are missing, changed or malformed, eight at a time. */
    private Downloads downloadChanged(Map<String, String> allEmojis, Map<String, String> cachedUrls, Path texturesDir) {
        Map<String, String> currentUrls = new ConcurrentHashMap<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>(allEmojis.entrySet());
        Set<String> downloadedEmojis = ConcurrentHashMap.newKeySet();
        AtomicBoolean anyNewImage = new AtomicBoolean(false);
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(8);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Map.Entry<String, String> entry : entries) {
                futures.add(CompletableFuture.runAsync(() -> {
                    String emojiName = entry.getKey();
                    String imageUrl = entry.getValue();
                    String safeName = EmojiPackWriter.safeNameFor(emojiName);
                    Path imagePath = texturesDir.resolve(safeName + ".png");

                    try {
                        boolean needsProcessing = false;
                        BufferedImage existingImg = null;

                        boolean urlChanged = cachedUrls.containsKey(emojiName)
                                && !Objects.equals(cachedUrls.get(emojiName), imageUrl);

                        if (!Files.exists(imagePath) || urlChanged) {
                            needsProcessing = true;
                        } else {
                            try {
                                existingImg = ImageIO.read(imagePath.toFile());
                                if (existingImg == null || existingImg.getWidth() != 32 || existingImg.getHeight() % 32 != 0) {
                                    needsProcessing = true;
                                } else if (imageUrl.contains(".gif") && (!animationCache.containsKey(emojiName) || existingImg.getHeight() == 32)) {
                                    needsProcessing = true;
                                } else if (animationCache.containsKey(emojiName) && existingImg.getHeight() != animationCache.get(emojiName).frameCount() * 32) {
                                    needsProcessing = true;
                                }
                            } catch (Exception e) {
                                needsProcessing = true;
                            }
                        }

                        if (needsProcessing) {
                            try {
                                AnimationCacheEntry anim = processAndSaveEmojiImage(imageUrl, imagePath);
                                if (anim != null) {
                                    animationCache.put(emojiName, anim);
                                } else {
                                    animationCache.remove(emojiName);
                                }
                                anyNewImage.set(true);
                                currentUrls.put(emojiName, imageUrl);
                            } catch (IOException downloadEx) {
                                // Offline fallback: if image exists on disk but was non-square, normalize it locally
                                if (existingImg != null && (existingImg.getWidth() != 32 || existingImg.getHeight() % 32 != 0)) {
                                    BufferedImage normalized = AnimatedImageDecoder.scaleToTarget(existingImg, 32);
                                    Path temp = imagePath.resolveSibling(imagePath.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");
                                    try {
                                        ImageIO.write(normalized, "png", temp.toFile());
                                        Files.move(temp, imagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                                        anyNewImage.set(true);
                                        currentUrls.put(emojiName, imageUrl);
                                    } finally {
                                        try {
                                            Files.deleteIfExists(temp);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                } else {
                                    throw downloadEx;
                                }
                            }
                        } else {
                            currentUrls.put(emojiName, imageUrl);
                            if (existingImg != null && existingImg.getHeight() > existingImg.getWidth()) {
                                int frameCount = existingImg.getHeight() / existingImg.getWidth();
                                if (!animationCache.containsKey(emojiName)) {
                                    int[] delays = new int[frameCount];
                                    Arrays.fill(delays, 100);
                                    animationCache.put(emojiName, new AnimationCacheEntry(frameCount, delays));
                                    anyNewImage.set(true);
                                }
                                if (AnimatedImageDecoder.normalizeFrameAdvances(existingImg, frameCount)) {
                                    Path temp = imagePath.resolveSibling(imagePath.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");
                                    try {
                                        ImageIO.write(existingImg, "png", temp.toFile());
                                        Files.move(temp, imagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                                        anyNewImage.set(true);
                                    } finally {
                                        try {
                                            Files.deleteIfExists(temp);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }
                        downloadedEmojis.add(emojiName);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to process emoji image for {} from {}",
                                emojiName, imageUrl, e);
                    }
                }, downloadExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            downloadExecutor.shutdown();
            try {
                downloadExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return new Downloads(currentUrls, downloadedEmojis, anyNewImage.get());
    }

    private void loadAnimationCache() {
        if (!Files.exists(animationCachePath)) {
            return;
        }
        try {
            String json = Files.readString(animationCachePath, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, AnimationCacheEntry>>() {}.getType();
            Map<String, AnimationCacheEntry> map = GSON.fromJson(json, type);
            if (map != null) {
                animationCache.putAll(map);
            }
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to load animation cache from {}", animationCachePath, e);
        }
    }

    private void saveAnimationCache() {
        Path temp = animationCachePath.resolveSibling("animations.json.tmp");
        try {
            Files.createDirectories(animationCachePath.getParent());
            String json = GSON.toJson(animationCache);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, animationCachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to save animation cache to {}", animationCachePath, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    EmojiManifest loadManifest() {
        if (!Files.exists(manifestPath)) {
            return null;
        }
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return GSON.fromJson(json, EmojiManifest.class);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to load emoji manifest from {}", manifestPath, e);
            return null;
        }
    }

    void saveManifest(EmojiManifest manifest) {
        Path temp = manifestPath.resolveSibling("manifest.json.tmp");
        try {
            Files.createDirectories(manifestPath.getParent());
            String json = GSON.toJson(manifest);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, manifestPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AvoUtilsMod.LOGGER.error("[AvoUtils] Failed to save emoji manifest to {}", manifestPath, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    private AnimationCacheEntry processAndSaveEmojiImage(String imageUrl, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AvoUtils Mod")
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode() + " for Image URL: " + imageUrl);
        }
        byte[] bytes;
        try (InputStream in = response.body()) {
            bytes = in.readAllBytes();
        }
        Files.createDirectories(destination.getParent());
        Path tempPath = destination.resolveSibling(destination.getFileName().toString() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");

        try {
            List<AnimationFrame> frames = AnimatedImageDecoder.decode(bytes);
            if (frames.isEmpty()) {
                throw new IOException("Failed to decode image data for " + imageUrl);
            }

            AnimationCacheEntry result;
            if (frames.size() > 1) {
                BufferedImage sheet = AnimatedImageDecoder.stitchVertically(frames);
                ImageIO.write(sheet, "png", tempPath.toFile());
                int[] delays = frames.stream().mapToInt(AnimationFrame::delayMs).toArray();
                result = new AnimationCacheEntry(frames.size(), delays);
            } else {
                ImageIO.write(frames.get(0).image(), "png", tempPath.toFile());
                result = null;
            }

            Files.move(tempPath, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return result;
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception ignored) {
            }
        }
    }
}

