package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationVO implements Serializable {

    private List<BucketVO> categories;
    private List<BucketVO> priceRanges;
}
