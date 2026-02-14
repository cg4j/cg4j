package net.cg4j;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the CG4j ASCII art banner with version information at startup.
 */
public class Banner {

  private static final Logger logger = LogManager.getLogger(Banner.class);

  private static final String TAGLINE = "CG4j: Call Graph Generation for Java";

  private static final String[] LOGO_LINES = {
      "   ______________ __  _ ",
      "  / ____/ ____/ // / (_)",
      " / /   / / __/ // /_/ / ",
      "/ /___/ /_/ /__  __/ /  ",
      "\\____/\\____/  /_/_/ /   ",
      "               /___/    "
  };

  private static final int LOGO_WIDTH = LOGO_LINES[0].length();

  private Banner() {
    // Utility class
  }

  /**
   * Prints the ASCII art banner with version information to the logger.
   */
  public static void print() {
    for (String line : buildBannerLines()) {
      logger.info("{}", line);
    }
  }

  /**
   * Builds the full banner as a list of lines. Package-private for testing.
   */
  static List<String> buildBannerLines() {
    String version = Version.getInstance().getFullVersion();

    // Box inner width: tagline + 4 chars padding (2 each side)
    int boxInnerWidth = TAGLINE.length() + 4;
    int boxOuterWidth = boxInnerWidth + 2;
    String border = "+" + "-".repeat(boxInnerWidth) + "+";

    // Center logo lines over the box
    int logoPadding = Math.max(0, (boxOuterWidth - LOGO_WIDTH) / 2);
    String logoIndent = " ".repeat(logoPadding);

    List<String> lines = new ArrayList<>();
    lines.add("");
    for (String line : LOGO_LINES) {
      lines.add(logoIndent + line);
    }
    lines.add(border);
    lines.add("|" + centerText(TAGLINE, boxInnerWidth) + "|");
    lines.add("|" + centerText(version, boxInnerWidth) + "|");
    lines.add(border);
    lines.add("");

    return lines;
  }

  /**
   * Centers text within a given width, padding with spaces.
   */
  private static String centerText(String text, int width) {
    if (text.length() >= width) {
      return text;
    }
    int totalPadding = width - text.length();
    int leftPadding = totalPadding / 2;
    int rightPadding = totalPadding - leftPadding;
    return " ".repeat(leftPadding) + text + " ".repeat(rightPadding);
  }
}
