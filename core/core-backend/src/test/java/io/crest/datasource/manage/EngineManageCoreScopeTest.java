package io.crest.datasource.manage;

import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.exception.CrestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineManageCoreScopeTest {

    @Test
    void saveRejectsNonObOracleEngine() {
        EngineManage manage = new EngineManage();
        CoreEngine engine = new CoreEngine();
        engine.setType("mysql");

        assertThatThrownBy(() -> manage.save(engine))
                .isInstanceOf(CrestException.class)
                .hasMessageContaining("only supports obOracle");
    }
}
