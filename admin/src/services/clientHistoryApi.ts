import api from './api';

/** A single entry in the client's in-memory stream-watch history (enriched by the server). */
export interface StreamHistoryEntry {
  /** External stream ID as seen in the request URL (e.g. "12345"). */
  streamId: string;
  /** Stream type: LIVE, VOD, or SERIES. */
  streamType: 'LIVE' | 'VOD' | 'SERIES';
  /**
   * Human-readable stream name resolved from the source catalogue at query time.
   * Null if the stream is no longer in the catalogue.
   */
  streamName: string | null;
  /**
   * Category name resolved from the source catalogue at query time.
   * Null if the category is no longer in the catalogue.
   */
  categoryName: string | null;
  /** ISO-8601 timestamp when this session started (first call). */
  start: string;
  /**
   * ISO-8601 timestamp of the last call within the update window,
   * or null if only one call was recorded for this session.
   */
  end: string | null;
}

/** Response envelope returned by GET /api/clients/{id}/history */
export interface ClientHistoryResponse {
  data: StreamHistoryEntry[];
  total: number;
}

class ClientHistoryApi {
  /**
   * Fetch the in-memory watch history for a client.
   * Entries are ordered most-recent-first.
   */
  async getHistory(clientId: number): Promise<{ data: ClientHistoryResponse }> {
    const response = await api.get<{ data: ClientHistoryResponse }>(
      `/clients/${clientId}/history`
    );
    return response.data;
  }

  /**
   * Clear all watch-history entries for a client.
   * Returns 204 No Content on success.
   */
  async clearHistory(clientId: number): Promise<void> {
    await api.delete(`/clients/${clientId}/history`);
  }
}

export const clientHistoryApi = new ClientHistoryApi();
export default clientHistoryApi;


