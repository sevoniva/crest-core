package io.crest.dataset.sync;

import io.crest.commons.constants.TaskStatus;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.dataset.sync.queue.DatasetSyncTaskQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetSyncManageTest {

    @Test
    void scheduledExecuteDoesNotRequeueStoppedTaskInQueueMode() {
        DatasetSyncManage manage = new DatasetSyncManage();
        DatasetSyncTaskManage taskManage = mock(DatasetSyncTaskManage.class);
        DatasetSyncTaskQueueService queueService = mock(DatasetSyncTaskQueueService.class);
        ReflectionTestUtils.setField(manage, "taskManage", taskManage);
        ReflectionTestUtils.setField(manage, "queueService", queueService);

        CoreDatasetSyncTask task = new CoreDatasetSyncTask();
        task.setId(1262155861866975232L);
        task.setDatasetGroupId(982004L);
        task.setTaskStatus(TaskStatus.Stopped.name());
        when(taskManage.selectById(task.getId())).thenReturn(task);
        when(queueService.enabled()).thenReturn(true);

        manage.execute(982004L, task.getId(), null);

        verify(taskManage, never()).markQueuedTaskEnqueued(anyLong(), anyLong(), anyLong());
        verify(queueService, never()).enqueueDatasetSyncTask(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), anyLong());
    }
}
