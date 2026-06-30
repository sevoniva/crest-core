package io.crest.menu.manage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MenuManageCoreScopeTest {

    @Test
    @SuppressWarnings("unchecked")
    void internalLiteMenusOnlyExposeCoreProductionEntries() {
        Set<Long> menuIds = (Set<Long>) ReflectionTestUtils.getField(MenuManage.class, "INTERNAL_LITE_MENU_IDS");

        assertThat(menuIds)
                .contains(1L, 2L, 3L, 4L, 5L, 6L, 11L, 12L, 15L, 16L, 67L, 68L, 69L, 73L, 74L, 75L)
                .doesNotContain(19L, 30L, 31L, 64L, 66L, 70L, 71L, 76L, 77L, 78L, 80L, 90L);
    }
}
