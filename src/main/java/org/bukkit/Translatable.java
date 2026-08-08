package org.bukkit;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an object with a text representation that can be translated by the
 * Minecraft client.
 */
public interface Translatable {

    /**
     * Get the translation key, suitable for use in a translation component.
     *
     * @return the translation key
     * @deprecated look for a {@code translationKey()} method instead
     */
    @NotNull
    String getTranslationKey();
}