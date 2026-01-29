package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.repositories.ClientRepository;

@ApplicationScoped
public class ClientService extends BaseService<Client, ClientRepository> {

  // Environment variable names
  private static final String ENV_STREAM_USE_REDIRECT = "STREAM_USE_REDIRECT";
  private static final String ENV_STREAM_USE_REDIRECT_XMLTV = "STREAM_USE_REDIRECT_XMLTV";

  @Inject ClientRepository repository;

  @Override
  protected ClientRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Client client) {
    if (client.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (client.getUsername() == null || client.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username is required");
    }
    if (client.getPassword() == null || client.getPassword().isBlank()) {
      throw new IllegalArgumentException("Password is required");
    }
    if (client.getIsActive() == null) {
      client.setIsActive(true);
    }
    return repository.insert(client);
  }

  /** Search clients by name/email/username */
  public List<Client> searchClients(String search, int page, int limit) {
    return repository.searchClients(search, page, limit);
  }

  /** Count clients matching search criteria */
  public Long countSearchClients(String search) {
    return repository.countSearchClients(search);
  }

  /**
   * Check if client has useRedirect configuration
   *
   * @param client The client
   * @return true if useRedirect is explicitly set (not null)
   */
  public boolean hasUseRedirectConfig(Client client) {
    return client != null && client.getUseRedirect() != null;
  }

  /**
   * Check if client has useRedirectXmltv configuration
   *
   * @param client The client
   * @return true if useRedirectXmltv is explicitly set (not null)
   */
  public boolean hasUseRedirectXmltvConfig(Client client) {
    return client != null && client.getUseRedirectXmltv() != null;
  }

  /**
   * Resolve useRedirect setting with priority: client -> source -> environment variable Uses
   * STREAM_USE_REDIRECT environment variable
   *
   * @param client The client
   * @param source The source
   * @return true if redirect should be used
   */
  public boolean resolveUseRedirect(Client client, Source source) {
    // Priority 1: Client-level setting
    if (hasUseRedirectConfig(client)) {
      return client.getUseRedirect();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getUseRedirect() != null) {
      return source.getUseRedirect();
    }

    // Priority 3: Environment variable
    String envValue = System.getenv(ENV_STREAM_USE_REDIRECT);
    if (envValue != null) {
      return Boolean.parseBoolean(envValue);
    }

    // Default: false (use proxy)
    return false;
  }

  /**
   * Resolve useRedirectXmltv setting with priority: client -> source -> environment variable Uses
   * STREAM_USE_REDIRECT_XMLTV environment variable
   *
   * @param client The client
   * @param source The source
   * @return true if redirect should be used for XMLTV
   */
  public boolean resolveUseRedirectXmltv(Client client, Source source) {
    // Priority 1: Client-level setting
    if (hasUseRedirectXmltvConfig(client)) {
      return client.getUseRedirectXmltv();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getUseRedirectXmltv() != null) {
      return source.getUseRedirectXmltv();
    }

    // Priority 3: Environment variable
    String envValue = System.getenv(ENV_STREAM_USE_REDIRECT_XMLTV);
    if (envValue != null) {
      return Boolean.parseBoolean(envValue);
    }

    // Default: false (stream content)
    return false;
  }

  /**
   * Resolve isActive setting with priority: client -> source. Default: true
   *
   * @param client The client
   * @param source The source
   * @return true if client/source is active
   */
  public boolean resolveIsActive(Client client, Source source) {
    // Priority 1: Client-level setting
    if (client != null && client.getIsActive() != null) {
      return client.getIsActive();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getIsActive() != null) {
      return source.getIsActive();
    }

    // Default: true
    return true;
  }

  /**
   * Resolve hideAdultContent setting. Note: Only exists on Client level Default: false
   *
   * @param client The client
   * @return true if adult content should be hidden
   */
  public boolean resolveHideAdultContent(Client client) {
    // Only client-level setting exists
    if (client != null && client.getHideAdultContent() != null) {
      return client.getHideAdultContent();
    }

    // Default: false
    return false;
  }

  /**
   * Resolve enableProxy setting with priority: client -> source. Default: true
   *
   * @param client The client
   * @param source The source
   * @return true if proxy should be enabled
   */
  public boolean resolveEnableProxy(Client client, Source source) {
    // Priority 1: Client-level setting
    if (client != null && client.getEnableProxy() != null) {
      return client.getEnableProxy();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getEnableProxy() != null) {
      return source.getEnableProxy();
    }

    // Default: true
    return true;
  }

  /**
   * Resolve disableStreamProxy setting with priority: client -> source. Default: false
   *
   * @param client The client
   * @param source The source
   * @return true if stream proxy should be disabled
   */
  public boolean resolveDisableStreamProxy(Client client, Source source) {
    // Priority 1: Client-level setting
    if (client != null && client.getDisableStreamProxy() != null) {
      return client.getDisableStreamProxy();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getDisableStreamProxy() != null) {
      return source.getDisableStreamProxy();
    }

    // Default: false
    return false;
  }

  /**
   * Resolve streamFollowLocation setting with priority: client -> source. Default: false
   *
   * @param client The client
   * @param source The source
   * @return true if HTTP redirect following should be enabled for streams
   */
  public boolean resolveStreamFollowLocation(Client client, Source source) {
    // Priority 1: Client-level setting
    if (client != null && client.getStreamFollowLocation() != null) {
      return client.getStreamFollowLocation();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getStreamFollowLocation() != null) {
      return source.getStreamFollowLocation();
    }

    // Default: false
    return false;
  }
}
