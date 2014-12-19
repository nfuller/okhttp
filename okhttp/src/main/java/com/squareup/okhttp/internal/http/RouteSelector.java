package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.Route;

import java.io.IOException;

/**
 * Created by nfuller on 12/18/14.
 */
public interface RouteSelector {

  boolean hasNext();

  Route next() throws IOException;

  // TODO(nfuller): Now returns a boolean indicating retryable.
  boolean connectFailed(Connection connection, IOException failure);
}
