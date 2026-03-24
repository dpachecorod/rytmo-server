package com.rytmo.models.yield

import com.fasterxml.jackson.annotation.JsonProperty

data class YieldBytecodeResponse(
    @JsonProperty("feeCharged") val feeCharged: String,
    @JsonProperty("metadata") val metadata: YieldBytecodeMetadata,
    @JsonProperty("bytecode") val bytecode: List<YieldBytecodeStep>,
)
