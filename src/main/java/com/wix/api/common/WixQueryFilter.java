package com.wix.api.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class WixQueryFilter {

    private Map<String, Object> filter;
    private List<Map<String, String>> sort;
    private PagingRequest paging;
    private CursorPagingRequest cursorPaging;
    private List<String> fields;

    public static WixQueryFilter create() {
        return new WixQueryFilter();
    }

    public WixQueryFilter eq(String field, Object value) {
        ensureFilter();
        filter.put(field, Map.of("$eq", value));
        return this;
    }

    public WixQueryFilter ne(String field, Object value) {
        ensureFilter();
        filter.put(field, Map.of("$ne", value));
        return this;
    }

    public WixQueryFilter gt(String field, Object value) {
        ensureFilter();
        filter.put(field, Map.of("$gt", value));
        return this;
    }

    public WixQueryFilter lt(String field, Object value) {
        ensureFilter();
        filter.put(field, Map.of("$lt", value));
        return this;
    }

    public WixQueryFilter contains(String field, String value) {
        ensureFilter();
        filter.put(field, Map.of("$contains", value));
        return this;
    }

    public WixQueryFilter startsWith(String field, String value) {
        ensureFilter();
        filter.put(field, Map.of("$startsWith", value));
        return this;
    }

    public WixQueryFilter in(String field, List<?> values) {
        ensureFilter();
        filter.put(field, Map.of("$in", values));
        return this;
    }

    public WixQueryFilter sortAsc(String field) {
        ensureSort();
        sort.add(Map.of("fieldName", field, "order", "ASC"));
        return this;
    }

    public WixQueryFilter sortDesc(String field) {
        ensureSort();
        sort.add(Map.of("fieldName", field, "order", "DESC"));
        return this;
    }

    public WixQueryFilter withPaging(PagingRequest paging) {
        this.paging = paging;
        return this;
    }

    public WixQueryFilter withCursorPaging(CursorPagingRequest cursorPaging) {
        this.cursorPaging = cursorPaging;
        return this;
    }

    public WixQueryFilter withFields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    private void ensureFilter() {
        if (filter == null) {
            filter = new HashMap<>();
        }
    }

    private void ensureSort() {
        if (sort == null) {
            sort = new ArrayList<>();
        }
    }
}
