
package io.antigen.core.api.dto;

import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
public class ContractFaultStrategyListResponse {
    
    private List<ContractFaultStrategyResponse> strategies;
    private Long total;
    private Integer page;
    private Integer size;

    public ContractFaultStrategyListResponse() {}
}