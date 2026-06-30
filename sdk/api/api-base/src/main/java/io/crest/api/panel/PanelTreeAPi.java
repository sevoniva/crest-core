package io.crest.api.panel;

import io.crest.api.panel.vo.PanelTreeNodeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface PanelTreeAPi {

    @GetMapping("/tree/{keyword}")
    List<PanelTreeNodeVO> tree(@PathVariable("keyword") String keyword);


}
