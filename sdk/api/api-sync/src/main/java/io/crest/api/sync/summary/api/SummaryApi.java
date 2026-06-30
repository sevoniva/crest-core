package io.crest.api.sync.summary.api;

import io.crest.auth.CrestApiPath;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import static io.crest.constant.AuthResourceEnum.SUMMARY;

/**
 * 数据同步概览统计接口。
 */
@CrestApiPath(value = "/sync/summary", rt = SUMMARY)
public interface SummaryApi {

    @GetMapping("/resource-count")
    Map<String, Long> resourceCount();

    @PostMapping("/log-chart-data")
    Map<String, Object> logChartData(@RequestBody String executeTaskLogDate);


}
