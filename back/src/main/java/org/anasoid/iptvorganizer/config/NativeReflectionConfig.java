package org.anasoid.iptvorganizer.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.anasoid.iptvorganizer.cache.CacheStat;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.dto.RequestType;
import org.anasoid.iptvorganizer.dto.history.StreamHistoryEntryDto;
import org.anasoid.iptvorganizer.dto.request.LoginRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.ErrorDetails;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.dto.xtream.XtreamAuthResponse;
import org.anasoid.iptvorganizer.dto.xtream.XtreamCategory;
import org.anasoid.iptvorganizer.dto.xtream.XtreamServerInfo;
import org.anasoid.iptvorganizer.dto.xtream.XtreamStream;
import org.anasoid.iptvorganizer.dto.xtream.XtreamUserInfo;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.models.entity.BaseEntity;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.ConnectionLog;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream.AllowDenyStatus;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.Category.BlackListStatus;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.models.filtering.CategoryMatch;
import org.anasoid.iptvorganizer.models.filtering.ChannelMatch;
import org.anasoid.iptvorganizer.models.filtering.FilterAction;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterField;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.anasoid.iptvorganizer.models.filtering.MatchCriteria;
import org.anasoid.iptvorganizer.models.history.StreamHistoryEntry;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.models.http.ProxyOptions;
import org.anasoid.iptvorganizer.models.http.RedirectCheckResult;
import org.anasoid.iptvorganizer.models.monitor.JvmMetricsEntry;
import org.anasoid.iptvorganizer.services.DatabaseMaintenanceService;

/**
 * Registers all Jackson-serialised/deserialised types for GraalVM native-image reflection.
 *
 * <p>Because every JAX-RS controller returns the raw {@code Response} type, Quarkus cannot
 * auto-detect the entity types that Jackson needs to access at runtime via reflection. Without this
 * class, all entity/DTO fields are invisible in native mode and Jackson silently returns empty
 * objects ({@code {}}) instead of the real data.
 *
 * <p><strong>IMPORTANT:</strong> {@code ignoreNested = false} only applies to the <em>annotated
 * class itself</em> ({@code NativeReflectionConfig}), NOT to classes listed in {@code targets}.
 * Therefore every nested/inner class that Jackson needs (e.g. inner enums with {@code @JsonValue})
 * must be added to this list explicitly.
 */
@RegisterForReflection(
    targets = {
      // ── Response / DTO wrappers ──────────────────────────────────────────
      ApiResponse.class,
      ErrorDetails.class,
      PaginationMeta.class,
      CacheStat.class,
      DatabaseMaintenanceService.DatabaseShrinkResult.class,

      // ── Request DTOs ─────────────────────────────────────────────────────
      LoginRequest.class,
      HttpRequestDto.class,
      RequestType.class,
      StreamHistoryEntryDto.class,

      // ── Xtream API DTOs ───────────────────────────────────────────────────
      XtreamAuthResponse.class,
      XtreamCategory.class,
      XtreamServerInfo.class,
      XtreamStream.class,
      XtreamUserInfo.class,

      // ── Core entities ─────────────────────────────────────────────────────
      BaseEntity.class,
      Source.class,
      Client.class,
      Filter.class,
      Proxy.class,
      ProxyType.class,
      AdminUser.class,
      SyncLog.class,
      SyncLogStatus.class, // inner enum — must be listed explicitly
      ConnectionLog.class,

      // ── Stream entities ───────────────────────────────────────────────────
      BaseStream.class,
      AllowDenyStatus.class, // inner enum of BaseStream — must be listed explicitly
      SourcedEntity.class,
      Category.class,
      BlackListStatus.class, // inner enum of Category — must be listed explicitly
      LiveStream.class,
      VodStream.class,
      Series.class,
      StreamType.class,

      // ── Enums ─────────────────────────────────────────────────────────────
      ConnectXtreamApiMode.class,
      ConnectXtreamStreamMode.class,
      ConnectXmltvMode.class,
      ClientConnectXtreamApiMode.class,
      ClientConnectXtreamStreamMode.class,
      ClientConnectXmltvMode.class,

      // ── Filtering model ───────────────────────────────────────────────────
      FilterConfig.class,
      FilterRule.class,
      FilterAction.class,
      FilterField.class,
      CategoryMatch.class,
      ChannelMatch.class,
      MatchCriteria.class,

      // ── History / metrics ─────────────────────────────────────────────────
      StreamHistoryEntry.class,
      JvmMetricsEntry.class,

      // ── HTTP / proxy helpers ──────────────────────────────────────────────
      HttpOptions.class,
      HttpStreamingResponse.class,
      ProxyOptions.class,
      RedirectCheckResult.class,
    })
public class NativeReflectionConfig {}
