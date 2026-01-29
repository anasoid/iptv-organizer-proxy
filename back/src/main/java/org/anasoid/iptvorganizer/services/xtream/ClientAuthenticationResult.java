package org.anasoid.iptvorganizer.services.xtream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;

/**
 * Result of client authentication and validation
 *
 * <p>Holds the authenticated client and associated source after successful validation.
 */
@Getter
@AllArgsConstructor
public class ClientAuthenticationResult {
  private final Client client;
  private final Source source;
}
