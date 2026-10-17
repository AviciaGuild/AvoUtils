package info.avicia.avoutils.core.config;

import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * A whole-number setting edited with a slider and kept between {@code min} and {@code max}.
 *
 * @param suffix shown after the value on the config screen, e.g. "%"
 */
public record SliderSetting(
        ConfigSection section,
        String label,
        ToIntFunction<ModConfig> getter,
        ObjIntConsumer<ModConfig> setter,
        int min,
        int max,
        String suffix,
        ToggleSetting parent) implements Setting {

    public static SliderSetting of(ConfigSection section, String label, int min, int max, String suffix,
                                   ToIntFunction<ModConfig> getter, ObjIntConsumer<ModConfig> setter) {
        return new SliderSetting(section, label, getter, setter, min, max, suffix, null);
    }

    /** Only editable while {@code parent} is on. */
    public SliderSetting requires(ToggleSetting parent) {
        return new SliderSetting(section, label, getter, setter, min, max, suffix, parent);
    }

    public int get(ModConfig config) {
        return getter.applyAsInt(config);
    }

    public void set(ModConfig config, int value) {
        setter.accept(config, value);
    }

    /** The {@link ModConfig} field's initial value. */
    public int defaultValue() {
        return get(new ModConfig());
    }

    /** The value as shown on the config screen, e.g. "90%". */
    public String format(ModConfig config) {
        return get(config) + suffix;
    }

    @Override
    public void validate(ModConfig config) {
        int value = get(config);
        if (value < min || value > max) {
            set(config, defaultValue());
        }
    }
}

