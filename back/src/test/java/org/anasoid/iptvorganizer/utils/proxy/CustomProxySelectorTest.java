package org.anasoid.iptvorganizer.utils.proxy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;
import org.junit.jupiter.api.Test;

/** Unit tests for CustomProxySelector – verifies component-based and URL-based proxy resolution. */
class CustomProxySelectorTest {

  // ---------------------------------------------------------------------------
  // Null / missing config guard
  // ---------------------------------------------------------------------------

  @Test
  void testNullProxy_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> new CustomProxySelector(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void testEmptyProxy_NeitherHostNorUrl_ThrowsIllegalArgument() {
    Proxy proxy = Proxy.builder().build();

    assertThatThrownBy(() -> new CustomProxySelector(proxy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("proxyHost")
        .hasMessageContaining("proxyUrl");
  }

  @Test
  void testOnlyHostWithoutPort_FallsBackToUrl_ThrowsWhenUrlAlsoMissing() {
    // proxyPort is null → condition fails → tries proxyUrl which is also null → throws
    Proxy proxy = Proxy.builder().proxyHost("proxy.example.com").build();

    assertThatThrownBy(() -> new CustomProxySelector(proxy))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testOnlyPortWithoutHost_FallsBackToUrl_ThrowsWhenUrlAlsoMissing() {
    // proxyHost is null → condition fails → tries proxyUrl which is also null → throws
    Proxy proxy = Proxy.builder().proxyPort(8080).build();

    assertThatThrownBy(() -> new CustomProxySelector(proxy))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------------------
  // Component-based: proxyHost + proxyPort (+ optional proxyType)
  // ---------------------------------------------------------------------------

  @Test
  void testComponentBased_HttpType_SelectReturnsHttpProxy() {
    Proxy proxy =
        Proxy.builder()
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .proxyType(ProxyType.HTTP)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    List<java.net.Proxy> result = selector.select(URI.create("http://target.example.com"));

    assertThat(result).hasSize(1);
    java.net.Proxy p = result.get(0);
    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.HTTP);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getHostString()).isEqualTo("proxy.example.com");
    assertThat(addr.getPort()).isEqualTo(8080);
  }

  @Test
  void testComponentBased_HttpsType_MapsToHttpProxyType() {
    // HTTPS proxy still uses java.net.Proxy.Type.HTTP tunnelling
    Proxy proxy =
        Proxy.builder()
            .proxyHost("proxy.example.com")
            .proxyPort(8443)
            .proxyType(ProxyType.HTTPS)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("https://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.HTTP);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getPort()).isEqualTo(8443);
  }

  @Test
  void testComponentBased_Socks5Type_MapsToSocksProxyType() {
    Proxy proxy =
        Proxy.builder()
            .proxyHost("socks.example.com")
            .proxyPort(1080)
            .proxyType(ProxyType.SOCKS5)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("http://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.SOCKS);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getHostString()).isEqualTo("socks.example.com");
    assertThat(addr.getPort()).isEqualTo(1080);
  }

  @Test
  void testComponentBased_NullProxyType_DefaultsToHttp() {
    // proxyType not set → should default to HTTP, not throw
    Proxy proxy =
        Proxy.builder().proxyHost("proxy.example.com").proxyPort(3128).proxyType(null).build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("http://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.HTTP);
  }

  @Test
  void testComponentBased_TakesPrecedenceOverProxyUrl() {
    // Both component fields and proxyUrl present – component wins
    Proxy proxy =
        Proxy.builder()
            .proxyHost("component-host.example.com")
            .proxyPort(9090)
            .proxyType(ProxyType.HTTP)
            .proxyUrl("http://url-host.example.com:7070")
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    InetSocketAddress addr =
        (InetSocketAddress) selector.select(URI.create("http://any")).get(0).address();

    assertThat(addr.getHostString()).isEqualTo("component-host.example.com");
    assertThat(addr.getPort()).isEqualTo(9090);
  }

  // ---------------------------------------------------------------------------
  // URL-based: proxyUrl
  // ---------------------------------------------------------------------------

  @Test
  void testUrlBased_HttpScheme_CorrectHostPortType() {
    Proxy proxy = Proxy.builder().proxyUrl("http://proxy.example.com:8080").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("http://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.HTTP);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getHostString()).isEqualTo("proxy.example.com");
    assertThat(addr.getPort()).isEqualTo(8080);
  }

  @Test
  void testUrlBased_HttpScheme_DefaultPort8080WhenAbsent() {
    Proxy proxy = Proxy.builder().proxyUrl("http://proxy.example.com").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    InetSocketAddress addr =
        (InetSocketAddress) selector.select(URI.create("http://target")).get(0).address();

    assertThat(addr.getPort()).isEqualTo(8080);
  }

  @Test
  void testUrlBased_HttpsScheme_MapsToHttpProxyTypeDefaultPort443() {
    Proxy proxy = Proxy.builder().proxyUrl("https://proxy.example.com").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("https://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.HTTP);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getPort()).isEqualTo(443);
  }

  @Test
  void testUrlBased_Socks5Scheme_MapsToSocksProxyTypeDefaultPort1080() {
    Proxy proxy = Proxy.builder().proxyUrl("socks5://socks.example.com").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("http://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.SOCKS);
    InetSocketAddress addr = (InetSocketAddress) p.address();
    assertThat(addr.getHostString()).isEqualTo("socks.example.com");
    assertThat(addr.getPort()).isEqualTo(1080);
  }

  @Test
  void testUrlBased_SocksScheme_MapsToSocksProxyType() {
    Proxy proxy = Proxy.builder().proxyUrl("socks://socks.example.com:1081").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    java.net.Proxy p = selector.select(URI.create("http://target.example.com")).get(0);

    assertThat(p.type()).isEqualTo(java.net.Proxy.Type.SOCKS);
    assertThat(((InetSocketAddress) p.address()).getPort()).isEqualTo(1081);
  }

  @Test
  void testUrlBased_WithEmbeddedCredentials_HostAndPortParsedCorrectly() {
    // Credentials in URL are ignored by selector (handled by ProxyAuthenticator)
    // but host and port must still be resolved correctly
    Proxy proxy = Proxy.builder().proxyUrl("http://user:secret@proxy.example.com:3128").build();

    CustomProxySelector selector = new CustomProxySelector(proxy);
    InetSocketAddress addr =
        (InetSocketAddress) selector.select(URI.create("http://target")).get(0).address();

    assertThat(addr.getHostString()).isEqualTo("proxy.example.com");
    assertThat(addr.getPort()).isEqualTo(3128);
  }

  @Test
  void testUrlBased_InvalidUrl_ThrowsIllegalArgument() {
    Proxy proxy = Proxy.builder().proxyUrl("not-a-valid-url:::").build();

    assertThatThrownBy(() -> new CustomProxySelector(proxy))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testUrlBased_BlankUrl_ThrowsIllegalArgument() {
    Proxy proxy = Proxy.builder().proxyUrl("   ").build();

    assertThatThrownBy(() -> new CustomProxySelector(proxy))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------------------
  // select() contract
  // ---------------------------------------------------------------------------

  @Test
  void testSelect_AlwaysReturnsSingleProxy() {
    Proxy proxy =
        Proxy.builder()
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .proxyType(ProxyType.HTTP)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);

    // Call select with different target URIs – always the same configured proxy
    assertThat(selector.select(URI.create("http://foo.com"))).hasSize(1);
    assertThat(selector.select(URI.create("https://bar.com"))).hasSize(1);
    assertThat(selector.select(URI.create("ftp://baz.com"))).hasSize(1);
  }

  @Test
  void testSelect_ReturnsConsistentProxyAcrossCalls() {
    Proxy proxy =
        Proxy.builder()
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .proxyType(ProxyType.HTTP)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);

    java.net.Proxy first = selector.select(URI.create("http://target1.com")).get(0);
    java.net.Proxy second = selector.select(URI.create("http://target2.com")).get(0);

    assertThat(first).isEqualTo(second);
  }

  // ---------------------------------------------------------------------------
  // connectFailed() – must not throw
  // ---------------------------------------------------------------------------

  @Test
  void testConnectFailed_DoesNotThrow() {
    Proxy proxy =
        Proxy.builder()
            .proxyHost("proxy.example.com")
            .proxyPort(8080)
            .proxyType(ProxyType.HTTP)
            .build();

    CustomProxySelector selector = new CustomProxySelector(proxy);

    assertThatCode(
            () ->
                selector.connectFailed(
                    URI.create("http://target.example.com"),
                    new InetSocketAddress("proxy.example.com", 8080),
                    new IOException("connection refused")))
        .doesNotThrowAnyException();
  }
}
