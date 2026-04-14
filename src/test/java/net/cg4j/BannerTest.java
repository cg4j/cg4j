package net.cg4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import org.junit.jupiter.api.Test;

class BannerTest {

  /** Unit test: Verifies Banner.print() completes without throwing any exception. */
  @Test
  void testPrint_DoesNotThrow() {
    assertThatNoException().isThrownBy(Banner::print);
  }

  /** Unit test: Verifies the banner contains the CG4j tagline. */
  @Test
  void testBuildBannerLines_ContainsTagline() {
    List<String> lines = Banner.buildBannerLines();

    String output = String.join("\n", lines);
    assertThat(output).contains("CG4j: Call Graph Generation for Java");
  }

  /** Unit test: Verifies the banner contains version information. */
  @Test
  void testBuildBannerLines_ContainsVersion() {
    List<String> lines = Banner.buildBannerLines();

    String output = String.join("\n", lines);
    String version = Version.getInstance().getFullVersion();
    assertThat(output).contains(version);
  }

  /** Unit test: Verifies the banner contains the ASCII art logo. */
  @Test
  void testBuildBannerLines_ContainsLogo() {
    List<String> lines = Banner.buildBannerLines();

    String output = String.join("\n", lines);
    assertThat(output).contains("/ ____/ ____/");
    assertThat(output).contains("/___/");
  }

  /** Unit test: Verifies the banner contains the dashed box border. */
  @Test
  void testBuildBannerLines_ContainsBox() {
    List<String> lines = Banner.buildBannerLines();

    String output = String.join("\n", lines);
    assertThat(output).contains("+--");
    assertThat(output).contains("--+");
  }
}
