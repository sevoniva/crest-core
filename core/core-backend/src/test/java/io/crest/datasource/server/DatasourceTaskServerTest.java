package io.crest.datasource.server;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceTaskServerTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void existUnderExecutionTaskUsesEnqueueTimeForQueuedWorkerClaim() {
        DatasourceTaskServer server = new DatasourceTaskServer();
        CoreDatasourceMapper datasourceMapper = mock(CoreDatasourceMapper.class);
        CoreDatasourceTaskMapper taskMapper = mock(CoreDatasourceTaskMapper.class);
        ReflectionTestUtils.setField(server, "coreDatasourceMapper", datasourceMapper);
        ReflectionTestUtils.setField(server, "datasourceTaskMapper", taskMapper);
        when(datasourceMapper.update(any(CoreDatasource.class), any(UpdateWrapper.class))).thenReturn(1);
        when(taskMapper.markQueuedWorkerStartedByEnqueueTime(eq(11L), eq("worker-a"), any(), eq(1781080000000L)))
                .thenReturn(1);

        assertFalse(server.existUnderExecutionTask(9L, 11L, "worker-a", 1781080000000L));

        verify(taskMapper).markQueuedWorkerStartedByEnqueueTime(eq(11L), eq("worker-a"), any(), eq(1781080000000L));
        verify(taskMapper, never()).markQueuedWorkerStarted(any(), any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void existUnderExecutionTaskRollsBackDatasourceWhenEnqueueTimeMismatch() {
        DatasourceTaskServer server = new DatasourceTaskServer();
        CoreDatasourceMapper datasourceMapper = mock(CoreDatasourceMapper.class);
        CoreDatasourceTaskMapper taskMapper = mock(CoreDatasourceTaskMapper.class);
        ReflectionTestUtils.setField(server, "coreDatasourceMapper", datasourceMapper);
        ReflectionTestUtils.setField(server, "datasourceTaskMapper", taskMapper);
        when(datasourceMapper.update(any(CoreDatasource.class), any(UpdateWrapper.class))).thenReturn(1);
        when(taskMapper.markQueuedWorkerStartedByEnqueueTime(eq(11L), eq("worker-a"), any(), eq(1781080000000L)))
                .thenReturn(0);

        assertTrue(server.existUnderExecutionTask(9L, 11L, "worker-a", 1781080000000L));

        verify(taskMapper).markQueuedWorkerStartedByEnqueueTime(eq(11L), eq("worker-a"), any(), eq(1781080000000L));
        verify(datasourceMapper, times(2)).update(any(CoreDatasource.class), any(UpdateWrapper.class));
    }
}
