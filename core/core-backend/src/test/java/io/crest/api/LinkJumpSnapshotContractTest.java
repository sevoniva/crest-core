package io.crest.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LinkJumpSnapshotContractTest {

    private static final Path REPO = findRepoRoot();

    @Test
    void jumpTargetViewListUsesSnapshotInEditor() throws IOException {
        String api = read("sdk/api/api-base/src/main/java/io/crest/api/visualization/VisualizationLinkJumpApi.java");
        String frontendApi = read("core/core-frontend/src/api/visualization/linkJump.ts");
        String linkJumpSet = read("core/core-frontend/src/components/visualization/LinkJumpSet.vue");
        String service = read("core/core-backend/src/main/java/io/crest/visualization/server/VisualizationLinkJumpService.java");
        String mapper = read("core/core-backend/src/main/resources/mybatis/ExtVisualizationLinkJumpMapper.xml");

        assertThat(api)
                .contains("/view-table-detail-list/{dvId}/{resourceTable}")
                .contains("@PathVariable(required = false) String resourceTable");
        assertThat(frontendApi).contains("viewTableDetailList(dvId, resourceTable = 'snapshot')");
        assertThat(linkJumpSet)
                .contains("viewTableDetailList(dvId, 'snapshot')")
                .contains("rsp.data.visualizationViewTables || []");
        assertThat(service).contains("StringUtils.defaultIfBlank(resourceTable, CommonConstants.RESOURCE_TABLE.SNAPSHOT)");
        assertThat(mapper)
                .contains("id=\"getViewTableDetailsSnapshot\"")
                .contains("core_chart_view_snapshot")
                .contains("core_visualization_snapshot")
                .contains("id=\"queryOutParamsTargetWithDvIdSnapshot\"")
                .contains("core_visualization_parameter_item_snapshot");
    }

    private static String read(String path) throws IOException {
        return Files.readString(REPO.resolve(path));
    }

    private static Path findRepoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.exists(path.resolve("sdk/api/api-base/src/main/java/io/crest/api/visualization/VisualizationLinkJumpApi.java"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
