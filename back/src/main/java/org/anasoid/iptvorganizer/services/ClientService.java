package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.repositories.ClientRepository;

@ApplicationScoped
public class ClientService extends BaseService<Client, ClientRepository> {

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
   * Resolve connectXtreamApi setting with priority: client -> source -> default
   *
   * @param client The client
   * @param source The source
   * @return Resolved ConnectXtreamApiMode
   */
  public ConnectXtreamApiMode resolveConnectXtreamApi(Client client, Source source) {
    // Priority 1: Client-level setting (if not INHERITED)
    if (client != null
        && client.getConnectXtreamApi() != null
        && client.getConnectXtreamApi() != ClientConnectXtreamApiMode.INHERITED) {
      return client.getConnectXtreamApi().toSourceMode().resolve();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getConnectXtreamApi() != null) {
      return source.getConnectXtreamApi().resolve();
    }

    // Default
    return ConnectXtreamApiMode.DEFAULT.resolve();
  }

  /**
   * Resolve connectXtreamStream setting with priority: client -> source -> inherit from API
   *
   * @param client The client
   * @param source The source
   * @return Resolved ConnectXtreamStreamMode
   */
  public ConnectXtreamStreamMode resolveConnectXtreamStream(Client client, Source source) {
    // Get resolved API mode for DEFAULT resolution
    ConnectXtreamApiMode resolvedApiMode = resolveConnectXtreamApi(client, source);
    // Priority 1: Client-level setting (if not INHERITED)
    if (client != null
        && client.getConnectXtreamStream() != null
        && client.getConnectXtreamStream() != ClientConnectXtreamStreamMode.INHERITED) {
      return client.getConnectXtreamStream().toSourceMode();
    }
    // Priority 2: Source-level setting
    if (source != null && source.getConnectXtreamStream() != null) {
      return source.getConnectXtreamStream();
    }
    // Default: resolve to API mode
    return ConnectXtreamStreamMode.DEFAULT;
  }

  /**
   * Resolve connectXmltv setting with priority: client -> source -> inherit from stream
   *
   * @param client The client
   * @param source The source
   * @return Resolved ConnectXmltvMode
   */
  public ConnectXmltvMode resolveConnectXmltv(Client client, Source source) {
    // Get resolved stream mode for DEFAULT resolution
    ConnectXtreamStreamMode resolvedStreamMode = resolveConnectXtreamStream(client, source);

    // Priority 1: Client-level setting (if not INHERITED)
    if (client != null
        && client.getConnectXmltv() != null
        && client.getConnectXmltv() != ClientConnectXmltvMode.INHERITED) {
      return client.getConnectXmltv().toSourceMode();
    }

    // Priority 2: Source-level setting
    if (source != null && source.getConnectXmltv() != null) {
      return source.getConnectXmltv();
    }

    // Default: resolve to stream mode
    return ConnectXmltvMode.DEFAULT;
  }

  /**
   * Resolve isActive setting with priority: client -> source. Default: true
   *
   * @param client The client
   * @param source The source
   * @return true if client/source is active
   */
  public boolean resolveIsActive(Client client, Source source) {
    boolean clientActive = true;
    if (client != null && client.getIsActive() != null) {
      clientActive = client.getIsActive();
    }

    boolean sourceActive = true;
    if (source != null && source.getIsActive() != null) {
      sourceActive = source.getIsActive();
    }

    // Default: true
    return clientActive && sourceActive;
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
}
