package io.crest.dataset.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetParameterFieldIdTest {

    @Test
    @DisplayName("数据集参数字段 ID 使用 Crest 运行标识")
    void buildShouldUseCrestRuntimeMarker() {
        String fieldId = DatasetParameterFieldId.build(982004L, "region");

        assertThat(fieldId).isEqualTo("982004|DATASET_PARAM|region");
        assertThat(DatasetParameterFieldId.matches(fieldId)).isTrue();
    }

    @Test
    @DisplayName("历史字段标识不再作为参数字段匹配")
    void matchesShouldRejectLegacyMarker() {
        assertThat(DatasetParameterFieldId.matches("982004|DE|region")).isFalse();
        assertThat(DatasetParameterFieldId.matches("982004DEregion")).isFalse();
        assertThat(DatasetParameterFieldId.matches(null)).isFalse();
    }
}
