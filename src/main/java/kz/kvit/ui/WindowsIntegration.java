package kz.kvit.ui;

import javafx.stage.Stage;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Two small Windows API calls made with Java's built-in Foreign Function API (no extra libraries):
 * a window title bar colored to match the app theme, and reading the system "Animation effects" setting.
 * Both are cosmetic: on failure or on other systems the app simply keeps the defaults.
 */
public final class WindowsIntegration {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;
    private static final int SPI_GETCLIENTAREAANIMATION = 0x1042;

    // Windows COLORREF is 0x00BBGGRR. Dark: #18181a / #f2f2f3, light: #f4f4f5 / #18181b.
    private static final int DARK_CAPTION = 0x001A1818;
    private static final int DARK_CAPTION_TEXT = 0x00F3F2F2;
    private static final int LIGHT_CAPTION = 0x00F5F4F4;
    private static final int LIGHT_CAPTION_TEXT = 0x001B1818;

    private WindowsIntegration() {
    }

    /** Colors the title bar to match the theme. Must be called after stage.show(), when the native window exists. */
    public static void applyTitleBar(Stage stage, boolean dark) {
        if (!IS_WINDOWS) {
            return;
        }
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle findWindow = linker.downcallHandle(
                    SymbolLookup.libraryLookup("user32", arena).find("FindWindowW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle setAttribute = linker.downcallHandle(
                    SymbolLookup.libraryLookup("dwmapi", arena).find("DwmSetWindowAttribute").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            MemorySegment title = arena.allocateFrom(stage.getTitle(), StandardCharsets.UTF_16LE);
            MemorySegment window = (MemorySegment) findWindow.invokeExact(MemorySegment.NULL, title);
            if (window.address() == 0) {
                return;
            }
            setIntAttribute(setAttribute, arena, window, DWMWA_USE_IMMERSIVE_DARK_MODE, dark ? 1 : 0);
            // Windows 11 only; Windows 10 just returns an error code that we can ignore.
            setIntAttribute(setAttribute, arena, window, DWMWA_CAPTION_COLOR, dark ? DARK_CAPTION : LIGHT_CAPTION);
            setIntAttribute(setAttribute, arena, window, DWMWA_TEXT_COLOR, dark ? DARK_CAPTION_TEXT : LIGHT_CAPTION_TEXT);
        } catch (Throwable e) {
            System.err.println("Dark title bar is not available: " + e);
        }
    }

    /** Windows Settings → Accessibility → Visual effects → "Animation effects". */
    public static boolean areAnimationsEnabled() {
        if (!IS_WINDOWS) {
            return true;
        }
        Linker linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle systemParametersInfo = linker.downcallHandle(
                    SymbolLookup.libraryLookup("user32", arena).find("SystemParametersInfoW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment result = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) systemParametersInfo.invokeExact(SPI_GETCLIENTAREAANIMATION, 0, result, 0);
            return ok == 0 || result.get(ValueLayout.JAVA_INT, 0) != 0;
        } catch (Throwable e) {
            System.err.println("Cannot read the animation setting: " + e);
            return true;
        }
    }

    private static void setIntAttribute(MethodHandle setAttribute, Arena arena, MemorySegment window,
                                        int attribute, int value) throws Throwable {
        MemorySegment valueSegment = arena.allocateFrom(ValueLayout.JAVA_INT, value);
        int result = (int) setAttribute.invokeExact(window, attribute, valueSegment, Integer.BYTES);
        if (result != 0) {
            System.err.println("DwmSetWindowAttribute(" + attribute + ") returned " + Integer.toHexString(result));
        }
    }
}
