package com.squareup.okhttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Android has disabled SSLv3 by default in AOSP. This socket factory re-enables it if needed.
 */
public class AndroidSafeSSLSocketFactory extends DelegatingSSLSocketFactory {

  public AndroidSafeSSLSocketFactory(SSLSocketFactory delegate) {
    super(delegate);
  }

  @Override
  protected void configureSocket(SSLSocket sslSocket) throws IOException {
    List<String> protocols = Arrays.asList(sslSocket.getEnabledProtocols());
    if (!protocols.contains("SSLv3")) {
      protocols = new ArrayList<String>(protocols);
      protocols.add("SSLv3");
      sslSocket.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
    }
  }
}
