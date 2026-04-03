package com.wix.api.module.cms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wix.api.common.PagingRequest;
import com.wix.api.common.WixQueryFilter;
import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.cms.dto.*;
import org.springframework.web.client.RestClient;

public class CmsService extends AbstractWixService {

    public CmsService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    // --- Data Collections ---

    public DataCollectionList listDataCollections(PagingRequest paging) {
        return retryHandler.executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/wix-data/v2/collections")
                                .queryParam("paging.limit", paging.getLimit())
                                .queryParam("paging.offset", paging.getOffset())
                                .build())
                        .retrieve()
                        .body(DataCollectionList.class)
        );
    }

    public DataCollection getDataCollection(String collectionId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.get()
                    .uri("/wix-data/v2/collections/{collectionId}", collectionId)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "collection", DataCollection.class);
        });
    }

    public DataCollection createDataCollection(DataCollection collection) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/wix-data/v2/collections")
                    .body(Map.of("collection", collection))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "collection", DataCollection.class);
        });
    }

    public DataCollection updateDataCollection(String collectionId, DataCollection collection) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.patch()
                    .uri("/wix-data/v2/collections/{collectionId}", collectionId)
                    .body(Map.of("collection", collection))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "collection", DataCollection.class);
        });
    }

    public void deleteDataCollection(String collectionId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.delete()
                        .uri("/wix-data/v2/collections/{collectionId}", collectionId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    // --- Data Items ---

    public DataItem getDataItem(String collectionId, String itemId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.get()
                    .uri("/wix-data/v2/collections/{collectionId}/items/{itemId}", collectionId, itemId)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "dataItem", DataItem.class);
        });
    }

    public DataItemList queryDataItems(String collectionId, WixQueryFilter query) {
        Map<String, Object> body = new HashMap<>();
        body.put("dataCollectionId", collectionId);
        if (query != null) {
            Map<String, Object> queryMap = new HashMap<>();
            if (query.getFilter() != null) queryMap.put("filter", query.getFilter());
            if (query.getSort() != null) queryMap.put("sort", query.getSort());
            if (query.getPaging() != null) {
                queryMap.put("paging", Map.of(
                        "limit", query.getPaging().getLimit(),
                        "offset", query.getPaging().getOffset()));
            }
            if (query.getFields() != null) queryMap.put("fields", query.getFields());
            body.put("query", queryMap);
        }
        body.put("returnTotalCount", true);

        return retryHandler.executeWithRetry(() ->
                restClient.post()
                        .uri("/wix-data/v2/items/query")
                        .body(body)
                        .retrieve()
                        .body(DataItemList.class)
        );
    }

    public DataItem insertDataItem(String collectionId, Map<String, Object> data) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/wix-data/v2/collections/{collectionId}/items", collectionId)
                    .body(Map.of("dataItem", Map.of("data", data)))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "dataItem", DataItem.class);
        });
    }

    public DataItem updateDataItem(String collectionId, String itemId, Map<String, Object> data) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.put()
                    .uri("/wix-data/v2/collections/{collectionId}/items/{itemId}", collectionId, itemId)
                    .body(Map.of("dataItem", Map.of("data", data)))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "dataItem", DataItem.class);
        });
    }

    public void removeDataItem(String collectionId, String itemId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.delete()
                        .uri("/wix-data/v2/collections/{collectionId}/items/{itemId}", collectionId, itemId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public List<DataItem> bulkInsertDataItems(String collectionId, List<Map<String, Object>> items) {
        List<Map<String, Object>> dataItems = items.stream()
                .map(data -> Map.<String, Object>of("data", data))
                .toList();

        return retryHandler.executeWithRetry(() -> {
            DataItemList response = restClient.post()
                    .uri("/wix-data/v2/collections/{collectionId}/items/bulk", collectionId)
                    .body(Map.of("dataItems", dataItems))
                    .retrieve()
                    .body(DataItemList.class);
            return response != null ? response.getDataItems() : List.of();
        });
    }

    public void bulkRemoveDataItems(String collectionId, List<String> itemIds) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.post()
                        .uri("/wix-data/v2/collections/{collectionId}/items/bulk/remove", collectionId)
                        .body(Map.of("dataItemIds", itemIds))
                        .retrieve()
                        .toBodilessEntity()
        );
    }
}
