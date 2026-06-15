package dev.cyr1en.promptui;

/**
 * Marker interface for dialog-based screens. Reserved for future use; no
 * current {@link ScreenProvider} implementation creates dialogs because Paper
 * has a native Dialog API that does not require a custom screen wrapper.
 *
 * <p>Kept in the SPI for forward compatibility.
 */
public interface DialogScreen extends InputScreen {
}
