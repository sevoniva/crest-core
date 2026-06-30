package io.crest.visualization.server;

import io.crest.api.visualization.vo.VisualizationViewTableVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationLinkJumpServiceTest {

    @Test
    void componentViewIdsReadsVisibleChartsFromNestedCanvasData() {
        String componentData = """
                [
                  {"component":"UserView","innerType":"bar","id":"12"},
                  {"component":"UserView","innerType":"VQuery","id":"13"},
                  {"component":"Tabs","propValue":[{"componentData":[{"component":"UserView","id":"112"}]}]},
                  {"component":"Group","propValue":[{"component":"UserView","id":"212"}]}
                ]
                """;

        assertThat(VisualizationLinkJumpService.componentViewIds(componentData))
                .isEqualTo(Set.of("12", "112", "212"));
    }

    @Test
    void filterViewTablesDoesNotMatchIdSubstrings() {
        VisualizationViewTableVO view12 = view(12L);
        VisualizationViewTableVO view112 = view(112L);
        String componentData = """
                [{"component":"UserView","id":"112"}]
                """;

        assertThat(VisualizationLinkJumpService.filterViewTables(componentData, List.of(view12, view112)))
                .containsExactly(view112);
    }

    @Test
    void filterViewTablesDoesNotFallbackToTextSearchWhenNoViewIdsAreParsed() {
        VisualizationViewTableVO view12 = view(12L);
        String componentData = """
                [{"component":"Decoration","text":"chart 12"}]
                """;

        assertThat(VisualizationLinkJumpService.filterViewTables(componentData, List.of(view12))).isEmpty();
    }

    private static VisualizationViewTableVO view(Long id) {
        VisualizationViewTableVO view = new VisualizationViewTableVO();
        view.setId(id);
        return view;
    }
}
