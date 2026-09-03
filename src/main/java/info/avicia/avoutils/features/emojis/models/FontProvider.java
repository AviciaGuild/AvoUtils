package info.avicia.avoutils.features.emojis.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for an individual provider within a Minecraft font configuration.
 */
public class FontProvider {
    public String type = "bitmap";
    public String file;
    public int ascent = 7;
    public int height = 8;
    public List<String> chars = new ArrayList<>();
}

