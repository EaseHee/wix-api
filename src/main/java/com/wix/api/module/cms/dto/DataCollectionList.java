package com.wix.api.module.cms.dto;

import java.util.List;

import com.wix.api.common.PagingMetadata;
import lombok.Data;

@Data
public class DataCollectionList {

    private List<DataCollection> collections;
    private PagingMetadata pagingMetadata;
}
